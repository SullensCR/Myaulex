package myau.management;

import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DelayManager {
    public static Minecraft mc = Minecraft.getMinecraft();
    private volatile DelayModules delayModule = DelayModules.NONE;
    private volatile long delay = 0L;
    private final Deque<DelayedPacket> delayedPacket = new ConcurrentLinkedDeque<>();

    public synchronized boolean shouldDelay(Packet<INetHandlerPlayClient> packet) {
        if (this.delayModule == DelayModules.NONE || mc.theWorld == null || mc.getNetHandler() == null) {
            return false;
        } else if (isImmediatePacket(packet)) {
            // Live player-list and world-state updates must not be replayed late.
            return false;
        } else if (!(packet instanceof S01PacketJoinGame) && !(packet instanceof S07PacketRespawn)) {
            if (packet instanceof S19PacketEntityStatus) {
                S19PacketEntityStatus s19 = (S19PacketEntityStatus) packet;
                Entity entity = s19.getEntity(mc.theWorld);
                if (entity != null && (!entity.equals(mc.thePlayer) || s19.getOpCode() != 2)) {
                    return false;
                }
            }
            this.delayedPacket.offer(new DelayedPacket(packet, mc.theWorld, mc.getNetHandler()));
            return true;
        } else {
            // Join/respawn packets mark a new play state. Queued packets belong
            // to the previous state and must not be replayed into the new world.
            this.clear();
            return false;
        }
    }

    public synchronized boolean setDelayState(boolean state, DelayModules delayModule) {
        if (state) {
            this.delay = 0;
            this.delayModule = delayModule;
        } else {
            this.delayModule = DelayModules.NONE;
            this.delay = 0L;
            INetHandlerPlayClient netHandler = Minecraft.getMinecraft().getNetHandler();
            if (netHandler == null || mc.theWorld == null) {
                this.delayedPacket.clear();
                return true;
            }
            while (true) {
                DelayedPacket delayed = this.delayedPacket.poll();
                if (delayed == null) {
                    this.delayedPacket.clear();
                    break;
                }
                if (mc.theWorld == delayed.world && netHandler == delayed.netHandler) {
                    delayed.packet.processPacket(netHandler);
                }
            }
        }
        return this.delayModule != DelayModules.NONE;
    }

    static boolean isImmediatePacket(Packet<?> packet) {
        return packet instanceof S00PacketKeepAlive
                || packet instanceof S08PacketPlayerPosLook
                || packet instanceof S38PacketPlayerListItem
                || packet instanceof S21PacketChunkData
                || packet instanceof S22PacketMultiBlockChange
                || packet instanceof S23PacketBlockChange
                || packet instanceof S26PacketMapChunkBulk;
    }

    /** Discards packets belonging to the world that is being unloaded. */
    public synchronized void clear() {
        this.delayModule = DelayModules.NONE;
        this.delay = 0L;
        this.delayedPacket.clear();
    }

    public DelayModules getDelayModule() {
        return this.delayModule;
    }

    public synchronized void delay(DelayModules modules) {
        this.delayModule = modules;
    }

    public long getDelay() {
        return this.delay;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.delayModule != DelayModules.NONE
                && (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse)) {
            this.clear();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer != null && mc.thePlayer.isDead) {
                this.setDelayState(false, this.delayModule);
            }
            if (this.delayModule != DelayModules.NONE) {
                this.delay++;
            }
        }
    }

    private static final class DelayedPacket {
        private final Packet<INetHandlerPlayClient> packet;
        private final WorldClient world;
        private final INetHandlerPlayClient netHandler;

        private DelayedPacket(
                Packet<INetHandlerPlayClient> packet,
                WorldClient world,
                INetHandlerPlayClient netHandler
        ) {
            this.packet = packet;
            this.world = world;
            this.netHandler = netHandler;
        }
    }
}

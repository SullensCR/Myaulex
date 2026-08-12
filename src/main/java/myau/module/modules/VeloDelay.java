package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Fixed-duration inbound blink used for delayed velocity handling. */
public final class VeloDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty delay = new IntProperty("delay-ms", 200, 50, 500);
    private final ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<>();
    private boolean replaying;
    private boolean discardOnDisable;

    public VeloDelay() {
        super("VeloDelay", false, false, "Delays inbound packets for a short velocity window.");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || replaying || event.getType() != EventType.RECEIVE
                || mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null
                || !shouldDelayInbound(event.getPacket())) return;
        event.setCancelled(true);
        queue.offer(new Entry(event.getPacket(), System.currentTimeMillis(), mc.theWorld, mc.getNetHandler()));
    }

    static boolean shouldDelayInbound(Packet<?> packet) {
        return !(packet instanceof S01PacketJoinGame)
                && !(packet instanceof S07PacketRespawn)
                && !(packet instanceof S08PacketPlayerPosLook)
                && !(packet instanceof S38PacketPlayerListItem)
                && !(packet instanceof S21PacketChunkData)
                && !(packet instanceof S22PacketMultiBlockChange)
                && !(packet instanceof S23PacketBlockChange)
                && !(packet instanceof S26PacketMapChunkBulk);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        long now = System.currentTimeMillis();
        Entry next;
        while ((next = queue.peek()) != null && now - next.created >= delay.getValue()) {
            queue.poll();
            replay(next);
        }
    }

    @EventTarget
    public void onWorldChange(LoadWorldEvent event) {
        discardOnDisable = true;
        queue.clear();
        if (isEnabled()) setEnabled(false);
    }

    @Override
    public void onDisabled() {
        if (discardOnDisable) {
            queue.clear();
            discardOnDisable = false;
            return;
        }
        Entry entry;
        while ((entry = queue.poll()) != null) replay(entry);
    }

    @SuppressWarnings("unchecked")
    private void replay(Entry entry) {
        if (mc.theWorld != entry.world || mc.getNetHandler() != entry.netHandler) return;
        replaying = true;
        try {
            ((Packet<INetHandlerPlayClient>) entry.packet).processPacket(entry.netHandler);
        } finally {
            replaying = false;
        }
    }

    private static final class Entry {
        private final Packet<?> packet;
        private final long created;
        private final WorldClient world;
        private final INetHandlerPlayClient netHandler;

        private Entry(Packet<?> packet, long created, WorldClient world, INetHandlerPlayClient netHandler) {
            this.packet = packet;
            this.created = created;
            this.world = world;
            this.netHandler = netHandler;
        }
    }
}

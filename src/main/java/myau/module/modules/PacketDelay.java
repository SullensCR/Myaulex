package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.PacketUtil;
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

/** Bidirectional, ordered play-packet delay with independently progressive directions. */
public final class PacketDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty inbound = new BooleanProperty("inbound", false);
    public final IntProperty inboundDelay = new IntProperty("inbound-delay-ms", 200, 50, 1000,
            inbound::getValue).childOf(inbound);
    public final BooleanProperty inboundProgressive = new BooleanProperty("inbound-progressive", false,
            inbound::getValue).childOf(inbound);
    public final BooleanProperty outbound = new BooleanProperty("outbound", true);
    public final IntProperty outboundDelay = new IntProperty("outbound-delay-ms", 200, 50, 1000,
            outbound::getValue).childOf(outbound);
    public final BooleanProperty outboundProgressive = new BooleanProperty("outbound-progressive", false,
            outbound::getValue).childOf(outbound);

    private final ConcurrentLinkedQueue<TimedPacket> inboundQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TimedPacket> outboundQueue = new ConcurrentLinkedQueue<>();
    private boolean replaying;
    private boolean discardOnDisable;
    private long progressiveStarted;

    public PacketDelay() {
        super("PacketDelay", false, false, "Delays inbound and outbound packets.");
    }

    @Override
    public void onEnabled() {
        clearQueues();
        discardOnDisable = false;
        progressiveStarted = System.currentTimeMillis();
    }

    @Override
    public void onDisabled() {
        if (discardOnDisable) {
            clearQueues();
            discardOnDisable = false;
        } else {
            flushQueues();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || replaying || mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) return;
        if (event.getType() == EventType.SEND && outbound.getValue()) {
            event.setCancelled(true);
            outboundQueue.offer(new TimedPacket(
                    event.getPacket(), System.currentTimeMillis(), mc.theWorld, mc.getNetHandler()));
        } else if (event.getType() == EventType.RECEIVE
                && inbound.getValue()
                && shouldDelayInbound(event.getPacket())) {
            event.setCancelled(true);
            inboundQueue.offer(new TimedPacket(
                    event.getPacket(), System.currentTimeMillis(), mc.theWorld, mc.getNetHandler()));
        }
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
        int elapsedSteps = (int) ((now - progressiveStarted) / 1000L);
        int inDelay = effectiveDelay(inboundDelay.getValue(), inboundProgressive.getValue(), elapsedSteps);
        int outDelay = effectiveDelay(outboundDelay.getValue(), outboundProgressive.getValue(), elapsedSteps);
        replayDue(inboundQueue, inDelay, true, now);
        replayDue(outboundQueue, outDelay, false, now);

        if (inbound.getValue() && inboundProgressive.getValue() && inDelay <= 0) inbound.setValue(false);
        if (outbound.getValue() && outboundProgressive.getValue() && outDelay <= 0) outbound.setValue(false);
        if (!inbound.getValue() && !outbound.getValue()) setEnabled(false);
    }

    @EventTarget
    public void onWorldChange(LoadWorldEvent event) {
        if (isEnabled()) {
            discardOnDisable = true;
            clearQueues();
            setEnabled(false);
        }
    }

    private int effectiveDelay(int configured, boolean progressive, int elapsedSteps) {
        return progressive ? Math.max(0, configured - elapsedSteps * 50) : configured;
    }

    private void replayDue(ConcurrentLinkedQueue<TimedPacket> queue, int delay, boolean incoming, long now) {
        TimedPacket next;
        while ((next = queue.peek()) != null && now - next.created >= delay) {
            queue.poll();
            replay(next, incoming);
        }
    }

    @SuppressWarnings("unchecked")
    private void replay(TimedPacket delayed, boolean incoming) {
        replaying = true;
        try {
            if (mc.theWorld != delayed.world || mc.getNetHandler() != delayed.netHandler) return;
            if (incoming) {
                ((Packet<INetHandlerPlayClient>) delayed.packet).processPacket(delayed.netHandler);
            } else {
                PacketUtil.sendPacketNoEvent(delayed.packet);
            }
        } finally {
            replaying = false;
        }
    }

    private void flushQueues() {
        TimedPacket packet;
        while ((packet = inboundQueue.poll()) != null) replay(packet, true);
        while ((packet = outboundQueue.poll()) != null) replay(packet, false);
    }

    private void clearQueues() {
        inboundQueue.clear();
        outboundQueue.clear();
        replaying = false;
    }

    private static final class TimedPacket {
        private final Packet<?> packet;
        private final long created;
        private final WorldClient world;
        private final INetHandlerPlayClient netHandler;

        private TimedPacket(Packet<?> packet, long created, WorldClient world, INetHandlerPlayClient netHandler) {
            this.packet = packet;
            this.created = created;
            this.world = world;
            this.netHandler = netHandler;
        }
    }
}

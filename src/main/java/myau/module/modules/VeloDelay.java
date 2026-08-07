package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Fixed-duration inbound blink used for delayed velocity handling. */
public final class VeloDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty delay = new IntProperty("delay-ms", 200, 50, 500);
    private final ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<>();
    private boolean replaying;

    public VeloDelay() {
        super("VeloDelay", false, false, "Delays inbound packets for a short velocity window.");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || replaying || event.getType() != EventType.RECEIVE
                || mc.thePlayer == null || mc.theWorld == null) return;
        event.setCancelled(true);
        queue.offer(new Entry(event.getPacket(), System.currentTimeMillis()));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        long now = System.currentTimeMillis();
        Entry next;
        while ((next = queue.peek()) != null && now - next.created >= delay.getValue()) {
            queue.poll();
            replay(next.packet);
        }
    }

    @EventTarget
    public void onWorldChange(LoadWorldEvent event) {
        queue.clear();
        if (isEnabled()) setEnabled(false);
    }

    @Override
    public void onDisabled() {
        Entry entry;
        while ((entry = queue.poll()) != null) replay(entry.packet);
    }

    @SuppressWarnings("unchecked")
    private void replay(Packet<?> packet) {
        if (mc.getNetHandler() == null) return;
        replaying = true;
        try {
            ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
        } finally {
            replaying = false;
        }
    }

    private static final class Entry {
        private final Packet<?> packet;
        private final long created;

        private Entry(Packet<?> packet, long created) {
            this.packet = packet;
            this.created = created;
        }
    }
}

package myau.events;

import myau.event.events.Event;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.Packet;

/**
 * Read-only notification emitted before inbound packet-delay modules can queue
 * a packet. Consumers must move any game-state work back to the client thread.
 */
public final class PacketArrivalEvent implements Event {
    private final Packet<?> packet;
    private final WorldClient world;
    private final long arrivalNanos;

    public PacketArrivalEvent(Packet<?> packet, WorldClient world, long arrivalNanos) {
        this.packet = packet;
        this.world = world;
        this.arrivalNanos = arrivalNanos;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public WorldClient getWorld() {
        return world;
    }

    public long getArrivalNanos() {
        return arrivalNanos;
    }
}

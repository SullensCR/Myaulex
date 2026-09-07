package myau.util;

import net.minecraft.network.Packet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Opt-in differential packet-order tracer for validating that refactors preserve
 * the exact sequence of packets seen by the network mixins. Enable with
 * {@code -Dmyau.packetTrace=true}; disabled by default and inert (single branch).
 *
 * Each line records a monotonically increasing sequence number plus a monotonic
 * nanosecond timestamp, so diffs prove strict ordering is unchanged.
 */
public final class PacketTrace {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-PacketTrace");
    private static final boolean ENABLED = Boolean.getBoolean("myau.packetTrace");
    private static long sequence;

    private PacketTrace() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void receive(Packet<?> packet) {
        if (ENABLED) {
            LOGGER.info("{} {} IN {}", ++sequence, System.nanoTime(), packet.getClass().getName());
        }
    }

    public static void send(Packet<?> packet) {
        if (ENABLED) {
            LOGGER.info("{} {} OUT {}", ++sequence, System.nanoTime(), packet.getClass().getName());
        }
    }
}
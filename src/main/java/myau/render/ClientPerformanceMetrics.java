package myau.render;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Low-overhead opt-in counters for comparing client render costs. Enable with
 * {@code -Dmyau.performanceMetrics=true}; normal clients perform no timing.
 */
public final class ClientPerformanceMetrics {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-Performance");
    private static final boolean ENABLED = Boolean.getBoolean("myau.performanceMetrics");
    private static final int REPORT_EVERY_FRAMES = 300;

    private static int frameCount;
    private static long entitySortNanos;
    private static int entitySortCalls;
    private static long backdropNanos;
    private static int backdropCaptures;
    private static long eventNanos;
    private static int eventDispatches;

    private ClientPerformanceMetrics() {
    }

    public static long start() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void recordEntitySort(long startedNanos) {
        if (!ENABLED) return;
        entitySortNanos += System.nanoTime() - startedNanos;
        entitySortCalls++;
    }

    public static void recordBackdropCapture(long startedNanos) {
        if (!ENABLED) return;
        backdropNanos += System.nanoTime() - startedNanos;
        backdropCaptures++;
    }

    public static void recordEventDispatch(long startedNanos) {
        if (!ENABLED) return;
        eventNanos += System.nanoTime() - startedNanos;
        eventDispatches++;
    }

    public static void onFrame() {
        if (!ENABLED || ++frameCount < REPORT_EVERY_FRAMES) return;
        LOGGER.info("Last {} frames: entitySort={} calls / {} ms, backdrop={} captures / {} ms, eventDispatch={} calls / {} ms",
                frameCount, entitySortCalls, millis(entitySortNanos), backdropCaptures, millis(backdropNanos),
                eventDispatches, millis(eventNanos));
        frameCount = 0;
        entitySortNanos = 0L;
        entitySortCalls = 0;
        backdropNanos = 0L;
        backdropCaptures = 0;
        eventNanos = 0L;
        eventDispatches = 0;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0D;
    }
}

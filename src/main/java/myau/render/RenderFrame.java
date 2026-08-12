package myau.render;

/**
 * Monotonic identifier for the client render loop. Rendering utilities use it
 * to safely reuse data only for the current visual frame.
 */
public final class RenderFrame {
    private static long id;

    private RenderFrame() {
    }

    public static long begin() {
        id++;
        ClientPerformanceMetrics.onFrame();
        return id;
    }

    public static long current() {
        return id;
    }

    public static void reset() {
        id = 0L;
    }
}

package myau.util;

/**
 * Core render behavior that should always be available instead of being toggled
 * through the module system.
 */
public final class RenderFixes {
    private RenderFixes() {
    }

    public static boolean shouldUseShaders() {
        return true;
    }
}

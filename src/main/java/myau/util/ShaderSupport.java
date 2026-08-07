package myau.util;

/**
 * Core shader capability used by visual components that remain in the client.
 */
public final class ShaderSupport {
    private ShaderSupport() {
    }

    public static boolean shouldUseShaders() {
        return true;
    }
}

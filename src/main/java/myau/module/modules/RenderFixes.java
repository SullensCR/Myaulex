package myau.module.modules;

import myau.module.Module;

/**
 * Minimal RenderFixes helper used by TargetHUD for shader checks.
 * This is a lightweight placeholder that exposes shouldUseShaders().
 * For full functionality port RenderFixes from OpenMyau-Plus.
 */
public class RenderFixes extends Module {
    public RenderFixes() {
        super("RenderFixes", true, false);
    }

    public static boolean shouldUseShaders() {
        // Return true to allow shader-based HUD effects. Replace with full logic if needed.
        return true;
    }
}


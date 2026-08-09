package myau.render;

import myau.util.AnimationUtil;

/**
 * Pure layout and animation rules for the JSON-defined Bedplates component.
 * Keeping these rules independent from Minecraft makes the important visual
 * transitions and the variable number of defense slots straightforward to test.
 */
public final class BedplateState {
    public static final float COLLAPSED_WIDTH = 102.0F;
    public static final float COLLAPSED_HEIGHT = 102.0F;
    public static final float EXPANDED_WIDTH = 352.0F;
    public static final float EXPANDED_HEIGHT = 106.0F;
    public static final float CORNER_RADIUS = 20.0F;
    public static final float PADDING = 15.0F;
    public static final float ITEM_SPACING = 10.0F;
    public static final float ITEM_SIZE = 72.0F;
    public static final long EXPANSION_MILLIS = 180L;
    public static final long FADE_OUT_MILLIS = 200L;

    private BedplateState() {
    }

    public static int clampLayers(int layers) {
        return Math.max(0, Math.min(3, layers));
    }

    /**
     * The JSON component is an auto-layout row.  The exported three-layer
     * endpoint is 352px wide, so intermediate counts divide that exact span
     * evenly while retaining the exported 10px item gap.
     */
    public static float expandedWidth(int layers) {
        return COLLAPSED_WIDTH + (EXPANDED_WIDTH - COLLAPSED_WIDTH) * clampLayers(layers) / 3.0F;
    }

    public static float width(int layers, float expansion) {
        float progress = clamp01(expansion);
        return COLLAPSED_WIDTH + (expandedWidth(layers) - COLLAPSED_WIDTH) * progress;
    }

    public static float height(float expansion) {
        return COLLAPSED_HEIGHT + (EXPANDED_HEIGHT - COLLAPSED_HEIGHT) * clamp01(expansion);
    }

    public static float itemX(int index) {
        return PADDING + index * (ITEM_SIZE + ITEM_SPACING);
    }

    public static float animateExpansion(float target, float current, float deltaSeconds) {
        // 24/s settles visually within the JSON interaction's 180ms duration.
        return AnimationUtil.animateSmooth(clamp01(target), clamp01(current), 24.0F,
                Math.max(0.001F, Math.min(0.1F, deltaSeconds)));
    }

    public static float fadeAlpha(long destroyedAtMillis, long nowMillis) {
        if (destroyedAtMillis < 0L) return 1.0F;
        return clamp01(1.0F - (nowMillis - destroyedAtMillis) / (float) FADE_OUT_MILLIS);
    }

    public static boolean fadeComplete(long destroyedAtMillis, long nowMillis) {
        return destroyedAtMillis >= 0L && nowMillis - destroyedAtMillis >= FADE_OUT_MILLIS;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}

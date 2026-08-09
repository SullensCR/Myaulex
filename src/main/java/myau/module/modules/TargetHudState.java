package myau.module.modules;

import myau.util.AnimationUtil;

/** Pure TargetHUD state helpers, kept independent from Minecraft rendering. */
public final class TargetHudState {
    public static final float COLLAPSED_HEIGHT = 40.0F;
    public static final float EXPANDED_HEIGHT = 72.0F;
    public static final float EXPANSION_SPEED = 9.0F;
    public static final float MIN_COMPONENT_WIDTH = 150.0F;
    public static final float LEFT_PADDING = 10.0F;
    public static final float HEART_WIDTH = 21.0F;
    public static final float HEALTH_GAP = 6.0F;
    public static final float NAME_GAP = 27.0F;
    public static final float RIGHT_PADDING = 14.0F;

    private TargetHudState() {
    }

    public enum Variant {
        HIDDEN,
        COLLAPSED,
        EXPANDED
    }

    public enum Source {
        NONE,
        AURA,
        MANUAL
    }

    public static Source resolveSource(boolean hasAuraTarget, boolean hasManualTarget) {
        if (hasAuraTarget) return Source.AURA;
        return hasManualTarget ? Source.MANUAL : Source.NONE;
    }

    public static Variant resolveVariant(boolean manualTarget, double distance,
                                         float autoBlockRange, float swingRange) {
        if (manualTarget) {
            return Variant.EXPANDED;
        }
        if (distance > autoBlockRange) {
            return Variant.HIDDEN;
        }
        return distance <= swingRange + 0.5F ? Variant.EXPANDED : Variant.COLLAPSED;
    }

    public static float clampDeltaSeconds(float deltaSeconds) {
        return Math.max(0.001F, Math.min(0.1F, deltaSeconds));
    }

    public static float animate(float target, float current, float deltaSeconds) {
        return AnimationUtil.animateSmooth(target, current, EXPANSION_SPEED,
                clampDeltaSeconds(deltaSeconds));
    }

    public static float height(float expansion) {
        float clamped = Math.max(0.0F, Math.min(1.0F, expansion));
        return COLLAPSED_HEIGHT + (EXPANDED_HEIGHT - COLLAPSED_HEIGHT) * clamped;
    }

    /** Calculates the panel from the actual visible glyph widths. */
    public static float componentWidth(float healthWidth, float nameWidth) {
        return Math.max(MIN_COMPONENT_WIDTH,
                LEFT_PADDING + HEART_WIDTH + HEALTH_GAP + Math.max(0.0F, healthWidth)
                        + NAME_GAP + Math.max(0.0F, nameWidth) + RIGHT_PADDING);
    }
}

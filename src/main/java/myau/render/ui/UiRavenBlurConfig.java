package myau.render.ui;

/**
 * Shared RavenBS-modern settings for UI backdrops.
 *
 * These values are intentionally kept in source so they can be adjusted and
 * tested without changing every screen that uses {@link UiRenderer}.
 */
public final class UiRavenBlurConfig {
    /** Set to false to skip Raven effects and use the configured fallback. */
    public static final boolean ENABLED = true;

    /** Keep the old Gaussian backdrop available when Raven cannot initialize. */
    public static final boolean ENABLE_GAUSSIAN_FALLBACK = true;

    /** RavenBS-modern defaults: three bloom passes at radius 2. */
    public static final int BLOOM_PASSES = 3;
    public static final float BLOOM_RADIUS = 2.0F;

    /** RavenBS-modern defaults: two blur passes at radius 3. */
    public static final int BLUR_PASSES = 2;
    public static final float BLUR_RADIUS = 3.0F;

    /** Alpha used by the rounded mask for the bloom and blur stages. */
    public static final int BLOOM_MASK_ALPHA = 210;
    public static final int BLUR_MASK_ALPHA = 110;

    private UiRavenBlurConfig() {
    }
}

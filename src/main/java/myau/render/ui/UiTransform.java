package myau.render.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public final class UiTransform {
    public static final float REFERENCE_WIDTH = 1920.0F;
    public static final float REFERENCE_HEIGHT = 1080.0F;
    private static final float MIN_SCALE = 0.1F;

    private final float designWidth;
    private final float designHeight;
    private final float physicalScale;
    private final float physicalScaleX;
    private final float physicalScaleY;
    private final float logicalScale;
    private final float physicalX;
    private final float physicalY;
    private final float logicalX;
    private final float logicalY;
    private final float logicalWidth;
    private final float logicalHeight;
    private final int guiScale;

    public UiTransform(Minecraft mc, float designWidth, float designHeight, float userScale, float margin) {
        this.designWidth = designWidth;
        this.designHeight = designHeight;
        ScaledResolution resolution = new ScaledResolution(mc);
        guiScale = resolution.getScaleFactor();

        // Modern UI vertices are submitted after Minecraft has switched to its
        // scaled overlay projection. Layout therefore has to be solved in
        // scaled GUI coordinates first. The old implementation solved the
        // fit in framebuffer pixels and then divided by guiScale, which made
        // the origin and scale disagree with the active projection whenever
        // the window or GUI scale changed.
        logicalWidth = (float) resolution.getScaledWidth_double();
        logicalHeight = (float) resolution.getScaledHeight_double();
        Metrics metrics = calculateMetrics(
                logicalWidth,
                logicalHeight,
                mc.displayWidth,
                mc.displayHeight,
                designWidth,
                designHeight,
                userScale,
                margin
        );
        logicalScale = metrics.logicalScale;
        logicalX = metrics.logicalX;
        logicalY = metrics.logicalY;
        physicalScaleX = metrics.physicalScaleX;
        physicalScaleY = metrics.physicalScaleY;
        physicalScale = Math.min(physicalScaleX, physicalScaleY);
        physicalX = metrics.physicalX;
        physicalY = metrics.physicalY;
    }

    public float mouseX(int logicalMouseX) {
        return (logicalMouseX - logicalX) / logicalScale;
    }

    public float mouseY(int logicalMouseY) {
        return (logicalMouseY - logicalY) / logicalScale;
    }

    public int scissorX(float x) {
        return Math.round(physicalX + x * physicalScaleX);
    }

    public int scissorY(float y, float height, int displayHeight) {
        return Math.round(displayHeight - physicalY - (y + height) * physicalScaleY);
    }

    public int scissorWidth(float width) {
        return Math.max(0, Math.round(width * physicalScaleX));
    }

    public int scissorHeight(float height) {
        return Math.max(0, Math.round(height * physicalScaleY));
    }

    public float getLogicalScale() {
        return logicalScale;
    }

    public float getLogicalX() {
        return logicalX;
    }

    public float getLogicalY() {
        return logicalY;
    }

    /** Width of the active Minecraft 2D overlay projection, in logical pixels. */
    public float getLogicalWidth() {
        return logicalWidth;
    }

    /** Height of the active Minecraft 2D overlay projection, in logical pixels. */
    public float getLogicalHeight() {
        return logicalHeight;
    }

    public float getPhysicalScale() {
        return physicalScale;
    }

    public int getGuiScale() {
        return guiScale;
    }

    public float getDesignWidth() {
        return designWidth;
    }

    public float getDesignHeight() {
        return designHeight;
    }

    static Metrics calculateMetrics(float logicalWidth, float logicalHeight,
                                    int displayWidth, int displayHeight,
                                    float designWidth, float designHeight,
                                    float userScale, float margin) {
        float safeLogicalWidth = Math.max(1.0F, logicalWidth);
        float safeLogicalHeight = Math.max(1.0F, logicalHeight);
        float safeDesignWidth = Math.max(1.0F, designWidth);
        float safeDesignHeight = Math.max(1.0F, designHeight);
        float safeMargin = Math.max(0.0F, margin);

        float responsive = Math.min(
                safeLogicalWidth / REFERENCE_WIDTH,
                safeLogicalHeight / REFERENCE_HEIGHT
        );
        float requested = Math.max(MIN_SCALE, responsive * Math.max(0.0F, userScale));
        float fit = Math.min(
                (safeLogicalWidth - safeMargin * 2.0F) / safeDesignWidth,
                (safeLogicalHeight - safeMargin * 2.0F) / safeDesignHeight
        );
        float logicalScale = Math.max(MIN_SCALE, Math.min(requested, fit));
        float logicalX = (safeLogicalWidth - safeDesignWidth * logicalScale) * 0.5F;
        float logicalY = (safeLogicalHeight - safeDesignHeight * logicalScale) * 0.5F;

        float pixelsPerLogicalX = Math.max(1.0F, displayWidth / safeLogicalWidth);
        float pixelsPerLogicalY = Math.max(1.0F, displayHeight / safeLogicalHeight);
        return new Metrics(
                logicalScale,
                logicalX,
                logicalY,
                logicalScale * pixelsPerLogicalX,
                logicalScale * pixelsPerLogicalY,
                logicalX * pixelsPerLogicalX,
                logicalY * pixelsPerLogicalY
        );
    }

    static final class Metrics {
        final float logicalScale;
        final float logicalX;
        final float logicalY;
        final float physicalScaleX;
        final float physicalScaleY;
        final float physicalX;
        final float physicalY;

        private Metrics(float logicalScale, float logicalX, float logicalY,
                        float physicalScaleX, float physicalScaleY,
                        float physicalX, float physicalY) {
            this.logicalScale = logicalScale;
            this.logicalX = logicalX;
            this.logicalY = logicalY;
            this.physicalScaleX = physicalScaleX;
            this.physicalScaleY = physicalScaleY;
            this.physicalX = physicalX;
            this.physicalY = physicalY;
        }
    }
}

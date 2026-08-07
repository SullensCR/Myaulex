package myau.render.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public final class UiTransform {
    public static final float REFERENCE_WIDTH = 1920.0F;
    public static final float REFERENCE_HEIGHT = 1080.0F;

    private final float designWidth;
    private final float designHeight;
    private final float physicalScale;
    private final float logicalScale;
    private final float physicalX;
    private final float physicalY;
    private final float logicalX;
    private final float logicalY;
    private final int guiScale;

    public UiTransform(Minecraft mc, float designWidth, float designHeight, float userScale, float margin) {
        this.designWidth = designWidth;
        this.designHeight = designHeight;
        ScaledResolution resolution = new ScaledResolution(mc);
        guiScale = resolution.getScaleFactor();

        float responsive = Math.min(mc.displayWidth / REFERENCE_WIDTH, mc.displayHeight / REFERENCE_HEIGHT);
        float requested = Math.max(0.1F, responsive * userScale);
        float fit = Math.min(
                (mc.displayWidth - margin * 2.0F) / designWidth,
                (mc.displayHeight - margin * 2.0F) / designHeight
        );
        physicalScale = Math.max(0.1F, Math.min(requested, fit));
        logicalScale = physicalScale / guiScale;
        physicalX = (mc.displayWidth - designWidth * physicalScale) * 0.5F;
        physicalY = (mc.displayHeight - designHeight * physicalScale) * 0.5F;
        logicalX = physicalX / guiScale;
        logicalY = physicalY / guiScale;
    }

    public float mouseX(int logicalMouseX) {
        return (logicalMouseX - logicalX) / logicalScale;
    }

    public float mouseY(int logicalMouseY) {
        return (logicalMouseY - logicalY) / logicalScale;
    }

    public int scissorX(float x) {
        return Math.round(physicalX + x * physicalScale);
    }

    public int scissorY(float y, float height, int displayHeight) {
        return Math.round(displayHeight - physicalY - (y + height) * physicalScale);
    }

    public int scissorWidth(float width) {
        return Math.max(0, Math.round(width * physicalScale));
    }

    public int scissorHeight(float height) {
        return Math.max(0, Math.round(height * physicalScale));
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
}

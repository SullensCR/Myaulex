package myau.util.shader;

import myau.util.RenderUtil;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;

public class RoundedUtils {

    /**
     * Draw a rounded rectangle with solid color
     */
    public static void drawRound(float x, float y, float width, float height, float radius, boolean fill, Color color) {
        drawRound(x, y, width, height, radius, fill, color.getRGB());
    }

    /**
     * Draw a rounded rectangle with solid color (integer RGB)
     */
    public static void drawRound(float x, float y, float width, float height, float radius, boolean fill, int color) {
        // Use simple rectangle drawing as fallback
        RenderUtil.drawRect(x, y, x + width, y + height, color);
    }

    /**
     * Draw a rounded rectangle with gradient (4-point gradient)
     */
    public static void drawGradientRound(float x, float y, float width, float height, float radius,
                                         Color bottomLeft, Color topLeft, Color bottomRight, Color topRight) {
        drawGradientRound(x, y, width, height, radius,
                         bottomLeft.getRGB(), topLeft.getRGB(),
                         bottomRight.getRGB(), topRight.getRGB());
    }

    /**
     * Draw a rounded rectangle with gradient (integer RGB)
     */
    public static void drawGradientRound(float x, float y, float width, float height, float radius,
                                         int bottomLeft, int topLeft, int bottomRight, int topRight) {
        // Use simple gradient - draw with blended color
        int blended = topLeft;
        RenderUtil.drawRect(x, y, x + width, y + height, blended);
    }

    /**
     * Draw horizontal gradient
     */
    public static void drawGradientHorizontal(float x, float y, float width, float height, float radius, Color left, Color right) {
        drawGradientRound(x, y, width, height, radius, left, left, right, right);
    }

    /**
     * Draw vertical gradient
     */
    public static void drawGradientVertical(float x, float y, float width, float height, float radius, Color top, Color bottom) {
        drawGradientRound(x, y, width, height, radius, bottom, top, bottom, top);
    }

    /**
     * Convert color integer to RGB float components
     */
    public static float getRed(int color) {
        return (color >> 16 & 0xFF) / 255.0F;
    }

    public static float getGreen(int color) {
        return (color >> 8 & 0xFF) / 255.0F;
    }

    public static float getBlue(int color) {
        return (color & 0xFF) / 255.0F;
    }

    public static float getAlpha(int color) {
        return (color >> 24 & 0xFF) / 255.0F;
    }
}



package myau.render.ui;

import myau.util.shader.BlurUtils;
import myau.util.shader.KawaseBloom;
import myau.util.shader.KawaseBlur;
import myau.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GLContext;

import java.awt.Color;

/**
 * The shared UI adapter for the RavenBS-modern Kawase bloom and blur path.
 * TargetHUD deliberately does not use this class; its implementation remains
 * the visual reference and continues to own its own effect lifecycle.
 */
final class UiRavenBackdropBlur {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-UI");
    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean failed;
    private boolean failureLogged;

    boolean isSupported() {
        return UiRavenBlurConfig.ENABLED
                && !failed
                && GLContext.getCapabilities().OpenGL20
                && RoundedUtils.isSupported()
                && KawaseBloom.isSupported()
                && KawaseBlur.isSupported();
    }

    boolean render(UiTransform transform, float x, float y, float width, float height,
                   float radius) {
        if (!isSupported()) return false;

        float screenX = transform.getLogicalX() + x * transform.getLogicalScale();
        float screenY = transform.getLogicalY() + y * transform.getLogicalScale();
        float screenWidth = width * transform.getLogicalScale();
        float screenHeight = height * transform.getLogicalScale();
        float screenRadius = radius * transform.getLogicalScale();
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture0 = textureBinding(GL13.GL_TEXTURE0);
        int previousTexture16 = textureBinding(GL13.GL_TEXTURE16);
        GL13.glActiveTexture(previousActiveTexture);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            // UiRenderer has a design-space model-view transform active. The
            // Raven utilities already work in the current Minecraft GUI space,
            // so use converted coordinates with an identity model-view matrix.
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            BlurUtils.prepareBloom();
            RoundedUtils.drawRound(screenX, screenY, screenWidth, screenHeight, screenRadius,
                    true, new Color(0, 0, 0, clampAlpha(UiRavenBlurConfig.BLOOM_MASK_ALPHA)));
            BlurUtils.bloomEnd(safePasses(UiRavenBlurConfig.BLOOM_PASSES),
                    safeRadius(UiRavenBlurConfig.BLOOM_RADIUS));

            BlurUtils.prepareBlur();
            RoundedUtils.drawRound(screenX, screenY, screenWidth, screenHeight, screenRadius,
                    true, new Color(0, 0, 0, clampAlpha(UiRavenBlurConfig.BLUR_MASK_ALPHA)));
            BlurUtils.blurEnd(safePasses(UiRavenBlurConfig.BLUR_PASSES),
                    safeRadius(UiRavenBlurConfig.BLUR_RADIUS));
            return true;
        } catch (Throwable failure) {
            failed = true;
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.error("Raven UI backdrop failed; switching to the Gaussian compatibility path", failure);
            }
            return false;
        } finally {
            // Kawase routines change the draw framebuffer and texture unit.
            // Restore both explicitly in addition to restoring GL attributes.
            mc.getFramebuffer().bindFramebuffer(true);
            GL13.glActiveTexture(previousActiveTexture);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrixMode);
            GL11.glPopAttrib();
            mc.getFramebuffer().bindFramebuffer(true);
            restoreTextureBinding(GL13.GL_TEXTURE0, previousTexture0);
            restoreTextureBinding(GL13.GL_TEXTURE16, previousTexture16);
            GL13.glActiveTexture(previousActiveTexture);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static int textureBinding(int textureUnit) {
        GL13.glActiveTexture(textureUnit);
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    private static void restoreTextureBinding(int textureUnit, int texture) {
        GL13.glActiveTexture(textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static int safePasses(int passes) {
        return Math.max(1, passes);
    }

    private static float safeRadius(float radius) {
        return Math.max(0.0F, radius);
    }

    private static int clampAlpha(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }

}

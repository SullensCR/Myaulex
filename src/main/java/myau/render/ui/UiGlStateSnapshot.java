package myau.render.ui;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Restores the parts of the legacy OpenGL state that glPushAttrib does not
 * keep in sync with Minecraft's GlStateManager cache.
 */
final class UiGlStateSnapshot {
    private final int matrixMode;
    private final int activeTexture;
    private final int texture0;
    private final int activeTextureBinding;
    private final int currentProgram;
    private final int[] viewport = new int[4];
    private final float[] color = new float[4];
    private final boolean texture2D;
    private final boolean blend;
    private final boolean alphaTest;
    private final boolean depthTest;
    private final boolean cull;
    private final boolean depthMask;
    private final int blendSrc;
    private final int blendDst;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;

    private UiGlStateSnapshot() {
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        texture0 = textureBinding(GL13.GL_TEXTURE0);
        activeTextureBinding = textureBinding(activeTexture);
        GL13.glActiveTexture(activeTexture);

        currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        // LWJGL 2 checks the buffer against the maximum return size of
        // glGetInteger/glGetFloat, even when the queried value is only a
        // four-element vector such as GL_VIEWPORT or GL_CURRENT_COLOR.
        IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        viewportBuffer.get(viewport);
        FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, colorBuffer);
        colorBuffer.get(color);
        texture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        depthMask = GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK) != GL11.GL_FALSE;
        blendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        blendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    }

    static UiGlStateSnapshot capture() {
        return new UiGlStateSnapshot();
    }

    void restore() {
        GL11.glMatrixMode(matrixMode);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL20.glUseProgram(currentProgram);

        // Minecraft 1.8.9 has a 16-entry texture cache (units 0 through 15).
        // Never pass GL_TEXTURE16 to GlStateManager: it indexes that cache at
        // 16 and crashes during the next vanilla font/texture bind.
        bindTexture(GL13.GL_TEXTURE0, texture0);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(texture0);
        if (isManagedTextureUnit(activeTexture) && activeTexture != GL13.GL_TEXTURE0) {
            bindTexture(activeTexture, activeTextureBinding);
            GlStateManager.setActiveTexture(activeTexture);
            GlStateManager.bindTexture(activeTextureBinding);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        }

        syncTexture(texture2D);
        syncBlend(blend);
        syncAlpha(alphaTest);
        syncDepth(depthTest);
        syncCull(cull);
        GlStateManager.depthMask(depthMask);
        GlStateManager.tryBlendFuncSeparate(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha);
        GlStateManager.color(color[0], color[1], color[2], color[3]);
    }

    private static int textureBinding(int textureUnit) {
        GL13.glActiveTexture(textureUnit);
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    private static void bindTexture(int textureUnit, int texture) {
        GL13.glActiveTexture(textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static boolean isManagedTextureUnit(int textureUnit) {
        return textureUnit >= GL13.GL_TEXTURE0 && textureUnit <= GL13.GL_TEXTURE0 + 15;
    }

    private static void syncTexture(boolean enabled) {
        if (enabled) GlStateManager.enableTexture2D();
        else GlStateManager.disableTexture2D();
    }

    private static void syncBlend(boolean enabled) {
        if (enabled) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
    }

    private static void syncAlpha(boolean enabled) {
        if (enabled) GlStateManager.enableAlpha();
        else GlStateManager.disableAlpha();
    }

    private static void syncDepth(boolean enabled) {
        if (enabled) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
    }

    private static void syncCull(boolean enabled) {
        if (enabled) GlStateManager.enableCull();
        else GlStateManager.disableCull();
    }
}

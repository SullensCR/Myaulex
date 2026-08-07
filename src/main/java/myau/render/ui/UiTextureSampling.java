package myau.render.ui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

final class UiTextureSampling {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-UI");
    private static boolean capabilityLogged;

    private UiTextureSampling() {
    }

    static void configure(DynamicTexture texture) {
        configure(texture, true);
    }

    static void configure(DynamicTexture texture, boolean mipmaps) {
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        boolean useMipmaps = mipmaps && generateMipmaps();
        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER,
                useMipmaps ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR
        );
        if (!capabilityLogged) {
            capabilityLogged = true;
            LOGGER.info("Modern UI texture sampling initialized: linear=true, mipmaps={}", useMipmaps);
        }
    }

    private static boolean generateMipmaps() {
        try {
            if (GLContext.getCapabilities().OpenGL30) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                return mipmapResult("OpenGL 3.0");
            }
            if (GLContext.getCapabilities().GL_EXT_framebuffer_object) {
                EXTFramebufferObject.glGenerateMipmapEXT(GL11.GL_TEXTURE_2D);
                return mipmapResult("EXT_framebuffer_object");
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Modern UI mipmap generation failed; using linear filtering only.", failure);
        }
        return false;
    }

    private static boolean mipmapResult(String backend) {
        int error = GL11.glGetError();
        if (error == GL11.GL_NO_ERROR) return true;
        LOGGER.warn("Modern UI mipmap generation through {} returned OpenGL error {}; "
                + "using linear filtering only.", backend, error);
        return false;
    }
}

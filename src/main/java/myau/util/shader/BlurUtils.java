package myau.util.shader;

import net.minecraft.client.shader.Framebuffer;

public class BlurUtils {
    private static Framebuffer stencilFrameBufferBlur = new Framebuffer(1, 1, false);
    private static Framebuffer stencilFrameBufferBloom = new Framebuffer(1, 1, false);

    public static void prepareBlur() {
        // Blur preparation
        if (stencilFrameBufferBlur != null) {
            stencilFrameBufferBlur.framebufferClear();
            stencilFrameBufferBlur.bindFramebuffer(false);
        }
    }

    public static void prepareBloom() {
        // Bloom preparation
        if (stencilFrameBufferBloom != null) {
            stencilFrameBufferBloom.framebufferClear();
            stencilFrameBufferBloom.bindFramebuffer(false);
        }
    }

    public static void blurEnd(int passes, float radius) {
        if (stencilFrameBufferBlur != null) {
            stencilFrameBufferBlur.unbindFramebuffer();
        }
    }

    public static void bloomEnd(int passes, float radius) {
        if (stencilFrameBufferBloom != null) {
            stencilFrameBufferBloom.unbindFramebuffer();
        }
    }
}



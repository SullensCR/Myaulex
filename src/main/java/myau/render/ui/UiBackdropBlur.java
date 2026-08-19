package myau.render.ui;

import myau.render.ClientPerformanceMetrics;
import myau.render.RenderFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class UiBackdropBlur {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-UI");
    private static final int MAX_CAPTURE_VARIANTS = 6;
    private static final String VERTEX =
            "#version 120\n" +
            "varying vec2 uv;\n" +
            "void main(){ gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex; uv=gl_MultiTexCoord0.st; }\n";
    private static final String FRAGMENT =
            "#version 120\n" +
            "uniform sampler2D textureIn;\n" +
            "uniform vec2 texel;\n" +
            "uniform vec2 direction;\n" +
            "uniform float radius;\n" +
            "varying vec2 uv;\n" +
            "void main(){\n" +
            " // Use a wider, evenly spaced kernel so large UI radii do not\n" +
            " // collapse into a few visible blur bands.\n" +
            " vec2 d=texel*direction*max(radius/8.0,0.75);\n" +
            " vec4 c=texture2D(textureIn,uv)*0.204164;\n" +
            " c+=texture2D(textureIn,uv+d)*0.180174;\n" +
            " c+=texture2D(textureIn,uv-d)*0.180174;\n" +
            " c+=texture2D(textureIn,uv+d*2.0)*0.123832;\n" +
            " c+=texture2D(textureIn,uv-d*2.0)*0.123832;\n" +
            " c+=texture2D(textureIn,uv+d*3.0)*0.066282;\n" +
            " c+=texture2D(textureIn,uv-d*3.0)*0.066282;\n" +
            " c+=texture2D(textureIn,uv+d*4.0)*0.027630;\n" +
            " c+=texture2D(textureIn,uv-d*4.0)*0.027630;\n" +
            " gl_FragColor=c;\n" +
            "}\n";

    private static UiBackdropBlur shared;

    private final Minecraft mc = Minecraft.getMinecraft();
    private UiShaderProgram shader;
    // Radius sliders can visit many values; keep the shared per-frame reuse while
    // bounding the number of full-screen framebuffer pairs retained by the client.
    private final Map<Integer, Capture> captures = new LinkedHashMap<>(8, 0.75F, true);
    private boolean supported;

    static UiBackdropBlur shared() {
        if (shared == null) shared = new UiBackdropBlur();
        return shared;
    }

    UiBackdropBlur() {
        supported = OpenGlHelper.isFramebufferEnabled()
                && GLContext.getCapabilities().OpenGL20;
        if (!supported) {
            LOGGER.error("Modern UI unsupported. vendor={} renderer={} gl={} glsl={} framebufferEnabled={}",
                    GL11.glGetString(GL11.GL_VENDOR),
                    GL11.glGetString(GL11.GL_RENDERER),
                    GL11.glGetString(GL11.GL_VERSION),
                    GL11.glGetString(org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION),
                    OpenGlHelper.isFramebufferEnabled());
        }
    }

    boolean isSupported() {
        return supported;
    }

    boolean preloadShader() {
        if (!supported) return false;
        try {
            if (shader == null) shader = new UiShaderProgram("myau-ui-gaussian", VERTEX, FRAGMENT);
            return true;
        } catch (RuntimeException failure) {
            supported = false;
            LOGGER.warn("Modern UI backdrop blur unavailable; continuing with translucent panels.", failure);
            delete();
            return false;
        }
    }

    void invalidateFramebuffers() {
        for (Capture capture : captures.values()) {
            if (capture.horizontal != null) capture.horizontal.deleteFramebuffer();
            if (capture.vertical != null) capture.vertical.deleteFramebuffer();
        }
        captures.clear();
    }

    int capture(float radius) {
        if (!supported) return 0;
        try {
            int key = Float.floatToIntBits(radius);
            Capture capture = captures.get(key);
            if (capture == null) {
                while (captures.size() >= MAX_CAPTURE_VARIANTS) {
                    Iterator<Map.Entry<Integer, Capture>> iterator = captures.entrySet().iterator();
                    if (!iterator.hasNext()) break;
                    Capture oldest = iterator.next().getValue();
                    iterator.remove();
                    deleteCapture(oldest);
                }
                capture = new Capture();
                captures.put(key, capture);
            }
            ensureResources(capture);
            long frame = RenderFrame.current();
            if (frame == 0L || capture.frame != frame) {
                long startedNanos = ClientPerformanceMetrics.start();
                renderPass(mc.getFramebuffer().framebufferTexture, capture.horizontal, radius, 1.0F, 0.0F);
                renderPass(capture.horizontal.framebufferTexture, capture.vertical, radius, 0.0F, 1.0F);
                capture.frame = frame;
                ClientPerformanceMetrics.recordBackdropCapture(startedNanos);
            }
            return capture.vertical.framebufferTexture;
        } catch (RuntimeException e) {
            supported = false;
            LOGGER.error("Backdrop blur failed at {}x{}, guiScale unknown, glError={}",
                    mc.displayWidth, mc.displayHeight, GL11.glGetError(), e);
            delete();
            throw e;
        } finally {
            mc.getFramebuffer().bindFramebuffer(true);
        }
    }

    private void ensureResources(Capture capture) {
        if (shader == null) shader = new UiShaderProgram("myau-ui-gaussian", VERTEX, FRAGMENT);
        if (capture.horizontal == null || capture.horizontal.framebufferWidth != mc.displayWidth || capture.horizontal.framebufferHeight != mc.displayHeight) {
            if (capture.horizontal != null) capture.horizontal.deleteFramebuffer();
            if (capture.vertical != null) capture.vertical.deleteFramebuffer();
            capture.horizontal = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            capture.vertical = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            capture.horizontal.setFramebufferFilter(GL11.GL_LINEAR);
            capture.vertical.setFramebufferFilter(GL11.GL_LINEAR);
            capture.frame = Long.MIN_VALUE;
            try {
                int horizontalStatus = framebufferStatus(capture.horizontal);
                int verticalStatus = framebufferStatus(capture.vertical);
                LOGGER.info("Modern UI framebuffers initialized at {}x{}: horizontal={}, vertical={}",
                        mc.displayWidth, mc.displayHeight, horizontalStatus, verticalStatus);
                if (horizontalStatus != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE
                        || verticalStatus != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Incomplete modern UI framebuffer: horizontal="
                            + horizontalStatus + ", vertical=" + verticalStatus);
                }
            } finally {
                mc.getFramebuffer().bindFramebuffer(true);
            }
        }
    }

    private static void deleteCapture(Capture capture) {
        if (capture == null) return;
        if (capture.horizontal != null) capture.horizontal.deleteFramebuffer();
        if (capture.vertical != null) capture.vertical.deleteFramebuffer();
    }

    private static int framebufferStatus(Framebuffer framebuffer) {
        framebuffer.bindFramebuffer(false);
        return OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
    }

    private void renderPass(int source, Framebuffer target, float radius, float dx, float dy) {
        target.framebufferClear();
        target.bindFramebuffer(true);
        shader.bind();
        try {
            shader.uniform1i("textureIn", 0);
            shader.uniform2f("texel", 1.0F / mc.displayWidth, 1.0F / mc.displayHeight);
            shader.uniform2f("direction", dx, dy);
            shader.uniform1f("radius", radius);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(source);
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            drawFullscreen();
        } finally {
            shader.unbind();
        }
    }

    private void drawFullscreen() {
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, mc.displayWidth, mc.displayHeight, 0.0D, -1.0D, 1.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(0, mc.displayHeight);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(mc.displayWidth, mc.displayHeight);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(mc.displayWidth, 0);
        GL11.glEnd();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(previousMatrixMode);
    }

    void delete() {
        if (shader != null) shader.delete();
        invalidateFramebuffers();
        shader = null;
    }

    private static final class Capture {
        private Framebuffer horizontal;
        private Framebuffer vertical;
        private long frame = Long.MIN_VALUE;
    }
}

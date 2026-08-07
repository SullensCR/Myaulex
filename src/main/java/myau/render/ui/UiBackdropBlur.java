package myau.render.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

final class UiBackdropBlur {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-UI");
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
            " vec2 d=texel*direction*max(radius/6.0,1.0);\n" +
            " vec4 c=texture2D(textureIn,uv)*0.227027;\n" +
            " c+=texture2D(textureIn,uv+d*1.384615)*0.316216;\n" +
            " c+=texture2D(textureIn,uv-d*1.384615)*0.316216;\n" +
            " c+=texture2D(textureIn,uv+d*3.230769)*0.070270;\n" +
            " c+=texture2D(textureIn,uv-d*3.230769)*0.070270;\n" +
            " gl_FragColor=c;\n" +
            "}\n";

    private final Minecraft mc = Minecraft.getMinecraft();
    private UiShaderProgram shader;
    private Framebuffer horizontal;
    private Framebuffer vertical;
    private boolean supported;
    private int texture;

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

    int texture() {
        return texture;
    }

    void capture(float radius) {
        if (!supported) return;
        try {
            ensureResources();
            renderPass(mc.getFramebuffer().framebufferTexture, horizontal, radius, 1.0F, 0.0F);
            renderPass(horizontal.framebufferTexture, vertical, radius, 0.0F, 1.0F);
            texture = vertical.framebufferTexture;
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

    private void ensureResources() {
        if (shader == null) shader = new UiShaderProgram("myau-ui-gaussian", VERTEX, FRAGMENT);
        if (horizontal == null || horizontal.framebufferWidth != mc.displayWidth || horizontal.framebufferHeight != mc.displayHeight) {
            if (horizontal != null) horizontal.deleteFramebuffer();
            if (vertical != null) vertical.deleteFramebuffer();
            horizontal = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            vertical = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            horizontal.setFramebufferFilter(GL11.GL_LINEAR);
            vertical.setFramebufferFilter(GL11.GL_LINEAR);
            try {
                int horizontalStatus = framebufferStatus(horizontal);
                int verticalStatus = framebufferStatus(vertical);
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
        if (horizontal != null) horizontal.deleteFramebuffer();
        if (vertical != null) vertical.deleteFramebuffer();
        shader = null;
        horizontal = null;
        vertical = null;
        texture = 0;
    }
}

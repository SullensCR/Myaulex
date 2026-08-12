package myau.render.ui;

import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

final class UiShadowRenderer {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-UI");
    private static final String VERTEX =
            "#version 120\n" +
            "varying vec2 localPosition;\n" +
            "void main(){\n" +
            " gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;\n" +
            " localPosition=gl_MultiTexCoord0.xy;\n" +
            "}\n";
    private static final String FRAGMENT =
            "#version 120\n" +
            "uniform vec2 boxSize;\n" +
            "uniform float cornerRadius;\n" +
            "uniform float softness;\n" +
            "uniform vec4 shadowColor;\n" +
            "varying vec2 localPosition;\n" +
            "float roundedBoxDistance(vec2 point, vec2 halfSize, float radius){\n" +
            " vec2 q=abs(point)-halfSize+vec2(radius);\n" +
            " return length(max(q,vec2(0.0)))+min(max(q.x,q.y),0.0)-radius;\n" +
            "}\n" +
            "void main(){\n" +
            " float distance=roundedBoxDistance(localPosition-boxSize*0.5,boxSize*0.5,cornerRadius);\n" +
            " float alpha=1.0-smoothstep(0.0,max(softness,0.001),distance);\n" +
            " gl_FragColor=vec4(shadowColor.rgb,shadowColor.a*alpha);\n" +
            "}\n";

    private UiShaderProgram shader;
    private boolean unavailable;

    boolean preload() {
        return initialize();
    }

    void draw(float x, float y, float width, float height, float radius,
              float offsetX, float offsetY, float blur, float spread, int color) {
        if (!initialize()) return;

        float expandedWidth = width + spread * 2.0F;
        float expandedHeight = height + spread * 2.0F;
        float expandedRadius = Math.max(0.0F, radius + spread);
        float padding = Math.max(1.0F, blur * 1.5F);
        float left = x + offsetX - spread - padding;
        float top = y + offsetY - spread - padding;
        float right = left + expandedWidth + padding * 2.0F;
        float bottom = top + expandedHeight + padding * 2.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        try {
            shader.uniform2f("boxSize", expandedWidth, expandedHeight);
            shader.uniform1f("cornerRadius", expandedRadius);
            shader.uniform1f("softness", Math.max(0.01F, blur));
            shader.uniform4f(
                    "shadowColor",
                    ((color >> 16) & 255) / 255.0F,
                    ((color >> 8) & 255) / 255.0F,
                    (color & 255) / 255.0F,
                    ((color >>> 24) & 255) / 255.0F
            );

            GL11.glBegin(GL11.GL_QUADS);
            vertex(left, top, -padding, -padding);
            vertex(left, bottom, -padding, expandedHeight + padding);
            vertex(right, bottom, expandedWidth + padding, expandedHeight + padding);
            vertex(right, top, expandedWidth + padding, -padding);
            GL11.glEnd();
        } finally {
            shader.unbind();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }

    void delete() {
        if (shader != null) shader.delete();
        shader = null;
    }

    private boolean initialize() {
        if (shader != null) return true;
        if (unavailable) return false;
        try {
            shader = new UiShaderProgram("myau-ui-soft-shadow", VERTEX, FRAGMENT);
            return true;
        } catch (RuntimeException failure) {
            unavailable = true;
            LOGGER.warn("Modern UI soft shadows unavailable; continuing without panel shadows.", failure);
            return false;
        }
    }

    private static void vertex(float x, float y, float localX, float localY) {
        GL11.glTexCoord2f(localX, localY);
        GL11.glVertex2f(x, y);
    }
}

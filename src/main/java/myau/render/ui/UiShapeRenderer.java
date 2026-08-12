package myau.render.ui;

import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

final class UiShapeRenderer {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-UI");
    private static final float PADDING = 8.0F;
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
            "uniform vec4 cornerRadii;\n" +
            "uniform vec4 topColor;\n" +
            "uniform vec4 bottomColor;\n" +
            "uniform float horizontalGradient;\n" +
            "uniform float outlineThickness;\n" +
            "varying vec2 localPosition;\n" +
            "float selectedRadius(vec2 point,vec4 radii){\n" +
            " if(point.y<0.0) return point.x<0.0?radii.x:radii.y;\n" +
            " return point.x<0.0?radii.w:radii.z;\n" +
            "}\n" +
            "float roundedBoxDistance(vec2 point,vec2 size,vec4 radii){\n" +
            " float radius=selectedRadius(point,radii);\n" +
            " vec2 q=abs(point)-size*0.5+vec2(radius);\n" +
            " return length(max(q,vec2(0.0)))+min(max(q.x,q.y),0.0)-radius;\n" +
            "}\n" +
            "float coverage(float distance){\n" +
            " float antialias=max(fwidth(distance)*0.75,0.0001);\n" +
            " return 1.0-smoothstep(-antialias,antialias,distance);\n" +
            "}\n" +
            "void main(){\n" +
            " vec2 point=localPosition-boxSize*0.5;\n" +
            " float outer=coverage(roundedBoxDistance(point,boxSize,cornerRadii));\n" +
            " float alpha=outer;\n" +
            " if(outlineThickness>0.0){\n" +
            "  vec2 innerSize=max(boxSize-vec2(outlineThickness*2.0),vec2(0.0));\n" +
            "  vec4 innerRadii=max(cornerRadii-vec4(outlineThickness),vec4(0.0));\n" +
            "  float inner=coverage(roundedBoxDistance(point,innerSize,innerRadii));\n" +
            "  alpha=outer*(1.0-inner);\n" +
            " }\n" +
            " float gradient=horizontalGradient>0.5\n" +
            "  ? clamp(localPosition.x/max(boxSize.x,0.0001),0.0,1.0)\n" +
            "  : clamp(localPosition.y/max(boxSize.y,0.0001),0.0,1.0);\n" +
            " vec4 color=mix(topColor,bottomColor,gradient);\n" +
            " gl_FragColor=vec4(color.rgb,color.a*alpha);\n" +
            "}\n";

    private UiShaderProgram shader;
    private boolean unavailable;

    boolean preload() {
        return initialize();
    }

    boolean fill(float x, float y, float width, float height,
                 float topLeft, float topRight, float bottomRight, float bottomLeft, int color) {
        return draw(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                color, color, -1.0F, false);
    }

    boolean horizontalFill(float x, float y, float width, float height, float radius,
                           int leftColor, int rightColor) {
        return draw(x, y, width, height, radius, radius, radius, radius,
                leftColor, rightColor, -1.0F, true);
    }

    boolean outline(float x, float y, float width, float height, float radius, float thickness, int color) {
        return draw(x, y, width, height, radius, radius, radius, radius,
                color, color, thickness, false);
    }

    private boolean draw(float x, float y, float width, float height,
                         float topLeft, float topRight, float bottomRight, float bottomLeft,
                         int topColor, int bottomColor, float outlineThickness,
                         boolean horizontalGradient) {
        if (width <= 0 || height <= 0 || unavailable || !initialize()) return false;

        float maximumRadius = Math.min(width, height) * 0.5F;
        topLeft = clampRadius(topLeft, maximumRadius);
        topRight = clampRadius(topRight, maximumRadius);
        bottomRight = clampRadius(bottomRight, maximumRadius);
        bottomLeft = clampRadius(bottomLeft, maximumRadius);

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        try {
            shader.uniform2f("boxSize", width, height);
            shader.uniform4f("cornerRadii", topLeft, topRight, bottomRight, bottomLeft);
            setColor("topColor", topColor);
            setColor("bottomColor", bottomColor);
            shader.uniform1f("horizontalGradient", horizontalGradient ? 1.0F : 0.0F);
            shader.uniform1f("outlineThickness", outlineThickness);

            GL11.glBegin(GL11.GL_QUADS);
            vertex(x - PADDING, y - PADDING, -PADDING, -PADDING);
            vertex(x - PADDING, y + height + PADDING, -PADDING, height + PADDING);
            vertex(x + width + PADDING, y + height + PADDING, width + PADDING, height + PADDING);
            vertex(x + width + PADDING, y - PADDING, width + PADDING, -PADDING);
            GL11.glEnd();
        } finally {
            shader.unbind();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1, 1, 1, 1);
        }
        return true;
    }

    private boolean initialize() {
        if (shader != null) return true;
        try {
            shader = new UiShaderProgram("myau-ui-antialiased-shape", VERTEX, FRAGMENT);
            LOGGER.info("Modern UI analytic shape antialiasing initialized.");
            return true;
        } catch (RuntimeException failure) {
            unavailable = true;
            LOGGER.warn("Analytic UI shape antialiasing unavailable; using polygon fallback.", failure);
            return false;
        }
    }

    private void setColor(String uniform, int color) {
        shader.uniform4f(
                uniform,
                ((color >> 16) & 255) / 255.0F,
                ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F,
                ((color >>> 24) & 255) / 255.0F
        );
    }

    void delete() {
        if (shader != null) shader.delete();
        shader = null;
    }

    private static float clampRadius(float radius, float maximum) {
        return Math.max(0.0F, Math.min(maximum, radius));
    }

    private static void vertex(float x, float y, float localX, float localY) {
        GL11.glTexCoord2f(localX, localY);
        GL11.glVertex2f(x, y);
    }
}

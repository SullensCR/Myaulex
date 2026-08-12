package myau.render.ui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import myau.util.font.variable.FontAxes;
import myau.util.font.variable.FreeTypeFace;
import myau.util.font.variable.OpenTypeVariableFont;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.font.TextAttribute;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class UiFont {
    private static final int FIRST = 32;
    private static final int LAST = 255;
    private static final int COLUMNS = 16;
    /** Empty texels around each glyph so a soft shadow cannot sample its neighbour. */
    private static final int SHADOW_PADDING = 4;
    private static final String SHADOW_VERTEX =
            "#version 120\n" +
            "varying vec2 uv;\n" +
            "varying vec4 tint;\n" +
            "void main(){\n" +
            " gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;\n" +
            " uv=gl_MultiTexCoord0.st;\n" +
            " tint=gl_Color;\n" +
            "}\n";
    private static final String SHADOW_FRAGMENT =
            "#version 120\n" +
            "uniform sampler2D fontTexture;\n" +
            "uniform vec2 texel;\n" +
            "uniform vec2 shadowOffset;\n" +
            "uniform float shadowOpacity;\n" +
            "varying vec2 uv;\n" +
            "varying vec4 tint;\n" +
            "float alphaAt(vec2 point){ return texture2D(fontTexture,point).a; }\n" +
            "float blurredAlpha(vec2 point){\n" +
            " vec2 d=texel*1.5;\n" +
            " float a=alphaAt(point)*0.204164;\n" +
            " a+=(alphaAt(point+vec2(d.x,0.0))+alphaAt(point-vec2(d.x,0.0)))*0.123841;\n" +
            " a+=(alphaAt(point+vec2(0.0,d.y))+alphaAt(point-vec2(0.0,d.y)))*0.123841;\n" +
            " a+=(alphaAt(point+vec2(d.x,d.y))+alphaAt(point+vec2(-d.x,d.y))\n" +
            "    +alphaAt(point+vec2(d.x,-d.y))+alphaAt(point-vec2(d.x,d.y)))*0.075118;\n" +
            " return a;\n" +
            "}\n" +
            "void main(){\n" +
            " float shadowAlpha=blurredAlpha(uv-texel*shadowOffset)*tint.a*shadowOpacity;\n" +
            " gl_FragColor=vec4(0.0,0.0,0.0,shadowAlpha);\n" +
            "}\n";

    private final Glyph[] glyphs = new Glyph[LAST + 1];
    private final DynamicTexture texture;
    private final int atlasSize;
    private final int cellSize;
    private final float lineHeight;
    private UiShaderProgram shadowShader;
    private boolean shadowShaderUnavailable;

    UiFont(Font font) {
        cellSize = Math.max(24, (int) Math.ceil(font.getSize2D() * 2.15F));
        int rows = (int) Math.ceil((LAST - FIRST + 1) / (float) COLUMNS);
        int required = Math.max(COLUMNS * cellSize, rows * cellSize);
        atlasSize = nextPowerOfTwo(required);

        BufferedImage image = new BufferedImage(atlasSize, atlasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setComposite(java.awt.AlphaComposite.Src);
        graphics.setColor(Color.WHITE);
        graphics.setFont(font);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics metrics = graphics.getFontMetrics();
        lineHeight = metrics.getHeight();

        for (int character = FIRST; character <= LAST; character++) {
            int index = character - FIRST;
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            int x = column * cellSize;
            int y = row * cellSize;
            String value = String.valueOf((char) character);
            float advance = (float) font.getStringBounds(value, graphics.getFontRenderContext()).getWidth();
            graphics.drawString(value, x + SHADOW_PADDING + 2, y + SHADOW_PADDING + 2 + metrics.getAscent());
            glyphs[character] = glyph(x, y, cellSize, advance);
        }
        graphics.dispose();
        texture = new DynamicTexture(image);
        UiTextureSampling.configure(texture, false);
    }

    UiFont(OpenTypeVariableFont variableFont, float size, FontAxes axes, Font fallback) {
        this(buildVariableAtlas(variableFont, size, axes, fallback));
    }

    private UiFont(Atlas atlas) {
        texture = new DynamicTexture(atlas.image);
        UiTextureSampling.configure(texture, false);
        atlasSize = atlas.atlasSize;
        cellSize = atlas.cellSize;
        lineHeight = atlas.lineHeight;
        System.arraycopy(atlas.glyphs, 0, glyphs, 0, glyphs.length);
    }

    private static Atlas buildVariableAtlas(OpenTypeVariableFont variableFont, float size,
                                            FontAxes axes, Font fallback) {
        int fallbackSize = Math.max(1, Math.round(size));
        FreeTypeFace face = FreeTypeFace.open(variableFont, axes, fallbackSize);
        if (face == null) {
            java.util.Map<TextAttribute, Object> attributes =
                    new java.util.HashMap<TextAttribute, Object>();
            float weight = axes.value("wght", 400.0F);
            attributes.put(TextAttribute.SIZE, size);
            attributes.put(TextAttribute.WEIGHT,
                    weight >= 800.0F ? TextAttribute.WEIGHT_ULTRABOLD
                            : weight >= 700.0F ? TextAttribute.WEIGHT_BOLD
                            : weight >= 600.0F ? TextAttribute.WEIGHT_SEMIBOLD
                            : TextAttribute.WEIGHT_REGULAR);
            return buildAwtAtlas(fallback.deriveFont(attributes));
        }
        try {
            int cellSize = Math.max(24, (int) Math.ceil(size * 2.15F));
            int rows = (int) Math.ceil((LAST - FIRST + 1) / (float) COLUMNS);
            int required = Math.max(COLUMNS * cellSize, rows * cellSize);
            int atlasSize = nextPowerOfTwo(required);
            BufferedImage image = new BufferedImage(atlasSize, atlasSize, BufferedImage.TYPE_INT_ARGB);
            float lineHeight = Math.max(1.0F, face.lineHeight());
            float ascent = face.ascent();
            Glyph[] glyphs = new Glyph[LAST + 1];
            for (int character = FIRST; character <= LAST; character++) {
                int index = character - FIRST;
                int column = index % COLUMNS;
                int row = index / COLUMNS;
                int x = column * cellSize;
                int y = row * cellSize;
                FreeTypeFace.GlyphBitmap glyph = face.glyph(character);
                int drawX = x + SHADOW_PADDING + 2 + glyph.left;
                int drawY = y + SHADOW_PADDING + Math.round(ascent - glyph.top);
                for (int glyphY = 0; glyphY < glyph.height; glyphY++) {
                    for (int glyphX = 0; glyphX < glyph.width; glyphX++) {
                        int targetX = drawX + glyphX;
                        int targetY = drawY + glyphY;
                        if (targetX >= 0 && targetX < atlasSize && targetY >= 0 && targetY < atlasSize) {
                            int alpha = glyph.alpha[glyphY * glyph.width + glyphX] & 0xFF;
                            image.setRGB(targetX, targetY, alpha << 24 | 0xFFFFFF);
                        }
                    }
                }
                float advance = glyph.advance > 0.0F ? glyph.advance : Math.max(1.0F, size * 0.5F);
                glyphs[character] = glyph(x, y, cellSize, advance);
            }
            return new Atlas(image, atlasSize, cellSize, lineHeight, glyphs);
        } finally {
            face.close();
        }
    }

    private static Atlas buildAwtAtlas(Font font) {
        int cellSize = Math.max(24, (int) Math.ceil(font.getSize2D() * 2.15F));
        int rows = (int) Math.ceil((LAST - FIRST + 1) / (float) COLUMNS);
        int required = Math.max(COLUMNS * cellSize, rows * cellSize);
        int atlasSize = nextPowerOfTwo(required);
        BufferedImage image = new BufferedImage(atlasSize, atlasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setComposite(java.awt.AlphaComposite.Src);
        graphics.setColor(Color.WHITE);
        graphics.setFont(font);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics metrics = graphics.getFontMetrics();
        float lineHeight = metrics.getHeight();
        Glyph[] glyphs = new Glyph[LAST + 1];
        for (int character = FIRST; character <= LAST; character++) {
            int index = character - FIRST;
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            int x = column * cellSize;
            int y = row * cellSize;
            String value = String.valueOf((char) character);
            float advance = (float) font.getStringBounds(value, graphics.getFontRenderContext()).getWidth();
            graphics.drawString(value, x + SHADOW_PADDING + 2, y + SHADOW_PADDING + 2 + metrics.getAscent());
            glyphs[character] = glyph(x, y, cellSize, advance);
        }
        graphics.dispose();
        return new Atlas(image, atlasSize, cellSize, lineHeight, glyphs);
    }

    public float width(String text) {
        if (text == null) return 0.0F;
        float width = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            Glyph glyph = character <= LAST ? glyphs[character] : glyphs['?'];
            if (glyph != null) width += glyph.advance;
        }
        return width;
    }

    /** Width of the actual atlas quads, including glyph-side visual padding. */
    public float visualWidth(String text) {
        if (text == null) return 0.0F;
        float cursor = 0.0F;
        float right = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            Glyph glyph = character <= LAST ? glyphs[character] : glyphs['?'];
            if (glyph == null) continue;
            right = Math.max(right, cursor + glyph.width);
            cursor += glyph.advance;
        }
        return right;
    }

    public float height() {
        return lineHeight;
    }

    public void draw(String text, float x, float y, int color) {
        draw(text, x, y, color, false);
    }

    public void draw(String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        if (shadow && drawSoftShadow(text, x, y, color)) return;
        if (shadow) {
            int alpha = color >>> 24;
            int shadowColor = (Math.max(24, alpha * 3 / 5) << 24);
            drawInternal(text, x, y + 1.5F, shadowColor);
        }
        drawInternal(text, x, y, color);
    }

    /** Compile the optional soft-shadow program without issuing a draw call. */
    void preloadShadowShader() {
        if (shadowShader != null || shadowShaderUnavailable) return;
        try {
            shadowShader = new UiShaderProgram("myau-ui-font-soft-shadow", SHADOW_VERTEX, SHADOW_FRAGMENT);
        } catch (RuntimeException failure) {
            shadowShaderUnavailable = true;
            shadowShader = null;
        }
    }

    private void drawInternal(String text, float x, float y, int baseColor) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.bindTexture(texture.getGlTextureId());

        float cursor = x;
        int color = baseColor;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00a7' && i + 1 < text.length()) {
                color = minecraftColor(text.charAt(++i), baseColor);
                applyColor(color);
                continue;
            }
            Glyph glyph = character <= LAST ? glyphs[character] : glyphs['?'];
            if (glyph == null) continue;
            applyColor(color);
            float u0 = glyph.x / (float) atlasSize;
            float v0 = glyph.y / (float) atlasSize;
            float u1 = (glyph.x + glyph.width) / (float) atlasSize;
            float v1 = (glyph.y + glyph.height) / (float) atlasSize;
            float drawWidth = glyph.width;
            float drawHeight = glyph.height;
            GL11.glTexCoord2f(u0, v0);
            GL11.glVertex2f(cursor, y);
            GL11.glTexCoord2f(u0, v1);
            GL11.glVertex2f(cursor, y + drawHeight);
            GL11.glTexCoord2f(u1, v1);
            GL11.glVertex2f(cursor + drawWidth, y + drawHeight);
            GL11.glTexCoord2f(u1, v0);
            GL11.glVertex2f(cursor + drawWidth, y);
            cursor += glyph.advance;
        }
        GL11.glEnd();
        GlStateManager.color(1, 1, 1, 1);
    }

    /**
     * Draws a Gaussian alpha shadow from the glyph atlas, then draws the
     * foreground after every shadow quad so shadows can never cover letters.
     */
    private boolean drawSoftShadow(String text, float x, float y, int baseColor) {
        if (shadowShaderUnavailable) return false;
        try {
            if (shadowShader == null) {
                shadowShader = new UiShaderProgram("myau-ui-font-soft-shadow", SHADOW_VERTEX, SHADOW_FRAGMENT);
            }
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.bindTexture(texture.getGlTextureId());
            shadowShader.bind();
            try {
                shadowShader.uniform1i("fontTexture", 0);
                shadowShader.uniform2f("texel", 1.0F / atlasSize, 1.0F / atlasSize);
                shadowShader.uniform2f("shadowOffset", 0.0F, 2.0F);
                shadowShader.uniform1f("shadowOpacity", 0.30F);

                float cursor = x;
                int color = baseColor;
                GL11.glBegin(GL11.GL_QUADS);
                for (int i = 0; i < text.length(); i++) {
                    char character = text.charAt(i);
                    if (character == '\u00a7' && i + 1 < text.length()) {
                        color = minecraftColor(text.charAt(++i), baseColor);
                        continue;
                    }
                    Glyph glyph = character <= LAST ? glyphs[character] : glyphs['?'];
                    if (glyph == null) continue;
                    applyColor(color);
                    float u0 = (glyph.x - SHADOW_PADDING) / (float) atlasSize;
                    float v0 = (glyph.y - SHADOW_PADDING) / (float) atlasSize;
                    float u1 = (glyph.x + glyph.width + SHADOW_PADDING) / (float) atlasSize;
                    float v1 = (glyph.y + glyph.height + SHADOW_PADDING) / (float) atlasSize;
                    float left = cursor - SHADOW_PADDING;
                    float top = y - SHADOW_PADDING;
                    float right = cursor + glyph.width + SHADOW_PADDING;
                    float bottom = y + glyph.height + SHADOW_PADDING;
                    GL11.glTexCoord2f(u0, v0);
                    GL11.glVertex2f(left, top);
                    GL11.glTexCoord2f(u0, v1);
                    GL11.glVertex2f(left, bottom);
                    GL11.glTexCoord2f(u1, v1);
                    GL11.glVertex2f(right, bottom);
                    GL11.glTexCoord2f(u1, v0);
                    GL11.glVertex2f(right, top);
                    cursor += glyph.advance;
                }
                GL11.glEnd();
            } finally {
                shadowShader.unbind();
            }
            drawInternal(text, x, y, baseColor);
            GlStateManager.color(1, 1, 1, 1);
            return true;
        } catch (RuntimeException failure) {
            shadowShaderUnavailable = true;
            if (shadowShader != null) {
                shadowShader.delete();
                shadowShader = null;
            }
            return false;
        }
    }

    private static void applyColor(int color) {
        GL11.glColor4f(
                ((color >> 16) & 255) / 255.0F,
                ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F,
                ((color >>> 24) & 255) / 255.0F
        );
    }

    private static int minecraftColor(char code, int fallback) {
        String codes = "0123456789abcdef";
        int index = codes.indexOf(Character.toLowerCase(code));
        if (index < 0) return code == 'r' ? fallback : fallback;
        int dark = (index >> 3 & 1) * 85;
        int red = (index >> 2 & 1) * 170 + dark;
        int green = (index >> 1 & 1) * 170 + dark;
        int blue = (index & 1) * 170 + dark;
        if (index == 6) red += 85;
        return (fallback & 0xFF000000) | red << 16 | green << 8 | blue;
    }

    public void delete() {
        if (shadowShader != null) shadowShader.delete();
        shadowShader = null;
        texture.deleteGlTexture();
    }

    private static Glyph glyph(int cellX, int cellY, int cellSize, float advance) {
        return new Glyph(cellX + SHADOW_PADDING, cellY + SHADOW_PADDING,
                Math.min(cellSize - SHADOW_PADDING * 2, Math.max(1, (int) Math.ceil(advance) + 5)),
                cellSize - SHADOW_PADDING * 2, advance);
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) result <<= 1;
        return result;
    }

    private static final class Atlas {
        final BufferedImage image;
        final int atlasSize;
        final int cellSize;
        final float lineHeight;
        final Glyph[] glyphs;

        Atlas(BufferedImage image, int atlasSize, int cellSize, float lineHeight, Glyph[] glyphs) {
            this.image = image;
            this.atlasSize = atlasSize;
            this.cellSize = cellSize;
            this.lineHeight = lineHeight;
            this.glyphs = glyphs;
        }
    }

    private static final class Glyph {
        final int x;
        final int y;
        final int width;
        final int height;
        final float advance;

        Glyph(int x, int y, int width, int height, float advance) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.advance = advance;
        }
    }
}

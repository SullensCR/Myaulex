package myau.render.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class UiRenderer {
    private static final int CORNER_SEGMENTS = 10;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final UiFonts fonts = new UiFonts();
    private final UiBackdropBlur gaussianFallback = new UiBackdropBlur();
    private final UiRavenBackdropBlur ravenBackdrop = new UiRavenBackdropBlur();
    private final UiShadowRenderer shadows = new UiShadowRenderer();
    private final UiShapeRenderer shapes = new UiShapeRenderer();
    private final Map<String, UiTexture> textures = new HashMap<>();
    private final Deque<UiBounds> clips = new ArrayDeque<>();
    private UiTransform transform;
    private boolean frameActive;
    private boolean ravenFrame;
    private boolean gaussianFrame;

    public boolean isSupported() {
        return ravenBackdrop.isSupported()
                || (UiRavenBlurConfig.ENABLE_GAUSSIAN_FALLBACK && gaussianFallback.isSupported());
    }

    public UiFonts fonts() {
        return fonts;
    }

    public UiTexture texture(String name) {
        UiTexture texture = textures.get(name);
        if (texture == null) {
            texture = new UiTexture("ui/icons/" + name + ".png");
            textures.put(name, texture);
        }
        return texture;
    }

    public void beginFrame(UiTransform transform, float legacyBlurRadius) {
        if (frameActive) throw new IllegalStateException("UI frame already active");
        this.transform = transform;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        boolean initialized = false;
        try {
            ravenFrame = ravenBackdrop.isSupported();
            gaussianFrame = !ravenFrame
                    && UiRavenBlurConfig.ENABLE_GAUSSIAN_FALLBACK
                    && gaussianFallback.isSupported();
            if (gaussianFrame) {
                try {
                    // The old per-screen radius is retained only for this
                    // compatibility path. Raven uses UiRavenBlurConfig.
                    gaussianFallback.capture(legacyBlurRadius);
                } catch (Throwable failure) {
                    gaussianFrame = false;
                }
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glTranslatef(transform.getLogicalX(), transform.getLogicalY(), 0);
            GL11.glScalef(transform.getLogicalScale(), transform.getLogicalScale(), 1);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableAlpha();
            GlStateManager.disableCull();
            frameActive = true;
            initialized = true;
        } finally {
            if (!initialized) {
                GL11.glPopAttrib();
                this.transform = null;
                ravenFrame = false;
                gaussianFrame = false;
            }
        }
    }

    public void endFrame() {
        if (!frameActive) return;
        while (!clips.isEmpty()) popClip();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.depthMask(true);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        frameActive = false;
        ravenFrame = false;
        gaussianFrame = false;
        transform = null;
    }

    public void backdrop(float x, float y, float width, float height, float radius, int tint) {
        if (ravenFrame) {
            if (!ravenBackdrop.render(transform, x, y, width, height, radius)) {
                ravenFrame = false;
                gaussianFrame = false;
            }
        } else if (gaussianFrame && gaussianFallback.texture() != 0) {
            texturedRounded(gaussianFallback.texture(), x, y, width, height, radius, radius, radius, radius,
                    framebufferU(x), framebufferV(y),
                    framebufferU(x + width), framebufferV(y + height), 0xFFFFFFFF);
        }
        roundedRect(x, y, width, height, radius, tint);
    }

    public void rect(float x, float y, float width, float height, int color) {
        GlStateManager.disableTexture2D();
        applyColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + height);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
    }

    public void gradientRect(float x, float y, float width, float height, int top, int bottom) {
        GlStateManager.disableTexture2D();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        applyColor(top);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        applyColor(bottom);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
    }

    private void gradientRectHorizontal(float x, float y, float width, float height, int left, int right) {
        GlStateManager.disableTexture2D();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        applyColor(left);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + height);
        applyColor(right);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
    }

    public void roundedRect(float x, float y, float width, float height, float radius, int color) {
        roundedRect(x, y, width, height, radius, radius, radius, radius, color);
    }

    public void gradientRoundedRect(float x, float y, float width, float height,
                                    float radius, int leftColor, int rightColor) {
        if (shapes.horizontalFill(x, y, width, height, radius, leftColor, rightColor)) return;
        gradientRectHorizontal(x, y, width, height, leftColor, rightColor);
    }

    public void roundedRect(float x, float y, float width, float height,
                            float topLeft, float topRight, float bottomRight, float bottomLeft, int color) {
        if (shapes.fill(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color)) return;
        polygonRoundedRect(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color);
    }

    private void polygonRoundedRect(float x, float y, float width, float height,
                                    float topLeft, float topRight, float bottomRight, float bottomLeft, int color) {
        GlStateManager.disableTexture2D();
        applyColor(color);
        GL11.glBegin(GL11.GL_POLYGON);
        corner(x + topLeft, y + topLeft, topLeft, 180, 270, false);
        corner(x + width - topRight, y + topRight, topRight, 270, 360, false);
        corner(x + width - bottomRight, y + height - bottomRight, bottomRight, 0, 90, false);
        corner(x + bottomLeft, y + height - bottomLeft, bottomLeft, 90, 180, false);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
    }

    public void outline(float x, float y, float width, float height, float radius, float thickness, int color) {
        if (shapes.outline(x, y, width, height, radius, thickness, color)) return;
        GlStateManager.disableTexture2D();
        applyColor(color);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(thickness);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        corner(x + radius, y + radius, radius, 180, 270, false);
        corner(x + width - radius, y + radius, radius, 270, 360, false);
        corner(x + width - radius, y + height - radius, radius, 0, 90, false);
        corner(x + radius, y + height - radius, radius, 90, 180, false);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableTexture2D();
    }

    public void shadow(float x, float y, float width, float height, float radius,
                       float offsetX, float offsetY, float blurRadius, float spread, int color) {
        shadows.draw(x, y, width, height, radius, offsetX, offsetY, blurRadius, spread, color);
    }

    public void image(String name, float x, float y, float width, float height, int color) {
        UiTexture texture = texture(name);
        drawImage(texture, x, y, width, height, color);
    }

    /** Draw an image from a path relative to /assets/myau/. */
    public void imageResource(String resourcePath, float x, float y, float width, float height, int color) {
        String key = "resource:" + resourcePath;
        UiTexture texture = textures.get(key);
        if (texture == null) {
            texture = new UiTexture(resourcePath);
            textures.put(key, texture);
        }
        drawImage(texture, x, y, width, height, color);
    }

    public void imageContained(String name, float x, float y, float width, float height, int color) {
        UiTexture texture = texture(name);
        float targetAspect = width / height;
        float sourceAspect = texture.aspect();
        if (targetAspect > sourceAspect) {
            float containedWidth = height * sourceAspect;
            x += (width - containedWidth) * 0.5F;
            width = containedWidth;
        } else {
            float containedHeight = width / sourceAspect;
            y += (height - containedHeight) * 0.5F;
            height = containedHeight;
        }
        drawImage(texture, x, y, width, height, color);
    }

    private void drawImage(UiTexture texture, float x, float y, float width, float height, int color) {
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(texture.id());
        applyColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
        GlStateManager.color(1, 1, 1, 1);
    }

    public void pushClip(UiBounds requested) {
        UiBounds clip = requested;
        if (!clips.isEmpty()) clip = intersect(clips.peek(), requested);
        clips.push(clip);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                transform.scissorX(clip.x),
                transform.scissorY(clip.y, clip.height, mc.displayHeight),
                transform.scissorWidth(clip.width),
                transform.scissorHeight(clip.height)
        );
    }

    public void popClip() {
        if (clips.isEmpty()) return;
        clips.pop();
        if (clips.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            UiBounds clip = clips.peek();
            GL11.glScissor(
                    transform.scissorX(clip.x),
                    transform.scissorY(clip.y, clip.height, mc.displayHeight),
                    transform.scissorWidth(clip.width),
                    transform.scissorHeight(clip.height)
            );
        }
    }

    public void delete() {
        gaussianFallback.delete();
        shadows.delete();
        shapes.delete();
        fonts.delete();
        for (UiTexture texture : textures.values()) texture.delete();
        textures.clear();
    }

    private void texturedRounded(int texture, float x, float y, float width, float height,
                                 float tl, float tr, float br, float bl,
                                 float u0, float v0, float u1, float v1, int color) {
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(texture);
        applyColor(color);
        GL11.glBegin(GL11.GL_POLYGON);
        texturedCorner(x + tl, y + tl, tl, 180, 270, x, y, width, height, u0, v0, u1, v1);
        texturedCorner(x + width - tr, y + tr, tr, 270, 360, x, y, width, height, u0, v0, u1, v1);
        texturedCorner(x + width - br, y + height - br, br, 0, 90, x, y, width, height, u0, v0, u1, v1);
        texturedCorner(x + bl, y + height - bl, bl, 90, 180, x, y, width, height, u0, v0, u1, v1);
        GL11.glEnd();
    }

    private void texturedCorner(float cx, float cy, float radius, float start, float end,
                                float x, float y, float width, float height,
                                float u0, float v0, float u1, float v1) {
        if (radius <= 0) {
            float px = cx;
            float py = cy;
            GL11.glTexCoord2f(lerp(u0, u1, (px - x) / width), lerp(v0, v1, (py - y) / height));
            GL11.glVertex2f(px, py);
            return;
        }
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            double angle = Math.toRadians(start + (end - start) * i / CORNER_SEGMENTS);
            float px = cx + (float) Math.cos(angle) * radius;
            float py = cy + (float) Math.sin(angle) * radius;
            GL11.glTexCoord2f(lerp(u0, u1, (px - x) / width), lerp(v0, v1, (py - y) / height));
            GL11.glVertex2f(px, py);
        }
    }

    private void corner(float cx, float cy, float radius, float start, float end, boolean unused) {
        if (radius <= 0) {
            GL11.glVertex2f(cx, cy);
            return;
        }
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            double angle = Math.toRadians(start + (end - start) * i / CORNER_SEGMENTS);
            GL11.glVertex2f(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
        }
    }

    private float framebufferU(float designX) {
        return (transform.scissorX(designX)) / (float) mc.displayWidth;
    }

    private float framebufferV(float designY) {
        float physicalY = transform.scissorY(designY, 0, mc.displayHeight);
        return physicalY / mc.displayHeight;
    }

    private static UiBounds intersect(UiBounds a, UiBounds b) {
        float x = Math.max(a.x, b.x);
        float y = Math.max(a.y, b.y);
        float right = Math.min(a.x + a.width, b.x + b.width);
        float bottom = Math.min(a.y + a.height, b.y + b.height);
        return new UiBounds(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    private static void applyColor(int color) {
        GL11.glColor4f(
                ((color >> 16) & 255) / 255.0F,
                ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F,
                ((color >>> 24) & 255) / 255.0F
        );
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}

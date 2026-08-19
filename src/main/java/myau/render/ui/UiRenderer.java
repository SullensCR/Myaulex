package myau.render.ui;

import myau.event.EventTarget;
import myau.events.ResizeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class UiRenderer {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-UI");
    // Backdrop textures use this path as their rounded mask. More segments
    // keep the blur edge smooth even when the shape shader is unavailable.
    private static final int CORNER_SEGMENTS = 24;

    private static final boolean GL_DIAGNOSTICS = Boolean.getBoolean("myau.ui.glDiagnostics");
    private static final String[] PRELOAD_RESOURCES = {
            "notifications/Icons/Icon=Info.svg",
            "notifications/Icons/Icon=Warning.svg",
            "notifications/Icons/Icon=Error.svg",
            "notifications/Icons/Icon=Analyze.svg",
            "notifications/Icons/Icon=Config-Error.svg",
            "notifications/Icons/Icon=Config-Success.svg",
            "notifications/Icons/Icon=Config-Edit.svg",
            "notifications/Icons/Icon=Enabled.svg",
            "notifications/Icons/Icon=Disabled.svg",
            "notifications/throbber/Track.svg",
            "notifications/throbber/Filled Track.svg",
            "ui/targethud/heart.svg",
            "ui/progressbar/progressbar-box@2x.png",
            "ui/progressbar/track@2x.png",
            "ui/icons/combat.png",
            "ui/icons/movement.png",
            "ui/icons/visuals.png",
            "ui/icons/player.png",
            "ui/icons/utilities.png",
            "ui/icons/module.png",
            "ui/icons/search.png",
            "ui/icons/dropdown.png",
            "ui/icons/eye-on-bg.png",
            "ui/icons/eye-on.png",
            "ui/icons/eye-off-bg.png",
            "ui/icons/eye-off.png"
    };

    private final Minecraft mc = Minecraft.getMinecraft();
    private final UiFonts fonts = new UiFonts();
    private final UiBackdropBlur blur = UiBackdropBlur.shared();
    private final UiShadowRenderer shadows = new UiShadowRenderer();
    private final UiShapeRenderer shapes = new UiShapeRenderer();
    private final UiResourceCache<UiTexture> textures = new UiResourceCache<>();
    private final Set<String> failedTextures = new HashSet<>();
    private final Deque<UiBounds> clips = new ArrayDeque<>();
    private final UiRendererLifecycle lifecycle = new UiRendererLifecycle();
    private UiTransform transform;
    private UiGlStateSnapshot stateSnapshot;
    private final String defaultOwner;
    private String activeOwner;
    private int backdropTexture;
    private float backdropStrength = 1.0F;

    public UiRenderer() {
        this("UI");
    }

    public UiRenderer(String diagnosticOwner) {
        this.defaultOwner = diagnosticOwner;
        this.activeOwner = diagnosticOwner;
    }

    /**
     * Preloads fixed modern-UI resources at the GL-ready end of Minecraft startup.
     * Framebuffer-sized blur targets remain deferred until the first real frame.
     */
    public void initialize(float arraylistScale) {
        if (!lifecycle.beginInitialization()) return;

        UiGlStateSnapshot snapshot = null;
        boolean pushed = false;
        try {
            snapshot = UiGlStateSnapshot.capture();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            pushed = true;

            blur.preloadShader();
            shapes.preload();
            shadows.preload();

            List<UiFont> startupFonts = startupFonts(arraylistScale);
            for (UiFont font : startupFonts) font.preloadShadowShader();
            for (String resource : PRELOAD_RESOURCES) resource(resource);

            LOGGER.info("Modern UI renderer initialized at client startup: fonts={}, textures={}, blurSupported={}",
                    startupFonts.size(), textures.size(), blur.isSupported());
        } catch (Throwable failure) {
            LOGGER.error("Modern UI startup preload was incomplete; lazy fallbacks remain available.", failure);
        } finally {
            try {
                if (pushed) GL11.glPopAttrib();
            } finally {
                if (snapshot != null) snapshot.restore();
            }
        }
    }

    public boolean isInitialized() {
        return lifecycle.isInitialized();
    }

    public boolean isSupported() {
        return blur.isSupported();
    }

    public UiFonts fonts() {
        return fonts;
    }

    public UiTexture texture(String name) {
        return resource("ui/icons/" + name + ".png");
    }

    /** Load or return a shared texture from a path relative to /assets/myau/. */
    public UiTexture resource(final String resourcePath) {
        if (resourcePath == null || failedTextures.contains(resourcePath)) return null;
        try {
            return textures.get(resourcePath, new UiResourceCache.Loader<UiTexture>() {
                @Override
                public UiTexture load() {
                    return new UiTexture(resourcePath);
                }
            });
        } catch (RuntimeException failure) {
            failedTextures.add(resourcePath);
            LOGGER.warn("Unable to load optional modern UI texture /assets/myau/{}; omitting it.",
                    resourcePath, failure);
            return null;
        }
    }

    public void beginFrame(UiTransform transform, float blurRadius) {
        beginFrame(defaultOwner, transform, blurRadius, 1.0F, true);
    }

    public void beginFrame(String owner, UiTransform transform, float blurRadius) {
        beginFrame(owner, transform, blurRadius, 1.0F, true);
    }

    /** Starts a frame with explicit scene-blur controls for modern UI consumers. */
    public void beginFrame(String owner, UiTransform transform, float blurRadius,
                           float blurStrength, boolean blurEnabled) {
        lifecycle.beginFrame();
        this.activeOwner = owner == null ? defaultOwner : owner;
        this.transform = transform;
        this.backdropStrength = clamp01(blurStrength);
        boolean attributesPushed = false;
        boolean matrixPushed = false;
        boolean initialized = false;
        try {
            this.stateSnapshot = UiGlStateSnapshot.capture();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attributesPushed = true;
            reportGlErrors("before beginFrame");
            backdropTexture = blurEnabled && backdropStrength > 0.0F
                    ? blur.capture(Math.max(0.0F, blurRadius)) : 0;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            matrixPushed = true;
            GL11.glTranslatef(transform.getLogicalX(), transform.getLogicalY(), 0);
            GL11.glScalef(transform.getLogicalScale(), transform.getLogicalScale(), 1);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableAlpha();
            GlStateManager.disableCull();
            initialized = true;
        } finally {
            if (!initialized) {
                try {
                    if (matrixPushed) {
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        GL11.glPopMatrix();
                    }
                    if (attributesPushed) GL11.glPopAttrib();
                    restoreState();
                } finally {
                    this.transform = null;
                    this.backdropTexture = 0;
                    this.backdropStrength = 1.0F;
                    this.activeOwner = defaultOwner;
                    lifecycle.endFrame();
                }
            }
        }
    }

    public void endFrame() {
        if (!lifecycle.isFrameActive()) return;
        try {
            while (!clips.isEmpty()) popClip();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.depthMask(true);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
        } finally {
            try {
                GL11.glPopAttrib();
            } finally {
                try {
                    restoreState();
                    reportGlErrors("after endFrame");
                } finally {
                    lifecycle.endFrame();
                    transform = null;
                    backdropTexture = 0;
                    backdropStrength = 1.0F;
                    activeOwner = defaultOwner;
                }
            }
        }
    }

    private void restoreState() {
        if (stateSnapshot != null) stateSnapshot.restore();
        stateSnapshot = null;
    }

    public void backdrop(float x, float y, float width, float height, float radius, int tint) {
        if (backdropTexture != 0 && backdropStrength > 0.0F) {
            texturedRounded(backdropTexture, x, y, width, height, radius, radius, radius, radius,
                    framebufferU(x), framebufferV(y),
                    framebufferU(x + width), framebufferV(y + height),
                    withAlpha(0xFFFFFFFF, Math.round(backdropStrength * 255.0F)));
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
        if (texture != null) drawImage(texture, x, y, width, height, color);
    }

    public void image(UiTexture texture, float x, float y, float width, float height, int color) {
        if (texture != null) drawImage(texture, x, y, width, height, color);
    }

    /** Draw an image from a path relative to /assets/myau/. */
    public void imageResource(String resourcePath, float x, float y, float width, float height, int color) {
        UiTexture texture = resource(resourcePath);
        if (texture != null) drawImage(texture, x, y, width, height, color);
    }

    public void imageContained(String name, float x, float y, float width, float height, int color) {
        UiTexture texture = texture(name);
        imageContained(texture, x, y, width, height, color);
    }

    public void imageContained(UiTexture texture, float x, float y, float width, float height, int color) {
        if (texture == null) return;
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
        blur.delete();
        shadows.delete();
        shapes.delete();
        fonts.delete();
        textures.clear(new UiResourceCache.Disposer<UiTexture>() {
            @Override
            public void dispose(UiTexture value) {
                value.delete();
            }
        });
        failedTextures.clear();
    }

    @EventTarget
    public void onResize(ResizeEvent event) {
        blur.invalidateFramebuffers();
        lifecycle.invalidateFramebuffers();
    }

    private void reportGlErrors(String stage) {
        if (!GL_DIAGNOSTICS) return;
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            org.apache.logging.log4j.LogManager.getLogger("Myaulex-UI")
                    .error("OpenGL error {} {} for {}", error, stage, activeOwner);
        }
    }

    private List<UiFont> startupFonts(float arraylistScale) {
        List<UiFont> result = new ArrayList<>();
        addUnique(result, fonts.snPro(30.0F, UiFonts.BOLD));
        addUnique(result, fonts.snPro(24.0F, UiFonts.REGULAR));
        addUnique(result, fonts.snPro(32.0F, UiFonts.BLACK));
        addUnique(result, fonts.snPro(32.0F * Math.max(0.5F, Math.min(1.5F, arraylistScale)), UiFonts.BLACK));
        addUnique(result, fonts.snPro(50.0F, UiFonts.BLACK));
        addUnique(result, fonts.snPro(58.0F, UiFonts.BLACK));
        addUnique(result, fonts.snPro(23.0F, UiFonts.REGULAR));
        addUnique(result, fonts.snPro(23.0F, UiFonts.SEMIBOLD));
        addUnique(result, fonts.snPro(32.0F, UiFonts.SEMIBOLD));
        addUnique(result, fonts.mojang(20.0F));
        addUnique(result, fonts.mojang(13.0F));
        addUnique(result, fonts.minecraft(38.0F));
        addUnique(result, fonts.google(14.0F, UiFonts.REGULAR));
        addUnique(result, fonts.google(15.0F, UiFonts.REGULAR));
        addUnique(result, fonts.google(16.0F, UiFonts.SEMIBOLD));
        addUnique(result, fonts.google(18.0F, UiFonts.REGULAR));
        addUnique(result, fonts.google(18.0F, UiFonts.SEMIBOLD));
        addUnique(result, fonts.google(20.0F, UiFonts.SEMIBOLD));
        addUnique(result, fonts.google(20.0F, UiFonts.BLACK));
        addUnique(result, fonts.google(24.0F, UiFonts.SEMIBOLD));
        return result;
    }

    private static void addUnique(List<UiFont> fonts, UiFont font) {
        if (!fonts.contains(font)) fonts.add(font);
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

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, value));
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

package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorGuiChat;
import myau.module.Module;
import myau.render.ArraylistLayout;
import myau.render.ui.UiFont;
import myau.render.ui.UiFonts;
import myau.render.ui.UiRenderer;
import myau.render.ui.UiTransform;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import myau.util.font.IFont;
import myau.util.shader.RoundedUtils;
import myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final UiFonts WATERMARK_FONTS = new UiFonts();
    private UiFont watermarkFont;
    public static int targetHUDX = 100;
    public static int targetHUDY = 100;

    public static void setTargetHUDPosition(int x, int y) {
        targetHUDX = x;
        targetHUDY = y;
    }

    public static void resetTargetHUDPosition() {
        targetHUDX = 100;
        targetHUDY = 100;
    }

    private List<Module> activeModules = new ArrayList<>();
    private final Map<Module, Float> moduleAnimations = new HashMap<>();
    private UiRenderer arraylistRenderer;
    private boolean arraylistRendererUnavailable;
    private float blinkTimerAlpha;
    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});
    // Retained for the blink timer and modules that still use the classic HUD text path.
    // The redesigned arraylist uses the same heavy SN Pro face as the watermark.
    public final ModeProperty font = new ModeProperty("font", 0, new String[]{"NUNITO", "PRODUCT_SANS", "TENACITY", "VISION", "MINECRAFT"});
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final FloatProperty progressbarSize = new FloatProperty("progressbar-size", 1.0F, 0.5F, 2.0F);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty watermark = new BooleanProperty("watermark", true);
    public final FloatProperty watermarkScale = new FloatProperty("watermark-scale", 1.0F, 0.25F, 2.0F,
            this.watermark::getValue);
    public final IntProperty watermarkOffsetX = new IntProperty("watermark-offset-x", 8, 0, 512,
            this.watermark::getValue);
    public final IntProperty watermarkOffsetY = new IntProperty("watermark-offset-y", 8, 0, 512,
            this.watermark::getValue);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final FloatProperty colorDistance = new FloatProperty("color-dist", 50F, 10F, 100F);
    public final ModeProperty fontMode = new ModeProperty("font-mode", 0, new String[]{"SANS", "MINECRAFT", "NUNITO"});

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix().clone();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private IFont getHudFont() {
        FontManager.initializeFonts();
        switch (this.font.getValue()) {
            case 1:
                return FontManager.productSans18 != null ? FontManager.productSans18 : FontManager.getMinecraft();
            case 2:
                return FontManager.tenacity16 != null ? FontManager.tenacity16 : FontManager.getMinecraft();
            case 3:
                return FontManager.vision16 != null ? FontManager.vision16 : FontManager.getMinecraft();
            case 4:
                return FontManager.getMinecraft();
            case 0:
            default:
                return FontManager.nunito18 != null ? FontManager.nunito18 : FontManager.getMinecraft();
        }
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public HUD() {
        super("HUD", true, true);
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        switch (this.colorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.custom1.getValue());
                break;
            case 4:
                double cycle1 = this.getColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.custom1.getValue()),
                        new Color(this.custom2.getValue())
                );
                break;
            case 5:
                double cycle2 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(this.custom1.getValue()), new Color(this.custom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.custom2.getValue()), new Color(this.custom3.getValue()));
                }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F)
        );
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            for (Module module : Myau.moduleManager.ordinaryModules()) {
                float current = this.moduleAnimations.containsKey(module) ? this.moduleAnimations.get(module) : 0.0F;
                float target = module.isEnabled() && !module.isHidden() ? 1.0F : 0.0F;
                float next = current + (target - current) * 0.22F;
                if (Math.abs(next - target) < 0.02F) {
                    next = target;
                }
                if (next > 0.0F) {
                    this.moduleAnimations.put(module, next);
                } else {
                    this.moduleAnimations.remove(module);
                }
            }
            this.activeModules = Myau.moduleManager.ordinaryModules().stream()
                    .filter(module -> this.moduleAnimations.containsKey(module))
                    .collect(Collectors.<Module>toList());
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Myau.commandManager != null && Myau.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            this.renderWatermark();
            this.renderArraylist();
            this.renderBlinkTimer();
        }
    }

    private void renderArraylist() {
        if (this.activeModules.isEmpty() || this.arraylistRendererUnavailable) return;
        try {
            if (this.arraylistRenderer == null) this.arraylistRenderer = new UiRenderer();
            if (!this.arraylistRenderer.isSupported()) {
                this.arraylistRendererUnavailable = true;
                return;
            }

            float userScale = this.scale.getValue();
            UiTransform transform = new UiTransform(mc, 1920.0F, 1080.0F, 1.0F, 0.0F);
            this.arraylistRenderer.beginFrame(transform, 31.0F);
            try {
                UiFont baseFont = this.arraylistRenderer.fonts().snPro(ArraylistLayout.FONT_SIZE, UiFonts.BLACK);
                UiFont font = this.arraylistRenderer.fonts().snPro(ArraylistLayout.FONT_SIZE * userScale, UiFonts.BLACK);
                List<Module> modules = new ArrayList<>(this.activeModules);
                Collections.sort(modules, new Comparator<Module>() {
                    @Override
                    public int compare(Module left, Module right) {
                        return Float.compare(arraylistWidth(right, baseFont), arraylistWidth(left, baseFont));
                    }
                });

                float cursor = this.posY.getValue() == 0
                        ? this.offsetY.getValue() * userScale
                        : 1080.0F - this.offsetY.getValue() * userScale;
                for (Module module : modules) {
                    float alpha = this.moduleAnimations.containsKey(module) ? this.moduleAnimations.get(module) : 1.0F;
                    String name = this.getModuleName(module);
                    String suffix = this.arraylistSuffix(module);
                    float width = ArraylistLayout.cardWidth(baseFont.width(name), baseFont.width(suffix)) * userScale;
                    float height = ArraylistLayout.CARD_HEIGHT * userScale;
                    float x = this.posX.getValue() == 0
                            ? this.offsetX.getValue() * userScale
                            : 1920.0F - this.offsetX.getValue() * userScale - width;
                    float y = this.posY.getValue() == 0 ? cursor : cursor - height;
                    x += (1.0F - alpha) * (this.posX.getValue() == 0 ? -12.0F : 12.0F) * userScale;
                    this.drawArraylistCard(name, suffix, x, y, width, height, userScale, alpha, font);
                    cursor = this.posY.getValue() == 0
                            ? ArraylistLayout.nextTopCursor(cursor, userScale, alpha)
                            : ArraylistLayout.nextBottomCursor(cursor, userScale, alpha);
                }
            } finally {
                this.arraylistRenderer.endFrame();
            }
        } catch (Throwable ignored) {
            // The modern renderer is optional; a graphics capability failure must not break the HUD.
            this.arraylistRendererUnavailable = true;
        }
    }

    private float arraylistWidth(Module module, UiFont font) {
        return ArraylistLayout.cardWidth(font.width(this.getModuleName(module)), font.width(this.arraylistSuffix(module)));
    }

    private String arraylistSuffix(Module module) {
        if (!this.suffixes.getValue()) return "";
        String[] parts = this.getModuleSuffix(module);
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(part);
        }
        return result.toString();
    }

    private void drawArraylistCard(String name, String suffix, float x, float y, float width, float height,
                                   float scale, float visibility, UiFont font) {
        int alpha = Math.round(ArraylistLayout.clamp01(visibility) * 255.0F);
        int panelAlpha = Math.round(0xB2 * alpha / 255.0F);
        int shadowAlpha = Math.round(0x63 * alpha / 255.0F);
        this.arraylistRenderer.shadow(x, y, width, height, ArraylistLayout.CARD_RADIUS * scale,
                0.0F, 3.0F * scale, 10.0F * scale, 0.0F, this.withAlpha(0x63000000, shadowAlpha));
        this.arraylistRenderer.backdrop(x, y, width, height, ArraylistLayout.CARD_RADIUS * scale,
                this.withAlpha(0xB21A1A24, panelAlpha));

        float pillX = x + ArraylistLayout.CARD_PADDING * scale;
        float pillY = ArraylistLayout.accentY(y, scale);
        this.arraylistRenderer.roundedRect(pillX, pillY, ArraylistLayout.ACCENT_WIDTH * scale,
                ArraylistLayout.ACCENT_HEIGHT * scale, 10.0F * scale, this.withAlpha(0xFF8FA7FF, alpha));

        float textY = y + (height - font.height()) * 0.5F;
        float textX = ArraylistLayout.textX(x, scale);
        font.draw(name, textX, textY, this.withAlpha(0xFFFFFFFF, alpha));
        if (!suffix.isEmpty()) {
            font.draw(suffix, textX + font.width(name) + ArraylistLayout.CONTENT_GAP * scale,
                    textY, this.withAlpha(0xFF8FA7FF, alpha));
        }
    }

    private void renderBlinkTimer() {
        if (!this.blinkTimer.getValue()) return;
        BlinkModules blinkingModule = Myau.blinkManager.getBlinkingModule();
        long movementPacketSize = Myau.blinkManager.countMovement();
        boolean showBlinkTimer = blinkingModule != BlinkModules.NONE
                && blinkingModule != BlinkModules.AUTO_BLOCK && movementPacketSize > 0L;
        this.blinkTimerAlpha += ((showBlinkTimer ? 1.0F : 0.0F) - this.blinkTimerAlpha) * 0.22F;
        if (this.blinkTimerAlpha <= 0.02F) return;

        IFont renderer = this.getHudFont();
        String blinkText = String.valueOf(movementPacketSize);
        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        renderer.drawString(
                blinkText,
                (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / this.scale.getValue()
                        - (float) renderer.width(blinkText) / 2.0F,
                (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 3.0F / this.scale.getValue(),
                this.withAlpha(this.getColor(System.currentTimeMillis()).getRGB(), (int) (190.0F * this.blinkTimerAlpha)),
                this.shadow.getValue()
        );
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /** Resolution-independent vector recreation of the exported watermark. */
    private void renderWatermark() {
        if (!this.watermark.getValue()) return;

        float scale = this.watermarkScale.getValue();
        int x = this.watermarkOffsetX.getValue();
        int y = this.watermarkOffsetY.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);

        RoundedUtils.drawRound(0, 0, 293, 85, 20, 0x631A1A24);
        RoundedUtils.drawRound(0, 0, 293, 85, 20, 0x801A1A24);
        RoundedUtils.drawRound(5, 5, 75, 75, 15, 0xCC1A1A24);
        this.renderWatermarkCat();
        if (this.watermarkFont == null) {
            // Use the variable font's black axis for the heavy display
            // silhouette in the reference wordmark.
            this.watermarkFont = WATERMARK_FONTS.snPro(50, UiFonts.BLACK);
        }
        String wordmark = "Myaulex";
        float visualWidth = this.watermarkFont.visualWidth(wordmark);
        float wordmarkX = 90.0F + (185.0F - visualWidth) / 2.0F;
        float wordmarkY = 5.0F;
        int wordmarkColor = 0xFFD4CFFE;
        this.watermarkFont.draw(wordmark, wordmarkX, wordmarkY, wordmarkColor, true);
        GlStateManager.popMatrix();
    }

    /** Cat mark traced from the supplied SVG paths; no bitmap is used. */
    private void renderWatermarkCat() {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(4.0F);
        RenderUtil.glColor(0xFFD4CFFE);
        drawWatermarkPath(new float[][]{{46.6F, 66.3F}, {54.0F, 65.8F}, {61.5F, 61.8F}, {66.5F, 54.8F}, {69.1F, 48.5F}});
        drawWatermarkPath(new float[][]{{18.4F, 65.2F}, {18.5F, 45.0F}, {20.8F, 29.4F}, {25.0F, 19.0F}, {29.1F, 18.5F}, {33.0F, 24.7F}, {37.6F, 32.8F}});
        drawWatermarkPath(new float[][]{{45.0F, 27.9F}, {49.5F, 28.2F}, {54.0F, 31.3F}, {58.2F, 36.5F}, {62.3F, 40.2F}, {67.9F, 41.8F}});
        drawWatermarkPath(new float[][]{{53.1F, 23.8F}, {50.8F, 18.7F}, {47.0F, 16.3F}, {43.7F, 20.2F}, {40.0F, 24.1F}});
        drawWatermarkPath(new float[][]{{47.8F, 41.0F}, {49.6F, 40.8F}, {51.0F, 42.7F}, {50.0F, 45.0F}, {48.0F, 44.7F}, {47.2F, 42.8F}});
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawWatermarkPath(float[][] points) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (float[] point : points) GL11.glVertex2f(point[0], point[1]);
        GL11.glEnd();
    }

    private float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private int withAlpha(int color, int alpha) {
        return RenderUtil.mergeAlpha(color, alpha);
    }
}

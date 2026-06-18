package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorGuiChat;
import myau.module.Module;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import myau.util.font.IFont;
import myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
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
    public final ModeProperty font = new ModeProperty("font", 0, new String[]{"NUNITO", "PRODUCT_SANS", "TENACITY", "VISION", "MINECRAFT"});
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("background", 25);
    public final BooleanProperty showBar = new BooleanProperty("bar", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);
    public final ModeProperty notificationPosition = new ModeProperty("notification-position", 0, new String[]{"BOTTOM_RIGHT", "TOP_RIGHT", "BOTTOM_LEFT", "TOP_LEFT"}, this.toggleAlerts::getValue);
    public final IntProperty notificationDuration = new IntProperty("notification-duration-ms", 2500, 500, 8000, this.toggleAlerts::getValue);
    public final FloatProperty notificationScale = new FloatProperty("notification-scale", 1.0F, 0.5F, 1.5F, this.toggleAlerts::getValue);
    public final ColorProperty notificationBackground = new ColorProperty("notification-background", new Color(10, 12, 16, 160).getRGB(), this.toggleAlerts::getValue);
    public final ColorProperty notificationEnabledColor = new ColorProperty("notification-enabled-color", new Color(65, 217, 130).getRGB(), this.toggleAlerts::getValue);
    public final ColorProperty notificationDisabledColor = new ColorProperty("notification-disabled-color", new Color(255, 92, 108).getRGB(), this.toggleAlerts::getValue);

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private int getModuleWidth(Module module) {
        return this.calculateStringWidth(
                this.getModuleName(module), this.getModuleSuffix(module)
        );
    }

    private int calculateStringWidth(String string, String[] arr) {
        IFont renderer = this.getHudFont();
        int width = (int) Math.ceil(renderer.width(string));
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + (int) Math.ceil(renderer.width(str));
            }
        }
        return width;
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
            for (Module module : Myau.moduleManager.modules.values()) {
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
            this.activeModules = Myau.moduleManager.modules.values().stream().filter(module -> this.moduleAnimations.containsKey(module)).sorted(Comparator.comparingInt(this::getModuleWidth).reversed()).collect(Collectors.<Module>toList());
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
            IFont renderer = this.getHudFont();
            float height = (float) renderer.height() - 1.0F;
            float x = (float) this.offsetX.getValue()
                    + (1.0F + (this.showBar.getValue() ? (this.shadow.getValue() ? 2.0F : 1.0F) : 0.0F)) * this.scale.getValue();
            float y = (float) this.offsetY.getValue() + 1.0F * this.scale.getValue();
            if (this.posX.getValue() == 1) {
                x = (float) new ScaledResolution(mc).getScaledWidth() - x;
            }
            if (this.posY.getValue() == 1) {
                y = (float) new ScaledResolution(mc).getScaledHeight() - y - height * this.scale.getValue();
            }
            GlStateManager.pushMatrix();
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
            long l = System.currentTimeMillis();
            long offset = 0L;
            for (Module module : this.activeModules) {
                float alpha = this.moduleAnimations.containsKey(module) ? this.moduleAnimations.get(module) : 1.0F;
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                float totalWidth = (float) (this.calculateStringWidth(moduleName, moduleSuffix) - (this.shadow.getValue() ? 0 : 1));
                int color = this.withAlpha(this.getColor(l, offset).getRGB(), (int) (255.0F * alpha));
                float slide = (1.0F - alpha) * (this.posX.getValue() == 0 ? -8.0F : 8.0F);
                float renderX = x / this.scale.getValue() + slide;
                float renderY = y / this.scale.getValue();
                RenderUtil.enableRenderState();
                if (this.background.getValue() > 0) {
                    RenderUtil.drawRect(
                            renderX - 1.0F - (this.posX.getValue() == 0 ? 0.0F : totalWidth),
                            renderY - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : (this.shadow.getValue() ? 1.0F : 0.0F)),
                            renderX + 1.0F + (this.posX.getValue() == 0 ? totalWidth : 0.0F),
                            renderY + height + (this.posY.getValue() == 0 ? (this.shadow.getValue() ? 1.0F : 0.0F) : (offset == 0L ? 1.0F : 0.0F)),
                            new Color(0.0F, 0.0F, 0.0F, this.background.getValue().floatValue() / 100.0F * alpha).getRGB()
                    );
                }
                if (this.showBar.getValue()) {
                    if (this.shadow.getValue()) {
                        RenderUtil.drawRect(
                                renderX + (this.posX.getValue() == 0 ? -3.0F : 1.0F),
                                renderY - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : 1.0F),
                                renderX + (this.posX.getValue() == 0 ? -2.0F : 2.0F),
                                renderY + height + (this.posY.getValue() == 0 ? 1.0F : (offset == 0L ? 1.0F : 0.0F)),
                                color
                        );
                        RenderUtil.drawRect(
                                renderX + (this.posX.getValue() == 0 ? -2.0F : 2.0F),
                                renderY - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : 1.0F),
                                renderX + (this.posX.getValue() == 0 ? -1.0F : 3.0F),
                                renderY + height + (this.posY.getValue() == 0 ? 1.0F : (offset == 0L ? 1.0F : 0.0F)),
                                (color & 16579836) >> 2 | color & 0xFF000000
                        );
                    } else {
                        RenderUtil.drawRect(
                                renderX + (this.posX.getValue() == 0 ? -2.0F : 1.0F),
                                renderY - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : 0.0F),
                                renderX + (this.posX.getValue() == 0 ? -1.0F : 2.0F),
                                renderY + height + (this.posY.getValue() == 0 ? 0.0F : (offset == 0L ? 1.0F : 0.0F)),
                                color
                        );
                    }
                }
                RenderUtil.disableRenderState();
                GlStateManager.disableDepth();
                renderer.drawString(
                        moduleName,
                        renderX - (this.posX.getValue() == 1 ? totalWidth : 0.0F),
                        renderY + (!this.shadow.getValue() && this.posY.getValue() == 1 ? 1.0F : 0.0F),
                        color,
                        this.shadow.getValue()
                );
                if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                    float width = (float) renderer.width(moduleName) + 3.0F;
                    for (String string : moduleSuffix) {
                        renderer.drawString(
                            string,
                                renderX - (this.posX.getValue() == 1 ? totalWidth : 0.0F) + width,
                                renderY + (!this.shadow.getValue() && this.posY.getValue() == 1 ? 1.0F : 0.0F),
                                this.withAlpha(ChatColors.GRAY.toAwtColor(), (int) (255.0F * alpha)),
                                this.shadow.getValue()
                        );
                        width += (float) renderer.width(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
                    }
                }
                y += (height + (this.shadow.getValue() ? 1.0F : 0.0F)) * this.scale.getValue() * alpha * (this.posY.getValue() == 0 ? 1.0F : -1.0F);
                offset++;
            }
            if (this.blinkTimer.getValue()) {
                BlinkModules blinkingModule = Myau.blinkManager.getBlinkingModule();
                long movementPacketSize = Myau.blinkManager.countMovement();
                boolean showBlinkTimer = blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK && movementPacketSize > 0L;
                this.blinkTimerAlpha += ((showBlinkTimer ? 1.0F : 0.0F) - this.blinkTimerAlpha) * 0.22F;
                if (this.blinkTimerAlpha > 0.02F) {
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        String blinkText = String.valueOf(movementPacketSize);
                        renderer.drawString(
                                blinkText,
                                (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / this.scale.getValue()
                                        - (float) renderer.width(blinkText) / 2.0F,
                                (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 3.0F / this.scale.getValue(),
                                this.withAlpha(this.getColor(l, offset).getRGB(), (int) (190.0F * this.blinkTimerAlpha)),
                                this.shadow.getValue()
                        );
                        GlStateManager.disableBlend();
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
            this.renderNotifications();
        }
    }

    private void renderNotifications() {
        if (!this.toggleAlerts.getValue() || Myau.notificationManager == null) {
            return;
        }

        List<myau.management.NotificationManager.NotificationEntry> entries = Myau.notificationManager.getActive();
        if (entries.isEmpty()) {
            return;
        }

        IFont renderer = this.getHudFont();
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        float notificationScale = Math.max(0.5F, Math.min(1.5F, this.notificationScale.getValue()));
        float screenWidth = scaledResolution.getScaledWidth() / notificationScale;
        float screenHeight = scaledResolution.getScaledHeight() / notificationScale;
        float margin = 8.0F;
        float paddingX = 8.0F;
        float paddingY = 5.0F;
        float spacing = 4.0F;
        float textHeight = (float) renderer.height();
        float boxHeight = textHeight + paddingY * 2.0F + 3.0F;
        boolean right = this.notificationPosition.getValue() == 0 || this.notificationPosition.getValue() == 1;
        boolean bottom = this.notificationPosition.getValue() == 0 || this.notificationPosition.getValue() == 2;
        float y = bottom ? screenHeight - margin : margin;

        GlStateManager.pushMatrix();
        GlStateManager.scale(notificationScale, notificationScale, 1.0F);

        for (int i = entries.size() - 1; i >= 0; i--) {
            myau.management.NotificationManager.NotificationEntry entry = entries.get(i);
            float alpha = this.notificationAlpha(entry);
            if (alpha <= 0.01F) {
                continue;
            }

            String text = entry.message;
            float boxWidth = Math.max(86.0F, (float) renderer.width(text) + paddingX * 2.0F + 2.0F);
            float x = right ? screenWidth - margin - boxWidth : margin;
            if (bottom) {
                y -= boxHeight;
            }

            this.drawNotification(entry, renderer, text, x, y, boxWidth, boxHeight, paddingX, paddingY, alpha, right);
            if (bottom) {
                y -= spacing;
            } else {
                y += boxHeight + spacing;
            }
        }

        GlStateManager.popMatrix();
    }

    private void drawNotification(
            myau.management.NotificationManager.NotificationEntry entry,
            IFont renderer,
            String text,
            float x,
            float y,
            float boxWidth,
            float boxHeight,
            float paddingX,
            float paddingY,
            float alpha,
            boolean right
    ) {
        float motion = this.notificationMotion(entry);
        float slide = (1.0F - motion) * (right ? 16.0F : -16.0F);
        float renderX = x + slide;
        int background = this.withAlpha(this.notificationBackground.getValue(), (int) (((this.notificationBackground.getValue() >>> 24) & 0xFF) * alpha));
        int border = this.withAlpha(Color.WHITE.getRGB(), (int) (24 * alpha));
        int depth = this.withAlpha(Color.BLACK.getRGB(), (int) (35 * alpha));
        int textColor = this.withAlpha(0xFFF1F5F9, (int) (245 * alpha));
        int statusColor = this.withAlpha(entry.color, (int) (245 * alpha));

        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRect(renderX + 1.0F, y + 1.5F, boxWidth, boxHeight, 7.0F, depth);
        RenderUtil.drawRoundedRect(renderX, y, boxWidth, boxHeight, 6.0F, background);
        RenderUtil.drawRoundedRectOutline(renderX + 0.5F, y + 0.5F, boxWidth - 1.0F, boxHeight - 1.0F, 6.0F, 1.0F, border, true, true, true, true);
        float progressWidth = boxWidth - 16.0F;
        RenderUtil.drawRoundedRect(renderX + 8.0F, y + boxHeight - 2.0F, Math.max(1.0F, progressWidth * this.notificationProgress(entry)), 1.0F, 0.5F, statusColor);
        RenderUtil.disableRenderState();

        this.drawNotificationText(renderer, text, renderX + paddingX + 1.0F, y + paddingY + 1.0F, textColor, statusColor);
    }

    private void drawNotificationText(IFont renderer, String text, float x, float y, int textColor, int statusColor) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" enabled")) {
            this.drawSplitNotificationText(renderer, text, " enabled", x, y, textColor, statusColor);
        } else if (lower.endsWith(" disabled")) {
            this.drawSplitNotificationText(renderer, text, " disabled", x, y, textColor, statusColor);
        } else {
            renderer.drawString(text, x, y, textColor, this.shadow.getValue());
        }
    }

    private void drawSplitNotificationText(IFont renderer, String text, String suffix, float x, float y, int textColor, int statusColor) {
        String main = text.substring(0, text.length() - suffix.length());
        renderer.drawString(main, x, y, textColor, this.shadow.getValue());
        renderer.drawString(suffix.trim(), x + (float) renderer.width(main + " "), y, statusColor, this.shadow.getValue());
    }

    private float notificationAlpha(myau.management.NotificationManager.NotificationEntry entry) {
        if (entry.durationMillis <= 0L) {
            return 1.0F;
        }

        float age = entry.getAge();
        float remaining = entry.durationMillis - age;
        float fade = Math.min(220.0F, entry.durationMillis / 3.0F);
        float alpha = Math.min(1.0F, Math.min(age / fade, remaining / fade));
        return this.smoothStep(Math.max(0.0F, Math.min(1.0F, alpha)));
    }

    private float notificationProgress(myau.management.NotificationManager.NotificationEntry entry) {
        if (entry.durationMillis <= 0L) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, 1.0F - entry.getAge() / (float) entry.durationMillis));
    }

    private float notificationMotion(myau.management.NotificationManager.NotificationEntry entry) {
        if (entry.durationMillis <= 0L) {
            return 1.0F;
        }

        float age = entry.getAge();
        float remaining = entry.durationMillis - age;
        float in = Math.max(0.0F, Math.min(1.0F, age / 260.0F));
        float out = Math.max(0.0F, Math.min(1.0F, remaining / 220.0F));
        return this.smoothStep(Math.min(in, out));
    }

    private float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private int withAlpha(int color, int alpha) {
        return RenderUtil.mergeAlpha(color, alpha);
    }
}

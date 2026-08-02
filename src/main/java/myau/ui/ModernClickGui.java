package myau.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.Myau;
import myau.config.Config;
import myau.module.Module;
import myau.module.modules.*;
import myau.property.Property;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.TextProperty;
import myau.ui.callback.GuiInput;
import myau.util.KeyBindUtil;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import myau.util.font.IFont;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModernClickGui extends GuiScreen {
    private static final float DEFAULT_PANEL_WIDTH = 160.0F;
    private static final float HEADER_HEIGHT = 38.0F;
    private static final float MODULE_HEIGHT = 27.0F;
    private static final float SETTING_HEIGHT = 22.0F;
    private static final int BACKGROUND = new Color(9, 10, 13, 190).getRGB();
    private static final int PANEL = new Color(27, 28, 34, 236).getRGB();
    private static final int PANEL_DARK = new Color(13, 14, 18, 210).getRGB();
    private static final int HEADER = new Color(34, 34, 40, 160).getRGB();
    private static final int ROW_LINE = new Color(58, 60, 68, 85).getRGB();
    private static final int TEXT_DISABLED = new Color(139, 139, 148).getRGB();
    private static final int TEXT_SETTING = new Color(168, 168, 178).getRGB();
    private static final int SWITCH_OFF = new Color(59, 61, 69).getRGB();
    private static final int SWITCH_KNOB = new Color(248, 248, 248).getRGB();

    private static ModernClickGui instance;

    private final File configFile = new File(Config.CONFIG_DIR, "clickgui-modern.txt");
    private final LinkedHashMap<String, CategoryPanel> panels = new LinkedHashMap<>();
    private final Set<Module> expandedModules = new HashSet<>();
    private final Map<Module, Float> toggleAnimations = new HashMap<>();
    private final Map<Module, Float> expandAnimations = new HashMap<>();
    private Module bindingModule;
    private CategoryPanel draggingPanel;
    private NumberDrag draggingNumber;
    private float dragOffsetX;
    private float dragOffsetY;

    public ModernClickGui() {
        instance = this;
        this.buildPanels();
    }

    public static ModernClickGui getInstance() {
        if (instance == null) {
            instance = new ModernClickGui();
        }
        return instance;
    }

    @Override
    public void initGui() {
        this.buildPanels();
    }

    private void buildPanels() {
        Map<String, float[]> previousPositions = new HashMap<>();
        for (CategoryPanel panel : this.panels.values()) {
            previousPositions.put(panel.name, new float[]{panel.x, panel.y, panel.scroll});
        }

        this.panels.clear();
        this.panels.put("Combat", new CategoryPanel("Combat", new Color(255, 92, 101).getRGB(), this.modules(
                AimAssist.class, AutoClicker.class, KillAura.class, Wtap.class, Velocity.class, Freeze.class,
                Reach.class, TargetStrafe.class, NoHitDelay.class, AntiFireball.class, LagRange.class, HitBox.class,
                MoreKB.class, Refill.class
        )));
        this.panels.put("Render", new CategoryPanel("Render", new Color(91, 164, 255).getRGB(), this.modules(
                ESP.class, Chams.class, FullBright.class, FKDRTracker.class, FloatingIsland.class, Tracers.class,
                NameTags.class, Xray.class, TargetHUD.class, Indicators.class, BedESP.class, ItemESP.class, ViewClip.class,
                NoHurtCam.class, HUD.class, GuiModule.class, ChestESP.class, Trajectories.class, Radar.class
        )));
        this.panels.put("Movement", new CategoryPanel("Movement", new Color(139, 86, 246).getRGB(), this.modules(
                AntiAFK.class, Fly.class, Speed.class, myau.module.modules.Timer.class, LongJump.class, Sprint.class,
                SafeWalk.class, Jesus.class, Blink.class, FakeLag.class, NoFall.class, NoSlow.class, KeepSprint.class,
                Eagle.class, NoJumpDelay.class, AntiVoid.class
        )));
        this.panels.put("Player", new CategoryPanel("Player", new Color(72, 200, 133).getRGB(), this.modules(
                AutoHeal.class, AutoTool.class, ChestStealer.class, InvManager.class, InvWalk.class, Scaffold.class,
                Clutch.class, AutoBlockIn.class, SpeedMine.class, FastPlace.class, GhostHand.class, MCF.class,
                AntiDebuff.class
        )));
        this.panels.put("Miscellaneous", new CategoryPanel("Miscellaneous", new Color(210, 165, 65).getRGB(), this.modules(
                AutoCaptcha.class, AutoRegister.class, Spammer.class, BedNuker.class, BedTracker.class, NoRotate.class,
                NickHider.class, ServerHider.class, AntiObfuscate.class, InventoryClicker.class
        )));

        for (CategoryPanel panel : this.panels.values()) {
            panel.modules.sort(Comparator.comparing(module -> module.getName().toLowerCase()));
        }

        this.addUncategorizedModules();
        this.applyDefaultPositions();

        for (CategoryPanel panel : this.panels.values()) {
            float[] position = previousPositions.get(panel.name);
            if (position != null) {
                panel.x = position[0];
                panel.y = position[1];
                panel.scroll = position[2];
            }
        }
        this.loadPositions();
    }

    private List<Module> modules(Class<?>... classes) {
        List<Module> modules = new ArrayList<>();
        for (Class<?> clazz : classes) {
            Module module = Myau.moduleManager.getModule(clazz);
            if (module != null && !module.isHidden()) {
                modules.add(module);
                this.toggleAnimations.putIfAbsent(module, module.isEnabled() ? 1.0F : 0.0F);
            }
        }
        return modules;
    }

    private void addUncategorizedModules() {
        Set<Module> registered = new HashSet<>();
        for (CategoryPanel panel : this.panels.values()) {
            registered.addAll(panel.modules);
        }

        CategoryPanel misc = this.panels.get("Miscellaneous");
        for (Module module : Myau.moduleManager.modules.values()) {
            if (module != null && !module.isHidden() && !registered.contains(module)) {
                misc.modules.add(module);
                this.toggleAnimations.putIfAbsent(module, module.isEnabled() ? 1.0F : 0.0F);
            }
        }
        misc.modules.sort(Comparator.comparing(module -> module.getName().toLowerCase()));
    }

    private void applyDefaultPositions() {
        float panelWidth = this.panelWidth();
        float gap = this.panelGap(panelWidth);
        float totalWidth = this.panels.size() * panelWidth + (this.panels.size() - 1) * gap;
        float x = Math.max(0.0F, (this.width - totalWidth) / 2.0F);
        float y = 10.0F;

        for (CategoryPanel panel : this.panels.values()) {
            panel.x = x;
            panel.y = y;
            panel.width = panelWidth;
            x += panelWidth + gap;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        FontManager.initializeFonts();
        IFont titleFont = FontManager.nunitoBold24 != null ? FontManager.nunitoBold24 : FontManager.getMinecraft();
        IFont moduleFont = FontManager.nunitoBold20 != null ? FontManager.nunitoBold20 : FontManager.getMinecraft();
        IFont settingFont = FontManager.nunitoBold16 != null ? FontManager.nunitoBold16 : moduleFont;
        IFont smallFont = FontManager.nunito16 != null ? FontManager.nunito16 : settingFont;

        this.drawDefaultBackground();
        RenderUtil.drawRect(0.0F, 0.0F, this.width, this.height, BACKGROUND);

        if (this.draggingPanel != null) {
            this.draggingPanel.x = mouseX - this.dragOffsetX;
            this.draggingPanel.y = mouseY - this.dragOffsetY;
        }
        if (this.draggingNumber != null) {
            this.applyNumberDrag(mouseX);
        }

        for (CategoryPanel panel : this.panels.values()) {
            this.drawPanel(panel, titleFont, moduleFont, settingFont, smallFont, mouseX, mouseY);
        }

        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            for (CategoryPanel panel : this.panels.values()) {
                if (this.isInside(mouseX, mouseY, panel.x, panel.y, panel.width, this.panelHeight(panel))) {
                    panel.scroll += wheel > 0 ? 18.0F : -18.0F;
                    panel.scroll = Math.min(0.0F, Math.max(panel.scroll, -Math.max(0.0F, this.contentHeight(panel) - (this.height - panel.y - HEADER_HEIGHT - 14.0F))));
                    break;
                }
            }
        }
    }

    private void drawPanel(CategoryPanel panel, IFont titleFont, IFont moduleFont, IFont settingFont, IFont smallFont, int mouseX, int mouseY) {
        float height = this.panelHeight(panel);
        this.drawRoundedRect(panel.x + 2.0F, panel.y + 3.0F, panel.width, height, 11.0F, new Color(0, 0, 0, 95).getRGB());
        this.drawRoundedRect(panel.x, panel.y, panel.width, height, 11.0F, PANEL_DARK);
        this.drawRoundedRect(panel.x + 2.0F, panel.y + 2.0F, panel.width - 4.0F, height - 4.0F, 9.0F, PANEL);
        this.drawRoundedRect(panel.x + 8.0F, panel.y + 7.0F, panel.width - 16.0F, 27.0F, 9.0F, HEADER);
        this.drawRoundedRect(panel.x + 9.0F, panel.y + 12.0F, 3.0F, 11.0F, 2.0F, panel.accent);
        titleFont.drawString(panel.name, panel.x + 17.0F, panel.y + 10.0F, Color.WHITE.getRGB(), true);

        this.startScissor(panel.x, panel.y + HEADER_HEIGHT, panel.width, height - HEADER_HEIGHT - 5.0F);
        float y = panel.y + HEADER_HEIGHT + panel.scroll;
        for (Module module : panel.modules) {
            if (y > panel.y + height - 6.0F) {
                break;
            }
            float rowHeight = MODULE_HEIGHT + this.expandedHeight(module) * this.expandProgress(module);
            if (y + rowHeight >= panel.y + HEADER_HEIGHT - 2.0F) {
                this.drawModule(panel, module, moduleFont, settingFont, smallFont, y, mouseX, mouseY);
            }
            y += rowHeight;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawModule(CategoryPanel panel, Module module, IFont moduleFont, IFont settingFont, IFont smallFont, float y, int mouseX, int mouseY) {
        float animation = this.toggleProgress(module);
        boolean enabled = module.isEnabled();
        int textColor = this.blend(TEXT_DISABLED, Color.WHITE.getRGB(), animation);
        if (this.isInside(mouseX, mouseY, panel.x, y, panel.width, MODULE_HEIGHT)) {
            RenderUtil.drawRect(panel.x + 7.0F, y + 1.0F, panel.x + panel.width - 7.0F, y + MODULE_HEIGHT - 1.0F, new Color(255, 255, 255, 10).getRGB());
        }

        if (enabled) {
            this.drawCircle(panel.x + 7.0F, y + 13.5F, 2.0F, panel.accent);
        }
        moduleFont.drawString(module.getName(), panel.x + (enabled ? 13.0F : 12.0F), y + 7.0F, textColor, true);
        this.drawSwitch(panel.x + panel.width - 35.0F, y + 8.0F, 26.0F, 12.0F, animation, panel.accent);
        RenderUtil.drawRect(panel.x + 8.0F, y + MODULE_HEIGHT - 1.0F, panel.x + panel.width - 8.0F, y + MODULE_HEIGHT, ROW_LINE);

        float expand = this.expandProgress(module);
        if (expand <= 0.01F) {
            return;
        }

        float settingY = y + MODULE_HEIGHT;
        List<Property<?>> properties = this.visibleProperties(module);
        for (Property<?> property : properties) {
            float alpha = expand;
            this.drawSetting(panel, module, property, settingFont, smallFont, settingY, alpha, mouseX, mouseY);
            settingY += SETTING_HEIGHT * expand;
        }

        this.drawBind(panel, module, smallFont, settingY, expand, mouseX, mouseY);
    }

    private void drawSetting(CategoryPanel panel, Module module, Property<?> property, IFont settingFont, IFont smallFont, float y, float alpha, int mouseX, int mouseY) {
        int text = this.withAlpha(TEXT_SETTING, (int) (255.0F * alpha));
        int accent = this.withAlpha(panel.accent, (int) (255.0F * alpha));
        float x = panel.x + 12.0F;
        float right = panel.x + panel.width - 12.0F;

        if (this.isInside(mouseX, mouseY, panel.x + 8.0F, y, panel.width - 16.0F, SETTING_HEIGHT)) {
            RenderUtil.drawRect(panel.x + 8.0F, y, panel.x + panel.width - 8.0F, y + SETTING_HEIGHT, new Color(88, 105, 136, (int) (35.0F * alpha)).getRGB());
        }

        if (property instanceof BooleanProperty) {
            BooleanProperty bool = (BooleanProperty) property;
            settingFont.drawString(this.pretty(property.getName()), x + 10.0F, y + 6.0F, text, true);
            if (bool.getValue()) {
                this.drawCircle(x + 4.0F, y + 11.0F, 2.0F, accent);
            }
            this.drawSwitch(right - 25.0F, y + 5.0F, 25.0F, 11.0F, bool.getValue() ? 1.0F : 0.0F, accent);
            return;
        }

        if (property instanceof ModeProperty) {
            ModeProperty mode = (ModeProperty) property;
            settingFont.drawString(this.pretty(property.getName()), x + 10.0F, y + 6.0F, text, true);
            smallFont.drawRightString(this.pretty(mode.getModeString()), right, y + 6.0F, this.withAlpha(Color.WHITE.getRGB(), (int) (220.0F * alpha)));
            if (module.isEnabled()) {
                this.drawCircle(x + 4.0F, y + 11.0F, 2.0F, accent);
            }
            return;
        }

        if (property instanceof FloatProperty || property instanceof IntProperty || property instanceof PercentProperty) {
            double value = this.numberValue(property);
            double min = this.numberMin(property);
            double max = this.numberMax(property);
            float percent = (float) ((value - min) / (max - min));
            percent = Math.max(0.0F, Math.min(1.0F, percent));
            settingFont.drawString(this.pretty(property.getName()), x + 10.0F, y + 4.0F, text, true);
            smallFont.drawRightString(this.numberText(property), right, y + 4.0F, this.withAlpha(Color.WHITE.getRGB(), (int) (230.0F * alpha)));
            float sliderX = x + 10.0F;
            float sliderY = y + 17.0F;
            float sliderWidth = right - sliderX;
            RenderUtil.drawRect(sliderX, sliderY, sliderX + sliderWidth, sliderY + 2.0F, this.withAlpha(new Color(45, 45, 52).getRGB(), (int) (255.0F * alpha)));
            RenderUtil.drawRect(sliderX, sliderY, sliderX + sliderWidth * percent, sliderY + 2.0F, accent);
            this.drawCircle(sliderX + sliderWidth * percent, sliderY + 1.0F, 4.0F, this.withAlpha(Color.WHITE.getRGB(), (int) (255.0F * alpha)));
            return;
        }

        if (property instanceof ColorProperty) {
            settingFont.drawString(this.pretty(property.getName()), x + 10.0F, y + 6.0F, text, true);
            this.drawRoundedRect(right - 23.0F, y + 5.0F, 23.0F, 11.0F, 5.0F, ((ColorProperty) property).getValue());
            return;
        }

        if (property instanceof TextProperty) {
            settingFont.drawString(this.pretty(property.getName()), x + 10.0F, y + 6.0F, text, true);
            smallFont.drawRightString(this.truncate(((TextProperty) property).getValue(), smallFont, 58.0F), right, y + 6.0F, this.withAlpha(Color.WHITE.getRGB(), (int) (210.0F * alpha)));
        }
    }

    private void drawBind(CategoryPanel panel, Module module, IFont smallFont, float y, float alpha, int mouseX, int mouseY) {
        int text = this.withAlpha(TEXT_SETTING, (int) (255.0F * alpha));
        if (this.isInside(mouseX, mouseY, panel.x + 8.0F, y, panel.width - 16.0F, SETTING_HEIGHT)) {
            RenderUtil.drawRect(panel.x + 8.0F, y, panel.x + panel.width - 8.0F, y + SETTING_HEIGHT, new Color(88, 105, 136, (int) (30.0F * alpha)).getRGB());
        }
        String value = this.bindingModule == module ? "Press a key" : KeyBindUtil.getKeyName(module.getKey());
        smallFont.drawString("Bind", panel.x + 22.0F, y + 6.0F, text, true);
        smallFont.drawRightString(value, panel.x + panel.width - 12.0F, y + 6.0F, this.withAlpha(Color.WHITE.getRGB(), (int) (220.0F * alpha)));
    }

    private void drawSwitch(float x, float y, float width, float height, float progress, int accent) {
        int track = this.blend(SWITCH_OFF, accent, progress);
        this.drawRoundedRect(x, y, width, height, height / 2.0F, track);
        float knobRadius = height / 2.0F - 0.7F;
        float knobX = x + height / 2.0F + (width - height) * progress;
        this.drawCircle(knobX, y + height / 2.0F, knobRadius, SWITCH_KNOB);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (this.bindingModule != null && mouseButton != 0) {
            this.bindingModule.setKey(mouseButton - 100);
            this.bindingModule = null;
            return;
        }

        for (CategoryPanel panel : this.panels.values()) {
            if (!this.isInside(mouseX, mouseY, panel.x, panel.y, panel.width, this.panelHeight(panel))) {
                continue;
            }

            if (this.isInside(mouseX, mouseY, panel.x, panel.y, panel.width, HEADER_HEIGHT) && mouseButton == 0) {
                this.draggingPanel = panel;
                this.dragOffsetX = mouseX - panel.x;
                this.dragOffsetY = mouseY - panel.y;
                return;
            }

            float y = panel.y + HEADER_HEIGHT + panel.scroll;
            for (Module module : panel.modules) {
                float moduleTotalHeight = MODULE_HEIGHT + this.expandedHeight(module) * this.expandProgress(module);
                if (this.isInside(mouseX, mouseY, panel.x, y, panel.width, MODULE_HEIGHT)) {
                    if (mouseButton == 0) {
                        module.toggle();
                    } else if (mouseButton == 1) {
                        if (this.expandedModules.contains(module)) {
                            this.expandedModules.remove(module);
                        } else {
                            this.expandAnimations.putIfAbsent(module, 0.0F);
                            this.expandedModules.add(module);
                        }
                    }
                    return;
                }

                if (this.expandedModules.contains(module) && this.isInside(mouseX, mouseY, panel.x, y + MODULE_HEIGHT, panel.width, Math.max(0.0F, moduleTotalHeight - MODULE_HEIGHT))) {
                    this.handleSettingClick(panel, module, y + MODULE_HEIGHT, mouseX, mouseY, mouseButton);
                    return;
                }
                y += moduleTotalHeight;
            }
        }
    }

    private void handleSettingClick(CategoryPanel panel, Module module, float startY, int mouseX, int mouseY, int mouseButton) {
        float y = startY;
        for (Property<?> property : this.visibleProperties(module)) {
            if (this.isInside(mouseX, mouseY, panel.x + 8.0F, y, panel.width - 16.0F, SETTING_HEIGHT)) {
                if (property instanceof BooleanProperty && mouseButton == 0) {
                    BooleanProperty bool = (BooleanProperty) property;
                    bool.setValue(!bool.getValue());
                } else if (property instanceof ModeProperty) {
                    if (mouseButton == 0) {
                        ((ModeProperty) property).nextMode();
                    } else if (mouseButton == 1) {
                        ((ModeProperty) property).previousMode();
                    }
                } else if ((property instanceof FloatProperty || property instanceof IntProperty || property instanceof PercentProperty) && mouseButton == 0) {
                    this.draggingNumber = new NumberDrag(property, panel.x + 22.0F, panel.x + panel.width - 12.0F);
                    this.applyNumberDrag(mouseX);
                } else if (property instanceof TextProperty && mouseButton == 0) {
                    TextProperty text = (TextProperty) property;
                    GuiInput.prompt(this.pretty(text.getName()), text.getValue(), text::parseString, this);
                } else if (property instanceof ColorProperty && mouseButton == 0) {
                    ColorProperty color = (ColorProperty) property;
                    GuiInput.prompt(this.pretty(color.getName()), String.format("%08X", color.getValue()), color::parseString, this);
                }
                return;
            }
            y += SETTING_HEIGHT;
        }

        if (this.isInside(mouseX, mouseY, panel.x + 8.0F, y, panel.width - 16.0F, SETTING_HEIGHT) && mouseButton == 0) {
            this.bindingModule = this.bindingModule == module ? null : module;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.draggingPanel = null;
        this.draggingNumber = null;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.bindingModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.bindingModule = null;
                return;
            }
            if (keyCode == Keyboard.KEY_0 || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
                this.bindingModule.setKey(this.bindingModule instanceof GuiModule ? Keyboard.KEY_RSHIFT : 0);
            } else {
                this.bindingModule.setKey(keyCode);
            }
            this.bindingModule = null;
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        this.savePositions();
    }

    private float expandedHeight(Module module) {
        return this.visibleProperties(module).size() * SETTING_HEIGHT + SETTING_HEIGHT;
    }

    private float contentHeight(CategoryPanel panel) {
        float height = 0.0F;
        for (Module module : panel.modules) {
            height += MODULE_HEIGHT + (this.expandedModules.contains(module) ? this.expandedHeight(module) : 0.0F);
        }
        return height;
    }

    private float panelHeight(CategoryPanel panel) {
        float contentHeight = HEADER_HEIGHT + this.contentHeight(panel) + 8.0F;
        return Math.min(contentHeight, Math.max(110.0F, this.height - panel.y - 8.0F));
    }

    private float panelWidth() {
        return Math.min(DEFAULT_PANEL_WIDTH, Math.max(118.0F, (this.width - 48.0F) / 5.0F));
    }

    private float panelGap(float panelWidth) {
        return Math.max(6.0F, Math.min(12.0F, (this.width - panelWidth * 5.0F) / 4.0F));
    }

    private List<Property<?>> visibleProperties(Module module) {
        List<Property<?>> visible = new ArrayList<>();
        List<Property<?>> properties = Myau.propertyManager.properties.get(module.getClass());
        if (properties == null) {
            return visible;
        }
        for (Property<?> property : properties) {
            if (property.isVisible()) {
                visible.add(property);
            }
        }
        return visible;
    }

    private float toggleProgress(Module module) {
        float current = this.toggleAnimations.getOrDefault(module, module.isEnabled() ? 1.0F : 0.0F);
        float target = module.isEnabled() ? 1.0F : 0.0F;
        current += (target - current) * 0.24F;
        if (Math.abs(target - current) < 0.01F) {
            current = target;
        }
        this.toggleAnimations.put(module, current);
        return current;
    }

    private float expandProgress(Module module) {
        float current = this.expandAnimations.getOrDefault(module, this.expandedModules.contains(module) ? 1.0F : 0.0F);
        float target = this.expandedModules.contains(module) ? 1.0F : 0.0F;
        current += (target - current) * 0.22F;
        if (Math.abs(target - current) < 0.01F) {
            current = target;
        }
        this.expandAnimations.put(module, current);
        return current;
    }

    private void applyNumberDrag(int mouseX) {
        if (this.draggingNumber == null) {
            return;
        }
        Property<?> property = this.draggingNumber.property;
        double min = this.numberMin(property);
        double max = this.numberMax(property);
        double percent = (mouseX - this.draggingNumber.minX) / Math.max(1.0D, this.draggingNumber.maxX - this.draggingNumber.minX);
        percent = Math.max(0.0D, Math.min(1.0D, percent));
        double value = min + (max - min) * percent;

        if (property instanceof FloatProperty) {
            property.setValue((float) (Math.round(value * 100.0D) / 100.0D));
        } else if (property instanceof IntProperty || property instanceof PercentProperty) {
            property.setValue((int) Math.round(value));
        }
    }

    private double numberValue(Property<?> property) {
        if (property instanceof FloatProperty) {
            return ((FloatProperty) property).getValue();
        }
        if (property instanceof IntProperty) {
            return ((IntProperty) property).getValue();
        }
        return ((PercentProperty) property).getValue();
    }

    private double numberMin(Property<?> property) {
        if (property instanceof FloatProperty) {
            return ((FloatProperty) property).getMinimum();
        }
        if (property instanceof IntProperty) {
            return ((IntProperty) property).getMinimum();
        }
        return ((PercentProperty) property).getMinimum();
    }

    private double numberMax(Property<?> property) {
        if (property instanceof FloatProperty) {
            return ((FloatProperty) property).getMaximum();
        }
        if (property instanceof IntProperty) {
            return ((IntProperty) property).getMaximum();
        }
        return ((PercentProperty) property).getMaximum();
    }

    private String numberText(Property<?> property) {
        if (property instanceof PercentProperty) {
            return ((PercentProperty) property).getValue() + "%";
        }
        if (property instanceof IntProperty) {
            return String.valueOf(((IntProperty) property).getValue());
        }
        return String.valueOf(((FloatProperty) property).getValue());
    }

    private String pretty(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value.replace("-", " ").replace("_", " ");
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private String truncate(String value, IFont font, float maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String text = value;
        while (text.length() > 1 && font.width(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    private boolean isInside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private int blend(int from, int to, float progress) {
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        Color a = new Color(from, true);
        Color b = new Color(to, true);
        int alpha = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * progress);
        int red = (int) (a.getRed() + (b.getRed() - a.getRed()) * progress);
        int green = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * progress);
        int blue = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * progress);
        return new Color(red, green, blue, alpha).getRGB();
    }

    private int withAlpha(int color, int alpha) {
        Color c = new Color(color, true);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha))).getRGB();
    }

    private void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        radius = Math.min(radius, Math.min(width, height) / 2.0F);
        RenderUtil.drawRect(x + radius, y, x + width - radius, y + height, color);
        RenderUtil.drawRect(x, y + radius, x + radius, y + height - radius, color);
        RenderUtil.drawRect(x + width - radius, y + radius, x + width, y + height - radius, color);
        this.drawCorner(x + radius, y + radius, radius, 180, 270, color);
        this.drawCorner(x + width - radius, y + radius, radius, 270, 360, color);
        this.drawCorner(x + width - radius, y + height - radius, radius, 0, 90, color);
        this.drawCorner(x + radius, y + height - radius, radius, 90, 180, color);
    }

    private void drawCircle(float x, float y, float radius, int color) {
        this.drawCorner(x, y, radius, 0, 360, color);
    }

    private void drawCorner(float x, float y, float radius, int startAngle, int endAngle, int color) {
        Color c = new Color(color, true);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F, c.getAlpha() / 255.0F);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x, y);
        for (int angle = startAngle; angle <= endAngle; angle += 6) {
            double radians = Math.toRadians(angle);
            GL11.glVertex2f((float) (x + Math.cos(radians) * radius), (float) (y + Math.sin(radians) * radius));
        }
        double radians = Math.toRadians(endAngle);
        GL11.glVertex2f((float) (x + Math.cos(radians) * radius), (float) (y + Math.sin(radians) * radius));
        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopAttrib();
    }

    private void startScissor(float x, float y, float width, float height) {
        ScaledResolution sr = new ScaledResolution(this.mc);
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                (int) (x * scale),
                (int) ((sr.getScaledHeight() - y - height) * scale),
                (int) (width * scale),
                (int) (height * scale)
        );
    }

    private void savePositions() {
        JsonObject json = new JsonObject();
        for (CategoryPanel panel : this.panels.values()) {
            JsonObject position = new JsonObject();
            position.addProperty("x", panel.x);
            position.addProperty("y", panel.y);
            position.addProperty("scroll", panel.scroll);
            json.add(panel.name, position);
        }
        try (FileWriter writer = new FileWriter(this.configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        if (!this.configFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(this.configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            for (CategoryPanel panel : this.panels.values()) {
                if (json.has(panel.name)) {
                    JsonObject position = json.getAsJsonObject(panel.name);
                    panel.x = position.get("x").getAsFloat();
                    panel.y = position.get("y").getAsFloat();
                    if (position.has("scroll")) {
                        panel.scroll = position.get("scroll").getAsFloat();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static class CategoryPanel {
        private final String name;
        private final int accent;
        private final List<Module> modules;
        private float x;
        private float y;
        private float width;
        private float scroll;

        private CategoryPanel(String name, int accent, List<Module> modules) {
            this.name = name;
            this.accent = accent;
            this.modules = modules;
            this.width = DEFAULT_PANEL_WIDTH;
        }
    }

    private static class NumberDrag {
        private final Property<?> property;
        private final float minX;
        private final float maxX;

        private NumberDrag(Property<?> property, float minX, float maxX) {
            this.property = property;
            this.minX = minX;
            this.maxX = maxX;
        }
    }
}

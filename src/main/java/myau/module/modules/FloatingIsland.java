package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import myau.util.font.IFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingIsland extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public final IntProperty positionX = new IntProperty("position-x", 0, -5000, 5000);
    public final IntProperty positionY = new IntProperty("position-y", 10, -5000, 5000);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final IntProperty padding = new IntProperty("padding", 12, 4, 32);
    public final IntProperty height = new IntProperty("height", 28, 18, 64);
    public final ColorProperty backgroundColor = new ColorProperty("background-color", new Color(12, 14, 18, 176).getRGB());
    public final ColorProperty accentColor = new ColorProperty("accent-color", new Color(60, 162, 253).getRGB());
    public final ColorProperty textColor = new ColorProperty("text-color", Color.WHITE.getRGB());
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty showFps = new BooleanProperty("show-fps", true);

    public FloatingIsland() {
        super("FloatingIsland", false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.showDebugInfo) {
            return;
        }

        FontManager.initializeFonts();
        IFont font = FontManager.nunito18 != null ? FontManager.nunito18 : FontManager.getMinecraft();
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        String brand = "Myaulex";
        String details = String.format(
                "%s  |  %s  |  %dms  |  %s%s",
                Myau.version,
                this.getServer(),
                this.getPing(),
                TIME_FORMAT.format(new Date()),
                this.showFps.getValue() ? String.format("  |  %dfps", Minecraft.getDebugFPS()) : ""
        );

        float contentWidth = (float) font.width(brand + "  " + details);
        float islandWidth = contentWidth + this.padding.getValue() * 2.0F;
        float islandHeight = this.height.getValue();
        float x = this.positionX.getValue() == 0
                ? (scaledResolution.getScaledWidth() - islandWidth * this.scale.getValue()) / 2.0F
                : this.positionX.getValue();
        float y = this.positionY.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
        float scaledX = x / this.scale.getValue();
        float scaledY = y / this.scale.getValue();
        this.drawIslandBackground(scaledX, scaledY, islandWidth, islandHeight);

        float textX = scaledX + this.padding.getValue();
        float textY = scaledY + (islandHeight - (float) font.height()) / 2.0F;
        int accent = this.accentColor.getValue();
        int text = this.textColor.getValue();

        font.drawString(brand, textX, textY, accent, this.shadow.getValue());
        font.drawString(details, textX + font.width(brand + "  "), textY, text, this.shadow.getValue());
        GlStateManager.popMatrix();
    }

    private void drawIslandBackground(float x, float y, float width, float height) {
        RenderUtil.enableRenderState();
        int bg = this.backgroundColor.getValue();
        int accent = RenderUtil.mergeAlpha(this.accentColor.getValue(), 110);
        RenderUtil.drawRoundedRect(x + 2.0F, y + 3.0F, width, height, 10.0F, RenderUtil.mergeAlpha(Color.BLACK.getRGB(), 70));
        RenderUtil.drawRoundedGradientRect(x, y, width, height, 10.0F, bg, RenderUtil.mergeAlpha(bg, 210));
        RenderUtil.drawRoundedRect(x, y + height - 2.0F, width, 2.0F, 1.0F, accent);
        RenderUtil.disableRenderState();
    }

    private int getPing() {
        try {
            if (mc.getNetHandler() == null || mc.thePlayer == null) {
                return 0;
            }
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getName());
            return info == null ? 0 : info.getResponseTime();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String getServer() {
        if (mc.isIntegratedServerRunning() || mc.getCurrentServerData() == null) {
            return "Singleplayer";
        }
        return mc.getCurrentServerData().serverIP;
    }
}

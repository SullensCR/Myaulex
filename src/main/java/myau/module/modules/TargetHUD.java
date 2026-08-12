package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.TargetHudStyleProperty;
import myau.render.ui.UiFont;
import myau.render.ui.UiRenderer;
import myau.render.ui.UiTransform;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.ShaderSupport;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import myau.util.shader.BlurUtils;
import myau.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.scoreboard.ScorePlayerTeam;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * Aura-aware target display. The modern MYAULEX design has collapsed and
 * expanded states; the two retained RavenBS appearances remain available as
 * Classic Blur and Classic.
 */
public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long MANUAL_TARGET_MILLIS = 1500L;
    private static final float DESIGN_WIDTH = 1920.0F;
    private static final float DESIGN_HEIGHT = 1080.0F;
    private static final float TOP_HEIGHT = 40.0F;
    private static final float BAR_Y = 50.0F;
    private static final float BAR_HEIGHT = 26.0F;
    private static final float BACKDROP_BLUR_RADIUS = 33.0F;
    // Preserve the Figma 70% tint while leaving the live backdrop blur visible.
    private static final int PANEL_COLOR = 0xB3181926;
    private static final int GREEN_START = 0xFF4AFFAB;
    private static final int GREEN_END = 0xFF59FF4A;

    public final TargetHudStyleProperty style = new TargetHudStyleProperty();
    public final BooleanProperty indicator = new BooleanProperty("indicator", true,
            () -> this.style.getValue() != 0);
    public final ModeProperty posX = new ModeProperty("position-x", 1,
            new String[]{"LEFT", "MIDDLE", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 1,
            new String[]{"TOP", "MIDDLE", "BOTTOM"});
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final IntProperty offX = new IntProperty("offset-x", 0, -500, 500);
    public final IntProperty offY = new IntProperty("offset-y", 40, -500, 500);

    private final TimerUtil lastAttackTimer = new TimerUtil();
    private EntityLivingBase lastAttackTarget;
    private EntityLivingBase renderTarget;
    private long lastRenderNanos;
    private float visibility;
    private float expansion;
    private float animatedHealth = -1.0F;
    private float lastClassicHealthBar;

    public TargetHUD() {
        super("TargetHUD", false, true, "Target HUD");
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }

        ResolvedTarget resolved = this.resolveTarget();
        if (resolved != null) {
            if (resolved.entity != this.renderTarget) {
                this.renderTarget = resolved.entity;
                this.animatedHealth = this.healthRatio(resolved.entity);
                if (resolved.manual) {
                    this.expansion = 1.0F;
                }
            }
        }

        long now = System.nanoTime();
        float deltaSeconds = this.lastRenderNanos == 0L
                ? 1.0F / 60.0F
                : (now - this.lastRenderNanos) / 1_000_000_000.0F;
        this.lastRenderNanos = now;

        this.visibility = TargetHudState.animate(resolved != null ? 1.0F : 0.0F,
                this.visibility, deltaSeconds);
        this.expansion = TargetHudState.animate(
                resolved != null && resolved.variant == TargetHudState.Variant.EXPANDED ? 1.0F : 0.0F,
                this.expansion, deltaSeconds);

        if (this.renderTarget == null || this.visibility <= 0.01F) {
            if (resolved == null) {
                this.renderTarget = null;
                this.animatedHealth = -1.0F;
            }
            return;
        }

        float targetHealth = this.healthRatio(this.renderTarget);
        this.animatedHealth = this.animatedHealth < 0.0F
                ? targetHealth
                : TargetHudState.animate(targetHealth, this.animatedHealth, deltaSeconds);

        if (this.style.getValue() == 0) {
            this.renderModern(this.renderTarget);
        } else {
            this.renderClassic(this.style.getValue() - 1, this.renderTarget);
        }
    }

    private ResolvedTarget resolveTarget() {
        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (aura != null) {
            EntityLivingBase auraTarget = aura.getHudTarget();
            if (TeamUtil.isEntityLoaded(auraTarget)) {
                TargetHudState.Variant variant = TargetHudState.resolveVariant(
                        false,
                        RotationUtil.distanceToEntity(auraTarget),
                        aura.autoBlockRange.getValue(),
                        aura.swingRange.getValue()
                );
                if (variant != TargetHudState.Variant.HIDDEN) {
                    return new ResolvedTarget(auraTarget, false, variant);
                }
            }
        }

        if (!this.lastAttackTimer.hasTimeElapsed(MANUAL_TARGET_MILLIS)
                && TeamUtil.isEntityLoaded(this.lastAttackTarget)) {
            return new ResolvedTarget(this.lastAttackTarget, true, TargetHudState.Variant.EXPANDED);
        }
        return null;
    }

    private void renderModern(EntityLivingBase entity) {
        UiRenderer renderer = Myau.uiRenderer;
        if (renderer == null || !renderer.isSupported()) {
            this.renderModernFallback(entity);
            return;
        }

        boolean frameStarted = false;
        try {
            UiTransform transform = new UiTransform(mc, DESIGN_WIDTH, DESIGN_HEIGHT,
                    this.scale.getValue(), 0.0F);
            renderer.beginFrame("Target HUD", transform, BACKDROP_BLUR_RADIUS);
            frameStarted = true;
            try {
                UiFont font = renderer.fonts().mojang(20.0F);
                String health = Integer.toString(Math.max(0, Math.round(entity.getHealth())));
                String name = entity.getName();
                float componentWidth = TargetHudState.componentWidth(
                        font.visualWidth(health), font.visualWidth(name));
                float visualHeight = TargetHudState.height(this.expansion);
                float x = this.positionX(componentWidth);
                float y = this.positionY(visualHeight);
                int alpha = Math.round(255.0F * this.visibility);
                this.drawModernComponent(renderer, entity, x, y, componentWidth, font, health, name, alpha);
            } finally {
                renderer.endFrame();
                frameStarted = false;
            }
        } catch (Throwable failure) {
            this.renderModernFallback(entity);
        } finally {
            if (frameStarted) renderer.endFrame();
        }
    }

    private void drawModernComponent(UiRenderer ui, EntityLivingBase entity, float x, float y, float componentWidth,
                                     UiFont font, String health, String name, int alpha) {
        int panel = withOpacity(PANEL_COLOR, alpha);
        ui.shadow(x, y, componentWidth, TOP_HEIGHT, 20.0F,
                0.0F, 0.0F, 6.0F, 2.0F, withAlpha(0xFF000000, Math.round(alpha * 0.5F)));
        ui.backdrop(x, y, componentWidth, TOP_HEIGHT, 20.0F, panel);
        ui.imageResource("ui/targethud/heart.svg", x + TargetHudState.LEFT_PADDING, y + 9.0F,
                21.0F, 21.0F, withAlpha(0xFFFFFFFF, alpha));

        float textY = y + (TOP_HEIGHT - font.height()) * 0.5F - 0.5F;
        float healthX = x + TargetHudState.LEFT_PADDING + TargetHudState.HEART_WIDTH + TargetHudState.HEALTH_GAP;
        font.draw(health, healthX, textY,
                withAlpha(0xFFFFFFFF, alpha));
        font.draw(name, healthX + font.visualWidth(health) + TargetHudState.NAME_GAP,
                textY, withAlpha(0xFFFFFFFF, alpha));

        int barAlpha = Math.round(alpha * this.expansion);
        if (barAlpha <= 0) {
            return;
        }

        float barX = x + 21.0F;
        float barY = y + BAR_Y;
        float barWidth = componentWidth - 42.0F;
        ui.shadow(barX, barY, barWidth, BAR_HEIGHT, 13.0F,
                0.0F, 0.0F, 6.0F, 2.0F, withAlpha(0xFF000000, Math.round(barAlpha * 0.5F)));
        ui.backdrop(barX, barY, barWidth, BAR_HEIGHT, 13.0F, withOpacity(PANEL_COLOR, barAlpha));
        float trackX = x + 28.0F;
        float trackWidth = barWidth - 14.0F;
        ui.shadow(trackX, y + 57.0F, trackWidth, 12.0F, 6.0F,
                0.0F, 0.0F, 4.0F, 1.0F, withAlpha(0xFF000000, Math.round(barAlpha * 0.2F)));
        ui.roundedRect(trackX, y + 57.0F, trackWidth, 12.0F, 6.0F,
                withAlpha(0xCC7A7DA6, barAlpha));

        float fillWidth = trackWidth * Math.max(0.0F, Math.min(1.0F, this.animatedHealth));
        if (fillWidth <= 0.01F) {
            return;
        }
        int[] colors = this.healthColors(entity, barAlpha);
        ui.shadow(trackX, y + 57.0F, fillWidth, 12.0F, Math.min(6.0F, fillWidth * 0.5F),
                0.0F, 0.0F, 3.0F, 1.0F, withAlpha(colors[1], Math.round(barAlpha * 0.35F)));
        ui.gradientRoundedRect(trackX, y + 57.0F, fillWidth, 12.0F,
                Math.min(6.0F, fillWidth * 0.5F), colors[0], colors[1]);
    }

    private void renderModernFallback(EntityLivingBase entity) {
        ScaledResolution resolution = new ScaledResolution(mc);
        float nameWidth = mc.fontRendererObj.getStringWidth(entity.getName());
        float healthWidth = mc.fontRendererObj.getStringWidth(Integer.toString(Math.max(0, Math.round(entity.getHealth()))));
        float componentWidth = TargetHudState.componentWidth(healthWidth, nameWidth);
        float width = componentWidth * this.scale.getValue();
        float visualHeight = TargetHudState.height(this.expansion) * this.scale.getValue();
        float x = this.positionX(resolution.getScaledWidth(), width);
        float y = this.positionY(resolution.getScaledHeight(), visualHeight);
        int alpha = Math.round(255.0F * this.visibility);
        RenderUtil.drawRoundedRect(x, y, width, TOP_HEIGHT * this.scale.getValue(), 20.0F * this.scale.getValue(),
                withOpacity(PANEL_COLOR, alpha));
        mc.fontRendererObj.drawString("\u2665 " + Math.max(0, Math.round(entity.getHealth())) + "   " + entity.getName(),
                x + 12.0F, y + 15.0F, withAlpha(0xFFFFFFFF, alpha), true);
        if (this.expansion <= 0.01F) {
            return;
        }
        float ratio = Math.max(0.0F, Math.min(1.0F, this.animatedHealth));
        float barY = y + BAR_Y * this.scale.getValue();
        float barWidth = (componentWidth - 56.0F) * this.scale.getValue();
        RenderUtil.drawRoundedRect(x + 28.0F * this.scale.getValue(), barY, barWidth,
                12.0F * this.scale.getValue(), 6.0F * this.scale.getValue(), withAlpha(0xCC7A7DA6, alpha));
        int[] colors = this.healthColors(entity, alpha);
        RenderUtil.drawRoundedGradientRect(x + 28.0F * this.scale.getValue(), barY,
                x + (28.0F + 237.0F * ratio) * this.scale.getValue(), barY + 12.0F * this.scale.getValue(),
                6.0F * this.scale.getValue(), colors[0], colors[0], colors[1], colors[1]);
    }

    /** Preserves the two surviving RavenBS renderers. mode 0 is the blurred version. */
    private void renderClassic(int mode, EntityLivingBase entity) {
        float health = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;
        float absorption = entity.getAbsorptionAmount() / 2.0F;
        float shownHealth = entity.getHealth() / 2.0F + absorption;
        String playerInfo = entity.getDisplayName().getFormattedText();
        double healthRatio = entity.isDead ? 0.0 : entity.getHealth() / entity.getMaxHealth();
        playerInfo += " \u00a7c" + String.format(java.util.Locale.US, "%.1f", shownHealth);
        if (this.indicator.getValue()) {
            playerInfo += " " + (healthRatio <= health / mc.thePlayer.getMaxHealth() ? "\u00a7aW" : "\u00a7cL");
        }

        int alpha = Math.round(255.0F * this.visibility);
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        int padding = 8;
        int targetStrWithPadding = mc.fontRendererObj.getStringWidth(playerInfo) + padding;
        int x = (scaledResolution.getScaledWidth() / 2 - targetStrWithPadding / 2) + this.offX.getValue();
        int y = (scaledResolution.getScaledHeight() / 2 + 15) + this.offY.getValue();
        int left = x - padding;
        int top = y - padding;
        int right = x + targetStrWithPadding;
        int bottom = y + (mc.fontRendererObj.FONT_HEIGHT + 5) - 6 + padding;
        int outlineAlpha = Math.min(alpha, 110);
        int backgroundAlpha = Math.min(alpha, 210);
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        int gradientLeft = hud != null ? hud.getColor(System.currentTimeMillis()).getRGB() : Color.WHITE.getRGB();
        int gradientRight = hud != null ? hud.getColor(System.currentTimeMillis() + 500).getRGB() : Color.WHITE.getRGB();

        if (mode == 0) {
            if (ShaderSupport.shouldUseShaders()) {
                BlurUtils.prepareBloom();
                RoundedUtils.drawRound(left, top, right - left, bottom + 13 - top, 8.0F, true,
                        new Color(0, 0, 0, backgroundAlpha));
                BlurUtils.bloomEnd(3, 2.0F);
                BlurUtils.prepareBlur();
                RoundedUtils.drawRound(left, top, right - left, bottom + 13 - top, 8.0F, true,
                        new Color(RenderUtil.mergeAlpha(Color.black.getRGB(), outlineAlpha)));
                BlurUtils.blurEnd(2, 3.0F);
            } else {
                RenderUtil.drawRoundedRect(left, top, right - left, bottom + 13 - top, 8.0F,
                        RenderUtil.mergeAlpha(Color.black.getRGB(), outlineAlpha), true, true, true, true);
            }
        } else {
            RenderUtil.drawRoundedGradientOutlinedRectangle(left, top, right, bottom + 13, 10.0F,
                    RenderUtil.mergeAlpha(Color.black.getRGB(), outlineAlpha),
                    RenderUtil.mergeAlpha(gradientLeft, alpha), RenderUtil.mergeAlpha(gradientRight, alpha));
        }

        int barLeft = left + 6;
        int barRight = right - 6;
        int barTop = bottom;
        RenderUtil.drawRoundedRectangle(barLeft, barTop, barRight, barTop + 5, 4.0F,
                RenderUtil.mergeAlpha(Color.black.getRGB(), outlineAlpha));
        float healthBar = (float) (barRight + (barLeft - barRight) * (1.0 - healthRatio));
        this.lastClassicHealthBar = this.lastClassicHealthBar == 0.0F
                ? healthBar
                : this.lastClassicHealthBar + (healthBar - this.lastClassicHealthBar) * 0.1F;
        this.lastClassicHealthBar = Math.min(this.lastClassicHealthBar, barRight);
        int leftGradient = RenderUtil.mergeAlpha(gradientLeft, backgroundAlpha);
        int rightGradient = RenderUtil.mergeAlpha(gradientRight, backgroundAlpha);
        if (mode == 0) {
            RenderUtil.drawRoundedRectangle(barLeft, barTop, this.lastClassicHealthBar, barTop + 5, 4.0F,
                    RenderUtil.darkenColor(rightGradient, 25));
        }
        RenderUtil.drawRoundedGradientRect(barLeft, barTop, healthBar, barTop + 5, 4.0F,
                leftGradient, leftGradient, rightGradient, rightGradient);
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        mc.fontRendererObj.drawString(playerInfo, x, y,
                withAlpha(0xFFDCDCDC, Math.min(alpha + 15, 255)), true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private float positionX(float componentWidth) {
        return this.positionX(DESIGN_WIDTH, componentWidth);
    }

    private float positionX(float canvasWidth, float componentWidth) {
        switch (this.posX.getValue()) {
            case 0:
                return this.offX.getValue();
            case 2:
                return canvasWidth - componentWidth - this.offX.getValue();
            case 1:
            default:
                return (canvasWidth - componentWidth) * 0.5F + this.offX.getValue();
        }
    }

    private float positionY(float componentHeight) {
        return this.positionY(DESIGN_HEIGHT, componentHeight);
    }

    private float positionY(float canvasHeight, float componentHeight) {
        switch (this.posY.getValue()) {
            case 0:
                return this.offY.getValue();
            case 2:
                return canvasHeight - componentHeight - this.offY.getValue();
            case 1:
            default:
                return (canvasHeight - componentHeight) * 0.5F + this.offY.getValue();
        }
    }

    private float healthRatio(EntityLivingBase entity) {
        return entity == null || entity.getMaxHealth() <= 0.0F
                ? 0.0F
                : Math.max(0.0F, Math.min(1.0F, entity.getHealth() / entity.getMaxHealth()));
    }

    private int[] healthColors(EntityLivingBase entity, int alpha) {
        int start = GREEN_START;
        int end = GREEN_END;
        if (entity instanceof EntityPlayer && mc.getNetHandler() != null) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(entity.getUniqueID());
            ScorePlayerTeam team = info == null ? null : info.getPlayerTeam();
            if (team != null) {
                String format = net.minecraft.client.gui.FontRenderer.getFormatFromString(team.getColorPrefix());
                if (format.length() >= 2) {
                    start = 0xFF000000 | mc.fontRendererObj.getColorCode(format.charAt(1));
                    end = lighten(start, 0.24F);
                }
            }
        }
        return new int[]{withAlpha(start, alpha), withAlpha(end, alpha)};
    }

    private static int lighten(int color, float amount) {
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        red += Math.round((255 - red) * amount);
        green += Math.round((255 - green) * amount);
        blue += Math.round((255 - blue) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int withOpacity(int color, int visibilityAlpha) {
        int baseAlpha = color >>> 24;
        int alpha = Math.round(baseAlpha * Math.max(0, Math.min(255, visibilityAlpha)) / 255.0F);
        return withAlpha(color, alpha);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() == Action.ATTACK) {
                Entity entity = packet.getEntityFromWorld(mc.theWorld);
                if (entity instanceof EntityLivingBase && !(entity instanceof EntityArmorStand)) {
                    this.lastAttackTimer.reset();
                    this.lastAttackTarget = (EntityLivingBase) entity;
                }
            }
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        this.renderTarget = null;
        this.lastAttackTarget = null;
        this.visibility = 0.0F;
        this.expansion = 0.0F;
        this.animatedHealth = -1.0F;
        this.lastClassicHealthBar = 0.0F;
        this.lastRenderNanos = 0L;
    }

    private static final class ResolvedTarget {
        private final EntityLivingBase entity;
        private final boolean manual;
        private final TargetHudState.Variant variant;

        private ResolvedTarget(EntityLivingBase entity, boolean manual, TargetHudState.Variant variant) {
            this.entity = entity;
            this.manual = manual;
            this.variant = variant;
        }
    }
}

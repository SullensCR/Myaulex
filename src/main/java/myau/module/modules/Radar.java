package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

import java.awt.*;
import java.util.stream.Collectors;

public class Radar extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty colorMode = new ModeProperty("color", 0, new String[]{"DEFAULT", "TEAMS", "HUD"});
    public final IntProperty position = new IntProperty("position", 0, 0, 3);
    public final IntProperty offsetX = new IntProperty("offsetX", 60, 0, 1000);
    public final IntProperty offsetY = new IntProperty("offsetY", 60, 0, 1000);
    public final PercentProperty opacity = new PercentProperty("opacity", 100);
    public final BooleanProperty showPlayers = new BooleanProperty("players", true);
    public final BooleanProperty showFriends = new BooleanProperty("friends", true);
    public final BooleanProperty showEnemies = new BooleanProperty("enemies", true);
    public final BooleanProperty showBots = new BooleanProperty("bots", false);
    public final ColorProperty fillColor = new ColorProperty("fillColor", Color.GRAY.getRGB(), 0x40);
    public final ColorProperty outlineColor = new ColorProperty("outlineColor", Color.DARK_GRAY.getRGB());
    public final ColorProperty crossColor = new ColorProperty("crossColor", Color.LIGHT_GRAY.getRGB(), 0x80);
    public Radar() {
        super("Radar", false);
    }

    private boolean shouldRender(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) {
            return false;
        } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
            if (TeamUtil.isBot(entityPlayer)) {
                return this.showBots.getValue();
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return this.showFriends.getValue();
            } else {
                return TeamUtil.isTarget(entityPlayer) ? this.showEnemies.getValue() : this.showPlayers.getValue();
            }
        } else {
            return false;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer, float alpha) {
        if (TeamUtil.isFriend(entityPlayer)) {
            Color color = Myau.friendManager.getColor();
            return new Color((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, alpha);
        } else if (TeamUtil.isTarget(entityPlayer)) {
            Color color = Myau.targetManager.getColor();
            return new Color((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, alpha);
        } else {
            switch (this.colorMode.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor(entityPlayer, alpha);
                case 1:
                    int teamColor = TeamUtil.isSameTeam(entityPlayer) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                    return new Color(teamColor & Color.WHITE.getRGB() | (int) (alpha * 255.0F) << 24, true);
                case 2:
                    int color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                    return new Color(color & Color.WHITE.getRGB() | (int) (alpha * 255.0F) << 24, true);
                default:
                    return new Color(1.0F, 1.0F, 1.0F, alpha);
            }
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);

        // 雷达显示参数
        double radarRadius = 55.0;           // 雷达半径（像素）
        double maxRange = 50.0;              // 世界单元（格）对应的检测半径
        int circleSegments = 64;             // 画圆细分
        double dotRadius = 2.0;              // 玩家点半径（像素）
        float partial = event.getPartialTicks();

        double centerX = (position.getValue() & 0x1) == 0x1 ? Math.max(sr.getScaledWidth() - offsetX.getValue(), 0) : Math.min(offsetX.getValue(), sr.getScaledWidth());
        double centerY = (position.getValue() & 0x2) == 0x2 ? Math.max(sr.getScaledHeight() - offsetY.getValue(), 0) : Math.min(offsetY.getValue(), sr.getScaledHeight());

        GlStateManager.pushMatrix();
        GlStateManager.scale(hud.scale.getValue(), hud.scale.getValue(), 1.0f);
        GlStateManager.translate(centerX, centerY, 0.0f);

        RenderUtil.enableRenderState();
        RenderUtil.drawRadarCircle(0.0, 0, radarRadius, circleSegments, fillColor.getValue(), outlineColor.getValue(), crossColor.getValue());
        for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRender((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList())) {
            // 插值位置
            double dx = (player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks()) - mc.thePlayer.posX;
            double dz = (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks()) - mc.thePlayer.posZ;

            // 旋转以匹配玩家朝向
            float yaw = (float)Math.toRadians(mc.thePlayer.rotationYaw);
            if (mc.gameSettings.thirdPersonView != 2) {
                yaw += (float)Math.toRadians(180.0F);
            }
            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);

            double relX = dx * cos + dz * sin;
            double relY = dz * cos - dx * sin;

            // 距离缩
            double dist = Math.sqrt(relX * relX + relY * relY);
            double scale = dist < radarRadius ? 1.0F : radarRadius / dist;
            double px = relX * scale;
            double py = relY * scale;

            RenderUtil.fillCircle(px, py, dotRadius, 12, getEntityColor(player, opacity.getValue().floatValue() / 100.0F).getRGB());

        }
        RenderUtil.disableRenderState();
        GlStateManager.popMatrix();
    }
}

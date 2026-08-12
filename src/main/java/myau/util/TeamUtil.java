package myau.util;

import myau.Myau;
import myau.render.ClientPerformanceMetrics;
import myau.render.RenderFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class TeamUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Comparator<Entity> FARTHEST_FIRST = new Comparator<Entity>() {
        @Override
        public int compare(Entity first, Entity second) {
            double firstDistance = mc.getRenderManager().getDistanceToCamera(first.posX, first.posY, first.posZ);
            double secondDistance = mc.getRenderManager().getDistanceToCamera(second.posX, second.posY, second.posZ);
            int distance = Double.compare(secondDistance, firstDistance);
            if (distance != 0) return distance;
            return compareUuidText(first.getUniqueID(), second.getUniqueID());
        }
    };
    private static long sortedFrame = Long.MIN_VALUE;
    private static Object sortedWorld;
    private static List<Entity> sortedEntities = Collections.emptyList();

    public static boolean isEntityLoaded(Entity entity) {
        if (entity == null || TeamUtil.mc.theWorld == null) return false;
        return TeamUtil.mc.theWorld.loadedEntityList.contains(entity);
    }

    public static List<Entity> getLoadedEntitiesSorted() {
        if (mc.theWorld == null || mc.getRenderManager() == null) return Collections.emptyList();
        long frame = RenderFrame.current();
        if (frame != 0L && sortedFrame == frame && sortedWorld == mc.theWorld) return sortedEntities;

        long startedNanos = ClientPerformanceMetrics.start();
        List<Entity> snapshot = new ArrayList<>(mc.theWorld.loadedEntityList);
        Collections.sort(snapshot, FARTHEST_FIRST);
        sortedEntities = Collections.unmodifiableList(snapshot);
        sortedWorld = mc.theWorld;
        sortedFrame = frame;
        ClientPerformanceMetrics.recordEntitySort(startedNanos);
        return sortedEntities;
    }

    public static void invalidateRenderSnapshot() {
        sortedFrame = Long.MIN_VALUE;
        sortedWorld = null;
        sortedEntities = Collections.emptyList();
    }

    /** Equivalent to comparing UUID.toString() without allocating two strings. */
    private static int compareUuidText(UUID first, UUID second) {
        int most = Long.compareUnsigned(first.getMostSignificantBits(), second.getMostSignificantBits());
        return most != 0 ? most : Long.compareUnsigned(first.getLeastSignificantBits(), second.getLeastSignificantBits());
    }

    public static float getHealthScore(EntityLivingBase entityLivingBase) {
        return entityLivingBase.getHealth() * (20.0f / (float) entityLivingBase.getTotalArmorValue());
    }

    public static String stripName(Entity entity) {
        return entity.getDisplayName().getFormattedText().replaceAll("§\\S$", "").replaceAll("(?i)§r", "§f").trim();
    }

    public static Color getTeamColor(EntityPlayer player, float alpha) {
        int colorCode = 0xFFFFFF;
        ScorePlayerTeam playerTeam = (ScorePlayerTeam) player.getTeam();
        if (playerTeam != null) {
            String colorPrefix = FontRenderer.getFormatFromString(playerTeam.getColorPrefix());
            if (colorPrefix.length() >= 2) {
                colorCode = TeamUtil.mc.fontRendererObj.getColorCode(colorPrefix.charAt(1));
            }
        }
        return new Color(colorCode & 0xFFFFFF | (int)(alpha * 255) << 24, true);
    }

    public static boolean isBot(EntityPlayer player) {
        if (player == TeamUtil.mc.thePlayer) {
            return false;
        }
        if (Myau.clientSettings != null) {
            int mode = Myau.clientSettings.getBotFilterMode();
            if (mode == 2) return false;
            if (mode == 1) {
                return player.ticksExisted > 40
                        && Math.abs(player.posX - player.lastTickPosX) < 1.0E-4
                        && Math.abs(player.posY - player.lastTickPosY) < 1.0E-4
                        && Math.abs(player.posZ - player.lastTickPosZ) < 1.0E-4;
            }
        }
        NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(player.getName());
        if (playerInfo == null) {
            return true;
        }
        if (!ServerUtil.isHypixel()) return false;
        if (player.getName().startsWith("§k")) {
            return player.isInvisible();
        }
        if (playerInfo.getResponseTime() < 1) {
            return true;
        }
        ScorePlayerTeam playerTeam = playerInfo.getPlayerTeam();
        if (playerTeam == null) return false;
        if (!playerTeam.getTeamName().isEmpty()) return false;
        return playerTeam.getColorPrefix().equals("§c");
    }

    public static boolean isSameTeam(EntityPlayer player) {
        if (player == TeamUtil.mc.thePlayer) {
            return true;
        }
        if (Myau.clientSettings != null) {
            int mode = Myau.clientSettings.getTeamsMode();
            if (mode == 2) return false;
            if (mode == 0) return hasMatchingArmor(player);
        }
        NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(TeamUtil.mc.thePlayer.getUniqueID());
        if (selfInfo == null) {
            return false;
        }
        ScorePlayerTeam selfTeam = selfInfo.getPlayerTeam();
        if (selfTeam == null) {
            return false;
        }
        NetworkPlayerInfo targetInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (targetInfo == null) {
            return false;
        }
        ScorePlayerTeam targetTeam = targetInfo.getPlayerTeam();
        if (targetTeam == null) {
            return false;
        }
        return selfTeam.getColorPrefix().equals(targetTeam.getColorPrefix());
    }

    public static boolean hasTeamColor(EntityLivingBase entity) {
        if (entity == TeamUtil.mc.thePlayer) {
            return true;
        }
        NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(TeamUtil.mc.thePlayer.getUniqueID());
        if (selfInfo == null) {
            return false;
        }
        ScorePlayerTeam selfTeam = selfInfo.getPlayerTeam();
        if (selfTeam == null) {
            return false;
        }
        if (selfTeam.getColorPrefix().length() < 2) {
            return false;
        }
        EntityLivingBase nearestArmorStand = TeamUtil.mc.theWorld.findNearestEntityWithinAABB(EntityArmorStand.class, entity.getEntityBoundingBox(), entity);
        if (nearestArmorStand != null) {
            return nearestArmorStand.getName().contains(selfTeam.getColorPrefix().substring(0, 2));
        }
        return false;
    }

    public static boolean isShop(EntityLivingBase entity) {
        if (entity == TeamUtil.mc.thePlayer) {
            return false;
        }
        EntityLivingBase armorStand = TeamUtil.mc.theWorld.findNearestEntityWithinAABB(EntityArmorStand.class, entity.getEntityBoundingBox(), entity);
        if (armorStand == null) return false;
        String displayName = armorStand.getName();
        if (displayName.contains("RIGHT CLICK")) return true;
        if (displayName.contains("ITEM SHOP")) return true;
        if (displayName.contains("UPGRADES")) return true;
        if (displayName.contains("BANKER")) return true;
        return displayName.contains("STREAK POWERS");
    }

    public static boolean isFriend(EntityPlayer player) {
        return Myau.friendManager.isFriend(player.getName());
    }

    public static boolean isTarget(EntityPlayer player) {
        return Myau.targetManager.isFriend(player.getName());
    }

    public static boolean isAllowedTarget(EntityLivingBase entity) {
        if (entity == null || Myau.clientSettings == null) return entity != null;
        if (entity instanceof EntityPlayer) return Myau.clientSettings.isTargetPlayers();
        if (entity instanceof EntityAnimal) return Myau.clientSettings.isTargetAnimals();
        if (entity instanceof EntityMob) return Myau.clientSettings.isTargetMobs();
        return true;
    }

    private static boolean hasMatchingArmor(EntityPlayer player) {
        if (mc.thePlayer == null) return false;
        for (int slot = 0; slot < 4; slot++) {
            ItemStack self = mc.thePlayer.inventory.armorInventory[slot];
            ItemStack other = player.inventory.armorInventory[slot];
            if (self == null || other == null) continue;
            if (!(self.getItem() instanceof ItemArmor) || !(other.getItem() instanceof ItemArmor)) continue;
            if (self.getItem() != other.getItem()) return false;
            ItemArmor selfArmor = (ItemArmor) self.getItem();
            ItemArmor otherArmor = (ItemArmor) other.getItem();
            if (selfArmor.getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER
                    && otherArmor.getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER
                    && selfArmor.getColor(self) != otherArmor.getColor(other)) return false;
            return true;
        }
        return false;
    }
}

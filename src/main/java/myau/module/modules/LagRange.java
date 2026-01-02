package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.util.ItemUtil;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.property.properties.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class LagRange extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int tickIndex = -1;
    private long delayCounter = 0L;
    private boolean hasTarget = false;
    private long maxTimeStarted = 0L;
    private Vec3 lastPosition = null;
    private Vec3 currentPosition = null;
    
    public final BooleanProperty advancedMode = new BooleanProperty("advanced-mode", false);
    
    public final IntProperty delay = new IntProperty("delay", 150, 0, 1000, () -> !this.advancedMode.getValue());
    public final FloatProperty range = new FloatProperty("range", 10.0F, 3.0F, 100.0F, () -> !this.advancedMode.getValue());
    
    public final IntProperty maxTime = new IntProperty("max-time", 1000, 0, 5000, this.advancedMode::getValue);
    public final FloatProperty closeRange = new FloatProperty("close-range", 6.0F, 3.0F, 100.0F, this.advancedMode::getValue);
    public final FloatProperty farRange = new FloatProperty("far-range", 10.0F, 3.0F, 100.0F, this.advancedMode::getValue);
    public final IntProperty delayClose = new IntProperty("packet-delay-close", 150, 0, 1000, this.advancedMode::getValue);
    public final IntProperty delayFar = new IntProperty("packet-delay-far", 300, 0, 1000, this.advancedMode::getValue);
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty botCheck = new BooleanProperty("bot-check", true);
    public final BooleanProperty teams = new BooleanProperty("teams", true);
    public final ModeProperty showPosition = new ModeProperty("show-position", 0, new String[]{"NONE", "DEFAULT", "HUD"});

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return (!this.teams.getValue() || !TeamUtil.isSameTeam(entityPlayer)) && (!this.botCheck.getValue() || !TeamUtil.isBot(entityPlayer));
            }
        } else {
            return false;
        }
    }

    private boolean shouldResetOnPacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            return true;
        } else if (packet instanceof C07PacketPlayerDigging) {
            return ((C07PacketPlayerDigging) packet).getStatus() != Action.RELEASE_USE_ITEM;
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            ItemStack item = ((C08PacketPlayerBlockPlacement) packet).getStack();
            return item == null || !(item.getItem() instanceof ItemSword);
        } else {
            return false;
        }
    }

    public LagRange() {
        super("LagRange", false);
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    Myau.lagManager.setDelay(0);
                    this.hasTarget = false;
                    BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
                    if ((!bedNuker.isEnabled() || !bedNuker.isReady())
                            && !((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
                            && (!mc.thePlayer.isUsingItem() || mc.thePlayer.isBlocking())
                            && (
                            !(Boolean) this.weaponsOnly.getValue()
                                    || ItemUtil.hasRawUnbreakingEnchant()
                                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()
                    )) {
                        List<EntityPlayer> players = mc.theWorld
                                .loadedEntityList
                                .stream()
                                .filter(entity -> entity instanceof EntityPlayer)
                                .map(entity -> (EntityPlayer) entity)
                                .filter(this::isValidTarget)
                                .collect(Collectors.toList());
                        if (players.isEmpty()) {
                            this.tickIndex = -1;
                            this.maxTimeStarted = 0L;
                        } else {
                            double height = mc.thePlayer.getEyeHeight();
                            Vec3 eyePosition = Myau.lagManager.getLastPosition().addVector(0.0, height, 0.0);
                            Vec3 targetEyePosition = new Vec3(mc.thePlayer.lastTickPosX, mc.thePlayer.lastTickPosY + height, mc.thePlayer.lastTickPosZ);
                            Vec3 playerEyePosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + height, mc.thePlayer.posZ);
                            
                            for (EntityPlayer player : players) {
                                double distance = RotationUtil.distanceToBox(player, playerEyePosition);
                                
                                // Determine which range to use
                                float maxRange = this.advancedMode.getValue() 
                                    ? Math.max(this.closeRange.getValue(), this.farRange.getValue())
                                    : this.range.getValue();
                                
                                if (!(distance > (double) maxRange)) {
                                    double targetDist = RotationUtil.distanceToBox(player, targetEyePosition);
                                    double eyeDist = RotationUtil.distanceToBox(player, eyePosition);
                                    
                                    if (distance < targetDist || distance < eyeDist) {
                                        // Determine delay based on distance
                                        int currentDelay;
                                        boolean isInCloseRange = false;
                                        
                                        if (this.advancedMode.getValue()) {
                                            isInCloseRange = distance <= (double) this.closeRange.getValue();
                                            
                                            if (isInCloseRange) {
                                                // Start timer when entering close range
                                                if (this.maxTimeStarted == 0L) {
                                                    this.maxTimeStarted = System.currentTimeMillis();
                                                }
                                                
                                                // Check if max time has elapsed
                                                long elapsed = System.currentTimeMillis() - this.maxTimeStarted;
                                                if (elapsed >= this.maxTime.getValue()) {
                                                    // Max time reached, stop lagging
                                                    Myau.lagManager.setDelay(0);
                                                    this.tickIndex = -1;
                                                    return;
                                                }
                                                
                                                currentDelay = this.delayClose.getValue();
                                            } else {
                                                // In far range, reset timer
                                                this.maxTimeStarted = 0L;
                                                currentDelay = this.delayFar.getValue();
                                            }
                                        } else {
                                            currentDelay = this.delay.getValue();
                                        }
                                        
                                        // Apply delay using original flush logic
                                        if (this.tickIndex < 0) {
                                            this.tickIndex = 0;
                                            for (this.delayCounter = this.delayCounter + (long) currentDelay;
                                                 this.delayCounter > 0L;
                                                 this.delayCounter = this.delayCounter - 50
                                            ) {
                                                this.tickIndex++;
                                            }
                                        }
                                        
                                        Myau.lagManager.setDelay(this.tickIndex);
                                        this.hasTarget = true;
                                        return;
                                    }
                                }
                            }
                            
                            // No valid targets found - reset
                            this.tickIndex = -1;
                            this.maxTimeStarted = 0L;
                        }
                    } else {
                        this.tickIndex = -1;
                        this.maxTimeStarted = 0L;
                    }
                    break;
                case POST:
                    Vec3 savedPosition = Myau.lagManager.getLastPosition();
                    if (this.currentPosition == null) {
                        this.lastPosition = savedPosition;
                    } else {
                        this.lastPosition = this.currentPosition;
                    }
                    this.currentPosition = savedPosition;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled()) {
            if (this.shouldResetOnPacket(event.getPacket())) {
                Myau.lagManager.setDelay(0);
                this.tickIndex = -1;
                this.maxTimeStarted = 0L;
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            if (this.showPosition.getValue() != 0
                    && mc.gameSettings.thirdPersonView != 0
                    && this.hasTarget
                    && this.lastPosition != null
                    && this.currentPosition != null) {
                Color color = new Color(-1);
                switch (this.showPosition.getValue()) {
                    case 1:
                        color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F);
                        break;
                    case 2:
                        color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                }
                double x = RenderUtil.lerpDouble(this.currentPosition.xCoord, this.lastPosition.xCoord, event.getPartialTicks());
                double y = RenderUtil.lerpDouble(this.currentPosition.yCoord, this.lastPosition.yCoord, event.getPartialTicks());
                double z = RenderUtil.lerpDouble(this.currentPosition.zCoord, this.lastPosition.zCoord, event.getPartialTicks());
                float size = mc.thePlayer.getCollisionBorderSize();
                AxisAlignedBB aabb = new AxisAlignedBB(
                        x - (double) mc.thePlayer.width / 2.0,
                        y,
                        z - (double) mc.thePlayer.width / 2.0,
                        x + (double) mc.thePlayer.width / 2.0,
                        y + (double) mc.thePlayer.height,
                        z + (double) mc.thePlayer.width / 2.0
                )
                        .expand(size, size, size)
                        .offset(
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                        );
                RenderUtil.enableRenderState();
                RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
                RenderUtil.disableRenderState();
            }
        }
    }

    @Override
    public void onDisabled() {
        Myau.lagManager.setDelay(0);
        this.tickIndex = -1;
        this.delayCounter = 0L;
        this.hasTarget = false;
        this.maxTimeStarted = 0L;
        this.lastPosition = null;
        this.currentPosition = null;
    }

    @Override
    public String[] getSuffix() {
        if (this.advancedMode.getValue()) {
            return new String[]{String.format("%dms", this.delayFar.getValue())};
        } else {
            return new String[]{String.format("%dms", this.delay.getValue())};
        }
    }

    @Override
    public void verifyValue(String value) {
        if (this.advancedMode.getValue()) {
            if (this.closeRange.getName().equals(value)) {
                if (this.closeRange.getValue() > this.farRange.getValue()) {
                    this.farRange.setValue(this.closeRange.getValue());
                }
            } else if (this.farRange.getName().equals(value)) {
                if (this.closeRange.getValue() > this.farRange.getValue()) {
                    this.closeRange.setValue(this.farRange.getValue());
                }
            }
        }
    }
}
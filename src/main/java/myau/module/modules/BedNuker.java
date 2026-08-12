package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.NotificationManager;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.KeyBindProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.BlockObsidian;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BedNuker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double AUTO_DETECT_RADIUS = 20.0D;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final TimerUtil timer = new TimerUtil();
    private final ArrayList<BlockPos> bedWhitelist = new ArrayList<BlockPos>();
    private final CopyOnWriteArraySet<BlockPos> bedEspBeds = new CopyOnWriteArraySet<BlockPos>();
    private final Color colorRed = new Color(ChatColors.RED.toAwtColor());
    private final Color colorYellow = new Color(ChatColors.YELLOW.toAwtColor());
    private final Color colorGreen = new Color(ChatColors.GREEN.toAwtColor());
    private BlockPos targetBed = null;
    private int breakStage = 0;
    private int tickCounter = 0;
    private float breakProgress = 0.0F;
    private boolean isBed = false;
    private int savedSlot = -1;
    private boolean readyToBreak = false;
    private boolean breaking = false;
    private boolean waitingForStart = false;
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 6.0F);
    public final PercentProperty speed = new PercentProperty("speed", 0);
    public final BooleanProperty groundSpeed = new BooleanProperty("ground-spoof", false);
    public final BooleanProperty surroundings = new BooleanProperty("surroundings", true);
    public final BooleanProperty toolCheck = new BooleanProperty("tool-check", true);
    public final BooleanProperty whiteList = new BooleanProperty("whitelist", true);
    public final KeyBindProperty keybindToWhitelist = new KeyBindProperty("keybind-to-whitelist", 0, this.whiteList::getValue);
    public final BooleanProperty autoDetect = new BooleanProperty("auto-detect", false, this.whiteList::getValue).childOf(this.whiteList);
    public final ColorProperty whitelistEspColor = new ColorProperty("whitelist-esp-color", new Color(75, 255, 75).getRGB(),
            this.whiteList::getValue).childOf(this.whiteList);
    public final BooleanProperty bedEsp = new BooleanProperty("bed-esp", false);
    public final ModeProperty bedEspMode = new ModeProperty("bed-esp-mode", 0, new String[]{"DEFAULT", "FULL"}, this.bedEsp::getValue).childOf(this.bedEsp);
    public final ModeProperty bedEspColor = new ModeProperty("bed-esp-color", 0, new String[]{"CUSTOM", "HUD"}, this.bedEsp::getValue).childOf(this.bedEsp);
    public final ColorProperty bedEspCustomColor = new ColorProperty("bed-esp-custom-color", (int) 8085714755840333141L,
            () -> this.bedEsp.getValue() && this.bedEspColor.getValue() == 0).childOf(this.bedEsp);
    public final PercentProperty bedEspOpacity = new PercentProperty("bed-esp-opacity", 25, this.bedEsp::getValue).childOf(this.bedEsp);
    public final BooleanProperty bedEspOutline = new BooleanProperty("bed-esp-outline", false, this.bedEsp::getValue).childOf(this.bedEsp);
    public final BooleanProperty bedEspObsidian = new BooleanProperty("bed-esp-obsidian", true, this.bedEsp::getValue).childOf(this.bedEsp);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT", "STRICT"});
    public final ModeProperty showTarget = new ModeProperty("show-target", 1, new String[]{"NONE", "DEFAULT", "HUD"});
    public final ModeProperty showProgress = new ModeProperty("show-progress", 1, new String[]{"NONE", "DEFAULT", "HUD"});

    private void resetBreaking() {
        if (this.targetBed != null) {
            mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), this.targetBed, -1);
        }
        this.targetBed = null;
        this.breakStage = 0;
        this.tickCounter = 0;
        this.breakProgress = 0.0F;
        this.isBed = false;
        this.readyToBreak = false;
        this.breaking = false;
    }

    private boolean isScaffoldEnabled() {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    /** Stops an already-started server-side dig before clearing BedBreaker's local state. */
    private void cancelBreaking() {
        if (this.targetBed != null && this.breakStage >= 1) {
            PacketUtil.sendPacket(new C07PacketPlayerDigging(
                    Action.ABORT_DESTROY_BLOCK, this.targetBed, this.getHitFacing(this.targetBed)
            ));
        }
        this.restoreSlot();
        this.resetBreaking();
    }

    private float calcProgress() {
        if (this.targetBed == null) {
            return 0.0F;
        } else {
            float progress = this.breakProgress;
            if (this.groundSpeed.getValue()) {
                int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, mc.theWorld.getBlockState(this.targetBed).getBlock());
                progress = (float) this.tickCounter * this.getBreakDelta(mc.theWorld.getBlockState(this.targetBed), this.targetBed, slot, true);
            }
            return Math.min(1.0F, progress / (1.0F - 0.3F * ((float) this.speed.getValue().intValue() / 100.0F)));
        }
    }

    private void restoreSlot() {
        if (this.savedSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.savedSlot;
            this.syncHeldItem();
            this.savedSlot = -1;
        }
    }

    private void syncHeldItem() {
        int currentPlayerItem = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
        if (mc.thePlayer.inventory.currentItem != currentPlayerItem) {
            mc.thePlayer.stopUsingItem();
        }
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }

    private boolean hasProperTool(Block block) {
        Material material = block.getMaterial();
        if (material != Material.iron && material != Material.anvil && material != Material.rock) {
            return true;
        } else {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null) {
                    Item item = stack.getItem();
                    if (item instanceof ItemPickaxe) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private EnumFacing getHitFacing(BlockPos blockPos) {
        double x = (double) blockPos.getX() + 0.5 - mc.thePlayer.posX;
        double y = (double) blockPos.getY() + 0.25 - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
        double z = (double) blockPos.getZ() + 0.5 - mc.thePlayer.posZ;
        float[] rotations = RotationUtil.getRotationsTo(x, y, z, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], 8.0, 1.0F);
        return mop == null ? EnumFacing.UP : mop.sideHit;
    }

    private float getDigSpeed(IBlockState iBlockState, int slot, boolean boolean5) {
        ItemStack item = mc.thePlayer.inventory.getStackInSlot(slot);
        float digSpeed = item == null ? 1.0F : item.getItem().getDigSpeed(item, iBlockState);
        if (digSpeed > 1.0F) {
            int enchantmentLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, item);
            if (enchantmentLevel > 0) {
                digSpeed += (float) (enchantmentLevel * enchantmentLevel + 1);
            }
        }
        if (mc.thePlayer.isPotionActive(Potion.digSpeed)) {
            digSpeed *= 1.0F + (float) (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2F;
        }
        if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
            switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
                case 0:
                    digSpeed *= 0.3F;
                    break;
                case 1:
                    digSpeed *= 0.09F;
                    break;
                case 2:
                    digSpeed *= 0.0027F;
                    break;
                default:
                    digSpeed *= 8.1E-4F;
            }
        }
        if (mc.thePlayer.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) {
            digSpeed /= 5.0F;
        }
        if (!boolean5) {
            digSpeed /= 5.0F;
        }
        return digSpeed;
    }

    boolean canHarvest(Block block, int slot) {
        if (block.getMaterial().isToolNotRequired()) {
            return true;
        } else {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            return stack != null && stack.canHarvestBlock(block);
        }
    }

    private float getBreakDelta(IBlockState iBlockState, BlockPos blockPos, int slot, boolean boolean5) {
        Block block = iBlockState.getBlock();
        float hardness = block.getBlockHardness(mc.theWorld, blockPos);
        float boost = this.canHarvest(block, slot) ? 30.0F : 100.0F;
        return hardness < 0.0F ? 0.0F : this.getDigSpeed(iBlockState, slot, boolean5) / hardness / boost;
    }

    private float calcBlockStrength(BlockPos blockPos) {
        IBlockState blockState = mc.theWorld.getBlockState(blockPos);
        int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, blockState.getBlock());
        return this.getBreakDelta(blockState, blockPos, slot, mc.thePlayer.onGround);
    }

    private BlockPos validateBedPlacement(BlockPos bedPosition) {
        IBlockState blockState = mc.theWorld.getBlockState(bedPosition);
        if (blockState.getBlock() instanceof BlockBed) {
            ArrayList<BlockPos> pos = new ArrayList<>();
            EnumPartType partType = blockState.getValue(BlockBed.PART);
            EnumFacing facing = blockState.getValue(BlockBed.FACING);
            for (BlockPos blockPos : Arrays.asList(bedPosition, bedPosition.offset(partType == EnumPartType.HEAD ? facing.getOpposite() : facing))) {
                for (EnumFacing enumFacing : Arrays.asList(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST)) {
                    Block block = mc.theWorld.getBlockState(blockPos.offset(enumFacing)).getBlock();
                    if (BlockUtil.isReplaceable(block)) {
                        return null;
                    }
                    if (!(block instanceof BlockBed)) {
                        pos.add(blockPos.offset(enumFacing));
                    }
                }
            }
            if (!pos.isEmpty()) {
                pos.sort(
                        (blockPos, blockPos2) -> {
                            int o = Float.compare(this.calcBlockStrength(blockPos2), this.calcBlockStrength(blockPos));
                            return o != 0
                                    ? o
                                    : Double.compare(
                                    blockPos.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ),
                                    blockPos2.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ)
                            );
                        }
                );
                return pos.get(0);
            }
        }
        return null;
    }

    private BlockPos findNearestBed() {
        return this.findTargetBed(mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }

    private BlockPos findNearestBedBlock() {
        return this.findNearestBedBlock(this.range.getValue().doubleValue(), true);
    }

    private BlockPos findNearestBedBlock(double radius, boolean requireBreakReach) {
        ArrayList<BlockPos> beds = new ArrayList<>();
        int sX = MathHelper.floor_double(mc.thePlayer.posX);
        int sY = MathHelper.floor_double(mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
        int sZ = MathHelper.floor_double(mc.thePlayer.posZ);
        int searchDistance = (int) Math.ceil(radius);
        double radiusSq = radius * radius;
        for (int i = sX - searchDistance; i <= sX + searchDistance; i++) {
            for (int j = sY - searchDistance; j <= sY + searchDistance; j++) {
                for (int k = sZ - searchDistance; k <= sZ + searchDistance; k++) {
                    BlockPos blockPos = new BlockPos(i, j, k);
                    double distanceSq = blockPos.distanceSqToCenter(
                            mc.thePlayer.posX,
                            mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                            mc.thePlayer.posZ
                    );
                    if (!this.bedWhitelist.contains(blockPos)
                            && mc.theWorld.getBlockState(blockPos).getBlock() instanceof BlockBed
                            && distanceSq <= radiusSq
                            && (!requireBreakReach || PlayerUtil.isBlockWithinReach(
                            blockPos,
                            mc.thePlayer.posX,
                            mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                            mc.thePlayer.posZ,
                            this.range.getValue().doubleValue()))) {
                        beds.add(blockPos);
                    }
                }
            }
        }
        beds.sort(
                Comparator.comparingDouble(
                        blockPos -> blockPos.distanceSqToCenter(
                                mc.thePlayer.posX,
                                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                                mc.thePlayer.posZ
                        )
                )
        );
        return beds.isEmpty() ? null : beds.get(0);
    }

    private void whitelistBed(BlockPos bedPosition) {
        IBlockState blockState = mc.theWorld.getBlockState(bedPosition);
        if (!(blockState.getBlock() instanceof BlockBed)) {
            return;
        }
        this.bedWhitelist.add(bedPosition);
        EnumPartType partType = blockState.getValue(BlockBed.PART);
        EnumFacing facing = blockState.getValue(BlockBed.FACING);
        BlockPos otherPart = bedPosition.offset(partType == EnumPartType.HEAD ? facing.getOpposite() : facing);
        if (mc.theWorld.getBlockState(otherPart).getBlock() instanceof BlockBed && !this.bedWhitelist.contains(otherPart)) {
            this.bedWhitelist.add(otherPart);
        }
        if (this.targetBed != null && (this.targetBed.equals(bedPosition) || this.targetBed.equals(otherPart))) {
            this.cancelBreaking();
        }
        if (Myau.notificationManager != null) {
            Myau.notificationManager.add(NotificationManager.NotificationType.INFO, "bedbreaker-whitelist",
                    "BedBreaker", "Bed whitelisted", false);
        }
    }

    /** Records a rendered bed head for the optional Bed ESP overlay. */
    public void observeBedForEsp(BlockPos bedPosition) {
        if (this.isEnabled() && this.bedEsp.getValue()) {
            this.bedEspBeds.add(new BlockPos(bedPosition));
        }
    }

    private boolean isWhitelistedBed(BlockPos firstPart, BlockPos secondPart) {
        return this.bedWhitelist.contains(firstPart) || this.bedWhitelist.contains(secondPart);
    }

    private double getBedEspHeight() {
        return this.bedEspMode.getValue() == 1 ? 1.0 : 0.5625;
    }

    private Color getBedEspColor() {
        return this.bedEspColor.getValue() == 0
                ? new Color(this.bedEspCustomColor.getValue())
                : ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
    }

    private void drawBedEspObsidianBox(AxisAlignedBB box) {
        if (this.bedEspOutline.getValue()) {
            RenderUtil.drawBoundingBox(box, 170, 0, 170, 255, 1.5F);
        }
        RenderUtil.drawFilledBox(box, 170, 0, 170);
    }

    private void drawBedEspObsidian(BlockPos position) {
        if (this.bedEspOutline.getValue()) {
            RenderUtil.drawBlockBoundingBox(position, 1.0, 170, 0, 170, 255, 1.5F);
        }
        RenderUtil.drawBlockBox(position, 1.0, 170, 0, 170);
    }

    private void renderBedEsp() {
        if (!this.isEnabled() || !this.bedEsp.getValue() || mc.theWorld == null) return;
        RenderUtil.enableRenderState();
        for (BlockPos bedHead : this.bedEspBeds) {
            IBlockState headState = mc.theWorld.getBlockState(bedHead);
            if (!(headState.getBlock() instanceof BlockBed) || headState.getValue(BlockBed.PART) != EnumPartType.HEAD) {
                this.bedEspBeds.remove(bedHead);
                continue;
            }
            BlockPos bedFoot = bedHead.offset(headState.getValue(BlockBed.FACING).getOpposite());
            IBlockState footState = mc.theWorld.getBlockState(bedFoot);
            if (!(footState.getBlock() instanceof BlockBed) || footState.getValue(BlockBed.PART) != EnumPartType.FOOT) {
                this.bedEspBeds.remove(bedHead);
                continue;
            }
            if (this.isWhitelistedBed(bedHead, bedFoot)) continue;

            if (this.bedEspObsidian.getValue()) {
                for (EnumFacing facing : Arrays.asList(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST)) {
                    BlockPos headSide = bedHead.offset(facing);
                    BlockPos footSide = bedFoot.offset(facing);
                    boolean headObsidian = mc.theWorld.getBlockState(headSide).getBlock() instanceof BlockObsidian;
                    boolean footObsidian = mc.theWorld.getBlockState(footSide).getBlock() instanceof BlockObsidian;
                    if (headObsidian && footObsidian) {
                        this.drawBedEspObsidianBox(new AxisAlignedBB(
                                Math.min(headSide.getX(), footSide.getX()), headSide.getY(), Math.min(headSide.getZ(), footSide.getZ()),
                                Math.max((double) headSide.getX() + 1.0, (double) footSide.getX() + 1.0), (double) headSide.getY() + 1.0,
                                Math.max((double) headSide.getZ() + 1.0, (double) footSide.getZ() + 1.0))
                                .offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()));
                    } else if (headObsidian) {
                        this.drawBedEspObsidian(headSide);
                    } else if (footObsidian) {
                        this.drawBedEspObsidian(footSide);
                    }
                }
            }

            AxisAlignedBB bedBox = new AxisAlignedBB(
                    Math.min(bedHead.getX(), bedFoot.getX()), bedHead.getY(), Math.min(bedHead.getZ(), bedFoot.getZ()),
                    Math.max((double) bedHead.getX() + 1.0, (double) bedFoot.getX() + 1.0), (double) bedHead.getY() + this.getBedEspHeight(),
                    Math.max((double) bedHead.getZ() + 1.0, (double) bedFoot.getZ() + 1.0))
                    .offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                            -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                            -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
            Color color = this.getBedEspColor();
            if (this.bedEspOutline.getValue()) {
                RenderUtil.drawBoundingBox(bedBox, color.getRed(), color.getGreen(), color.getBlue(), 255, 1.5F);
            }
            RenderUtil.drawFilledBox(bedBox, color.getRed(), color.getGreen(), color.getBlue());
        }
        RenderUtil.disableRenderState();
    }

    private void renderWhitelistEsp() {
        if (!this.isEnabled() || !this.whiteList.getValue() || this.bedWhitelist.isEmpty() || mc.theWorld == null) return;

        Set<BlockPos> renderedBeds = new HashSet<BlockPos>();
        Color color = new Color(this.whitelistEspColor.getValue(), true);
        RenderUtil.enableRenderState();
        for (BlockPos whitelistedPart : this.bedWhitelist) {
            IBlockState state = mc.theWorld.getBlockState(whitelistedPart);
            if (!(state.getBlock() instanceof BlockBed)) continue;

            EnumFacing facing = state.getValue(BlockBed.FACING);
            BlockPos bedHead = state.getValue(BlockBed.PART) == EnumPartType.HEAD
                    ? whitelistedPart
                    : whitelistedPart.offset(facing);
            if (!renderedBeds.add(bedHead)) continue;

            IBlockState headState = mc.theWorld.getBlockState(bedHead);
            BlockPos bedFoot = bedHead.offset(facing.getOpposite());
            IBlockState footState = mc.theWorld.getBlockState(bedFoot);
            if (!(headState.getBlock() instanceof BlockBed)
                    || headState.getValue(BlockBed.PART) != EnumPartType.HEAD
                    || !(footState.getBlock() instanceof BlockBed)
                    || footState.getValue(BlockBed.PART) != EnumPartType.FOOT) {
                continue;
            }

            AxisAlignedBB bedBox = new AxisAlignedBB(
                    Math.min(bedHead.getX(), bedFoot.getX()), bedHead.getY(), Math.min(bedHead.getZ(), bedFoot.getZ()),
                    Math.max((double) bedHead.getX() + 1.0, (double) bedFoot.getX() + 1.0), (double) bedHead.getY() + 0.5625,
                    Math.max((double) bedHead.getZ() + 1.0, (double) bedFoot.getZ() + 1.0))
                    .offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                            -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                            -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
            RenderUtil.drawBoundingBox(bedBox, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), 1.5F);
            RenderUtil.drawFilledBox(bedBox, color.getRed(), color.getGreen(), color.getBlue());
        }
        RenderUtil.disableRenderState();
    }

    private BlockPos findTargetBed(double x, double y, double z) {
        ArrayList<BlockPos> targets = new ArrayList<>();
        int sX = MathHelper.floor_double(x);
        int sY = MathHelper.floor_double(y);
        int sZ = MathHelper.floor_double(z);
        for (int i = sX - 6; i <= sX + 6; i++) {
            for (int j = sY - 6; j <= sY + 6; j++) {
                for (int k = sZ - 6; k <= sZ + 6; k++) {
                    BlockPos newPos = new BlockPos(i, j, k);
                    if (!(Boolean) this.whiteList.getValue() || !this.bedWhitelist.contains(newPos)) {
                        Block block = mc.theWorld.getBlockState(newPos).getBlock();
                        if (block instanceof BlockBed
                                && PlayerUtil.isBlockWithinReach(newPos, x, y, z, this.range.getValue().doubleValue())) {
                            targets.add(newPos);
                        }
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            return null;
        } else {
            targets.sort(
                    Comparator.comparingDouble(
                            blockPos -> blockPos.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ)
                    )
            );
            for (BlockPos blockPos : targets) {
                if (this.surroundings.getValue()) {
                    BlockPos pos = this.validateBedPlacement(blockPos);
                    if (pos != null) {
                        Block block = mc.theWorld.getBlockState(pos).getBlock();
                        if (this.toolCheck.getValue() && !this.hasProperTool(block)) {
                            continue;
                        }
                        return pos;
                    }
                }
                return blockPos;
            }
            return null;
        }
    }

    private void doSwing() {
        if (this.swing.getValue()) {
            mc.thePlayer.swingItem();
        } else {
            PacketUtil.sendPacket(new C0APacketAnimation());
        }
    }

    private Color getProgressColor(int mode) {
        switch (mode) {
            case 1:
                float progress = this.calcProgress();
                if (progress <= 0.5F) {
                    return ColorUtil.interpolate(progress / 0.5F, this.colorRed, this.colorYellow);
                }
                return ColorUtil.interpolate((progress - 0.5F) / 0.5F, this.colorYellow, this.colorGreen);
            case 2:
                return ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
            default:
                return new Color(-1);
        }
    }

    public BedNuker() {
        super("BedBreaker", false);
    }

    public boolean isReady() {
        return this.targetBed != null && this.readyToBreak;
    }

    public boolean isBreaking() {
        return this.targetBed != null && this.breaking;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (!this.whiteList.getValue()
                || this.isScaffoldEnabled()
                || this.keybindToWhitelist.getValue() == 0
                || event.getKey() != this.keybindToWhitelist.getValue()
                || mc.currentScreen != null
                || mc.theWorld == null
                || mc.thePlayer == null) {
            return;
        }
        BlockPos nearestBed = this.autoDetect.getValue()
                ? this.findNearestBedBlock(AUTO_DETECT_RADIUS, false)
                : this.findNearestBedBlock();
        if (nearestBed != null) {
            this.whitelistBed(nearestBed);
        }
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.isScaffoldEnabled()) {
                this.cancelBreaking();
                return;
            }
            if (this.targetBed != null) {
                if (mc.theWorld.isAirBlock(this.targetBed) || !PlayerUtil.canReach(this.targetBed, this.range.getValue().doubleValue())) {
                    this.restoreSlot();
                    this.resetBreaking();
                } else if (!this.isBed) {
                    BlockPos nearestBed = this.findNearestBed();
                    if (nearestBed != null && mc.theWorld.getBlockState(nearestBed).getBlock() instanceof BlockBed) {
                        this.resetBreaking();
                    }
                }
            }
            if (this.targetBed != null) {
                int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, mc.theWorld.getBlockState(this.targetBed).getBlock());
                if (this.savedSlot == -1) {
                    this.savedSlot = mc.thePlayer.inventory.currentItem;
                    mc.thePlayer.inventory.currentItem = slot;
                    this.syncHeldItem();
                }
                switch (this.breakStage) {
                    case 0:
                        if (!mc.thePlayer.isUsingItem()) {
                            this.doSwing();
                            PacketUtil.sendPacket(
                                    new C07PacketPlayerDigging(Action.START_DESTROY_BLOCK, this.targetBed, this.getHitFacing(this.targetBed))
                            );
                            this.doSwing();
                            mc.effectRenderer.addBlockHitEffects(this.targetBed, this.getHitFacing(this.targetBed));
                            this.breakStage = 1;
                        }
                        break;
                    case 1:
                        this.breaking = true;
                        this.tickCounter++;
                        this.breakProgress = this.breakProgress
                                + this.getBreakDelta(mc.theWorld.getBlockState(this.targetBed), this.targetBed, slot, mc.thePlayer.onGround);
                        float tick = (float) this.tickCounter;
                        IBlockState blockState = mc.theWorld.getBlockState(this.targetBed);
                        boolean canBreak = mc.thePlayer.onGround && this.groundSpeed.getValue();
                        BlockPos target = this.targetBed;
                        float delta = tick * this.getBreakDelta(blockState, target, slot, canBreak);
                        mc.effectRenderer.addBlockHitEffects(this.targetBed, this.getHitFacing(this.targetBed));
                        if (this.breakProgress >= 1.0F - 0.3F * ((float) this.speed.getValue().intValue() / 100.0F)
                                || delta >= 1.0F - 0.3F * ((float) this.speed.getValue().intValue() / 100.0F)) {
                            this.breaking = false;
                            PacketUtil.sendPacket(
                                    new C07PacketPlayerDigging(Action.STOP_DESTROY_BLOCK, this.targetBed, this.getHitFacing(this.targetBed))
                            );
                            this.doSwing();
                            IBlockState blockState_ = mc.theWorld.getBlockState(this.targetBed);
                            Block block = blockState_.getBlock();
                            if (block.getMaterial() != Material.air) {
                                mc.theWorld.playAuxSFX(2001, this.targetBed, Block.getStateId(blockState_));
                                mc.theWorld.setBlockToAir(this.targetBed);
                            }
                            if (block instanceof BlockBed) {
                                this.timer.reset();
                            }
                            this.breakStage = 2;
                        }
                        break;
                    case 2:
                        this.restoreSlot();
                        this.resetBreaking();
                }
                if (this.targetBed != null) {
                    return;
                }
            }
            if (mc.thePlayer.capabilities.allowEdit && this.timer.hasTimeElapsed(500)) {
                this.targetBed = this.findNearestBed();
                this.breakStage = 0;
                this.tickCounter = 0;
                this.breakProgress = 0.0F;
                this.isBed = this.targetBed != null && mc.theWorld.getBlockState(this.targetBed).getBlock() instanceof BlockBed;
                this.restoreSlot();
                if (this.targetBed != null) {
                    this.readyToBreak = true;
                }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && !this.isScaffoldEnabled() && event.getType() == EventType.PRE) {
            if (this.isReady()) {
                double x = (double) this.targetBed.getX() + 0.5 - mc.thePlayer.posX;
                double y = (double) this.targetBed.getY() + 0.5 - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                double z = (double) this.targetBed.getZ() + 0.5 - mc.thePlayer.posZ;
                float[] rotations = RotationUtil.getRotationsTo(x, y, z, event.getYaw(), event.getPitch());
                event.setRotation(rotations[0], rotations[1], 5);
                event.setPervRotation(this.moveFix.getValue() != 0 ? rotations[0] : mc.thePlayer.rotationYaw, 5);
            }
        }
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled() && !this.isScaffoldEnabled()) {
            if (this.isBreaking()
                    && !Myau.playerStateManager.attacking
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing
                    && !Myau.playerStateManager.swinging) {
                this.doSwing();
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && !this.isScaffoldEnabled()) {
            if (this.moveFix.getValue() == 1
                    && RotationState.isActived()
                    && RotationState.getPriority() == 5.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.isEnabled() && !this.isScaffoldEnabled()) {
            if (this.targetBed != null && (!this.isBed || !this.surroundings.getValue())) {
                if (this.showProgress.getValue() != 0) {
                    HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
                    float scale = hud.scale.getValue();
                    String text = String.format("%d%%", (int) (this.calcProgress() * 100.0F));
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(scale, scale, 0.0F);
                    GlStateManager.disableDepth();
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    int width = mc.fontRendererObj.getStringWidth(text);
                    mc.fontRendererObj
                            .drawString(
                                    text,
                                    (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / scale - (float) width / 2.0F,
                                    (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 2.0F / scale,
                                    this.getProgressColor(this.showProgress.getValue()).getRGB() & 16777215 | -1090519040,
                                    hud.shadow.getValue()
                            );
                    GlStateManager.disableBlend();
                    GlStateManager.enableDepth();
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onRender3D(Render3DEvent event) {
        this.renderBedEsp();
        this.renderWhitelistEsp();
        if (this.isEnabled() && !this.isScaffoldEnabled() && this.targetBed != null && !mc.theWorld.isAirBlock(this.targetBed)) {
            mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), this.targetBed, (int) (this.calcProgress() * 10.0F) - 1);
            if (this.showTarget.getValue() != 0) {
                Color color = this.getProgressColor(this.showTarget.getValue());
                RenderUtil.enableRenderState();
                BlockPos target = this.targetBed;
                double newHeight = this.isBed ? this.getBedEspHeight() : 1.0;
                int r = color.getRed();
                int g = color.getBlue();
                int b = color.getGreen();
                RenderUtil.drawBlockBox(target, newHeight, r, b, g);
                RenderUtil.disableRenderState();
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.waitingForStart = false;
        this.bedWhitelist.clear();
        this.bedEspBeds.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isCancelled() && !this.isScaffoldEnabled()) {
            if (event.getPacket() instanceof S02PacketChat) {
                String text = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
                if (text.contains("§e§lProtect your bed and destroy the enemy bed") || text.contains("§e§lDestroy the enemy bed and then eliminate them")) {
                    this.waitingForStart = true;
                }
            }
            if (event.getPacket() instanceof S08PacketPlayerPosLook && this.waitingForStart) {
                this.waitingForStart = false;
                this.bedWhitelist.clear();
                this.scheduler.schedule(() -> {
                    if (this.isScaffoldEnabled() || mc.theWorld == null || mc.thePlayer == null) {
                        return;
                    }
                    int sX = MathHelper.floor_double(mc.thePlayer.posX);
                    int sY = MathHelper.floor_double(mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
                    int sZ = MathHelper.floor_double(mc.thePlayer.posZ);
                    for (int i = sX - 25; i <= sX + 25; i++) {
                        for (int j = sY - 25; j <= sY + 25; j++) {
                            for (int k = sZ - 25; k <= sZ + 25; k++) {
                                BlockPos blockPos = new BlockPos(i, j, k);
                                Block block = mc.theWorld.getBlockState(blockPos).getBlock();
                                if (block instanceof BlockBed) {
                                    this.bedWhitelist.add(blockPos);
                                }
                            }
                        }
                    }
                }, 1L, TimeUnit.SECONDS);
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !this.isScaffoldEnabled()) {
            if (this.isReady() || this.targetBed != null && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled() && !this.isScaffoldEnabled()) {
            if (this.isReady()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled() && !this.isScaffoldEnabled()) {
            if (this.isReady() || this.targetBed != null && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled() && !this.isScaffoldEnabled()) {
            if (this.savedSlot != -1) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onEnabled() {
        this.refreshBedEsp();
    }

    @Override
    public void onDisabled() {
        this.resetBreaking();
        this.savedSlot = -1;
        this.bedEspBeds.clear();
    }

    @Override
    public void verifyValue(String name) {
        if ("bed-esp".equals(name)) {
            this.refreshBedEsp();
        }
    }

    private void refreshBedEsp() {
        this.bedEspBeds.clear();
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

}

package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.KeyEvent;
import myau.events.KnockbackEvent;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.StrafeEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.ChatUtil;
import myau.util.MoveUtil;
import myau.util.PacketUtil;
import myau.util.RandomUtil;
import myau.util.RotationUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

public final class LongJump extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil fireballTimer = new TimerUtil();
    private final TimerUtil jumpTimer = new TimerUtil();
    private boolean jumping;
    private int tickCounter;
    private int jumpModeStage;
    private boolean readyToUseFireball;
    private boolean fireballLaunched;
    private int savedHotbarSlot = -1;
    public final ModeProperty mode = new ModeProperty("mode", 0,
            new String[]{"FIREBALL", "FIREBALL_MANUAL", "FIREBALL_HIGH", "FIREBALL_FLAT"});
    public final FloatProperty motion = new FloatProperty("motion", 1.0F, 1.0F, 20.0F);
    public final FloatProperty speedMotion = new FloatProperty("speed-motion", 1.0F, 1.0F, 20.0F);
    public final PercentProperty strafe = new PercentProperty("strafe", 0);

    public LongJump() {
        super("LongJump", false);
        this.markUnsafe();
    }

    private int findFireballInHotbar() {
        if (mc.thePlayer == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemFireball) return i;
        }
        return -1;
    }

    private boolean isAutoMode() { return this.mode.getValue() == 0 || this.mode.getValue() == 2 || this.mode.getValue() == 3; }
    private boolean isManualMode() { return this.mode.getValue() == 1; }
    private boolean isLongJumpMode() { return this.isAutoMode() || this.isManualMode(); }
    private boolean canStartJump() { return !this.fireballTimer.hasTimeElapsed(1000L) && !this.jumping; }
    private double motionFactor() { return MoveUtil.getSpeedLevel() > 0 ? this.speedMotion.getValue() : this.motion.getValue(); }

    @EventTarget(Priority.HIGHEST)
    public void onKnockback(KnockbackEvent event) {
        if (!this.isOperational() || event.isCancelled() || !this.isLongJumpMode() || !this.canStartJump()) return;
        event.setCancelled(true);
        this.jumping = true;
        this.tickCounter = 0;
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (!this.isOperational() || mc.thePlayer == null) return;
        if (event.getType() == EventType.PRE && this.isAutoMode() && !this.fireballLaunched && this.readyToUseFireball) {
            int slot = this.findFireballInHotbar();
            if (slot >= 0) {
                this.savedHotbarSlot = mc.thePlayer.inventory.currentItem;
                mc.thePlayer.inventory.currentItem = slot;
                ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                this.fireballTimer.reset();
                this.fireballLaunched = true;
            }
        } else if (event.getType() == EventType.POST && this.savedHotbarSlot >= 0) {
            mc.thePlayer.inventory.currentItem = this.savedHotbarSlot;
            this.savedHotbarSlot = -1;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isOperational() || event.getType() != EventType.PRE || mc.thePlayer == null) return;
        if (this.isLongJumpMode() && this.jumping) {
            this.tickCounter++;
            if (this.tickCounter == 1) {
                this.jumpModeStage = this.mode.getValue() == 2 ? 1
                        : this.mode.getValue() == 3 ? (MoveUtil.isForwardPressed() ? 2 : 1) : 0;
            }
            if (this.tickCounter == 2 && MoveUtil.isForwardPressed()) {
                MoveUtil.setSpeed(MoveUtil.getSpeed() * this.motionFactor());
            }
            if (this.tickCounter >= 1 && this.tickCounter <= 30) {
                if (this.jumpModeStage == 1) {
                    if (this.tickCounter == 1) mc.thePlayer.motionY *= 0.75D;
                    else {
                        double next = mc.thePlayer.motionY / 0.98F + 0.055D;
                        if (next > 0.0D) mc.thePlayer.motionY = next;
                    }
                } else if (this.jumpModeStage == 2) {
                    mc.thePlayer.motionY = this.tickCounter == 1 ? mc.thePlayer.motionY * 0.75D
                            : 0.01D + this.tickCounter * 0.003D;
                }
            }
            if (this.tickCounter >= 30) {
                this.stopJump();
                if (this.isAutoMode()) this.setEnabled(false);
                return;
            }
        }
        if (this.isAutoMode() && !this.jumping && !this.fireballLaunched) {
            if (this.jumpTimer.hasTimeElapsed(1500L)) {
                this.setEnabled(false);
                return;
            }
            this.readyToUseFireball = true;
            float yaw = RotationUtil.quantizeAngle(mc.thePlayer.rotationYaw - 180.0F - RandomUtil.nextFloat(0.0F, 1.0F));
            float pitch = RotationUtil.quantizeAngle(89.0F + RandomUtil.nextFloat(-0.25F, 0.25F));
            event.setRotation(yaw, pitch, 4);
            event.setPervRotation(yaw, 4);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isOperational() && RotationState.isActived() && RotationState.getPriority() == 4.0F
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isOperational() || !this.isLongJumpMode() || !this.jumping
                || this.tickCounter < 5 || this.tickCounter > 30 || this.strafe.getValue() <= 0) return;
        double speed = MoveUtil.getSpeed();
        MoveUtil.setSpeed(speed * (100 - this.strafe.getValue()) / 100.0D, MoveUtil.getDirectionYaw());
        MoveUtil.addSpeed(speed * this.strafe.getValue() / 100.0D, MoveUtil.getMoveYaw());
        MoveUtil.setSpeed(speed);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (!this.isOperational() || mc.thePlayer == null || event.getKey() != mc.gameSettings.keyBindUseItem.getKeyCode()) return;
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        if (stack != null && stack.getItem() instanceof ItemFireball) this.fireballTimer.reset();
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (!this.isOperational() || event.getType() != EventType.RECEIVE || event.isCancelled()
                || !(event.getPacket() instanceof S08PacketPlayerPosLook)) return;
        this.stopJump();
        if (this.isAutoMode()) this.setEnabled(false);
    }

    @Override
    public void onEnabled() {
        this.jumpTimer.reset();
        if (this.isAutoMode() && this.findFireballInHotbar() < 0) {
            this.setEnabled(false);
            ChatUtil.sendFormatted(String.format("%s%s: &cNo fireball found in your hotbar!&r", Myau.clientName, this.getName()));
        }
    }

    @Override
    public void onDisabled() {
        this.stopJump();
        this.readyToUseFireball = false;
        this.fireballLaunched = false;
        this.savedHotbarSlot = -1;
    }

    private void stopJump() {
        this.jumping = false;
        this.tickCounter = 0;
        this.jumpModeStage = 0;
    }

    @Override
    public String[] getSuffix() {
        String value = this.mode.getModeString();
        return new String[]{value.contains("FIREBALL") ? "Fireball" : CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, value)};
    }
}

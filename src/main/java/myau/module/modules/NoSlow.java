package myau.module.modules;

import myau.Myau;
import myau.enums.FloatModules;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.LivingUpdateEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.RightClickMouseEvent;
import myau.mixin.IAccessorEntityPlayer;
import myau.module.Module;
import myau.util.BlockUtil;
import myau.util.ItemUtil;
import myau.util.PlayerUtil;
import myau.util.TeamUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.BlockPos;

public class NoSlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int lastSlot = -1;
    private boolean grimSlowdownTick;
    public final ModeProperty swordMode = new ModeProperty("sword-mode", 1, new String[]{"NONE", "VANILLA", "Grim"});
    public final PercentProperty swordMotion = new PercentProperty("sword-motion", 100, () -> this.swordMode.getValue() != 0);
    public final BooleanProperty swordSprint = new BooleanProperty("sword-sprint", true, () -> this.swordMode.getValue() != 0);
    public final ModeProperty consumablesMode = new ModeProperty("consumables-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT", "Grim"});
    public final PercentProperty consumablesMotion = new PercentProperty("consumables-motion", 100, () -> this.consumablesMode.getValue() != 0);
    public final BooleanProperty consumablesSprint = new BooleanProperty("consumables-sprint", true, () -> this.consumablesMode.getValue() != 0);
    public final BooleanProperty lastTickSlowdown = new BooleanProperty("last-tick-slowdown", false,
            () -> this.consumablesMode.getValue() == 3).childOf(this.consumablesMode);
    public final ModeProperty bowMode = new ModeProperty("bow-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT", "Grim"});
    public final PercentProperty bowMotion = new PercentProperty("bow-motion", 100, () -> this.bowMode.getValue() != 0);
    public final BooleanProperty bowSprint = new BooleanProperty("bow-sprint", true, () -> this.bowMode.getValue() != 0);

    public NoSlow() {
        super("NoSlow", false);
    }

    public boolean isSwordActive() {
        return this.swordMode.getValue() != 0 && ItemUtil.isHoldingSword();
    }

    public boolean isConsumablesActive() {
        return this.consumablesMode.getValue() != 0 && ItemUtil.isEating();
    }

    public boolean isBowActive() {
        return this.bowMode.getValue() != 0 && ItemUtil.isUsingBow();
    }

    public boolean isFloatMode() {
        return this.consumablesMode.getValue() == 2 && ItemUtil.isEating()
                || this.bowMode.getValue() == 2 && ItemUtil.isUsingBow();
    }

    public boolean isGrimMode() {
        return this.swordMode.getValue() == 2 && ItemUtil.isHoldingSword()
                || this.consumablesMode.getValue() == 3 && ItemUtil.isEating()
                || this.bowMode.getValue() == 3 && ItemUtil.isUsingBow();
    }

    public boolean shouldUseVanillaSlowdown() {
        int remainingTicks = ((IAccessorEntityPlayer) mc.thePlayer).getItemInUseCount();
        return this.isEnabled()
                && this.consumablesMode.getValue() == 3
                && this.lastTickSlowdown.getValue()
                && ItemUtil.isEating()
                && remainingTicks > 0
                && remainingTicks <= 3;
    }

    public boolean isAnyActive() {
        return mc.thePlayer.isUsingItem() && (this.isSwordActive() || this.isConsumablesActive() || this.isBowActive());
    }

    public boolean canSprint() {
        return this.isSwordActive() && this.swordSprint.getValue()
                || this.isConsumablesActive() && this.consumablesSprint.getValue()
                || this.isBowActive() && this.bowSprint.getValue();
    }

    public int getMotionMultiplier() {
        if (this.isGrimMode()) {
            return this.grimSlowdownTick ? 20 : 100;
        } else if (ItemUtil.isHoldingSword()) {
            return this.swordMotion.getValue();
        } else if (ItemUtil.isEating()) {
            return this.consumablesMotion.getValue();
        } else {
            return ItemUtil.isUsingBow() ? this.bowMotion.getValue() : 100;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.swordMode.getModeString()};
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.isAnyActive()) {
            if (this.shouldUseVanillaSlowdown()) {
                this.grimSlowdownTick = false;
            } else {
                if (this.isGrimMode()) {
                    this.grimSlowdownTick = !this.grimSlowdownTick;
                } else {
                    this.grimSlowdownTick = false;
                }
                float multiplier = (float) this.getMotionMultiplier() / 100.0F;
                mc.thePlayer.movementInput.moveForward *= multiplier;
                mc.thePlayer.movementInput.moveStrafe *= multiplier;
            }
            if (!this.canSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        } else {
            this.grimSlowdownTick = false;
        }
    }

    @EventTarget(Priority.LOW)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled() && this.isFloatMode()) {
            int item = mc.thePlayer.inventory.currentItem;
            if (this.lastSlot != item && PlayerUtil.isUsingItem()) {
                this.lastSlot = item;
                Myau.floatManager.setFloatState(true, FloatModules.NO_SLOW);
            }
        } else {
            this.lastSlot = -1;
            Myau.floatManager.setFloatState(false, FloatModules.NO_SLOW);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            if (mc.objectMouseOver != null) {
                switch (mc.objectMouseOver.typeOfHit) {
                    case BLOCK:
                        BlockPos blockPos = mc.objectMouseOver.getBlockPos();
                        if (BlockUtil.isInteractable(blockPos) && !PlayerUtil.isSneaking()) {
                            return;
                        }
                        break;
                    case ENTITY:
                        Entity entityHit = mc.objectMouseOver.entityHit;
                        if (entityHit instanceof EntityVillager) {
                            return;
                        }
                        if (entityHit instanceof EntityLivingBase && TeamUtil.isShop((EntityLivingBase) entityHit)) {
                            return;
                        }
                }
            }
            if (this.isFloatMode() && !Myau.floatManager.isPredicted() && mc.thePlayer.onGround) {
                event.setCancelled(true);
                mc.thePlayer.motionY = 0.42F;
            }
        }
    }
}

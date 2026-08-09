package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.SteppedFloatProperty;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import myau.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AutoClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private boolean leftClickPending;
    private boolean rightClickPending;
    private long leftClickDelay;
    private long rightClickDelay;

    public final BooleanProperty leftClicks = new BooleanProperty("left-clicks", true);
    public final SteppedFloatProperty leftTargetCPS = new SteppedFloatProperty(
            "left-target-cps", 10.0F, 1.0F, 20.0F, 0.5F, this.leftClicks::getValue
    );
    public final BooleanProperty rightClicks = new BooleanProperty("right-clicks", true);
    public final SteppedFloatProperty rightTargetCPS = new SteppedFloatProperty(
            "right-target-cps", 10.0F, 1.0F, 20.0F, 0.5F, this.rightClicks::getValue
    );
    public final BooleanProperty breakBlocks = new BooleanProperty("allow-breaking-blocks", true);
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
    public final IntProperty randomization = new IntProperty("randomization", 100, 50, 150);

    public AutoClicker() {
        super("AutoClicker", false);
        this.leftClicks.setDisplayName("Left Clicks");
        this.leftTargetCPS.setDisplayName("Target CPS");
        this.rightClicks.setDisplayName("Right Clicks");
        this.rightTargetCPS.setDisplayName("Target CPS");
        this.breakBlocks.setDisplayName("Allow breaking blocks");
        this.weaponsOnly.setDisplayName("Weapons only");
        this.randomization.setDisplayName("Randomization");
    }

    /**
     * Computes one interval from the selected CPS and symmetric millisecond jitter.
     * The result is always positive, even when jitter is larger than the base interval.
     */
    static long nextClickDelay(float targetCPS, int randomizationMS) {
        long baseDelay = Math.round(1000.0D / targetCPS);
        long jitter = RandomUtil.nextLong(-randomizationMS, randomizationMS);
        return Math.max(1L, baseDelay + jitter);
    }

    private long nextLeftClickDelay() {
        return nextClickDelay(this.leftTargetCPS.getValue(), this.randomization.getValue());
    }

    private long nextRightClickDelay() {
        return nextClickDelay(this.rightTargetCPS.getValue(), this.randomization.getValue());
    }

    private boolean isBreakingBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    private boolean isWeaponEligible() {
        return !this.weaponsOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant();
    }

    private boolean canLeftClick() {
        return this.isBreakingBlock() ? this.breakBlocks.getValue() : this.isWeaponEligible();
    }

    private boolean canRightClick() {
        return this.isWeaponEligible();
    }

    private boolean shouldUseVanillaBlockBreaking() {
        return this.leftClicks.getValue()
                && this.breakBlocks.getValue()
                && this.isBreakingBlock()
                && mc.gameSettings.keyBindAttack.isKeyDown()
                && !mc.thePlayer.isUsingItem();
    }

    private void resetClickState() {
        this.leftClickPending = false;
        this.rightClickPending = false;
        this.leftClickDelay = 0L;
        this.rightClickDelay = 0L;
        if (mc.gameSettings != null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;

        if (this.leftClickDelay > 0L) this.leftClickDelay -= 50L;
        if (this.rightClickDelay > 0L) this.rightClickDelay -= 50L;

        if (mc.currentScreen != null || mc.thePlayer == null) {
            this.leftClickPending = false;
            this.rightClickPending = false;
            return;
        }

        if (this.leftClickPending) {
            this.leftClickPending = false;
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        if (this.rightClickPending) {
            this.rightClickPending = false;
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }

        if (!this.isEnabled()) return;

        if (this.shouldUseVanillaBlockBreaking()) {
            // Block damage is continuous in Minecraft 1.8. Do not pulse the
            // attack key, otherwise sendClickBlockToController can reset the
            // block-damage progress between AutoClicker clicks.
            this.leftClickPending = false;
            this.leftClickDelay = 0L;
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
        } else if (this.leftClicks.getValue()
                && mc.gameSettings.keyBindAttack.isKeyDown()
                && !mc.thePlayer.isUsingItem()) {
            if (this.canLeftClick()) {
                while (this.leftClickDelay <= 0L) {
                    this.leftClickPending = true;
                    this.leftClickDelay += this.nextLeftClickDelay();
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                    KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                }
            } else {
                this.leftClickDelay = 0L;
            }
        } else if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            this.leftClickDelay = 0L;
        }

        if (this.rightClicks.getValue()
                && mc.gameSettings.keyBindUseItem.isKeyDown()
                && !mc.thePlayer.isUsingItem()) {
            if (this.canRightClick()) {
                while (this.rightClickDelay <= 0L) {
                    this.rightClickPending = true;
                    this.rightClickDelay += this.nextRightClickDelay();
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
                }
            } else {
                this.rightClickDelay = 0L;
            }
        } else if (!mc.gameSettings.keyBindUseItem.isKeyDown()) {
            this.rightClickDelay = 0L;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && this.leftClicks.getValue() && !event.isCancelled()) {
            this.leftClickDelay += this.nextLeftClickDelay();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled() && this.rightClicks.getValue() && !event.isCancelled()) {
            this.rightClickDelay += this.nextRightClickDelay();
        }
    }

    @Override
    public void onEnabled() {
        this.resetClickState();
    }

    @Override
    public void onDisabled() {
        this.resetClickState();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%.1f / %.1f", this.leftTargetCPS.getValue(), this.rightTargetCPS.getValue())};
    }
}

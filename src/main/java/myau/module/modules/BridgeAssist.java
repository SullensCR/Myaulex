package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.KeyBindUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;

import java.util.concurrent.ThreadLocalRandom;

/** Automatically sneaks at a projected block edge while bridging. */
public final class BridgeAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty edgeOffset = new FloatProperty("edge-offset", 0.15F, 0.0F, 0.3F);
    public final IntProperty unsneakDelay = new IntProperty("unsneak-delay-ms", 60, 0, 500);
    public final ModeProperty selectBlocks = new ModeProperty("select-blocks", 0,
            new String[]{"NO", "ON_DEPLETION", "ALWAYS"});
    public final BooleanProperty randomize = new BooleanProperty("randomize", true);
    public final BooleanProperty sneakOnJump = new BooleanProperty("sneak-on-jump", true);
    public final BooleanProperty avoidDoubleSneaking = new BooleanProperty("avoid-double-sneaking", true);
    public final BooleanProperty requireSneakKey = new BooleanProperty("require-sneak-key", false);
    public final BooleanProperty requireBlocks = new BooleanProperty("require-blocks", true);
    public final BooleanProperty requireLookingDown = new BooleanProperty("require-looking-down", true);
    public final BooleanProperty requireNotForward = new BooleanProperty("require-not-moving-forward", false);

    private long releaseAt;
    private boolean forced;
    private int previousSlot = -1;

    public BridgeAssist() {
        super("BridgeAssist", false, false, "Sneaks automatically near block edges.");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) return;
        if (!conditionsMet()) {
            releaseIfDue(true);
            return;
        }
        selectBlockIfNeeded();

        float randomized = randomize.getValue()
                ? (float) ThreadLocalRandom.current().nextDouble(-0.025, 0.025) : 0.0F;
        double projection = 0.31D - Math.max(0.0F, Math.min(0.3F, edgeOffset.getValue() + randomized));
        float yaw = mc.thePlayer.rotationYaw;
        double x = mc.thePlayer.posX + -Math.sin(Math.toRadians(yaw)) * projection;
        double z = mc.thePlayer.posZ + Math.cos(Math.toRadians(yaw)) * projection;
        boolean edge = isReplaceable(new BlockPos(x, mc.thePlayer.posY - 1.0D, z));
        boolean jumping = sneakOnJump.getValue() && !mc.thePlayer.onGround && mc.thePlayer.motionY > 0;
        if (edge || jumping) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            forced = true;
            releaseAt = System.currentTimeMillis() + effectiveUnsneakDelay();
        } else {
            releaseIfDue(false);
        }
    }

    private boolean conditionsMet() {
        if (mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return false;
        if (requireSneakKey.getValue() && !GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) return false;
        if (requireBlocks.getValue() && findBlockSlot() < 0) return false;
        if (requireLookingDown.getValue() && mc.thePlayer.rotationPitch < 55.0F) return false;
        return !requireNotForward.getValue() || mc.thePlayer.movementInput.moveForward <= 0.0F;
    }

    private int effectiveUnsneakDelay() {
        if (!randomize.getValue()) return unsneakDelay.getValue();
        return Math.max(0, unsneakDelay.getValue() + ThreadLocalRandom.current().nextInt(-20, 21));
    }

    private void selectBlockIfNeeded() {
        if (selectBlocks.getValue() == 0) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        boolean depleted = held == null || held.stackSize <= 1;
        if (selectBlocks.getValue() == 1 && !depleted) return;
        int slot = findBlockSlot();
        if (slot >= 0 && slot != mc.thePlayer.inventory.currentItem) {
            if (previousSlot < 0) previousSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = slot;
        }
    }

    private int findBlockSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock) return slot;
        }
        return -1;
    }

    private boolean isReplaceable(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block.isReplaceable(mc.theWorld, pos);
    }

    private void releaseIfDue(boolean immediate) {
        if (!forced || (!immediate && System.currentTimeMillis() < releaseAt)) return;
        forced = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
        if (previousSlot >= 0) {
            mc.thePlayer.inventory.currentItem = previousSlot;
            previousSlot = -1;
        }
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null) releaseIfDue(true);
    }
}

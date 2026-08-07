package myau.render.ui;

import myau.util.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Reusable renderer for the exported Scaffold progressbar design. */
public final class ProgressbarRenderer {
    private static final String BOX_ASSET = "ui/progressbar/progressbar-box@2x.png";
    private static final String TRACK_ASSET = "ui/progressbar/track@2x.png";

    private ProgressbarRenderer() {
    }

    public static void render(UiRenderer renderer, float componentX, float componentY,
                              float progress, ItemStack displayStack, int alpha) {
        float exportX = componentX + ProgressbarSizes.BOX_EXPORT_OFFSET_X;
        float exportY = componentY + ProgressbarSizes.BOX_EXPORT_OFFSET_Y;
        int tint = (alpha & 255) << 24 | 0x00FFFFFF;

        // The PNG contains the exported tint and shadow. The transparent
        // backdrop pass adds the live Minecraft scene blur behind it.
        renderer.backdrop(
                componentX + ProgressbarSizes.BOX_X,
                componentY + ProgressbarSizes.BOX_Y,
                ProgressbarSizes.BOX_WIDTH,
                ProgressbarSizes.BOX_HEIGHT,
                ProgressbarSizes.BOX_RADIUS,
                0x00000000
        );
        renderer.imageResource(
                BOX_ASSET,
                exportX,
                exportY,
                ProgressbarSizes.EXPORT_WIDTH,
                ProgressbarSizes.EXPORT_HEIGHT,
                tint
        );
        renderer.imageResource(
                TRACK_ASSET,
                componentX + ProgressbarSizes.TRACK_ASSET_X,
                componentY + ProgressbarSizes.TRACK_ASSET_Y,
                ProgressbarSizes.TRACK_ASSET_WIDTH,
                ProgressbarSizes.TRACK_ASSET_HEIGHT,
                tint
        );

        float amount = clamp(progress, 0.0F, 1.0F);
        if (amount <= 0.0F) return;

        float fillWidth = ProgressbarSizes.TRACK_WIDTH * amount;
        float fillRadius = Math.min(ProgressbarSizes.TRACK_RADIUS, fillWidth * 0.5F);
        int[] gradient = gradientFor(displayStack);
        renderer.gradientRoundedRect(
                componentX + ProgressbarSizes.TRACK_X,
                componentY + ProgressbarSizes.TRACK_Y,
                fillWidth,
                ProgressbarSizes.TRACK_HEIGHT,
                fillRadius,
                withAlpha(gradient[0], alpha),
                withAlpha(gradient[1], alpha)
        );
    }

    public static int countableBlockCount(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null) return 0;
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (isCountable(stack)) count += stack.stackSize;
        }
        return count;
    }

    public static ItemStack displayStack(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null) return null;
        ItemStack current = player.getHeldItem();
        if (isCountable(current)) return current;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (isCountable(stack)) return stack;
        }
        return null;
    }

    public static ItemStack displayStack(net.minecraft.entity.player.EntityPlayer player, int slot) {
        if (player == null) return null;
        if (slot >= 0 && slot < 9) {
            return player.inventory.getStackInSlot(slot);
        }
        return displayStack(player);
    }

    private static boolean isCountable(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return !BlockUtil.isInteractable(block) && BlockUtil.isSolid(block);
    }

    private static int[] gradientFor(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
            return new int[]{ProgressbarSizes.OTHER_START, ProgressbarSizes.OTHER_END};
        }

        Block block = ((ItemBlock) stack.getItem()).getBlock();
        if (block == Blocks.ice || block == Blocks.packed_ice) {
            return new int[]{ProgressbarSizes.ICE_START, ProgressbarSizes.ICE_END};
        }
        if (block == Blocks.wool) {
            return woolGradient(stack.getItemDamage() & 15);
        }
        if (block instanceof BlockPlanks || block instanceof BlockLog
                || block == Blocks.wooden_slab || block == Blocks.double_wooden_slab) {
            return new int[]{ProgressbarSizes.WOOD_START, ProgressbarSizes.WOOD_END};
        }
        return new int[]{ProgressbarSizes.OTHER_START, ProgressbarSizes.OTHER_END};
    }

    private static int[] woolGradient(int metadata) {
        int base;
        switch (metadata) {
            case 1: base = 0xFFF9801D; break; // orange
            case 2: base = 0xFFC74EBD; break; // magenta
            case 3: base = 0xFF3AB3DA; break; // light blue
            case 4: base = 0xFFFED83D; break; // yellow
            case 5: base = 0xFF80C71F; break; // lime
            case 6: base = 0xFFF38BAA; break; // pink
            case 7: base = 0xFF474F52; break; // gray
            case 8: base = 0xFF9D9D97; break; // light gray
            case 9: base = 0xFF169C9C; break; // cyan
            case 10: base = 0xFF8932B8; break; // purple
            case 11: base = 0xFF3C44AA; break; // blue
            case 12: base = 0xFF835432; break; // brown
            case 13: base = 0xFF5E7C16; break; // green
            case 14: base = 0xFFB02E26; break; // red
            case 15: base = 0xFF1D1D21; break; // black
            case 0:
            default: base = 0xFFF9FFFE; break; // white
        }
        return new int[]{base, lighten(base, 0.35F)};
    }

    private static int lighten(int color, float amount) {
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        r += Math.round((255 - r) * amount);
        g += Math.round((255 - g) * amount);
        b += Math.round((255 - b) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

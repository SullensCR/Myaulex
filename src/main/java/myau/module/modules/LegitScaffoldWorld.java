package myau.module.modules;

import myau.util.BlockUtil;
import myau.util.ItemUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Live world adapter. Every placement goes through Minecraft's normal right-click path. */
class LegitScaffoldWorld {
    private final Minecraft mc;
    final EntityPlayerSP player;
    final WorldClient world;

    LegitScaffoldWorld(Minecraft mc) {
        this.mc = mc;
        player = mc == null ? null : mc.thePlayer;
        world = mc == null ? null : mc.theWorld;
    }

    static boolean usable(ItemStack stack) {
        if (!ItemUtil.isBlock(stack)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return block.isFullCube() && !(block instanceof BlockFalling);
    }

    boolean fullBlock(BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return false;
        Block block = world.getBlockState(pos).getBlock();
        if (!block.isFullCube() || block instanceof BlockFalling || BlockUtil.isInteractable(block)) return false;
        AxisAlignedBB box = block.getCollisionBoundingBox(world, pos, world.getBlockState(pos));
        return box != null && box.minX == pos.getX() && box.maxX == pos.getX() + 1
                && box.minY == pos.getY() && box.maxY == pos.getY() + 1
                && box.minZ == pos.getZ() && box.maxZ == pos.getZ() + 1;
    }

    BlockPos support(int direction) {
        AxisAlignedBB feet = player.getEntityBoundingBox();
        int y = (int) Math.floor(feet.minY + 0.001D) - 1;
        if (Math.abs(feet.minY - (y + 1)) > 0.001D) return null;
        EnumFacing facing = LegitScaffoldGeometry.facing(direction);
        BlockPos best = null;
        double progress = -Double.MAX_VALUE;
        for (int x = (int) Math.floor(feet.minX + 0.001D); x <= (int) Math.floor(feet.maxX - 0.001D); x++) {
            for (int z = (int) Math.floor(feet.minZ + 0.001D); z <= (int) Math.floor(feet.maxZ - 0.001D); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                boolean inLane = facing.getFrontOffsetX() == 0
                        ? player.posX >= x && player.posX < x + 1 : player.posZ >= z && player.posZ < z + 1;
                double candidate = LegitScaffoldGeometry.edge(pos, facing);
                if (inLane && fullBlock(pos) && candidate > progress) {
                    best = pos;
                    progress = candidate;
                }
            }
        }
        return best;
    }

    double projectedX(int direction) {
        return player.posX + player.motionX + LegitScaffoldGeometry.facing(direction).getFrontOffsetX() * 0.13D;
    }

    double projectedZ(int direction) {
        return player.posZ + player.motionZ + LegitScaffoldGeometry.facing(direction).getFrontOffsetZ() * 0.13D;
    }

    boolean atEdge(BlockPos support, int direction) {
        EnumFacing facing = LegitScaffoldGeometry.facing(direction);
        if (fullBlock(support.offset(facing))) return false;
        return LegitScaffoldGeometry.progress(projectedX(direction), projectedZ(direction), facing)
                >= LegitScaffoldGeometry.edge(support, facing) - 0.05D;
    }

    boolean safeToRelease(BlockPos placed, int direction) {
        return fullBlock(placed) && LegitScaffoldGeometry.centerSupported(placed, projectedX(direction), projectedZ(direction));
    }

    boolean targetOpen(BlockPos target) {
        return world.isBlockLoaded(target) && world.getBlockState(target).getBlock().isReplaceable(world, target)
                && world.checkNoEntityCollision(LegitScaffoldGeometry.cube(target));
    }

    boolean placementWindow(BlockPos support, int direction, float yaw) {
        // Placement geometry must not gate walking across already-supported ground.
        // Check it only when the next movement step actually needs an extension.
        return !atEdge(support, direction) || clearPlacementRay(support, direction, yaw);
    }

    boolean clearPlacementRay(BlockPos support, int direction, float yaw) {
        double reach = mc.playerController.getBlockReachDistance();
        if (!LegitScaffoldGeometry.hasPlacementWindow(support, player.posX, player.posZ, direction, yaw, reach)) return false;
        // Existing blocks ahead are walked across; only an extension needs a clear future ray.
        if (fullBlock(support.offset(LegitScaffoldGeometry.facing(direction)))) return true;
        Vec3 eyes = LegitScaffoldGeometry.windowEyes(support, player.posX, player.posZ, direction);
        MovingObjectPosition hit = world.rayTraceBlocks(eyes,
                LegitScaffoldGeometry.end(eyes, yaw, LegitScaffoldController.PITCH, reach), false, false, false);
        return matchesFace(hit, support, direction);
    }

    MovingObjectPosition placementHit(BlockPos support, int direction, float yaw, float pitch) {
        Vec3 eyes = player.getPositionEyes(1.0F);
        MovingObjectPosition hit = world.rayTraceBlocks(eyes,
                LegitScaffoldGeometry.end(eyes, yaw, pitch, mc.playerController.getBlockReachDistance()), false, false, false);
        return matchesFace(hit, support, direction) ? hit : null;
    }

    static boolean matchesFace(MovingObjectPosition hit, BlockPos support, int direction) {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && support.equals(hit.getBlockPos()) && hit.sideHit == LegitScaffoldGeometry.facing(direction);
    }

    boolean place(MovingObjectPosition hit) {
        ItemStack stack = player.inventory.getCurrentItem();
        if (!usable(stack)) return false;
        boolean placed = mc.playerController.onPlayerRightClick(player, world, stack, hit.getBlockPos(), hit.sideHit, hit.hitVec);
        if (placed) player.swingItem();
        return placed;
    }
}

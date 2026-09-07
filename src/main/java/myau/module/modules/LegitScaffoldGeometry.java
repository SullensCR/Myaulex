package myau.module.modules;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Full-cube geometry shared by live checks and deterministic ray tests. */
final class LegitScaffoldGeometry {
    static EnumFacing facing(int direction) {
        return new EnumFacing[]{EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.EAST}[direction];
    }

    static AxisAlignedBB cube(BlockPos pos) {
        return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    static Vec3 look(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    static Vec3 end(Vec3 eyes, float yaw, float pitch, double reach) {
        Vec3 look = look(yaw, pitch);
        return eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
    }

    static double progress(double x, double z, EnumFacing facing) {
        return x * facing.getFrontOffsetX() + z * facing.getFrontOffsetZ();
    }

    static double edge(BlockPos support, EnumFacing facing) {
        return progress(support.getX() + 0.5D, support.getZ() + 0.5D, facing) + 0.5D;
    }

    /** Does the locked ray have a side-face window before vanilla sneak's 0.3 block overhang? */
    static boolean hasPlacementWindow(BlockPos support, double x, double z, int direction,
                                      float yaw, double reach) {
        EnumFacing facing = facing(direction);
        Vec3 eyes = windowEyes(support, x, z, direction);
        MovingObjectPosition hit = cube(support).calculateIntercept(eyes, end(eyes, yaw, LegitScaffoldController.PITCH, reach));
        // Face edges/corners are deliberately rejected, since floating-point ray ties are unstable.
        if (hit == null || hit.sideHit != facing) return false;
        double lateral = facing.getFrontOffsetX() == 0 ? hit.hitVec.xCoord - support.getX() : hit.hitVec.zCoord - support.getZ();
        return lateral > 0.02D && lateral < 0.98D;
    }

    static Vec3 windowEyes(BlockPos support, double x, double z, int direction) {
        EnumFacing facing = facing(direction);
        double advance = edge(support, facing) + 0.27D - progress(x, z, facing);
        return new Vec3(x + facing.getFrontOffsetX() * advance, support.getY() + 2.54D,
                z + facing.getFrontOffsetZ() * advance);
    }

    static boolean centerSupported(BlockPos block, double x, double z) {
        return x >= block.getX() && x < block.getX() + 1 && z >= block.getZ() && z < block.getZ() + 1;
    }

    private LegitScaffoldGeometry() { }
}

package myau.module.modules;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegitScaffoldGeometryTest {
    @Test public void thirtyConsecutiveFacesPerDirectionAndSideUseIdenticalAngles() {
        for (int d = 0; d < 4; d++) for (LegitScaffoldController.Side side : LegitScaffoldController.Side.values()) {
            float yaw = LegitScaffoldController.targetYaw(d, side);
            EnumFacing facing = LegitScaffoldGeometry.facing(d);
            BlockPos support = new BlockPos(-10, 60, -10);
            for (int i = 0; i < 30; i++) {
                double x = support.getX() + 0.5 + facing.getFrontOffsetX() * 0.77;
                double z = support.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.77;
                assertTrue(LegitScaffoldGeometry.hasPlacementWindow(support, x, z, d, yaw, 4.5));
                Vec3 eyes = new Vec3(x, support.getY() + 2.54, z);
                MovingObjectPosition hit = LegitScaffoldGeometry.cube(support).calculateIntercept(eyes,
                        LegitScaffoldGeometry.end(eyes, yaw, 78, 4.5));
                assertNotNull(hit);
                assertEquals(facing, hit.sideHit);
                assertTrue(hit.hitVec.yCoord > support.getY());
                assertTrue(hit.hitVec.yCoord < support.getY() + 1);
                BlockPos placed = support.offset(facing);
                assertTrue(LegitScaffoldGeometry.centerSupported(placed, x + facing.getFrontOffsetX() * 0.2,
                        z + facing.getFrontOffsetZ() * 0.2));
                support = placed;
            }
        }
    }

    @Test public void obstructionOrWrongFaceNeverCountsAsAPlacement() {
        BlockPos support = new BlockPos(0, 0, 0);
        Vec3 point = new Vec3(0.5, 0.8, 1);
        assertTrue(LegitScaffoldWorld.matchesFace(new MovingObjectPosition(point, EnumFacing.SOUTH, support), support, 0));
        assertFalse(LegitScaffoldWorld.matchesFace(new MovingObjectPosition(point, EnumFacing.UP, support), support, 0));
        assertFalse(LegitScaffoldWorld.matchesFace(new MovingObjectPosition(point, EnumFacing.SOUTH, support.up()), support, 0));
        assertFalse(LegitScaffoldWorld.matchesFace(null, support, 0));
    }

    @Test public void bad_lateral_positions_and_short_reach_are_rejected_without_reaiming() {
        BlockPos support = new BlockPos(0, 0, 0);
        assertFalse(LegitScaffoldGeometry.hasPlacementWindow(support, 0.05, 0.5, 0, 135, 4.5));
        assertFalse(LegitScaffoldGeometry.hasPlacementWindow(support, 0.95, 0.5, 0, -135, 4.5));
        assertFalse(LegitScaffoldGeometry.hasPlacementWindow(support, 0.5, 0.5, 0, 135, 1.0));
        assertFalse(LegitScaffoldGeometry.centerSupported(support, 1.01, 0.5));
    }
}

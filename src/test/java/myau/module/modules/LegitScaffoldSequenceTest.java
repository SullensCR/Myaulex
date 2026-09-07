package myau.module.modules;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

/** Headless level-cube fixture, not a live Minecraft/server acceptance test. */
public class LegitScaffoldSequenceTest {
    @Test public void walksSneaksPlacesAndReleasesForThirtyBlocksWithoutTurning() {
        for (int d = 0; d < 4; d++) for (LegitScaffoldController.Side side : LegitScaffoldController.Side.values()) {
            simulate(d, side);
        }
    }

    private void simulate(int direction, LegitScaffoldController.Side side) {
        LegitScaffoldController c = new LegitScaffoldController();
        float yaw = LegitScaffoldController.targetYaw(direction, side);
        c.begin(direction, yaw, 78); c.turn();
        assertEquals(side, c.side());
        EnumFacing facing = LegitScaffoldGeometry.facing(direction);
        int placements = 0, releases = 0, edgeTick = -1;
        double distance = 0.0, motion = 0.0;
        boolean previousSneak = false;
        for (int tick = 0; tick < 1000 && placements < 30; tick++) {
            if (c.phase() == LegitScaffoldController.Phase.WAIT_SUPPORT) c.confirm(tick, true);
            if (c.phase() == LegitScaffoldController.Phase.APPROACH && distance + motion + 0.13 >= placements + 0.45) {
                c.edge(tick);
                edgeTick = tick;
            }
            // PRE placement uses the previous tick's position, look and sneak state.
            // The newly selected input and movement packet happen afterwards.
            BlockPos support = new BlockPos(facing.getFrontOffsetX() * placements, 0, facing.getFrontOffsetZ() * placements);
            Vec3 eyes = new Vec3(0.5 + facing.getFrontOffsetX() * distance, 2.54,
                    0.5 + facing.getFrontOffsetZ() * distance);
            boolean placedThisTick = false;
            if (previousSneak && c.canPlace(tick, 6)) {
                MovingObjectPosition hit = LegitScaffoldGeometry.cube(support).calculateIntercept(eyes,
                        LegitScaffoldGeometry.end(eyes, yaw, 78, 4.5));
                if (hit != null && hit.sideHit == facing) {
                    if (placements == 0) assertTrue(tick - edgeTick >= 6);
                    else assertTrue("Normal cycle repeated startup delay", tick - edgeTick < 6);
                    c.placed(tick);
                    placements++;
                    placedThisTick = true;
                }
            }
            boolean sneak = c.sneaking(false);
            if (placedThisTick) assertTrue("Placement must stay sneaking through movement", sneak);
            if (previousSneak && !sneak) releases++;
            previousSneak = sneak;
            // Vanilla full-cube walking: .98 input damping, .1 acceleration, .546 ground drag.
            // Sneaking scales each diagonal key by .3 before vanilla normalizes only lengths above 1.
            double acceleration = sneak ? Math.sqrt(2) * 0.3 * 0.98 * 0.1 : 0.1;
            double delta = motion + acceleration;
            double boundary = placements + 0.5;
            if (sneak) while (delta > 0 && distance + delta >= boundary + 0.3) delta = Math.max(0, delta - 0.05);
            distance += delta;
            motion = delta * 0.546;
            assertTrue("Fixture stepped beyond support", distance < boundary + 0.3);
            assertEquals(yaw, c.yaw(), 0);
            assertEquals(78, c.pitch(), 0);
        }
        assertEquals(30, placements);
        assertEquals(29, releases);
    }
}

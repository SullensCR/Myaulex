package myau.module.modules;

import net.minecraft.util.BlockPos;
import org.junit.Test;

import static org.junit.Assert.*;

public class LegitScaffoldApproachTest {
    @Test
    public void supportedApproachDoesNotRequireAnAlreadyValidPlacementRay() {
        ProbeWorld world = new ProbeWorld();
        BlockPos support = new BlockPos(0, 0, 0);
        for (int direction = 0; direction < 4; direction++) {
            for (LegitScaffoldController.Side side : LegitScaffoldController.Side.values()) {
                float yaw = LegitScaffoldController.targetYaw(direction, side);
                assertTrue(world.placementWindow(support, direction, yaw));
            }
        }
        assertEquals(0, world.rayChecks);
    }

    @Test
    public void invalidRayStillPausesAtTheEdgeAndValidRayAllowsStartup() {
        ProbeWorld world = new ProbeWorld();
        world.atEdge = true;
        assertFalse(world.placementWindow(new BlockPos(0, 0, 0), 0, 135));
        world.clearRay = true;
        assertTrue(world.placementWindow(new BlockPos(0, 0, 0), 0, 135));
        assertEquals(2, world.rayChecks);
    }

    private static final class ProbeWorld extends LegitScaffoldWorld {
        boolean atEdge;
        boolean clearRay;
        int rayChecks;

        ProbeWorld() { super(null); }

        @Override boolean atEdge(BlockPos support, int direction) { return atEdge; }

        @Override boolean clearPlacementRay(BlockPos support, int direction, float yaw) {
            rayChecks++;
            return clearRay;
        }
    }
}

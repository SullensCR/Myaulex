package myau.module.modules;

import org.junit.Test;
import static org.junit.Assert.*;

public class LegitScaffoldControllerTest {
    private LegitScaffoldController ready(int direction) {
        LegitScaffoldController c = new LegitScaffoldController();
        c.begin(direction, direction * 90, 0);
        for (int i = 0; i < 8 && c.phase() == LegitScaffoldController.Phase.TURNING; i++) c.turn();
        assertEquals(LegitScaffoldController.Phase.APPROACH, c.phase());
        return c;
    }

    @Test public void firstEdgeWaitsSixWholeTicksAndLaterCyclesDoNot() {
        LegitScaffoldController c = ready(0);
        c.edge(100);
        for (int t = 100; t < 106; t++) assertFalse(c.canPlace(t, 6));
        assertTrue(c.canPlace(106, 6));
        c.placed(106);
        assertFalse(c.confirm(106, true));
        assertTrue(c.sneaking(false));
        assertTrue(c.confirm(107, true));
        assertFalse(c.sneaking(false));
        c.edge(110);
        assertTrue(c.canPlace(110, 6));
    }

    @Test public void missingSupportNeverReleasesAndPauseRequiresFreshStartup() {
        LegitScaffoldController c = ready(0);
        c.edge(1); c.placed(7);
        assertFalse(c.confirm(8, false));
        assertTrue(c.sneaking(false));
        float yaw = c.yaw();
        c.pause();
        assertTrue(c.paused());
        assertFalse(c.canPlace(100, 6));
        assertEquals(yaw, c.yaw(), 0);
        c.begin(0, 0, yaw, 78); c.turn(); c.edge(100);
        assertFalse(c.canPlace(105, 6));
        assertTrue(c.canPlace(106, 6));
    }

    @Test public void everyCameraHeadingMapsToItsNearestRowWithoutAnAlignmentCutoff() {
        for (int step = -14400; step <= 14400; step++) {
            float camera = step / 10.0F;
            int direction = LegitScaffoldController.facingDirection(camera);
            assertTrue(direction >= 0 && direction < 4);
            assertTrue(Math.abs(LegitScaffoldController.wrap(camera - direction * 90)) <= 45.001F);
        }
        assertEquals(-1, LegitScaffoldController.facingDirection(Float.NaN));
        assertEquals(-1, LegitScaffoldController.facingDirection(Float.POSITIVE_INFINITY));
    }

    @Test public void heldMovementStartsAfterEnableWithoutAReleaseOrAnotherKey() {
        for (int step = 0; step < 360; step++) {
            float camera = step;
            LegitScaffoldController c = new LegitScaffoldController();
            c.reset(); // Enabling while the physical movement key is already held.
            float reportedYaw = camera, reportedPitch = 0;
            boolean moving = false;
            for (int tick = 0; tick < 10; tick++) {
                // This is the PRE path used every tick with the same held movement input.
                boolean turning = c.prepare(c.resolveDirection(camera), camera, reportedYaw, reportedPitch);
                reportedYaw = c.yaw(); reportedPitch = c.pitch();
                if (!turning) {
                    assertFalse(c.paused());
                    float[] input = LegitScaffoldController.movement(c.direction(), c.yaw(), false);
                    assertTrue(Math.abs(input[0]) + Math.abs(input[1]) > 0);
                    moving = true;
                    break;
                }
            }
            assertTrue("Held input did not start at camera yaw " + camera, moving);
        }
    }

    @Test public void recoverablePauseResumesWithTheSameHeldInput() {
        LegitScaffoldController c = ready(0);
        c.pause();
        assertTrue(c.prepare(c.resolveDirection(20), 20, c.yaw(), c.pitch()));
        assertFalse(c.prepare(c.resolveDirection(20), 20, c.yaw(), c.pitch()));
        assertFalse(c.paused());
        c.edge(100);
        assertFalse(c.canPlace(105, 6));
        assertTrue(c.canPlace(106, 6));
    }

    @Test public void boundaryJitterDoesNotRepeatedlyRestartTurning() {
        LegitScaffoldController c = ready(0);
        for (float camera : new float[]{44, 45, 46, 44.9F, 49.9F, 45.1F}) {
            assertEquals(0, c.resolveDirection(camera));
            assertFalse(c.prepare(c.resolveDirection(camera), camera, c.yaw(), c.pitch()));
        }
        assertEquals(1, c.resolveDirection(51));
        assertTrue(c.prepare(c.resolveDirection(51), 51, c.yaw(), c.pitch()));
        assertEquals(1, c.resolveDirection(44));
        c.stop();
        assertEquals(0, c.resolveDirection(44)); // Releasing movement allows a fresh nearest-row choice.
    }

    @Test public void turnsAreCappedAndSideSurvivesStopsAndDirectionChanges() {
        LegitScaffoldController c = new LegitScaffoldController();
        c.begin(0, 0, 0, -90);
        assertEquals(LegitScaffoldController.Side.RIGHT, c.side());
        for (int i = 0; i < 10; i++) {
            float yaw = c.yaw(), pitch = c.pitch();
            if (c.phase() == LegitScaffoldController.Phase.TURNING) c.turn();
            assertTrue(Math.abs(c.yaw() - yaw) <= 45);
            assertTrue(Math.abs(c.pitch() - pitch) <= 30);
        }
        assertEquals(135, c.yaw(), 0);
        assertEquals(78, c.pitch(), 0);
        c.stop(); c.begin(1, -45, c.yaw(), c.pitch());
        assertEquals(LegitScaffoldController.Side.RIGHT, c.side());
        c.reset();
        assertNull(c.side());
        c.begin(0, -135, 78);
        assertEquals(LegitScaffoldController.Side.LEFT, c.side());
    }

    @Test public void reportedYawNeverWrapsOnStartOrWhileCrossingAFullTurn() {
        float[] starts = {-1440, -721, -360, -181, -179, 179, 181, 359, 361, 720, 1440};
        for (float start : starts) for (int direction = 0; direction < 4; direction++) {
            for (LegitScaffoldController.Side side : LegitScaffoldController.Side.values()) {
                LegitScaffoldController c = new LegitScaffoldController();
                float target = LegitScaffoldController.targetYaw(direction, side);
                c.begin(direction, target, start, 78);
                assertEquals("Setup normalized the last reported yaw", start, c.yaw(), 0);
                for (int tick = 0; tick < 8 && c.phase() == LegitScaffoldController.Phase.TURNING; tick++) {
                    float previous = c.yaw();
                    c.turn();
                    // Inspect raw packet deltas: wrapping this assertion would hide the bug.
                    assertTrue("Yaw jumped across the wrap boundary", Math.abs(c.yaw() - previous) <= 45);
                }
                assertEquals(LegitScaffoldController.Phase.APPROACH, c.phase());
                assertEquals(start + LegitScaffoldController.wrap(target - start), c.yaw(), 0);
                assertEquals(side, c.side());
            }
        }
    }

    @Test public void directionChangesAndRestartsRetainContinuousYawAndFixedSide() {
        LegitScaffoldController c = new LegitScaffoldController();
        float reportedYaw = 720, reportedPitch = 0;
        for (int segment = 0; segment < 16; segment++) {
            int direction = segment % 4;
            if (segment % 3 == 1) c.pause();
            if (segment % 3 == 2) c.stop();
            for (int tick = 0; tick < 8; tick++) {
                boolean turning = c.prepare(direction, direction * 90, reportedYaw, reportedPitch);
                assertTrue(Math.abs(c.yaw() - reportedYaw) <= 45);
                assertTrue(Math.abs(c.pitch() - reportedPitch) <= 30);
                reportedYaw = c.yaw(); reportedPitch = c.pitch();
                if (!turning) break;
            }
            assertEquals(LegitScaffoldController.Phase.APPROACH, c.phase());
            assertEquals(LegitScaffoldController.Side.RIGHT, c.side());
            assertEquals(855 + segment * 90, c.yaw(), 0);
            for (int tick = 0; tick < 20; tick++) {
                assertFalse(c.prepare(direction, direction * 90, reportedYaw, reportedPitch));
                assertEquals(reportedYaw, c.yaw(), 0);
                assertEquals(78, c.pitch(), 0);
            }
        }
    }

    @Test public void manualSneakAlwaysWinsIncludingRelease() {
        LegitScaffoldController c = ready(0);
        assertTrue(c.sneaking(true));
        c.edge(0); c.placed(6); c.confirm(7, true);
        assertTrue(c.sneaking(true));
        assertFalse(c.sneaking(false));
    }

    @Test public void movementUsesVanillaKeyMagnitudesAndSneakScaling() {
        for (int d = 0; d < 4; d++) for (LegitScaffoldController.Side side : LegitScaffoldController.Side.values()) {
            float yaw = LegitScaffoldController.targetYaw(d, side);
            float[] walk = LegitScaffoldController.movement(d, yaw, false);
            float[] sneak = LegitScaffoldController.movement(d, yaw, true);
            assertEquals(-1, walk[0], 0); // No forward sprint while facing back/sideways.
            assertEquals(walk[0] * 0.3F, sneak[0], 0);
            assertEquals(walk[1] * 0.3F, sneak[1], 0);
            double angle = Math.toRadians(yaw);
            double x = walk[1] * Math.cos(angle) - walk[0] * Math.sin(angle);
            double z = walk[0] * Math.cos(angle) + walk[1] * Math.sin(angle);
            assertEquals(LegitScaffoldGeometry.facing(d).getFrontOffsetX(), x / Math.hypot(x, z), 0.00001);
            assertEquals(LegitScaffoldGeometry.facing(d).getFrontOffsetZ(), z / Math.hypot(x, z), 0.00001);
        }
    }
}

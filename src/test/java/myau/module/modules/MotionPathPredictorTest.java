package myau.module.modules;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class MotionPathPredictorTest {
    private static final double EPSILON = 1.0E-4D;

    @Test
    public void gravityBeginsAfterTheFirstMotionStep() {
        TestEnvironment environment = new TestEnvironment();
        MotionPathPredictor.Prediction prediction = predict(
                playerBox(0.0D, 10.0D, 0.0D), 0.0D, 0.0D, 0.0D, false, 2, null, false, environment
        );

        assertEquals(3, prediction.points().size());
        assertEquals(10.02D, prediction.points().get(1).yCoord, EPSILON);
        assertTrue(prediction.points().get(2).yCoord < prediction.points().get(1).yCoord);
        assertNull(prediction.surface());
    }

    @Test
    public void fallingPathStopsOnTheSupportingFace() {
        TestEnvironment environment = new TestEnvironment(floor(1.0D));
        MotionPathPredictor.Prediction prediction = predict(
                playerBox(0.0D, 3.0D, 0.0D), 0.0D, -0.5D, 0.0D, false, 20, null, false, environment
        );

        assertNotNull(prediction.surface());
        assertEquals(1.0D, prediction.surface().y, EPSILON);
        assertEquals(1.02D, last(prediction).yCoord, EPSILON);
        assertTrue(prediction.points().size() < 21);
    }

    @Test
    public void supportingSurfaceUsesPartialBlockHeight() {
        TestEnvironment environment = new TestEnvironment(floor(0.5D));
        MotionPathPredictor.Prediction prediction = predict(
                playerBox(0.0D, 0.5D, 0.0D), 0.0D, -0.0784D, 0.0D, true, 5, null, false, environment
        );

        assertNotNull(prediction.surface());
        assertEquals(0.5D, prediction.surface().y, EPSILON);
    }

    @Test
    public void wallCollisionClampsHorizontalMotion() {
        AxisAlignedBB floor = floor(1.0D);
        AxisAlignedBB wall = new AxisAlignedBB(1.0D, 1.0D, -2.0D, 2.0D, 4.0D, 2.0D);
        TestEnvironment environment = new TestEnvironment(floor, wall);
        MotionPathPredictor.Prediction prediction = predict(
                playerBox(0.0D, 1.0D, 0.0D), 1.0D, -0.0784D, 0.0D, true, 5, null, false, environment
        );

        assertTrue(last(prediction).xCoord <= 0.7001D);
    }

    @Test
    public void heldForwardInputContinuesGroundMovement() {
        TestEnvironment environment = new TestEnvironment(floor(1.0D));
        MotionPathPredictor.Input input = new MotionPathPredictor.Input(
                0.0F, 1.0F, 0.0F, 0.1F, 0.02F, false, false, 0.0D
        );
        MotionPathPredictor.Prediction prediction = predict(
                playerBox(0.0D, 1.0D, 0.0D), 0.0D, -0.0784D, 0.0D, true, 5, input, false, environment
        );

        assertTrue(last(prediction).zCoord > prediction.points().get(0).zCoord + 0.1D);
    }

    @Test
    public void heldJumpLaunchesFromSupport() {
        TestEnvironment environment = new TestEnvironment(floor(1.0D));
        MotionPathPredictor.Input input = new MotionPathPredictor.Input(
                0.0F, 0.0F, 0.0F, 0.1F, 0.02F, true, false, 0.0D
        );
        MotionPathPredictor.Prediction prediction = predict(
                playerBox(0.0D, 1.0D, 0.0D), 0.0D, -0.0784D, 0.0D, true, 3, input, false, environment
        );

        assertTrue(prediction.points().get(1).yCoord > prediction.points().get(0).yCoord + 0.4D);
    }

    @Test
    public void stepHeightClimbsHalfBlockObstacle() {
        AxisAlignedBB floor = floor(1.0D);
        AxisAlignedBB step = new AxisAlignedBB(0.8D, 1.0D, -1.0D, 2.0D, 1.5D, 1.0D);
        TestEnvironment environment = new TestEnvironment(floor, step);
        MotionPathPredictor.Prediction prediction = predict(
                playerBox(0.0D, 1.0D, 0.0D), 0.5D, -0.0784D, 0.0D, true, 3, null, false, environment
        );

        assertTrue(last(prediction).yCoord >= 1.519D);
        assertTrue(last(prediction).xCoord > 0.5D);
    }

    @Test
    public void waterAndWebApplyTheirOwnDrag() {
        TestEnvironment air = new TestEnvironment();
        TestEnvironment water = new TestEnvironment();
        water.water = true;
        TestEnvironment web = new TestEnvironment();
        web.web = true;

        MotionPathPredictor.Prediction airPrediction = predict(
                playerBox(0.0D, 5.0D, 0.0D), 1.0D, 0.0D, 0.0D, false, 2, null, false, air
        );
        MotionPathPredictor.Prediction waterPrediction = predict(
                playerBox(0.0D, 5.0D, 0.0D), 1.0D, 0.0D, 0.0D, false, 2, null, false, water
        );
        MotionPathPredictor.Prediction webPrediction = predict(
                playerBox(0.0D, 5.0D, 0.0D), 1.0D, 0.0D, 0.0D, false, 1, null, false, web
        );

        assertTrue(last(waterPrediction).yCoord > last(airPrediction).yCoord);
        assertEquals(0.25D, last(webPrediction).xCoord, EPSILON);
    }

    @Test
    public void configuredPredictionLimitCapsSamples() {
        MotionPathPredictor.Prediction prediction = predict(
                playerBox(0.0D, 50.0D, 0.0D), 0.2D, 0.0D, 0.0D, false, 5, null, false,
                new TestEnvironment()
        );

        assertEquals(6, prediction.points().size());
    }

    private static MotionPathPredictor.Prediction predict(
            AxisAlignedBB box,
            double motionX,
            double motionY,
            double motionZ,
            boolean supported,
            int ticks,
            MotionPathPredictor.Input input,
            boolean maintainObserved,
            TestEnvironment environment
    ) {
        return MotionPathPredictor.predict(
                new MotionPathPredictor.Request(
                        box, motionX, motionY, motionZ, supported, 0.6D, ticks, input, maintainObserved
                ),
                environment
        );
    }

    private static AxisAlignedBB playerBox(double x, double y, double z) {
        return new AxisAlignedBB(x - 0.3D, y, z - 0.3D, x + 0.3D, y + 1.8D, z + 0.3D);
    }

    private static AxisAlignedBB floor(double top) {
        return new AxisAlignedBB(-20.0D, top - 1.0D, -20.0D, 20.0D, top, 20.0D);
    }

    private static Vec3 last(MotionPathPredictor.Prediction prediction) {
        return prediction.points().get(prediction.points().size() - 1);
    }

    private static final class TestEnvironment implements MotionPathPredictor.Environment {
        private final List<AxisAlignedBB> boxes;
        boolean water;
        boolean lava;
        boolean web;
        boolean ladder;

        TestEnvironment(AxisAlignedBB... boxes) {
            this.boxes = Arrays.asList(boxes);
        }

        @Override
        public List<AxisAlignedBB> collisions(AxisAlignedBB query) {
            if (this.boxes.isEmpty()) return Collections.emptyList();
            List<AxisAlignedBB> collisions = new ArrayList<>();
            for (AxisAlignedBB box : this.boxes) {
                if (box.intersectsWith(query)) collisions.add(box);
            }
            return collisions;
        }

        @Override
        public MotionPathPredictor.Surface supportingSurface(AxisAlignedBB box) {
            AxisAlignedBB best = null;
            for (AxisAlignedBB candidate : this.boxes) {
                if (candidate.maxY > box.minY + 0.011D || candidate.maxY < box.minY - 0.081D) continue;
                double overlapX = Math.min(box.maxX, candidate.maxX) - Math.max(box.minX, candidate.minX);
                double overlapZ = Math.min(box.maxZ, candidate.maxZ) - Math.max(box.minZ, candidate.minZ);
                if (overlapX > 0.0D && overlapZ > 0.0D && (best == null || candidate.maxY > best.maxY)) {
                    best = candidate;
                }
            }
            return best == null ? null : MotionPathPredictor.Surface.fromBox(best);
        }

        @Override
        public float slipperiness(AxisAlignedBB box) {
            return 0.6F;
        }

        @Override
        public boolean isWater(AxisAlignedBB box) {
            return this.water;
        }

        @Override
        public boolean isLava(AxisAlignedBB box) {
            return this.lava;
        }

        @Override
        public boolean isWeb(AxisAlignedBB box) {
            return this.web;
        }

        @Override
        public boolean isLadder(AxisAlignedBB box) {
            return this.ladder;
        }
    }
}

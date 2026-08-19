package myau.module.modules;

import net.minecraft.util.Vec3;
import org.junit.Test;

import static org.junit.Assert.*;

public class MotionPathStateTest {
    @Test
    public void moduleDefaultsMatchTheVisualSpecification() {
        MotionPath module = new MotionPath();

        assertEquals(0, module.selfTrigger.getValue().intValue());
        assertEquals(20, module.predictionTicks.getValue().intValue());
        assertEquals(1.5F, module.lineWidth.getValue(), 0.0F);
        assertEquals(85, module.opacity.getValue().intValue());
        assertEquals(0, module.colorMode.getValue().intValue());
        assertTrue(module.landingMarker.getValue());
        assertFalse(module.throughWalls.getValue());
        assertTrue(module.firstPerson.getValue());
        assertTrue(module.thirdPerson.getValue());
        assertTrue(module.players.getValue());
        assertEquals(8.0F, module.playerRadius.getValue(), 0.0F);
    }

    @Test
    public void selfTriggerModesHaveIndependentSemantics() {
        assertFalse(MotionPath.shouldShowSelf(0, true, false, true));
        assertTrue(MotionPath.shouldShowSelf(0, true, true, false));
        assertTrue(MotionPath.shouldShowSelf(1, false, false, false));
        assertFalse(MotionPath.shouldShowSelf(1, true, false, true));
        assertTrue(MotionPath.shouldShowSelf(2, true, false, true));
    }

    @Test
    public void radiusIncludesExactBoundaryAndRejectsOutside() {
        assertTrue(MotionPath.withinRadius(64.0D, 8.0F));
        assertFalse(MotionPath.withinRadius(64.0001D, 8.0F));
    }

    @Test
    public void observedMotionAveragesThreeTicks() {
        MotionPathObservedMotion history = new MotionPathObservedMotion(0.0D, 0.0D, 0.0D);
        history.observe(1.0D, 0.0D, 0.0D);
        history.observe(3.0D, 0.0D, 0.0D);
        Vec3 average = history.observe(6.0D, 0.0D, 0.0D);

        assertEquals(2.0D, average.xCoord, 1.0E-8D);
        assertEquals(0.0D, average.yCoord, 1.0E-8D);
        assertEquals(0.0D, average.zCoord, 1.0E-8D);
    }

    @Test
    public void teleportClearsObservedMotionHistory() {
        MotionPathObservedMotion history = new MotionPathObservedMotion(0.0D, 0.0D, 0.0D);
        history.observe(1.0D, 0.0D, 0.0D);
        Vec3 teleport = history.observe(10.0D, 0.0D, 0.0D);
        Vec3 resumed = history.observe(11.0D, 0.0D, 0.0D);

        assertEquals(0.0D, teleport.xCoord, 1.0E-8D);
        assertEquals(1.0D, resumed.xCoord, 1.0E-8D);
    }

    @Test
    public void fadeIsSmoothForTwoHundredFiftyMilliseconds() {
        assertEquals(1.0F, MotionPath.fade(1_000L, 0L, true), 0.0F);
        assertEquals(0.5F, MotionPath.fade(125_000_000L, 0L, false), 1.0E-6F);
        assertEquals(0.0F, MotionPath.fade(250_000_000L, 0L, false), 0.0F);
    }
}

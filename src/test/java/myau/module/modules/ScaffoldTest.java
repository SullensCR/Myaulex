package myau.module.modules;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScaffoldTest {
    @Test
    public void switchPropertyHasRequestedDefaultAndSliderRange() {
        Scaffold scaffold = new Scaffold();

        assertEquals(Integer.valueOf(3), scaffold.switchAmount.getValue());
        assertEquals(Integer.valueOf(0), scaffold.switchAmount.getMinimum());
        assertEquals(Integer.valueOf(64), scaffold.switchAmount.getMaximum());
        assertFalse(scaffold.avoidIceOnTower.getValue());
    }

    @Test
    public void switchThresholdIsInclusiveAndZeroKeepsEmptyOnlyBehavior() {
        assertTrue(Scaffold.shouldSwitch(3, 3));
        assertTrue(Scaffold.shouldSwitch(0, 3));
        assertFalse(Scaffold.shouldSwitch(4, 3));
        assertTrue(Scaffold.shouldSwitch(0, 0));
        assertFalse(Scaffold.shouldSwitch(1, 0));
    }
}

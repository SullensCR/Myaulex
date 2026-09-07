package myau.management;

import myau.enums.BlinkModules;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlinkManagerOwnershipTest {
    @Test
    public void activeOwnerCannotBeReplaced() {
        BlinkManager manager = new BlinkManager();
        manager.blinking = true;
        manager.blinkModule = BlinkModules.BLINK;

        assertFalse(manager.setBlinkState(true, BlinkModules.AUTO_BLOCK));
        assertTrue(manager.isBlinking());
        assertEquals(BlinkModules.BLINK, manager.getBlinkingModule());
    }

    @Test
    public void teardownCanDiscardOnlyTheMatchingOwner() {
        BlinkManager manager = new BlinkManager();
        manager.blinking = true;
        manager.blinkModule = BlinkModules.AUTO_BLOCK;

        assertFalse(manager.discardBlinkState(BlinkModules.BLINK));
        assertTrue(manager.discardBlinkState(BlinkModules.AUTO_BLOCK));
        assertFalse(manager.isBlinking());
        assertEquals(BlinkModules.NONE, manager.getBlinkingModule());
    }
}

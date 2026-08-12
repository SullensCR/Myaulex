package myau.module.modules;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoRejoinTest {
    @Test
    public void acceptsColorizedMixedCaseUnbanNameTagInSlotFive() {
        assertTrue(AutoRejoin.matchesTrigger(5, true, "§cClick to UnBaN now"));
    }

    @Test
    public void rejectsMatchingNameTagInEveryOtherHotbarSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (slot != AutoRejoin.TRIGGER_HOTBAR_SLOT) {
                assertFalse(AutoRejoin.matchesTrigger(slot, true, "unban"));
            }
        }
    }

    @Test
    public void rejectsMissingWrongAndUnrelatedItems() {
        assertFalse(AutoRejoin.matchesTrigger(5, false, null));
        assertFalse(AutoRejoin.matchesTrigger(5, false, "unban"));
        assertFalse(AutoRejoin.matchesTrigger(5, true, "Account status"));
    }

    @Test
    public void ignoresUnbanTextFoundOnlyInLore() {
        assertFalse(AutoRejoin.matchesTrigger(5, true, "Name Tag"));
    }
}

package myau.module.modules;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DisablerTest {
    @Test
    public void acceptsAnyHotbarSlotForAFormattingStrippedAsaDeltaCarpet() {
        assertTrue(Disabler.matchesTrigger(0, true, "§bAsa §lDelta"));
        assertTrue(Disabler.matchesTrigger(8, true, "Asa Delta"));
    }

    @Test
    public void rejectsNonCarpetWrongNamesAndNonHotbarSlots() {
        assertFalse(Disabler.matchesTrigger(2, false, "Asa Delta"));
        assertFalse(Disabler.matchesTrigger(2, true, "Asa Delta!"));
        assertFalse(Disabler.matchesTrigger(9, true, "Asa Delta"));
    }

    @Test
    public void extractsOnlyTheInstanceAfterTheDateDelimiter() {
        assertEquals("06B19", Disabler.extractInstance("§722/08/26 §8| §f06B19"));
        assertEquals("Lobby-A", Disabler.extractInstance("22/08/2026 | Lobby-A"));
        assertNull(Disabler.extractInstance("Mapa: Downtown"));
    }
}

package myau.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BedplateStateTest {
    @Test
    public void usesTheExactJsonEndpointsAndLimitsLayers() {
        assertEquals(102.0F, BedplateState.expandedWidth(0), 0.001F);
        assertEquals(352.0F, BedplateState.expandedWidth(3), 0.001F);
        assertEquals(352.0F, BedplateState.expandedWidth(99), 0.001F);
        assertEquals(102.0F, BedplateState.width(3, 0.0F), 0.001F);
        assertEquals(352.0F, BedplateState.width(3, 1.0F), 0.001F);
        assertEquals(102.0F, BedplateState.height(0.0F), 0.001F);
        assertEquals(106.0F, BedplateState.height(1.0F), 0.001F);
    }

    @Test
    public void rowsKeepTheExportedPaddingAndGap() {
        assertEquals(15.0F, BedplateState.itemX(0), 0.001F);
        assertEquals(97.0F, BedplateState.itemX(1), 0.001F);
        assertEquals(179.0F, BedplateState.itemX(2), 0.001F);
        assertEquals(261.0F, BedplateState.itemX(3), 0.001F);
    }

    @Test
    public void expansionIsSmoothAndBounded() {
        float expansion = 0.0F;
        for (int i = 0; i < 90; i++) {
            expansion = BedplateState.animateExpansion(1.0F, expansion, 1.0F / 60.0F);
        }
        assertTrue(expansion > 0.99F);
        assertTrue(expansion <= 1.0F);
        assertTrue(BedplateState.animateExpansion(0.0F, expansion, 1.0F / 60.0F) < expansion);
    }

    @Test
    public void destroyedBedFadesForExactlyTheConfiguredWindow() {
        long destroyed = 1_000L;
        assertEquals(1.0F, BedplateState.fadeAlpha(destroyed, destroyed), 0.001F);
        assertEquals(0.5F, BedplateState.fadeAlpha(destroyed, destroyed + 100L), 0.001F);
        assertEquals(0.0F, BedplateState.fadeAlpha(destroyed, destroyed + 200L), 0.001F);
        assertFalse(BedplateState.fadeComplete(destroyed, destroyed + 199L));
        assertTrue(BedplateState.fadeComplete(destroyed, destroyed + 200L));
    }
}

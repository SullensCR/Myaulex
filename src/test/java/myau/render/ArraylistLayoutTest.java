package myau.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArraylistLayoutTest {
    @Test
    public void matchesTheJsonAutoLayoutWidths() {
        assertEquals(292.0F, ArraylistLayout.cardWidth(117.0F, 148.0F), 0.001F);
        assertEquals(194.0F, ArraylistLayout.cardWidth(69.0F, 98.0F), 0.001F);
        assertEquals(213.0F, ArraylistLayout.cardWidth(115.0F, 71.0F), 0.001F);
        assertEquals(90.0F, ArraylistLayout.cardWidth(69.0F, 0.0F), 0.001F);
    }

    @Test
    public void positionsAccentAndTextFromTheExportedPadding() {
        assertEquals(16.0F, ArraylistLayout.textX(0.0F, 1.0F), 0.001F);
        assertEquals(9.0F, ArraylistLayout.accentY(0.0F, 1.0F), 0.001F);
        assertEquals(32.0F, ArraylistLayout.textX(0.0F, 2.0F), 0.001F);
    }

    @Test
    public void stackingUsesTheJsonGapAndVisibility() {
        assertEquals(49.0F, ArraylistLayout.nextTopCursor(0.0F, 1.0F, 1.0F), 0.001F);
        assertEquals(24.5F, ArraylistLayout.nextTopCursor(0.0F, 1.0F, 0.5F), 0.001F);
        assertEquals(151.0F, ArraylistLayout.nextBottomCursor(200.0F, 1.0F, 1.0F), 0.001F);
    }

    @Test
    public void customRowGapControlsTopAndBottomSpacing() {
        assertEquals(52.0F, ArraylistLayout.nextTopCursor(0.0F, 1.0F, 1.0F, 10.0F), 0.001F);
        assertEquals(148.0F, ArraylistLayout.nextBottomCursor(200.0F, 1.0F, 1.0F, 10.0F), 0.001F);
        assertEquals(42.0F, ArraylistLayout.nextTopCursor(0.0F, 1.0F, 1.0F, -5.0F), 0.001F);
    }
}

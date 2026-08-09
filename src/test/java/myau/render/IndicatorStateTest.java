package myau.render;

import org.junit.Test;

import java.awt.Color;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IndicatorStateTest {
    @Test
    public void indicatorIsAuraGated() {
        IndicatorState.Frame frame = IndicatorState.resolve(true, false,
                true, true, 1_000L,
                true, 300, 1_000L, 1_100L);

        assertEquals(IndicatorState.Source.HIDDEN, frame.getSource());
        assertEquals(0.0F, frame.getSweep(), 0.001F);
    }

    @Test
    public void blinkTakesPrecedenceOverLagRange() {
        IndicatorState.Frame frame = IndicatorState.resolve(true, true,
                true, false, 1_000L,
                true, 300, 1_000L, 1_250L);

        assertEquals(IndicatorState.Source.BLINK, frame.getSource());
        assertEquals(0.5F, frame.getSweep(), 0.001F);
    }

    @Test
    public void defaultBlinkExpiresButPulseRemainsVisible() {
        IndicatorState.Frame expired = IndicatorState.resolve(true, true,
                true, false, 1_000L,
                false, 0, 0L, 1_501L);
        IndicatorState.Frame pulse = IndicatorState.resolve(true, true,
                true, true, 1_000L,
                false, 0, 0L, 1_501L);

        assertEquals(IndicatorState.Source.AURA, expired.getSource());
        assertEquals(IndicatorState.Source.BLINK, pulse.getSource());
        assertEquals(1.0F, pulse.getSweep(), 0.001F);
    }

    @Test
    public void lagRangeSweepUsesItsConfiguredDelayAndThenHoldsFull() {
        IndicatorState.Frame half = IndicatorState.resolve(true, true,
                false, false, 0L,
                true, 300, 1_000L, 1_150L);
        IndicatorState.Frame complete = IndicatorState.resolve(true, true,
                false, false, 0L,
                true, 300, 1_000L, 1_450L);

        assertEquals(IndicatorState.Source.LAG_RANGE, half.getSource());
        assertEquals(0.5F, half.getSweep(), 0.001F);
        assertEquals(1.0F, complete.getSweep(), 0.001F);
    }

    @Test
    public void vividColorsNeverBecomeGrayBlackOrWhite() {
        Random random = new Random(9L);
        for (int i = 0; i < 100; i++) {
            Color color = IndicatorState.randomVividColor(random);
            float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
            assertTrue(hsb[1] >= 0.5F);
            assertTrue(hsb[2] >= 0.5F);
            assertFalse(color.getRed() == color.getGreen() && color.getGreen() == color.getBlue());
        }
    }
}

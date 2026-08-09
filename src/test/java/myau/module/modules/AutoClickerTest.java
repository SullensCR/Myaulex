package myau.module.modules;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AutoClickerTest {
    @Test
    public void zeroRandomizationUsesTheTargetCpsInterval() {
        assertEquals(100L, AutoClicker.nextClickDelay(10.0F, 0));
        assertEquals(50L, AutoClicker.nextClickDelay(20.0F, 0));
    }

    @Test
    public void randomizationStaysWithinTheSymmetricDelayWindow() {
        for (int i = 0; i < 500; i++) {
            long delay = AutoClicker.nextClickDelay(10.0F, 100);
            assertTrue(delay >= 1L);
            assertTrue(delay <= 200L);
        }
    }

    @Test
    public void largeNegativeJitterCannotCreateANonPositiveDelay() {
        for (int i = 0; i < 500; i++) {
            assertTrue(AutoClicker.nextClickDelay(20.0F, 150) >= 1L);
        }
    }
}

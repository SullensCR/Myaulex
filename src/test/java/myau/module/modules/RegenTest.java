package myau.module.modules;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegenTest {
    @Test
    public void regeneratesOnlyBelowTheConfiguredHealth() {
        assertTrue(Regen.shouldRegenerate(14.5F, 15));
        assertFalse(Regen.shouldRegenerate(15.0F, 15));
        assertFalse(Regen.shouldRegenerate(20.0F, 15));
    }
}

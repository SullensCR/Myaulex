package myau.ui.modern;

import org.junit.Test;

import static org.junit.Assert.*;

public class ModuleCatalogTest {
    @Test
    public void authoritativeCatalogContainsExactlyFortyThreeModules() {
        assertEquals(43, ModuleCatalog.ordinaryNames().size());
        assertTrue(ModuleCatalog.ordinaryNames().contains("Aura"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Hit Select"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Keep Sprint"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("MotionPath"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("PacketDelay"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("FastQueue"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("FlagDetector"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("AutoRejoin"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("Client"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("KillAura"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("FakeLag"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("BlockESP"));
    }
}

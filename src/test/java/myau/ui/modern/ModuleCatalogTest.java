package myau.ui.modern;

import org.junit.Test;

import static org.junit.Assert.*;

public class ModuleCatalogTest {
    @Test
    public void authoritativeCatalogContainsExactlyFiftyModules() {
        assertEquals(50, ModuleCatalog.ordinaryNames().size());
        assertTrue(ModuleCatalog.ordinaryNames().contains("Aura"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Hit Select"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Keep Sprint"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("MotionPath"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("PacketDelay"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("FastQueue"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("FlagDetector"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("AutoRejoin"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Disabler"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("NoFall"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Regen"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Fly"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("LongJump"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Speed"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Criticals"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Legit Scaffold"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("BridgeAssist"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("Client"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("KillAura"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("FakeLag"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("BlockESP"));
    }
}

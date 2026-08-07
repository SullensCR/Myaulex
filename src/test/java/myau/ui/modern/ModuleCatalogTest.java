package myau.ui.modern;

import org.junit.Test;

import static org.junit.Assert.*;

public class ModuleCatalogTest {
    @Test
    public void authoritativeCatalogContainsExactlyThirtyNineModules() {
        assertEquals(39, ModuleCatalog.ordinaryNames().size());
        assertTrue(ModuleCatalog.ordinaryNames().contains("Aura"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("Keep Sprint"));
        assertTrue(ModuleCatalog.ordinaryNames().contains("PacketDelay"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("Client"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("KillAura"));
        assertFalse(ModuleCatalog.ordinaryNames().contains("FakeLag"));
    }
}

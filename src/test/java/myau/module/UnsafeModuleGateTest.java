package myau.module;

import myau.Myau;
import myau.config.ClientSettings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnsafeModuleGateTest {
    @Test
    public void requestedUnsafeModuleWaitsUntilTheClientOverrideAllowsIt() {
        ClientSettings previous = Myau.clientSettings;
        try {
            Myau.clientSettings = new ClientSettings();
            ProbeModule module = new ProbeModule();
            module.setEnabled(true);

            assertTrue(module.isEnabled());
            assertFalse(module.isOperational());
            assertEquals(0, module.enabledCalls);

            Myau.clientSettings.setUnsafeModuleOverride(true);
            module.refreshUnsafeOperationalState();
            assertTrue(module.isOperational());
            assertEquals(1, module.enabledCalls);

            Myau.clientSettings.setUnsafeModuleOverride(false);
            module.refreshUnsafeOperationalState();
            assertFalse(module.isOperational());
            assertEquals(1, module.disabledCalls);
        } finally {
            Myau.clientSettings = previous;
        }
    }

    private static final class ProbeModule extends Module {
        private int enabledCalls;
        private int disabledCalls;

        private ProbeModule() {
            super("Probe", false);
            this.markUnsafe();
        }

        @Override
        public void onEnabled() {
            enabledCalls++;
        }

        @Override
        public void onDisabled() {
            disabledCalls++;
        }
    }
}

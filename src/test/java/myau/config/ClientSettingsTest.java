package myau.config;

import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ClientSettingsTest {
    @Test
    public void crashGuardIsPersistedAsAnInternalClientFeature() {
        assertTrue(ClientSettings.isIntegratedModuleName("CrashGuard"));
        assertFalse(ClientSettings.isIntegratedModuleName("PacketDelay"));
    }

    @Test
    public void targetingAndNetworkControlsRoundTripThroughClientStore() {
        ClientSettings source = new ClientSettings();
        source.cycleMoveFixMode();
        source.cycleBotFilterMode();
        source.cycleTeamsMode();
        source.setTargetPlayers(false);
        source.setTargetMobs(true);
        source.setTargetAnimals(true);
        source.setVerifyTcpNoDelay(false);
        source.setIndicatorEnabled(false);

        JsonObject root = new JsonObject();
        source.write(root);
        ClientSettings restored = new ClientSettings();
        restored.read(root);

        assertEquals(source.getMoveFixMode(), restored.getMoveFixMode());
        assertEquals(source.getBotFilterMode(), restored.getBotFilterMode());
        assertEquals(source.getTeamsMode(), restored.getTeamsMode());
        assertFalse(restored.isTargetPlayers());
        assertTrue(restored.isTargetMobs());
        assertTrue(restored.isTargetAnimals());
        assertFalse(restored.isVerifyTcpNoDelay());
        assertFalse(restored.isIndicatorEnabled());
    }
}

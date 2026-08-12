package myau.module.modules;

import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlagDetectorTest {
    @Test
    public void onlyServerPositionCorrectionsAreFlagTeleportPackets() {
        assertTrue(FlagDetector.isPlayerTeleportPacket(new S08PacketPlayerPosLook()));
        assertFalse(FlagDetector.isPlayerTeleportPacket(new S01PacketJoinGame()));
        assertFalse(FlagDetector.isPlayerTeleportPacket(null));
    }
}

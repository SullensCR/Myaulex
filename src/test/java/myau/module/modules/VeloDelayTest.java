package myau.module.modules;

import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VeloDelayTest {
    @Test
    public void worldTransitionAndWorldStatePacketsAreNeverDelayedInbound() {
        assertFalse(VeloDelay.shouldDelayInbound(new S01PacketJoinGame()));
        assertFalse(VeloDelay.shouldDelayInbound(new S07PacketRespawn()));
        assertFalse(VeloDelay.shouldDelayInbound(new S08PacketPlayerPosLook()));
        assertFalse(VeloDelay.shouldDelayInbound(new S38PacketPlayerListItem()));
        assertFalse(VeloDelay.shouldDelayInbound(new S21PacketChunkData()));
        assertFalse(VeloDelay.shouldDelayInbound(new S22PacketMultiBlockChange()));
        assertFalse(VeloDelay.shouldDelayInbound(new S23PacketBlockChange()));
        assertFalse(VeloDelay.shouldDelayInbound(new S26PacketMapChunkBulk()));
        assertTrue(VeloDelay.shouldDelayInbound(null));
    }
}

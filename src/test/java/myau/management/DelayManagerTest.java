package myau.management;

import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DelayManagerTest {
    @Test
    public void tablistUpdatesBypassGlobalPacketDelay() {
        assertTrue(DelayManager.isImmediatePacket(new S38PacketPlayerListItem()));
        assertTrue(DelayManager.isImmediatePacket(new S21PacketChunkData()));
        assertTrue(DelayManager.isImmediatePacket(new S22PacketMultiBlockChange()));
        assertTrue(DelayManager.isImmediatePacket(new S23PacketBlockChange()));
        assertTrue(DelayManager.isImmediatePacket(new S26PacketMapChunkBulk()));
        assertFalse(DelayManager.isImmediatePacket(null));
    }
}

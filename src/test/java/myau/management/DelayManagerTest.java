package myau.management;

import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S47PacketPlayerListHeaderFooter;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3DPacketDisplayScoreboard;
import net.minecraft.network.play.server.S3EPacketTeams;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DelayManagerTest {
    @Test
    public void tablistUpdatesBypassGlobalPacketDelay() {
        assertTrue(DelayManager.isImmediatePacket(new S38PacketPlayerListItem()));
        assertTrue(DelayManager.isImmediatePacket(new S47PacketPlayerListHeaderFooter()));
        assertTrue(DelayManager.isImmediatePacket(new S21PacketChunkData()));
        assertTrue(DelayManager.isImmediatePacket(new S22PacketMultiBlockChange()));
        assertTrue(DelayManager.isImmediatePacket(new S23PacketBlockChange()));
        assertTrue(DelayManager.isImmediatePacket(new S26PacketMapChunkBulk()));
        assertTrue(DelayManager.isImmediatePacket(new S3BPacketScoreboardObjective()));
        assertTrue(DelayManager.isImmediatePacket(new S3CPacketUpdateScore()));
        assertTrue(DelayManager.isImmediatePacket(new S3DPacketDisplayScoreboard()));
        assertTrue(DelayManager.isImmediatePacket(new S3EPacketTeams()));
        assertFalse(DelayManager.isImmediatePacket(null));
    }
}

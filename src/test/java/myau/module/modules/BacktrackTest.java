package myau.module.modules;

import net.minecraft.util.Vec3;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.status.server.S01PacketPong;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class BacktrackTest {
    @Test
    public void randomDelayAlwaysUsesInclusiveNormalizedBounds() {
        for (int i = 0; i < 500; i++) {
            int delay = Backtrack.chooseDelay(120, 80);
            assertTrue(delay >= 80);
            assertTrue(delay <= 120);
        }
        assertEquals(80, Backtrack.chooseDelay(80, 80));
    }

    @Test
    public void distanceWindowNormalizesBoundsAndIncludesEdges() {
        assertTrue(Backtrack.inDistanceWindow(2.0, 3.0, 2.0));
        assertTrue(Backtrack.inDistanceWindow(2.5, 2.0, 3.0));
        assertTrue(Backtrack.inDistanceWindow(3.0, 2.0, 3.0));
        assertFalse(Backtrack.inDistanceWindow(1.99, 2.0, 3.0));
        assertFalse(Backtrack.inDistanceWindow(3.01, 2.0, 3.0));
    }

    @Test
    public void serverPositionTrackerAppliesRelativeAndTeleportCoordinates() {
        Backtrack.ServerPositionTracker tracker = new Backtrack.ServerPositionTracker();
        assertNull(tracker.current());

        Vec3 initial = tracker.setEncoded(320, 2048, -160);
        assertEquals(10.0, initial.xCoord, 0.0);
        assertEquals(64.0, initial.yCoord, 0.0);
        assertEquals(-5.0, initial.zCoord, 0.0);

        Vec3 moved = tracker.add(16, -8, 32);
        assertEquals(10.5, moved.xCoord, 0.0);
        assertEquals(63.75, moved.yCoord, 0.0);
        assertEquals(-4.0, moved.zCoord, 0.0);

        Vec3 teleported = tracker.setEncoded(-64, 2560, 96);
        assertEquals(-2.0, teleported.xCoord, 0.0);
        assertEquals(80.0, teleported.yCoord, 0.0);
        assertEquals(3.0, teleported.zCoord, 0.0);
    }

    @Test
    public void renderInterpolationMovesTowardTruePosition() {
        Vec3 result = Backtrack.interpolate(new Vec3(0, 10, 20), new Vec3(10, 20, 40), 0.25);
        assertEquals(2.5, result.xCoord, 0.0);
        assertEquals(12.5, result.yCoord, 0.0);
        assertEquals(25.0, result.zCoord, 0.0);
    }

    @Test
    public void pulseAndSmoothTimingUseTheSelectedDelay() {
        assertFalse(Backtrack.pulseExpired(true, 1_000, 1_079, 80));
        assertTrue(Backtrack.pulseExpired(true, 1_000, 1_080, 80));
        assertFalse(Backtrack.pulseExpired(false, 1_000, 2_000, 80));
        assertEquals(920, Backtrack.smoothCutoff(1_000, 80));
    }

    @Test
    public void rangeHistoryReturnsTheOldestMatchingCutoff() {
        long cutoff = Backtrack.findFirstTimestampInWindow(
                Arrays.asList(
                        new Backtrack.PositionSnapshot(new Vec3(1, 0, 0), 10),
                        new Backtrack.PositionSnapshot(new Vec3(2, 0, 0), 20),
                        new Backtrack.PositionSnapshot(new Vec3(3, 0, 0), 30)),
                position -> position.xCoord,
                2.0,
                3.0);
        assertEquals(20, cutoff);
    }

    @Test
    public void safetyPacketsBypassOrFlushTheQueue() {
        assertTrue(Backtrack.isImmediatePacket(new S01PacketPong(1L), null));
        assertTrue(Backtrack.isImmediatePacket(new S01PacketJoinGame(), null));
        assertTrue(Backtrack.isImmediatePacket(new S07PacketRespawn(), null));
        assertTrue(Backtrack.isImmediatePacket(new S08PacketPlayerPosLook(), null));
        assertTrue(Backtrack.isImmediatePacket(new S38PacketPlayerListItem(), null));
        assertTrue(Backtrack.isImmediatePacket(new S21PacketChunkData(), null));
        assertTrue(Backtrack.isImmediatePacket(new S22PacketMultiBlockChange(), null));
        assertTrue(Backtrack.isImmediatePacket(new S23PacketBlockChange(), null));
        assertTrue(Backtrack.isImmediatePacket(new S26PacketMapChunkBulk(), null));
        assertTrue(Backtrack.packetRequiresFlush(new S01PacketJoinGame(), null));
        assertTrue(Backtrack.packetRequiresFlush(new S07PacketRespawn(), null));
        assertTrue(Backtrack.packetRequiresFlush(new S08PacketPlayerPosLook(), null));
        assertTrue(Backtrack.packetRequiresFlush(
                new S06PacketUpdateHealth(0.0F, 20, 5.0F), null));
        assertFalse(Backtrack.packetRequiresFlush(
                new S06PacketUpdateHealth(20.0F, 20, 5.0F), null));
    }
}

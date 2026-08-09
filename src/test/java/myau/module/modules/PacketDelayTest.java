package myau.module.modules;

import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PacketDelayTest {
    @Test
    public void tablistUpdatesAreNeverDelayedInbound() {
        assertFalse(PacketDelay.shouldDelayInbound(new S38PacketPlayerListItem()));
        assertFalse(PacketDelay.shouldDelayInbound(new S21PacketChunkData()));
        assertFalse(PacketDelay.shouldDelayInbound(new S22PacketMultiBlockChange()));
        assertFalse(PacketDelay.shouldDelayInbound(new S23PacketBlockChange()));
        assertFalse(PacketDelay.shouldDelayInbound(new S26PacketMapChunkBulk()));
        assertTrue(PacketDelay.shouldDelayInbound(null));
    }
}

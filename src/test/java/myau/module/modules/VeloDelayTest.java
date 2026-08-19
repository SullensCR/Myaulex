package myau.module.modules;

import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S47PacketPlayerListHeaderFooter;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VeloDelayTest {
    @Test
    public void worldTransitionAndWorldStatePacketsAreNeverDelayedInbound() {
        assertFalse(VeloDelay.shouldDelayInbound(new S01PacketJoinGame()));
        assertFalse(VeloDelay.shouldDelayInbound(new S07PacketRespawn()));
        assertFalse(VeloDelay.shouldDelayInbound(new S08PacketPlayerPosLook()));
        assertFalse(VeloDelay.shouldDelayInbound(new S38PacketPlayerListItem()));
        assertFalse(VeloDelay.shouldDelayInbound(new S47PacketPlayerListHeaderFooter()));
        assertFalse(VeloDelay.shouldDelayInbound(new S21PacketChunkData()));
        assertFalse(VeloDelay.shouldDelayInbound(new S22PacketMultiBlockChange()));
        assertFalse(VeloDelay.shouldDelayInbound(new S23PacketBlockChange()));
        assertFalse(VeloDelay.shouldDelayInbound(new S26PacketMapChunkBulk()));
        assertTrue(VeloDelay.shouldDelayInbound(null));
    }

    @Test
    public void defaultsMatchGnuKnockbackDelay() {
        VeloDelay module = new VeloDelay();

        assertEquals(Integer.valueOf(200), module.delay.getValue());
        assertEquals(Float.valueOf(6.0F), module.distance.getValue());
        assertEquals(Integer.valueOf(100), module.chance.getValue());
        assertTrue(module.inAir.getValue());
        assertFalse(module.lookingAtPlayer.getValue());
        assertFalse(module.requireLeftMouse.getValue());
        assertEquals(Integer.valueOf(50), module.delay.getMinimum());
        assertEquals(Integer.valueOf(1000), module.delay.getMaximum());
        assertEquals("200ms", module.getSuffix()[0]);
    }

    @Test
    public void selfVelocityDetectionUsesThePacketEntityId() {
        S12PacketEntityVelocity packet = new S12PacketEntityVelocity();

        assertTrue(VeloDelay.isSelfVelocity(packet, 0));
        assertFalse(VeloDelay.isSelfVelocity(packet, 1));
        assertTrue(VeloDelay.shouldDelayInbound(packet));
    }
}

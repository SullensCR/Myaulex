package myau.module.modules;

import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.Vec3;
import org.junit.Test;

import java.util.Collections;
import java.util.EnumSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CrashGuardTest {
    @Test
    public void acceptsOrdinaryPositionExplosionAndParticlePackets() {
        assertFalse(CrashGuard.isUnsafePosition(new S08PacketPlayerPosLook(
                100.0D, 64.0D, -100.0D, 90.0F, 25.0F, EnumSet.noneOf(S08PacketPlayerPosLook.EnumFlags.class))));
        assertFalse(CrashGuard.isUnsafeExplosion(new S27PacketExplosion(
                0.0D, 64.0D, 0.0D, 4.0F, Collections.emptyList(), new Vec3(0.5D, 0.25D, 0.5D))));
        assertFalse(CrashGuard.isUnsafeParticles(new S2APacketParticles(
                EnumParticleTypes.FLAME, false, 0.0F, 64.0F, 0.0F,
                0.1F, 0.1F, 0.1F, 0.01F, 8)));
    }

    @Test
    public void rejectsNonFiniteAndOutOfRangeCrashFields() {
        assertTrue(CrashGuard.isUnsafePosition(new S08PacketPlayerPosLook(
                Double.NaN, 64.0D, 0.0D, 0.0F, 0.0F, EnumSet.noneOf(S08PacketPlayerPosLook.EnumFlags.class))));
        assertTrue(CrashGuard.isUnsafePosition(new S08PacketPlayerPosLook(
                0.0D, 64.0D, 0.0D, Float.POSITIVE_INFINITY, 0.0F,
                EnumSet.noneOf(S08PacketPlayerPosLook.EnumFlags.class))));
        assertTrue(CrashGuard.isUnsafeExplosion(new S27PacketExplosion(
                0.0D, 64.0D, 0.0D, Float.POSITIVE_INFINITY, Collections.emptyList(), new Vec3(0.0D, 0.0D, 0.0D))));
        assertTrue(CrashGuard.isUnsafeParticles(new S2APacketParticles(
                EnumParticleTypes.FLAME, false, Float.NaN, 64.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 1)));
        assertTrue(CrashGuard.isUnsafeParticles(new S2APacketParticles(
                EnumParticleTypes.FLAME, false, 0.0F, 64.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, CrashGuard.MAX_PARTICLE_COUNT + 1)));
    }
}

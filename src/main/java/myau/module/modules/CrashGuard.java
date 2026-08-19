package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S2APacketParticles;

/** Drops malformed server packets before vanilla can process unsafe numeric values. */
public final class CrashGuard extends Module {
    static final double MAX_WORLD_COORDINATE = 30_000_000.0D;
    static final float MAX_ROTATION_MAGNITUDE = 1_000_000.0F;
    static final float MAX_EXPLOSION_STRENGTH = 128.0F;
    static final float MAX_EXPLOSION_MOTION = 1_000_000.0F;
    static final float MAX_PARTICLE_VALUE = 1_000_000.0F;
    static final int MAX_PARTICLE_COUNT = 16_384;

    public final BooleanProperty position = new BooleanProperty("position", true);
    public final BooleanProperty explosion = new BooleanProperty("explosion", true);
    public final BooleanProperty particle = new BooleanProperty("particle", true);

    public CrashGuard() {
        super("CrashGuard", true, true,
                "Drops malformed position, explosion, and particle packets before they crash the client.");
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && this.blocks(event.getPacket())) {
            event.setCancelled(true);
        }
    }

    /** Used by the NetworkManager hook before packet-delay systems can queue a malformed packet. */
    public static boolean shouldBlockIncomingPacket(Packet<?> packet) {
        if (Myau.moduleManager == null) return false;

        Module module = Myau.moduleManager.getModule(CrashGuard.class);
        return module instanceof CrashGuard
                && module.isEnabled()
                && ((CrashGuard) module).blocks(packet);
    }

    boolean blocks(Packet<?> packet) {
        if (packet instanceof S08PacketPlayerPosLook) {
            return this.position.getValue() && isUnsafePosition((S08PacketPlayerPosLook) packet);
        }
        if (packet instanceof S27PacketExplosion) {
            return this.explosion.getValue() && isUnsafeExplosion((S27PacketExplosion) packet);
        }
        if (packet instanceof S2APacketParticles) {
            return this.particle.getValue() && isUnsafeParticles((S2APacketParticles) packet);
        }
        return false;
    }

    static boolean isUnsafePosition(S08PacketPlayerPosLook packet) {
        return isUnsafeCoordinate(packet.getX())
                || isUnsafeCoordinate(packet.getY())
                || isUnsafeCoordinate(packet.getZ())
                || isUnsafeRotation(packet.getYaw())
                || isUnsafeRotation(packet.getPitch());
    }

    static boolean isUnsafeExplosion(S27PacketExplosion packet) {
        return isUnsafeCoordinate(packet.getX())
                || isUnsafeCoordinate(packet.getY())
                || isUnsafeCoordinate(packet.getZ())
                || isUnsafeMagnitude(packet.getStrength(), MAX_EXPLOSION_STRENGTH)
                || isUnsafeMagnitude(packet.func_149149_c(), MAX_EXPLOSION_MOTION)
                || isUnsafeMagnitude(packet.func_149144_d(), MAX_EXPLOSION_MOTION)
                || isUnsafeMagnitude(packet.func_149147_e(), MAX_EXPLOSION_MOTION);
    }

    static boolean isUnsafeParticles(S2APacketParticles packet) {
        return isUnsafeCoordinate(packet.getXCoordinate())
                || isUnsafeCoordinate(packet.getYCoordinate())
                || isUnsafeCoordinate(packet.getZCoordinate())
                || isUnsafeMagnitude(packet.getXOffset(), MAX_PARTICLE_VALUE)
                || isUnsafeMagnitude(packet.getYOffset(), MAX_PARTICLE_VALUE)
                || isUnsafeMagnitude(packet.getZOffset(), MAX_PARTICLE_VALUE)
                || isUnsafeMagnitude(packet.getParticleSpeed(), MAX_PARTICLE_VALUE)
                || packet.getParticleCount() < 0
                || packet.getParticleCount() > MAX_PARTICLE_COUNT;
    }

    private static boolean isUnsafeCoordinate(double value) {
        return !Double.isFinite(value) || Math.abs(value) > MAX_WORLD_COORDINATE;
    }

    private static boolean isUnsafeRotation(float value) {
        return isUnsafeMagnitude(value, MAX_ROTATION_MAGNITUDE);
    }

    private static boolean isUnsafeMagnitude(float value, float maximum) {
        return !Float.isFinite(value) || Math.abs(value) > maximum;
    }
}

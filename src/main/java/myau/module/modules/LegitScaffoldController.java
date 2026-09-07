package myau.module.modules;

/** Tick-based sequencing only. No inventory, packet, camera or world mutation. */
final class LegitScaffoldController {
    enum Phase { IDLE, TURNING, APPROACH, SNEAK, WAIT_SUPPORT, PAUSED }
    enum Side { LEFT, RIGHT }

    static final float PITCH = 78.0F;
    private static final float DIRECTION_HYSTERESIS = 5.0F;
    private Phase phase = Phase.IDLE;
    private Side side;
    private int direction = -1;
    private float yaw;
    private float pitch;
    private boolean startup = true;
    private int sneakTick;
    private int placedTick;

    static float wrap(float angle) {
        angle %= 360.0F;
        return angle >= 180.0F ? angle - 360.0F : angle < -180.0F ? angle + 360.0F : angle;
    }

    /** Any finite camera heading maps to a row: 0 south, 1 west, 2 north, 3 east. */
    static int facingDirection(float cameraYaw) {
        return Float.isFinite(cameraYaw) ? Math.floorMod(Math.round(wrap(cameraYaw) / 90.0F), 4) : -1;
    }

    int resolveDirection(float cameraYaw) {
        if (!Float.isFinite(cameraYaw)) return -1;
        // Keep an established row through tiny movements around its 45-degree boundary.
        if (direction >= 0 && phase != Phase.IDLE
                && Math.abs(wrap(cameraYaw - direction * 90.0F)) <= 45.0F + DIRECTION_HYSTERESIS) return direction;
        return facingDirection(cameraYaw);
    }

    /** Called every PRE tick while movement is held, including immediately after enabling. */
    boolean prepare(int requestedDirection, float cameraYaw, float previousYaw, float previousPitch) {
        if (phase == Phase.IDLE || phase == Phase.PAUSED || direction != requestedDirection) {
            begin(requestedDirection, cameraYaw, previousYaw, previousPitch);
        }
        if (phase != Phase.TURNING) return false;
        turn();
        // The final settling tick remains stationary; the next tick can approach the edge.
        return true;
    }

    static float targetYaw(int direction, Side side) {
        return wrap(direction * 90.0F + (side == Side.RIGHT ? 135.0F : -135.0F));
    }

    void begin(int direction, float cameraYaw, float cameraPitch) {
        begin(direction, cameraYaw, cameraYaw, cameraPitch);
    }

    void begin(int direction, float cameraYaw, float startYaw, float startPitch) {
        if (side == null) {
            float left = Math.abs(wrap(targetYaw(direction, Side.LEFT) - cameraYaw));
            float right = Math.abs(wrap(targetYaw(direction, Side.RIGHT) - cameraYaw));
            side = left < right ? Side.LEFT : Side.RIGHT;
        }
        this.direction = direction;
        // Minecraft's reported yaw is continuous across full turns. Only wrap
        // differences; normalizing the output would send a sudden 360-degree jump.
        yaw = startYaw;
        pitch = startPitch;
        startup = true;
        phase = Phase.TURNING;
    }

    void turn() {
        yaw += clamp(wrap(targetYaw(direction, side) - yaw), 45.0F);
        pitch += clamp(PITCH - pitch, 30.0F);
        if (Math.abs(wrap(yaw - targetYaw(direction, side))) < 0.001F && Math.abs(pitch - PITCH) < 0.001F) {
            yaw += wrap(targetYaw(direction, side) - yaw);
            pitch = PITCH;
            phase = Phase.APPROACH;
        }
    }

    void edge(int tick) {
        if (phase == Phase.APPROACH) {
            phase = Phase.SNEAK;
            sneakTick = tick;
        }
    }

    boolean canPlace(int tick, int initialTicks) {
        return phase == Phase.SNEAK && (!startup || tick - sneakTick >= Math.max(2, Math.min(20, initialTicks)));
    }

    void placed(int tick) {
        phase = Phase.WAIT_SUPPORT;
        placedTick = tick;
    }

    boolean confirm(int tick, boolean supported) {
        if (phase != Phase.WAIT_SUPPORT || tick <= placedTick || !supported) return false;
        startup = false;
        phase = Phase.APPROACH;
        return true;
    }

    void pause() { phase = Phase.PAUSED; startup = true; }
    void stop() { phase = Phase.IDLE; direction = -1; startup = true; }
    void reset() { stop(); side = null; yaw = 0.0F; pitch = 0.0F; }
    boolean sneaking(boolean physicalSneak) { return physicalSneak || phase != Phase.APPROACH; }
    boolean paused() { return phase == Phase.IDLE || phase == Phase.PAUSED || phase == Phase.TURNING; }
    Phase phase() { return phase; }
    Side side() { return side; }
    int direction() { return direction; }
    float yaw() { return yaw; }
    float pitch() { return pitch; }
    static float[] movement(int direction, float yaw, boolean sneak) {
        double relative = Math.toRadians(wrap(direction * 90.0F - yaw));
        float scale = sneak ? 0.3F : 1.0F;
        return new float[]{Math.round(Math.cos(relative)) * scale, Math.round(-Math.sin(relative)) * scale};
    }

    private static float clamp(float value, float limit) { return Math.max(-limit, Math.min(limit, value)); }
}

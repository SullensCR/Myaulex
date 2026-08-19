package myau.module.modules;

import net.minecraft.util.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/** Three-tick observed-motion smoothing with teleport rejection. */
final class MotionPathObservedMotion {
    private static final double TELEPORT_THRESHOLD_SQ = 16.0D;
    private final Deque<Vec3> samples = new ArrayDeque<>(3);
    private boolean initialized;
    private double lastX;
    private double lastY;
    private double lastZ;

    MotionPathObservedMotion(double x, double y, double z) {
        this.reset(x, y, z);
    }

    Vec3 observe(double x, double y, double z) {
        if (!this.initialized) {
            this.reset(x, y, z);
            return zero();
        }
        double deltaX = x - this.lastX;
        double deltaY = y - this.lastY;
        double deltaZ = z - this.lastZ;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > TELEPORT_THRESHOLD_SQ) {
            this.samples.clear();
            return zero();
        }
        this.samples.addLast(new Vec3(deltaX, deltaY, deltaZ));
        while (this.samples.size() > 3) this.samples.removeFirst();
        double totalX = 0.0D;
        double totalY = 0.0D;
        double totalZ = 0.0D;
        for (Vec3 motion : this.samples) {
            totalX += motion.xCoord;
            totalY += motion.yCoord;
            totalZ += motion.zCoord;
        }
        double count = this.samples.size();
        return count == 0.0D ? zero() : new Vec3(totalX / count, totalY / count, totalZ / count);
    }

    private void reset(double x, double y, double z) {
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.samples.clear();
        this.initialized = true;
    }

    private static Vec3 zero() {
        return new Vec3(0.0D, 0.0D, 0.0D);
    }
}

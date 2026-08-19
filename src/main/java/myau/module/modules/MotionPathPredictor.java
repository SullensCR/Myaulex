package myau.module.modules;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, non-mutating Minecraft 1.8.9-style player motion forecast. */
final class MotionPathPredictor {
    private static final double EPSILON = 1.0E-7D;
    private static final double SETTLED_SPEED = 0.003D;

    interface Environment {
        List<AxisAlignedBB> collisions(AxisAlignedBB query);

        Surface supportingSurface(AxisAlignedBB box);

        float slipperiness(AxisAlignedBB box);

        boolean isWater(AxisAlignedBB box);

        boolean isLava(AxisAlignedBB box);

        boolean isWeb(AxisAlignedBB box);

        boolean isLadder(AxisAlignedBB box);
    }

    static final class Input {
        final float strafe;
        final float forward;
        final float yaw;
        final float moveSpeed;
        final float airAcceleration;
        final boolean jump;
        final boolean sprinting;
        final double jumpBoost;

        Input(
                float strafe,
                float forward,
                float yaw,
                float moveSpeed,
                float airAcceleration,
                boolean jump,
                boolean sprinting,
                double jumpBoost
        ) {
            this.strafe = strafe;
            this.forward = forward;
            this.yaw = yaw;
            this.moveSpeed = moveSpeed;
            this.airAcceleration = airAcceleration;
            this.jump = jump;
            this.sprinting = sprinting;
            this.jumpBoost = jumpBoost;
        }

        boolean isMoving() {
            return Math.abs(this.strafe) > 0.001F || Math.abs(this.forward) > 0.001F || this.jump;
        }
    }

    static final class Request {
        final AxisAlignedBB box;
        final double motionX;
        final double motionY;
        final double motionZ;
        final boolean supported;
        final double stepHeight;
        final int ticks;
        final Input input;
        final boolean maintainObservedHorizontal;

        Request(
                AxisAlignedBB box,
                double motionX,
                double motionY,
                double motionZ,
                boolean supported,
                double stepHeight,
                int ticks,
                Input input,
                boolean maintainObservedHorizontal
        ) {
            this.box = box;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.supported = supported;
            this.stepHeight = stepHeight;
            this.ticks = ticks;
            this.input = input;
            this.maintainObservedHorizontal = maintainObservedHorizontal;
        }
    }

    static final class Surface {
        final double minX;
        final double y;
        final double minZ;
        final double maxX;
        final double maxZ;

        Surface(double minX, double y, double minZ, double maxX, double maxZ) {
            this.minX = minX;
            this.y = y;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        static Surface fromBox(AxisAlignedBB box) {
            return new Surface(box.minX, box.maxY, box.minZ, box.maxX, box.maxZ);
        }

        static Surface interpolate(Surface previous, Surface current, double progress) {
            if (previous == null) return current;
            if (current == null) return previous;
            return new Surface(
                    lerp(previous.minX, current.minX, progress),
                    lerp(previous.y, current.y, progress),
                    lerp(previous.minZ, current.minZ, progress),
                    lerp(previous.maxX, current.maxX, progress),
                    lerp(previous.maxZ, current.maxZ, progress)
            );
        }
    }

    static final class Prediction {
        private final List<Vec3> points;
        private final Surface surface;

        Prediction(List<Vec3> points, Surface surface) {
            this.points = Collections.unmodifiableList(points);
            this.surface = surface;
        }

        List<Vec3> points() {
            return this.points;
        }

        Surface surface() {
            return this.surface;
        }

        Vec3 sample(double normalized) {
            if (this.points.isEmpty()) return new Vec3(0.0D, 0.0D, 0.0D);
            if (this.points.size() == 1 || normalized <= 0.0D) return this.points.get(0);
            if (normalized >= 1.0D) return this.points.get(this.points.size() - 1);
            double index = normalized * (this.points.size() - 1);
            int lower = (int) Math.floor(index);
            int upper = Math.min(lower + 1, this.points.size() - 1);
            double progress = index - lower;
            Vec3 first = this.points.get(lower);
            Vec3 second = this.points.get(upper);
            return new Vec3(
                    lerp(first.xCoord, second.xCoord, progress),
                    lerp(first.yCoord, second.yCoord, progress),
                    lerp(first.zCoord, second.zCoord, progress)
            );
        }
    }

    private static final class MoveResult {
        final AxisAlignedBB box;
        final double x;
        final double y;
        final double z;
        final boolean collidedX;
        final boolean collidedY;
        final boolean collidedZ;

        MoveResult(
                AxisAlignedBB box,
                double x,
                double y,
                double z,
                boolean collidedX,
                boolean collidedY,
                boolean collidedZ
        ) {
            this.box = box;
            this.x = x;
            this.y = y;
            this.z = z;
            this.collidedX = collidedX;
            this.collidedY = collidedY;
            this.collidedZ = collidedZ;
        }
    }

    private MotionPathPredictor() {
    }

    static Prediction predict(Request request, Environment environment) {
        AxisAlignedBB box = request.box;
        double motionX = request.motionX;
        double motionY = request.motionY;
        double motionZ = request.motionZ;
        double observedX = motionX;
        double observedZ = motionZ;
        boolean supported = request.supported || environment.supportingSurface(box) != null;
        boolean beganAirborne = !supported || motionY > 0.08D;
        int jumpCooldown = 0;
        int settledTicks = 0;
        Surface landing = null;
        List<Vec3> points = new ArrayList<>();
        points.add(centerAtFeet(box));

        for (int tick = 0; tick < request.ticks; tick++) {
            if (jumpCooldown > 0) jumpCooldown--;

            boolean water = environment.isWater(box);
            boolean lava = !water && environment.isLava(box);
            boolean web = environment.isWeb(box);

            if (request.maintainObservedHorizontal && supported) {
                motionX = observedX;
                motionZ = observedZ;
            }

            if (request.input != null) {
                if (request.input.jump && supported && !water && !lava && jumpCooldown == 0) {
                    motionY = 0.42D + request.input.jumpBoost;
                    if (request.input.sprinting) {
                        float radians = request.input.yaw * (float) Math.PI / 180.0F;
                        motionX -= Math.sin(radians) * 0.2D;
                        motionZ += Math.cos(radians) * 0.2D;
                    }
                    supported = false;
                    beganAirborne = true;
                    jumpCooldown = 10;
                } else if (request.input.jump && (water || lava)) {
                    motionY += 0.04D;
                }

                float acceleration;
                if (water || lava) {
                    acceleration = 0.02F;
                } else if (supported) {
                    float friction = environment.slipperiness(box) * 0.91F;
                    acceleration = request.input.moveSpeed * (0.16277136F / (friction * friction * friction));
                } else {
                    acceleration = request.input.airAcceleration;
                }
                double[] accelerated = moveFlying(
                        motionX,
                        motionZ,
                        request.input.strafe,
                        request.input.forward,
                        request.input.yaw,
                        acceleration
                );
                motionX = accelerated[0];
                motionZ = accelerated[1];
            }

            double requestedX = motionX;
            double requestedY = motionY;
            double requestedZ = motionZ;
            if (web) {
                requestedX *= 0.25D;
                requestedY *= 0.05D;
                requestedZ *= 0.25D;
                motionX = 0.0D;
                motionY = 0.0D;
                motionZ = 0.0D;
            }

            if (environment.isLadder(box)) {
                requestedX = clamp(requestedX, -0.15D, 0.15D);
                requestedZ = clamp(requestedZ, -0.15D, 0.15D);
                requestedY = Math.max(requestedY, -0.15D);
                motionX = requestedX;
                motionZ = requestedZ;
                motionY = requestedY;
            }

            MoveResult moved = move(
                    box,
                    requestedX,
                    requestedY,
                    requestedZ,
                    request.stepHeight,
                    supported,
                    environment
            );
            box = moved.box;
            points.add(centerAtFeet(box));

            if (moved.collidedX) {
                motionX = 0.0D;
                observedX = 0.0D;
            }
            if (moved.collidedZ) {
                motionZ = 0.0D;
                observedZ = 0.0D;
            }
            if (moved.collidedY) motionY = 0.0D;

            Surface support = environment.supportingSurface(box);
            boolean landedThisTick = moved.collidedY && requestedY < 0.0D && support != null;
            supported = support != null && requestedY <= 0.0D;

            if (environment.isLadder(box) && (moved.collidedX || moved.collidedZ)) {
                motionY = 0.2D;
            }

            if (water) {
                motionX *= 0.8D;
                motionY *= 0.8D;
                motionZ *= 0.8D;
                motionY -= 0.02D;
            } else if (lava) {
                motionX *= 0.5D;
                motionY *= 0.5D;
                motionZ *= 0.5D;
                motionY -= 0.02D;
            } else {
                float friction = supported ? environment.slipperiness(box) * 0.91F : 0.91F;
                motionY = (motionY - 0.08D) * 0.98D;
                motionX *= friction;
                motionZ *= friction;
            }

            if (beganAirborne && landedThisTick) {
                landing = support;
                break;
            }

            boolean noInput = request.input == null || !request.input.isMoving();
            if (supported && noInput && Math.hypot(motionX, motionZ) < SETTLED_SPEED) {
                settledTicks++;
                if (settledTicks >= 2) {
                    landing = support;
                    break;
                }
            } else {
                settledTicks = 0;
            }
        }

        if (landing == null) landing = environment.supportingSurface(box);
        return new Prediction(points, landing);
    }

    private static MoveResult move(
            AxisAlignedBB original,
            double requestedX,
            double requestedY,
            double requestedZ,
            double stepHeight,
            boolean supported,
            Environment environment
    ) {
        MoveResult normal = resolve(original, requestedX, requestedY, requestedZ, environment);
        boolean horizontalCollision = normal.collidedX || normal.collidedZ;
        boolean canStep = stepHeight > 0.0D
                && horizontalCollision
                && (supported || (requestedY < 0.0D && normal.collidedY));
        if (!canStep) return normal;

        List<AxisAlignedBB> collisions = environment.collisions(
                original.addCoord(requestedX, stepHeight, requestedZ)
        );
        double up = stepHeight;
        for (AxisAlignedBB collision : collisions) {
            up = collision.calculateYOffset(original, up);
        }
        AxisAlignedBB stepped = original.offset(0.0D, up, 0.0D);

        double stepX = requestedX;
        for (AxisAlignedBB collision : collisions) {
            stepX = collision.calculateXOffset(stepped, stepX);
        }
        stepped = stepped.offset(stepX, 0.0D, 0.0D);

        double stepZ = requestedZ;
        for (AxisAlignedBB collision : collisions) {
            stepZ = collision.calculateZOffset(stepped, stepZ);
        }
        stepped = stepped.offset(0.0D, 0.0D, stepZ);

        double down = -up;
        for (AxisAlignedBB collision : collisions) {
            down = collision.calculateYOffset(stepped, down);
        }
        stepped = stepped.offset(0.0D, down, 0.0D);
        double stepY = up + down;

        double normalDistance = normal.x * normal.x + normal.z * normal.z;
        double stepDistance = stepX * stepX + stepZ * stepZ;
        if (stepDistance <= normalDistance + EPSILON) return normal;
        return new MoveResult(
                stepped,
                stepX,
                stepY,
                stepZ,
                Math.abs(stepX - requestedX) > EPSILON,
                Math.abs(stepY - requestedY) > EPSILON,
                Math.abs(stepZ - requestedZ) > EPSILON
        );
    }

    private static MoveResult resolve(
            AxisAlignedBB original,
            double requestedX,
            double requestedY,
            double requestedZ,
            Environment environment
    ) {
        List<AxisAlignedBB> collisions = environment.collisions(original.addCoord(requestedX, requestedY, requestedZ));
        AxisAlignedBB box = original;
        double y = requestedY;
        for (AxisAlignedBB collision : collisions) y = collision.calculateYOffset(box, y);
        box = box.offset(0.0D, y, 0.0D);

        double x = requestedX;
        for (AxisAlignedBB collision : collisions) x = collision.calculateXOffset(box, x);
        box = box.offset(x, 0.0D, 0.0D);

        double z = requestedZ;
        for (AxisAlignedBB collision : collisions) z = collision.calculateZOffset(box, z);
        box = box.offset(0.0D, 0.0D, z);
        return new MoveResult(
                box,
                x,
                y,
                z,
                Math.abs(x - requestedX) > EPSILON,
                Math.abs(y - requestedY) > EPSILON,
                Math.abs(z - requestedZ) > EPSILON
        );
    }

    private static double[] moveFlying(
            double motionX,
            double motionZ,
            float strafe,
            float forward,
            float yaw,
            float acceleration
    ) {
        float magnitude = strafe * strafe + forward * forward;
        if (magnitude < 1.0E-4F) return new double[]{motionX, motionZ};
        magnitude = (float) Math.sqrt(magnitude);
        if (magnitude < 1.0F) magnitude = 1.0F;
        magnitude = acceleration / magnitude;
        strafe *= magnitude;
        forward *= magnitude;
        float sin = (float) Math.sin(yaw * Math.PI / 180.0F);
        float cos = (float) Math.cos(yaw * Math.PI / 180.0F);
        return new double[]{
                motionX + strafe * cos - forward * sin,
                motionZ + forward * cos + strafe * sin
        };
    }

    private static Vec3 centerAtFeet(AxisAlignedBB box) {
        return new Vec3((box.minX + box.maxX) * 0.5D, box.minY + 0.02D, (box.minZ + box.maxZ) * 0.5D);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double lerp(double previous, double current, double progress) {
        return previous + (current - previous) * progress;
    }
}

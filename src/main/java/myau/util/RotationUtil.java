package myau.util;

import myau.mixin.IAccessorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RotationUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static float wrapAngleDiff(float angle, float target) {
        return target + MathHelper.wrapAngleTo180_float(angle - target);
    }

    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        if (angle > maxAngle) {
            angle = maxAngle;
        } else if (angle < -maxAngle) {
            angle = -maxAngle;
        }
        return angle;
    }

    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - Math.max(0.0f, Math.min(1.0f, smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f)))));
    }

    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % (double) 0.0096f);
    }

    public static float[] getRotationsToBox(AxisAlignedBB boundingBox, float yaw, float pitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        double minTargetY = boundingBox.minY + 0.05 * (boundingBox.maxY - boundingBox.minY);
        double maxTargetY = boundingBox.minY + 0.75 * (boundingBox.maxY - boundingBox.minY);
        double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eyePos.xCoord;
        double deltaY = eyePos.yCoord >= maxTargetY ? maxTargetY - eyePos.yCoord : (eyePos.yCoord <= minTargetY ? minTargetY - eyePos.yCoord : 0.0);
        double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eyePos.zCoord;
        return RotationUtil.getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        return RotationUtil.getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
    }

    public static float[] getRotationsTo(Vec3 vec3, float yaw, float pitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        double deltaX = vec3.xCoord- eyePos.xCoord;
        double deltaY = vec3.yCoord- eyePos.yCoord;
        double deltaZ = vec3.zCoord - eyePos.zCoord;
        return RotationUtil.getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
    }

    public static float[] getRotations(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = MathHelper.wrapAngleTo180_float((float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float((float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{RotationUtil.quantizeAngle(currentYaw + yawDelta), RotationUtil.quantizeAngle(currentPitch + pitchDelta)};
    }

    public static Vec3 clampVecToBox(Vec3 vector, AxisAlignedBB boundingBox) {
        double[] coords = new double[]{vector.xCoord, vector.yCoord, vector.zCoord};
        double[] minCoords = new double[]{boundingBox.minX, boundingBox.minY, boundingBox.minZ};
        double[] maxCoords = new double[]{boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ};
        for (int i = 0; i < 3; ++i) {
            if (coords[i] > maxCoords[i]) {
                coords[i] = maxCoords[i];
                continue;
            }
            if (!(coords[i] < minCoords[i])) continue;
            coords[i] = minCoords[i];
        }
        return new Vec3(coords[0], coords[1], coords[2]);
    }

    public static double distanceToEntity(Entity entity) {
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return RotationUtil.distanceToBox(boundingBox);
    }

    public static double distanceToBox(Entity entity, Vec3 point) {
        float borderSize = entity.getCollisionBorderSize();
        return RotationUtil.clampVecToBox(entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize), point);
    }

    public static double distanceToBox(AxisAlignedBB boundingBox) {
        return RotationUtil.clampVecToBox(boundingBox, RotationUtil.mc.thePlayer.getPositionEyes(1.0f));
    }

    public static double squareDistanceToPoint(Vec3 point1, Vec3 point2) {
        return point1.squareDistanceTo(point2);
    }

    public static double clampVecToBox(AxisAlignedBB boundingBox, Vec3 point) {
        if (boundingBox.isVecInside(point)) {
            return 0.0;
        }
        Vec3 clampedPoint = RotationUtil.clampVecToBox(point, boundingBox);
        double deltaX = clampedPoint.xCoord - point.xCoord;
        double deltaY = clampedPoint.yCoord - point.yCoord;
        double deltaZ = clampedPoint.zCoord - point.zCoord;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    public static float angleToEntity(Entity entity) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        if (boundingBox.isVecInside(eyePos)) {
            return 0.0f;
        }
        double deltaX = entity.posX - eyePos.xCoord;
        double deltaZ = entity.posZ - eyePos.zCoord;
        return Math.abs(MathHelper.wrapAngleTo180_float((float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - RotationUtil.mc.thePlayer.rotationYaw)) * 2.0f;
    }

    public static float getYawBetween(double x1, double z1, double x2, double z2) {
        return MathHelper.wrapAngleTo180_float((float) (Math.atan2(z2 - z1, x2 - x1) * 180.0 / Math.PI) - 90.0f - RotationUtil.mc.thePlayer.rotationYaw);
    }

    public static MovingObjectPosition rayTrace(float yaw, float pitch, double distance, float partialTicks) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(partialTicks);
        Vec3 lookVec = ((IAccessorEntity) RotationUtil.mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    // true if blocked
    public static boolean notRayTrace(AttackData attackData, double distance) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        if (attackData.getAttackPoint() != null) {
            MovingObjectPosition movingObjectPosition = RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, attackData.getAttackPoint());
            if (movingObjectPosition == null) return false;
        }
        Entity entity = attackData.getEntity();
        if (entity instanceof AbstractClientPlayer) {
            double squareDistance = distance * distance;
            AxisAlignedBB boundingBox = entity.getEntityBoundingBox();
            List<Vec3> points1 = new ArrayList<>();
            List<Vec3> points2 = new ArrayList<>();
            double minX = boundingBox.minX;
            double maxX = boundingBox.maxX;
            double minY = boundingBox.minY;
            double maxY = boundingBox.maxY;
            double minZ = boundingBox.minZ;
            double maxZ = boundingBox.maxZ;
            double midX = (minX + maxX) * 0.5;
            double midZ = (minZ + maxZ) * 0.5;
            double dy = (maxY - minY) / 4.0;
            for (int i = 0; i < 5; i++) {
                double y = i == 0 ? minY : minY + dy * i + dy / 2;
                points1.add(new Vec3(minX, y, minZ));
                points1.add(new Vec3(minX, y, maxZ));
                points1.add(new Vec3(maxX, y, minZ));
                points1.add(new Vec3(maxX, y, maxZ));
                points2.add(new Vec3(minX, y, midZ));
                points2.add(new Vec3(maxX, y, midZ));
                points2.add(new Vec3(midX, y, minZ));
                points2.add(new Vec3(midX, y, maxZ));
            }
            for (Vec3 vec3: points2.stream().filter(p -> eyePos.squareDistanceTo(p) <= squareDistance).sorted(Comparator.comparingDouble(eyePos::squareDistanceTo)).collect(Collectors.toList())) {
                MovingObjectPosition movingObjectPosition = RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, vec3);
                if (movingObjectPosition == null) {
                    attackData.setAttackPoint(vec3);
                    return false;
                }
            }
            for (Vec3 vec3: points1.stream().filter(p -> eyePos.squareDistanceTo(p) <= squareDistance).sorted(Comparator.comparingDouble(eyePos::squareDistanceTo)).collect(Collectors.toList())) {
                MovingObjectPosition movingObjectPosition = RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, vec3);
                if (movingObjectPosition == null) {
                    attackData.setAttackPoint(vec3);
                    return false;
                }
            }
            return true;
        } else {
            float borderSize = entity.getCollisionBorderSize();
            Vec3 targetPos = RotationUtil.clampVecToBox(eyePos, entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize));
            attackData.setAttackPoint(targetPos);
            return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos) != null;
        }
    }

    public static MovingObjectPosition rayTrace(AxisAlignedBB boundingBox, float yaw, float pitch, double distance) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        Vec3 lookVec = ((IAccessorEntity) RotationUtil.mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return boundingBox.calculateIntercept(eyePos, targetPos);
    }

    public static class AttackData {
        private final EntityLivingBase entity;
        private final AxisAlignedBB box;
        private final double x;
        private final double y;
        private final double z;
        private Vec3 attackPoint;

        public AttackData(EntityLivingBase entityLivingBase, Vec3 attackPoint) {
            this.entity = entityLivingBase;
            double collisionBorderSize = entityLivingBase.getCollisionBorderSize();
            this.box = entityLivingBase.getEntityBoundingBox().expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
            this.x = entityLivingBase.posX;
            this.y = entityLivingBase.posY;
            this.z = entityLivingBase.posZ;
            if (attackPoint != null) {
                this.attackPoint = clampVecToBox(attackPoint, this.box);
            } else {
                this.attackPoint = null;
            }
        }

        public AttackData(EntityLivingBase entityLivingBase) {
            this(entityLivingBase, null);
        }

        public EntityLivingBase getEntity() {
            return this.entity;
        }

        public AxisAlignedBB getBox() {
            return this.box;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }

        public void setAttackPoint(Vec3 vec3) {
            if (vec3 != null) {
                this.attackPoint = clampVecToBox(vec3, this.box);
            } else {
                this.attackPoint = null;
            }
        }
        public Vec3 getAttackPoint() {
            return attackPoint;
        }
    }
}
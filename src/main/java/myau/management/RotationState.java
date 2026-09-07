package myau.management;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;

public class RotationState {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static int state = -1;
    private static float prevRenderYawOffset;
    private static float renderYawOffset;
    private static float prevRotationYawHead;
    private static float rotationYawHead;
    private static float prevRotationPitch;
    private static float rotationPitch;
    private static float smoothYaw;
    private static int priority;
    /*
     * EntityPlayerSP.rotationYaw is temporarily replaced by silent rotations.
     * Keep the real mouse-controlled camera separately so movement intent never
     * gets classified from the last server-facing angle.
     */
    private static EntityPlayerSP cameraPlayer;
    private static float cameraYaw;
    private static float cameraPitch;
    private static boolean cameraInitialized;

    public static void captureCameraInput(EntityPlayerSP player, float yawDelta, float pitchDelta) {
        if (player == null) return;
        ensureCamera(player);
        cameraYaw = MathHelper.wrapAngleTo180_float(cameraYaw + yawDelta * 0.15F);
        cameraPitch = MathHelper.clamp_float(cameraPitch - pitchDelta * 0.15F, -90.0F, 90.0F);
    }

    public static float getCameraYaw(EntityPlayerSP player) {
        if (player == null) return 0.0F;
        ensureCamera(player);
        return cameraYaw;
    }

    public static float getCameraPitch(EntityPlayerSP player) {
        if (player == null) return 0.0F;
        ensureCamera(player);
        return cameraPitch;
    }

    private static void ensureCamera(EntityPlayerSP player) {
        if (!cameraInitialized || cameraPlayer != player) {
            cameraPlayer = player;
            cameraYaw = player.rotationYaw;
            cameraPitch = player.rotationPitch;
            cameraInitialized = true;
        }
    }

    private static float calculateRenderYawOffset(float targetYaw, float currentYawOffset) {
        float newYawOffset = currentYawOffset;
        double deltaX = RotationState.mc.thePlayer.posX - RotationState.mc.thePlayer.prevPosX;
        double deltaZ = RotationState.mc.thePlayer.posZ - RotationState.mc.thePlayer.prevPosZ;
        if ((float) (deltaX * deltaX + deltaZ * deltaZ) > 0.0025000002f) {
            newYawOffset = (float) MathHelper.atan2(deltaZ, deltaX) * 180.0f / (float) Math.PI - 90.0f;
        }
        if (RotationState.mc.thePlayer.swingProgress > 0.0f) {
            newYawOffset = targetYaw;
        }
        float f4 = MathHelper.wrapAngleTo180_float(newYawOffset - currentYawOffset);
        float f5 = MathHelper.wrapAngleTo180_float(targetYaw - (currentYawOffset += f4 * 0.3f));
        if (f5 < -75.0f) {
            f5 = -75.0f;
        }
        if (f5 >= 75.0f) {
            f5 = 75.0f;
        }
        newYawOffset = targetYaw - f5;
        if (f5 * f5 > 2500.0f) {
            newYawOffset += f5 * 0.2f;
        }
        return newYawOffset;
    }

    public static void applyState(boolean bl, float f, float f2, float f3, int n) {
        state = bl ? 0 : state + 1;
        prevRenderYawOffset = renderYawOffset;
        renderYawOffset = bl ? RotationState.calculateRenderYawOffset(f, renderYawOffset) : RotationState.mc.thePlayer.renderYawOffset;
        prevRotationYawHead = rotationYawHead;
        rotationYawHead = bl ? f : RotationState.mc.thePlayer.rotationYawHead;
        prevRotationPitch = rotationPitch;
        rotationPitch = bl ? f2 : RotationState.mc.thePlayer.rotationPitch;
        smoothYaw = f3;
        priority = n;
    }

    public static boolean isActived() {
        return RotationState.isRotated(0);
    }

    public static boolean isRotated(int state) {
        if (RotationState.state < 0) return false;
        return RotationState.state <= state;
    }

    public static float getPrevRenderYawOffset() {
        return prevRenderYawOffset;
    }

    public static float getRenderYawOffset() {
        return renderYawOffset;
    }

    public static float getPrevRotationYawHead() {
        return prevRotationYawHead;
    }

    public static float getRotationYawHead() {
        return rotationYawHead;
    }

    public static float getPrevRotationPitch() {
        return prevRotationPitch;
    }

    public static float getRotationPitch() {
        return rotationPitch;
    }

    public static float getSmoothedYaw() {
        return smoothYaw;
    }

    public static float getPriority() {
        return priority;
    }
}

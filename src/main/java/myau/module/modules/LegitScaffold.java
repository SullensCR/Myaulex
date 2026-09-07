package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LivingUpdateEvent;
import myau.events.LoadWorldEvent;
import myau.events.MoveInputEvent;
import myau.events.UpdateEvent;
import myau.management.RotationState;
import myau.module.Module;
import myau.property.properties.TickDurationProperty;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovementInput;

/** Straight, level speed bridging. Aim is a segment invariant, never a placement search result. */
public final class LegitScaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int ROTATION_PRIORITY = 3;
    public final TickDurationProperty initialSneak = new TickDurationProperty("initial-sneak", 6, 2, 20);
    private final LegitScaffoldController controller = new LegitScaffoldController();
    private final LegitScaffoldInventory inventory = new LegitScaffoldInventory();
    private LegitScaffoldWorld environment;
    private BlockPos support;
    private BlockPos pending;
    private UpdateEvent rotationRequest;
    private float cameraYaw;
    private boolean inputPaused = true;
    private boolean wroteInput;
    private float appliedForward;
    private float appliedStrafe;
    private boolean appliedSneak;
    private PauseReason pauseReason = PauseReason.NONE;
    private PauseReason reportedReason = PauseReason.NONE;
    private int lastNoticeTick = -100;

    enum PauseReason {
        NONE("", false), IDLE("Waiting for movement", false), SETUP("Setting aim", false),
        ALIGNMENT("Waiting for a valid camera direction", true),
        BLOCKS("Put full building blocks in your hotbar", true),
        SUPPORT("Stand on a level full block", true),
        GEOMETRY("No clear placement face from this position", true),
        ROTATION("Another module is controlling rotation", true),
        JUMP("Release jump to bridge straight", true),
        MENU("Close the menu to resume", false),
        GROUNDED("Land and stop flying or riding to resume", true),
        TERRAIN("Leave water, lava or ladders to resume", true),
        USING_ITEM("Finish using the item to resume", true),
        STASIS("Stasis is blocking placement", true);

        final String message;
        final boolean notify;
        PauseReason(String message, boolean notify) { this.message = message; this.notify = notify; }
    }

    public LegitScaffold() {
        super("Legit Scaffold", false, false,
                "Straight speed bridging with fixed sideways aim. Your camera chooses the nearest block row; hold a movement key to bridge. Jump pauses.");
        initialSneak.setDisplayName("Initial Sneak");
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;
        if (event.getType() == EventType.POST) {
            reportPause();
            return;
        }
        syncEnvironment();
        rotationRequest = null;
        support = null;
        inputPaused = true;
        pauseReason = PauseReason.NONE;
        if (environment == null) return;
        // PRE runs before EntityPlayerSP temporarily applies the server-facing yaw.
        cameraYaw = mc.thePlayer.rotationYaw;
        boolean movementHeld = physicalMovementHeld();
        int direction = controller.resolveDirection(cameraYaw);
        if (!movementHeld) {
            controller.stop();
            pending = null;
            pauseReason = PauseReason.IDLE;
        } else if (!eligible()) {
            pause(eligibilityReason());
        } else if (direction < 0) {
            pause(PauseReason.ALIGNMENT);
        } else {
            int slot = inventory.select(index -> LegitScaffoldWorld.usable(mc.thePlayer.inventory.getStackInSlot(index)));
            if (slot < 0) {
                pause(PauseReason.BLOCKS);
            } else {
                mc.thePlayer.inventory.currentItem = slot;
                support = environment.support(direction);
                if (support == null) {
                    pause(PauseReason.SUPPORT);
                } else if (!environment.placementWindow(support, direction,
                        LegitScaffoldController.targetYaw(direction, controller.side() == null
                                ? chooseSide(cameraYaw, direction) : controller.side()))) {
                    pause(PauseReason.GEOMETRY);
                } else {
                    if (controller.prepare(direction, cameraYaw, event.getYaw(), event.getPitch())) {
                        pending = null;
                        pauseReason = PauseReason.SETUP;
                    } else {
                        updateSequence(direction);
                        inputPaused = controller.paused();
                    }
                }
            }
        }
        if (controller.side() != null) {
            event.setRotation(controller.yaw(), controller.pitch(), ROTATION_PRIORITY);
            event.setPervRotation(controller.yaw(), ROTATION_PRIORITY);
            rotationRequest = event;
        }
        // Like vanilla right-click and regular Scaffold, send the slot/placement/
        // swing before this tick's movement packet. POST is too late for actions.
        place(event);
    }

    private void updateSequence(int direction) {
        int tick = mc.thePlayer.ticksExisted;
        if (controller.phase() == LegitScaffoldController.Phase.WAIT_SUPPORT) {
            if (pending == null || !environment.fullBlock(pending)) {
                pause(PauseReason.SUPPORT);
                return;
            }
            if (controller.confirm(tick, environment.safeToRelease(pending, direction))) pending = null;
        }
        if (controller.phase() == LegitScaffoldController.Phase.APPROACH && environment.atEdge(support, direction)) {
            controller.edge(tick);
        }
        if (controller.phase() == LegitScaffoldController.Phase.SNEAK
                && !environment.targetOpen(support.offset(LegitScaffoldGeometry.facing(direction)))) pause(PauseReason.GEOMETRY);
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || !sameEnvironment()) return;
        if (!eligible()) {
            pause(eligibilityReason());
        } else if (!physicalMovementHeld()) {
            controller.stop();
            pending = null;
            inputPaused = true;
            pauseReason = PauseReason.IDLE;
        } else if (pauseReason == PauseReason.NONE || pauseReason == PauseReason.SETUP) {
            if (controller.direction() < 0) {
                pause(PauseReason.ALIGNMENT);
            } else if (!ownsRotation()) {
                pause(PauseReason.ROTATION);
            }
        }
        MovementInput input = mc.thePlayer.movementInput;
        boolean sneak = inputPaused || controller.sneaking(down(mc.gameSettings.keyBindSneak));
        float[] movement = inputPaused ? new float[]{0, 0}
                : LegitScaffoldController.movement(controller.direction(), controller.yaw(), sneak);
        input.moveForward = appliedForward = movement[0];
        input.moveStrafe = appliedStrafe = movement[1];
        input.sneak = appliedSneak = sneak;
        input.jump = false;
        wroteInput = true;
        mc.thePlayer.setSprinting(false);
    }

    @EventTarget(Priority.LOWEST)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!isEnabled() || !sameEnvironment()) return;
        // Runs after vanilla sprint-key/double-tap handling, before movement acceleration.
        mc.thePlayer.setSprinting(false);
        mc.thePlayer.movementInput.jump = false;
    }

    private void place(UpdateEvent event) {
        if (!hasPlacementRotation(event, controller.yaw(), controller.pitch())
                || !sameEnvironment() || !eligible() || inputPaused || !ownsRotation()
                || !mc.thePlayer.movementInput.sneak || !controller.canPlace(mc.thePlayer.ticksExisted, initialSneak.getValue())) return;
        if (support == null || !environment.fullBlock(support)
                || mc.thePlayer.inventory.currentItem != inventory.selected()
                || !LegitScaffoldWorld.usable(mc.thePlayer.inventory.getCurrentItem())) {
            pause(PauseReason.GEOMETRY);
            return;
        }
        BlockPos target = support.offset(LegitScaffoldGeometry.facing(controller.direction()));
        if (!environment.targetOpen(target)) {
            pause(PauseReason.GEOMETRY);
            return;
        }
        MovingObjectPosition hit = environment.placementHit(support, controller.direction(), controller.yaw(), controller.pitch());
        if (hit == null) {
            // Before this window, continue ordinary sneak movement. Beyond it, do not hunt for another angle.
            if (LegitScaffoldGeometry.progress(mc.thePlayer.posX, mc.thePlayer.posZ, LegitScaffoldGeometry.facing(controller.direction()))
                    > LegitScaffoldGeometry.edge(support, LegitScaffoldGeometry.facing(controller.direction())) + 0.27D) pause(PauseReason.GEOMETRY);
            return;
        }
        if (environment.place(hit)) {
            pending = target;
            controller.placed(mc.thePlayer.ticksExisted);
        } else {
            pause(PauseReason.GEOMETRY);
        }
    }

    private boolean ownsRotation() {
        return rotationRequest != null && RotationState.isActived()
                && RotationState.getPriority() == ROTATION_PRIORITY
                && matchesRotation(rotationRequest, controller.yaw(), controller.pitch())
                && Math.abs(LegitScaffoldController.wrap(RotationState.getSmoothedYaw() - controller.yaw())) < 0.001F;
    }

    static boolean matchesRotation(UpdateEvent request, float yaw, float pitch) {
        return request != null && request.isRotated() && request.isRotating() == ROTATION_PRIORITY
                && Math.abs(LegitScaffoldController.wrap(request.getNewYaw() - yaw)) < 0.001F
                && Math.abs(request.getNewPitch() - pitch) < 0.001F
                && Math.abs(LegitScaffoldController.wrap(request.getPreYaw() - yaw)) < 0.001F;
    }

    static boolean hasPlacementRotation(UpdateEvent request, float yaw, float pitch) {
        // PRE still has the previous movement packet's look. Do not place with
        // an angle that will only be transmitted later in this tick.
        return request != null && request.getType() == EventType.PRE
                && matchesRotation(request, yaw, pitch)
                && Math.abs(request.getYaw() - yaw) < 0.001F
                && Math.abs(request.getPitch() - pitch) < 0.001F;
    }

    private boolean eligible() { return eligibilityReason() == PauseReason.NONE; }

    private PauseReason eligibilityReason() {
        if (mc.currentScreen != null) return PauseReason.MENU;
        if (!mc.thePlayer.onGround || mc.thePlayer.isDead || mc.thePlayer.capabilities.isFlying || mc.thePlayer.isRiding())
            return PauseReason.GROUNDED;
        if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || mc.thePlayer.isOnLadder()) return PauseReason.TERRAIN;
        if (mc.thePlayer.isUsingItem()) return PauseReason.USING_ITEM;
        if (down(mc.gameSettings.keyBindJump)) return PauseReason.JUMP;
        return Stasis.blocksAutomatedUse() ? PauseReason.STASIS : PauseReason.NONE;
    }

    private void pause(PauseReason reason) {
        controller.pause();
        pending = null;
        inputPaused = true;
        pauseReason = reason;
    }

    private void reportPause() {
        if (!sameEnvironment() || mc.currentScreen != null || !pauseReason.notify || reportedReason == pauseReason) return;
        int tick = mc.thePlayer.ticksExisted;
        if (tick - lastNoticeTick < 40) return;
        ChatUtil.sendFormatted("&bLegit Scaffold: &7" + pauseReason.message + ".");
        reportedReason = pauseReason;
        lastNoticeTick = tick;
    }

    @Override
    public String[] getSuffix() {
        return pauseReason == PauseReason.NONE ? new String[0] : new String[]{pauseReason.message};
    }

    private void syncEnvironment() {
        if (sameEnvironment()) return;
        clearSession();
        if (mc != null && mc.thePlayer != null && mc.theWorld != null) {
            environment = new LegitScaffoldWorld(mc);
            inventory.begin(mc.thePlayer.inventory.currentItem);
        }
    }

    private boolean sameEnvironment() {
        return environment != null && mc != null && environment.player == mc.thePlayer && environment.world == mc.theWorld;
    }

    private void clearSession() {
        if (sameEnvironment()) {
            if (inventory.original() >= 0) mc.thePlayer.inventory.currentItem = inventory.original();
            if (wroteInput) {
                MovementInput input = mc.thePlayer.movementInput;
                boolean physicalSneak = down(mc.gameSettings.keyBindSneak);
                float scale = physicalSneak ? 0.3F : 1.0F;
                if (input.moveForward == appliedForward) input.moveForward = physicalForward() * scale;
                if (input.moveStrafe == appliedStrafe) input.moveStrafe = physicalStrafe() * scale;
                if (input.sneak == appliedSneak) input.sneak = physicalSneak;
                if (!input.jump) input.jump = down(mc.gameSettings.keyBindJump);
            }
        }
        environment = null;
        inventory.reset();
        controller.reset();
        support = null;
        pending = null;
        rotationRequest = null;
        inputPaused = true;
        wroteInput = false;
        pauseReason = PauseReason.NONE;
        reportedReason = PauseReason.NONE;
        lastNoticeTick = -100;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) { clearSession(); }

    @Override
    public void onEnabled() {
        ScaffoldActivity.disableOther(this);
        clearSession();
        syncEnvironment();
    }

    @Override
    public void onDisabled() { clearSession(); }

    public boolean blocksSprint() { return isEnabled(); }
    private static boolean down(KeyBinding key) { return GameSettings.isKeyDown(key); }
    private static boolean physicalMovementHeld() {
        return down(mc.gameSettings.keyBindForward) || down(mc.gameSettings.keyBindBack)
                || down(mc.gameSettings.keyBindLeft) || down(mc.gameSettings.keyBindRight);
    }
    private static int physicalForward() { return (down(mc.gameSettings.keyBindForward) ? 1 : 0) - (down(mc.gameSettings.keyBindBack) ? 1 : 0); }
    private static int physicalStrafe() { return (down(mc.gameSettings.keyBindLeft) ? 1 : 0) - (down(mc.gameSettings.keyBindRight) ? 1 : 0); }
    private static LegitScaffoldController.Side chooseSide(float camera, int direction) {
        return Math.abs(LegitScaffoldController.wrap(LegitScaffoldController.targetYaw(direction, LegitScaffoldController.Side.LEFT) - camera))
                < Math.abs(LegitScaffoldController.wrap(LegitScaffoldController.targetYaw(direction, LegitScaffoldController.Side.RIGHT) - camera))
                ? LegitScaffoldController.Side.LEFT : LegitScaffoldController.Side.RIGHT;
    }
}

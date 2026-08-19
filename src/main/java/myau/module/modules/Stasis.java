package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

public class Stasis extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float ROTATION_EPSILON = 1.0E-4F;

    public final ModeProperty mode = new ModeProperty(
            "mode",
            StasisActionState.CLASSIC,
            new String[]{"CLASSIC", "EXPERIMENTAL_1", "EXPERIMENTAL_2", "EXPERIMENTAL_3"}
    );

    private final StasisActionState actionState = new StasisActionState();
    private boolean rotationInitialized;
    private float observedYaw;
    private float observedPitch;
    private float sentYaw;
    private float sentPitch;
    private boolean bufferOwnership;
    private boolean flushingBuffers;

    public Stasis() {
        super("Stasis", false);
    }

    public boolean isFreezing() {
        return this.isEnabled() && this.actionState.isFreezing();
    }

    public boolean isExperimentalActive() {
        return this.isEnabled() && this.actionState.isExperimental();
    }

    public boolean hasPacketPriority() {
        return this.isExperimentalActive() && this.bufferOwnership;
    }

    public boolean shouldSuppressAutomatedUse() {
        return this.isExperimentalActive();
    }

    public boolean shouldBlockVanillaUseInvocation() {
        return this.isExperimentalActive() && !this.actionState.isReplayingUse();
    }

    public static Stasis getActiveInstance() {
        if (Myau.moduleManager == null) {
            return null;
        }
        Module module = Myau.moduleManager.getModule(Stasis.class);
        return module instanceof Stasis ? (Stasis) module : null;
    }

    public static boolean isAutoClickerPaused() {
        Stasis stasis = getActiveInstance();
        return stasis != null && stasis.isExperimentalActive();
    }

    public static boolean ownsOutgoingPackets() {
        Stasis stasis = getActiveInstance();
        return stasis != null && stasis.hasPacketPriority();
    }

    public static boolean blocksAutomatedUse() {
        Stasis stasis = getActiveInstance();
        return stasis != null && stasis.shouldSuppressAutomatedUse();
    }

    public void onRawKeyState(int keyCode, boolean pressed) {
        if (!pressed
                || !this.isExperimentalActive()
                || mc.gameSettings == null
                || keyCode != mc.gameSettings.keyBindUseItem.getKeyCode()) {
            return;
        }
        this.actionState.armRawUse();
    }

    public void onPlayerUpdateCompleted() {
        if (!this.isExperimentalActive()) {
            return;
        }
        boolean replayFastUse = this.actionState.onPlayerUpdateCompleted();
        if (replayFastUse) {
            this.replayVanillaUse();
        }
        this.releasePacketPriorityIfIdle();
    }

    @EventTarget(Priority.HIGHEST)
    public void onTickStart(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (this.actionState.getMode() != this.mode.getValue()) {
            this.resetTransientState();
        }
        if (!this.actionState.isExperimental()) {
            return;
        }
        if (!this.hasValidGameState()) {
            this.resetTransientState();
            return;
        }

        this.initializeRotationSnapshot();
        float yaw = mc.thePlayer.rotationYaw;
        float pitch = mc.thePlayer.rotationPitch;
        boolean rotationChanged = differs(yaw, this.observedYaw) || differs(pitch, this.observedPitch);
        this.observedYaw = yaw;
        this.observedPitch = pitch;

        if (rotationChanged) {
            // Camera movement is only staged locally. It must not wake Stasis
            // or put a look packet on the wire until a real action consumes it.
            this.actionState.recordRotation(yaw, pitch);
        }

        if (this.actionState.pollStrictReplayAtTickStart()) {
            this.replayVanillaUse();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onTickEnd(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }
        if (this.actionState.isExperimental() && this.actionState.pollLookReplayAtTickEnd()) {
            this.replayVanillaUse();
        }
        this.actionState.clearRawUseArm();
        this.releasePacketPriorityIfIdle();
    }

    @EventTarget(Priority.HIGHEST)
    public void captureRightClick(RightClickMouseEvent event) {
        if (!this.isExperimentalActive()) {
            return;
        }
        if (this.actionState.isReplayingUse()) {
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);
        this.initializeRotationSnapshot();
        this.actionState.recordRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        if (!this.actionState.captureRawUse()) {
            return;
        }

        this.acquirePacketPriority();
        if (this.actionState.getMode() == StasisActionState.EXPERIMENTAL_3) {
            this.observedYaw = mc.thePlayer.rotationYaw;
            this.observedPitch = mc.thePlayer.rotationPitch;
            this.sendLookIfNeeded(this.actionState.consumeLatestRotation());
        } else {
            this.actionState.consumeLatestRotation();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void enforceRightClickOwnership(RightClickMouseEvent event) {
        if (!this.isExperimentalActive()) {
            return;
        }
        event.setCancelled(!this.actionState.isReplayingUse());
    }

    @EventTarget(Priority.HIGHEST)
    public void suppressUnownedUsePackets(PacketEvent event) {
        if (this.isExperimentalActive()
                && event.getType() == EventType.SEND
                && event.getPacket() instanceof C08PacketPlayerBlockPlacement
                && !this.actionState.isReplayingUse()
                && !this.flushingBuffers) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        this.resetTransientState();
        // loadWorld fires before Minecraft swaps out the old player instance.
        // Force the first tick in the new world to establish a fresh baseline.
        this.rotationInitialized = false;
    }

    @Override
    public void onEnabled() {
        this.resetTransientState();
    }

    @Override
    public void onDisabled() {
        this.resetTransientState();
    }

    private boolean hasValidGameState() {
        return mc.thePlayer != null
                && mc.theWorld != null
                && !mc.thePlayer.isDead
                && mc.currentScreen == null;
    }

    private void initializeRotationSnapshot() {
        if (this.rotationInitialized || mc.thePlayer == null) {
            return;
        }
        this.rotationInitialized = true;
        this.observedYaw = mc.thePlayer.rotationYaw;
        this.observedPitch = mc.thePlayer.rotationPitch;
        this.sentYaw = this.observedYaw;
        this.sentPitch = this.observedPitch;
    }

    private void sendLookIfNeeded(StasisActionState.RotationSample queuedRotation) {
        if (!this.hasValidGameState()) {
            return;
        }
        float yaw = queuedRotation == null ? mc.thePlayer.rotationYaw : queuedRotation.yaw;
        float pitch = queuedRotation == null ? mc.thePlayer.rotationPitch : queuedRotation.pitch;
        if (!differs(yaw, this.sentYaw) && !differs(pitch, this.sentPitch)) {
            return;
        }

        this.acquirePacketPriority();
        PacketUtil.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, mc.thePlayer.onGround));
        this.sentYaw = yaw;
        this.sentPitch = pitch;
        this.releasePacketPriorityIfIdle();
    }

    private void replayVanillaUse() {
        if (!this.hasValidGameState() || !this.actionState.isExperimental()) {
            return;
        }
        this.acquirePacketPriority();
        this.actionState.beginReplay();
        try {
            ((IAccessorMinecraft) mc).callRightClickMouse();
        } finally {
            this.actionState.endReplay();
        }
    }

    private void acquirePacketPriority() {
        if (this.bufferOwnership) {
            return;
        }
        this.bufferOwnership = true;
        this.flushingBuffers = true;
        try {
            if (Myau.lagManager != null) {
                Myau.lagManager.flushForStasis();
            }
            if (Myau.blinkManager != null) {
                Myau.blinkManager.flushForStasis();
            }
            if (Myau.moduleManager != null) {
                Module module = Myau.moduleManager.getModule(FakeLag.class);
                if (module instanceof FakeLag) {
                    ((FakeLag) module).flushForStasis();
                }
            }
        } finally {
            this.flushingBuffers = false;
        }
    }

    private void releasePacketPriorityIfIdle() {
        if (!this.actionState.hasActiveCycle()) {
            this.bufferOwnership = false;
        }
    }

    private void resetTransientState() {
        this.bufferOwnership = false;
        this.flushingBuffers = false;
        this.rotationInitialized = false;
        this.actionState.reset(this.mode.getValue());
        this.initializeRotationSnapshot();
    }

    private static boolean differs(float first, float second) {
        return Math.abs(first - second) > ROTATION_EPSILON;
    }
}

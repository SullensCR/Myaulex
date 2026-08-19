package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.PacketArrivalEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.KeyBindUtil;
import myau.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/** Filters only Aura's scheduled attacks. It deliberately does not subscribe
 * to manual clicks or other modules' attack paths. */
public final class HitSelect extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long SAMPLE_INTERVAL_NANOS = 1_000_000_000L;

    public final ModeProperty mode = new ModeProperty("mode", 1, new String[]{"CUSTOM", "SMART"});

    public final ModeProperty customMode = new ModeProperty("custom-mode", 0,
            new String[]{"BURST", "CRITICALS"}, this::isCustom);
    public final IntProperty pauseDuration = new IntProperty("pause-duration", 500, 0, 1000, this::isCustom);
    public final IntProperty waitForFirstHit = new IntProperty("wait-for-first-hit", 0, 0, 1000,
            () -> isCustom() && customMode.getValue() == 0);
    public final IntProperty hitLaterInTrades = new IntProperty("hit-later-in-trades", 0, 0, 1000,
            () -> isCustom() && customMode.getValue() == 0);
    public final BooleanProperty disableDuringKnockback = new BooleanProperty("disable-during-knockback", false,
            () -> isCustom() && customMode.getValue() == 1);
    public final BooleanProperty onlyWhileDamaged = new BooleanProperty("only-while-damaged", false,
            () -> isCustom() && customMode.getValue() == 1);
    public final BooleanProperty useServerAttackTime = new BooleanProperty("use-server-attack-time", false, this::isCustom);
    public final BooleanProperty fakeSwing = new BooleanProperty("fake-swing", true, this::isCustom);
    public final PercentProperty inCombat = new PercentProperty("in-combat", 100, this::isCustom);
    public final PercentProperty missedSwings = new PercentProperty("missed-swings", 100, this::isCustom);

    public final BooleanProperty outOfRangeAttack = new BooleanProperty("out-of-range-attack", false, this::isSmart);
    public final BooleanProperty sprintReset = new BooleanProperty("sprint-reset", false, this::isSmart);
    public final IntProperty sprintDelay = new IntProperty("sprint-delay", 100, 20, 150,
            () -> isSmart() && sprintReset.getValue());
    public final BooleanProperty onlyWhenCombo = new BooleanProperty("only-when-combo", true,
            () -> isSmart() && sprintReset.getValue());

    private final HitSelectState state = new HitSelectState();
    private final HitSelectState.SprintWindow sprintWindow = new HitSelectState.SprintWindow();
    private final HitSelectState.MotionHistory playerMotion = new HitSelectState.MotionHistory();
    private final HitSelectState.MotionHistory targetMotion = new HitSelectState.MotionHistory();
    private final Queue<StatusObservation> statusQueue = new ConcurrentLinkedQueue<>();

    private EntityLivingBase target;
    private long targetAcquiredNanos;
    private long lastSelfDamageNanos;
    private long lastAuraAttackNanos;
    private long blockedSinceNanos;
    private long lastPingSampleNanos;
    private boolean sendingManagedAuraAttack;
    private boolean pendingSprintReset;

    public HitSelect() {
        super("Hit Select", false, false, "Times Aura attacks for full-damage hits.");
        customMode.childOf(mode);
        pauseDuration.childOf(mode);
        waitForFirstHit.childOf(customMode);
        hitLaterInTrades.childOf(customMode);
        disableDuringKnockback.childOf(customMode);
        onlyWhileDamaged.childOf(customMode);
        useServerAttackTime.childOf(mode);
        fakeSwing.childOf(mode);
        inCombat.childOf(mode);
        missedSwings.childOf(mode);
        outOfRangeAttack.childOf(mode);
        sprintReset.childOf(mode);
        sprintDelay.childOf(sprintReset);
        onlyWhenCombo.childOf(sprintReset);
    }

    private boolean isCustom() {
        return mode.getValue() == 0;
    }

    private boolean isSmart() {
        return mode.getValue() == 1;
    }

    /** Called exactly once for each Aura attempt whose CPS delay became due. */
    public AttackDecision selectAuraAttempt(EntityLivingBase auraTarget, boolean rayHit, long nowNanos) {
        if (!isEnabled()) return rayHit ? AttackDecision.ATTACK : AttackDecision.NORMAL_MISS;
        synchronizeTarget(auraTarget, nowNanos);

        if (!rayHit) {
            boolean cancel = isCustom() && rollCancel(missedSwings.getValue());
            return cancel ? (fakeSwing.getValue() ? AttackDecision.FAKE_SWING : AttackDecision.CANCEL)
                    : AttackDecision.NORMAL_MISS;
        }

        state.addHurtTimeFallback(nowNanos, auraTarget.hurtTime);
        boolean ready = state.isReady(nowNanos);
        if (!ready && isSmart() && outOfRangeAttack.getValue()) {
            ready = shouldSendBeforeLeavingRange(auraTarget, nowNanos);
        }
        if (ready && isCustom()) ready = !customWaitApplies(nowNanos);
        if (ready) {
            blockedSinceNanos = 0L;
            return AttackDecision.ATTACK;
        }

        if (isCustom()) {
            if (pauseDuration.getValue() == 0) {
                blockedSinceNanos = 0L;
                return AttackDecision.ATTACK;
            }
            if (blockedSinceNanos == 0L) blockedSinceNanos = nowNanos;
            if (nowNanos - blockedSinceNanos >= pauseDuration.getValue() * 1_000_000L) {
                blockedSinceNanos = 0L;
                return AttackDecision.ATTACK;
            }
        }

        boolean cancel = !isCustom() || rollCancel(inCombat.getValue());
        if (!cancel) return AttackDecision.ATTACK;
        return isCustom() && fakeSwing.getValue() ? AttackDecision.FAKE_SWING : AttackDecision.CANCEL;
    }

    private boolean customWaitApplies(long nowNanos) {
        if (customMode.getValue() == 1 && !mc.thePlayer.onGround) {
            if (disableDuringKnockback.getValue() && mc.thePlayer.hurtTime > 0) return burstWaitApplies(nowNanos);
            if (onlyWhileDamaged.getValue() && lastSelfDamageNanos < targetAcquiredNanos) {
                return burstWaitApplies(nowNanos);
            }
            return mc.thePlayer.motionY >= 0.0
                    || mc.thePlayer.fallDistance <= 0.0F
                    || mc.thePlayer.isOnLadder()
                    || mc.thePlayer.isInWater()
                    || mc.thePlayer.isPotionActive(Potion.blindness)
                    || mc.thePlayer.ridingEntity != null;
        }
        return burstWaitApplies(nowNanos);
    }

    private boolean burstWaitApplies(long nowNanos) {
        if (state.getConfirmedHits() == 0 && waitForFirstHit.getValue() > 0
                && lastSelfDamageNanos < targetAcquiredNanos
                && nowNanos - targetAcquiredNanos < waitForFirstHit.getValue() * 1_000_000L) return true;
        return lastAuraAttackNanos != 0L && hitLaterInTrades.getValue() > 0
                && lastSelfDamageNanos < lastAuraAttackNanos
                && nowNanos - lastAuraAttackNanos < hitLaterInTrades.getValue() * 1_000_000L;
    }

    private boolean rollCancel(int percent) {
        return percent >= 100 || percent > 0 && ThreadLocalRandom.current().nextInt(100) < percent;
    }

    private boolean shouldSendBeforeLeavingRange(EntityLivingBase auraTarget, long nowNanos) {
        long arrivalNanos = nowNanos + state.halfSmoothedPingNanos();
        if (!state.isReady(arrivalNanos)) return false;

        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (aura == null) return false;
        double arrivalTicks = Math.min(10.0, state.halfSmoothedPingNanos() / 50_000_000.0);
        double nextTicks = Math.min(10.0, arrivalTicks + aura.getScheduledAttackDelayMs() / 50.0);
        double currentDistance = predictedDistance(auraTarget, arrivalTicks);
        double nextDistance = predictedDistance(auraTarget, nextTicks);
        double relativeX = auraTarget.posX - mc.thePlayer.posX;
        double relativeZ = auraTarget.posZ - mc.thePlayer.posZ;
        double relativeVelocityX = targetMotion.velocityX() - playerMotion.velocityX();
        double relativeVelocityZ = targetMotion.velocityZ() - playerMotion.velocityZ();
        boolean separating = relativeX * relativeVelocityX + relativeZ * relativeVelocityZ > 0.0;
        return currentDistance <= aura.attackRange.getValue()
                && nextDistance > aura.attackRange.getValue() + 0.1
                && separating;
    }

    private double predictedDistance(EntityLivingBase auraTarget, double ticks) {
        double playerX = mc.thePlayer.posX + playerMotion.velocityX() * ticks;
        double playerZ = mc.thePlayer.posZ + playerMotion.velocityZ() * ticks;
        double targetShiftX = targetMotion.velocityX() * ticks;
        double targetShiftZ = targetMotion.velocityZ() * ticks;
        AxisAlignedBB box = auraTarget.getEntityBoundingBox().offset(targetShiftX, 0.0, targetShiftZ);
        Vec3 eye = new Vec3(playerX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), playerZ);
        return RotationUtil.clampVecToBox(box, eye);
    }

    public void beginManagedAuraAttack() {
        pendingSprintReset = shouldResetSprintNow();
        sendingManagedAuraAttack = pendingSprintReset;
    }

    public void endManagedAuraAttack() {
        sendingManagedAuraAttack = false;
    }

    public boolean isSendingManagedAuraAttack() {
        return sendingManagedAuraAttack;
    }

    public boolean ownsAuraSprintReset(Entity targetEntity) {
        return sendingManagedAuraAttack && targetEntity != null && targetEntity == target;
    }

    public void recordAuraAttack(EntityLivingBase auraTarget, long nowNanos) {
        synchronizeTarget(auraTarget, nowNanos);
        state.recordAttack(nowNanos);
        lastAuraAttackNanos = nowNanos;
        if (pendingSprintReset && mc.thePlayer != null) {
            sprintWindow.begin(nowNanos, sprintDelay.getValue());
            suppressSprint();
        }
        pendingSprintReset = false;
    }

    private boolean shouldResetSprintNow() {
        return isEnabled() && isSmart() && sprintReset.getValue()
                && mc.thePlayer != null && mc.thePlayer.isSprinting()
                && (!onlyWhenCombo.getValue() || state.getConfirmedHits() >= 1);
    }

    public boolean isSuppressingSprint() {
        return isEnabled() && sprintWindow.isActive(System.nanoTime());
    }

    private void suppressSprint() {
        if (mc.thePlayer == null) return;
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        mc.thePlayer.setSprinting(false);
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) return;
        long now = System.nanoTime();
        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        synchronizeTarget(aura == null ? null : aura.getTarget(), now);
        drainStatusQueue();
        samplePing(now);
        playerMotion.add(mc.thePlayer.posX, mc.thePlayer.posZ);
        if (target != null) targetMotion.add(target.posX, target.posZ);

        if (sprintWindow.shouldRestore(now)) {
            sprintWindow.clear();
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
        } else if (sprintWindow.isActive(now)) {
            suppressSprint();
        }
    }

    private void samplePing(long nowNanos) {
        if (nowNanos - lastPingSampleNanos < SAMPLE_INTERVAL_NANOS || mc.getNetHandler() == null) return;
        lastPingSampleNanos = nowNanos;
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (info != null) state.samplePing(info.getResponseTime());
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacketArrival(PacketArrivalEvent event) {
        if (isEnabled() && event.getPacket() instanceof S19PacketEntityStatus
                && ((S19PacketEntityStatus) event.getPacket()).getOpCode() == 2) {
            statusQueue.offer(new StatusObservation((S19PacketEntityStatus) event.getPacket(),
                    event.getWorld(), event.getArrivalNanos()));
        }
    }

    private void drainStatusQueue() {
        StatusObservation observation;
        while ((observation = statusQueue.poll()) != null) {
            if (observation.world != mc.theWorld) continue;
            Entity entity = observation.packet.getEntity(observation.world);
            if (entity == mc.thePlayer) {
                lastSelfDamageNanos = observation.arrivalNanos;
                state.clearCombo();
            } else if (entity == target && (isSmart() || useServerAttackTime.getValue())) {
                state.recordDamageStatus(observation.arrivalNanos);
            }
        }
    }

    private void synchronizeTarget(EntityLivingBase newTarget, long nowNanos) {
        if (newTarget == target) return;
        target = newTarget;
        targetAcquiredNanos = nowNanos;
        lastAuraAttackNanos = 0L;
        blockedSinceNanos = 0L;
        state.resetCombat();
        targetMotion.clear();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        reset(true);
    }

    @Override
    public void onEnabled() {
        reset(false);
    }

    @Override
    public void onDisabled() {
        reset(true);
    }

    private void reset(boolean restoreSprintKey) {
        target = null;
        targetAcquiredNanos = 0L;
        lastSelfDamageNanos = 0L;
        lastAuraAttackNanos = 0L;
        blockedSinceNanos = 0L;
        lastPingSampleNanos = 0L;
        sendingManagedAuraAttack = false;
        pendingSprintReset = false;
        statusQueue.clear();
        state.resetAll();
        playerMotion.clear();
        targetMotion.clear();
        boolean hadSprintWindow = sprintWindow.isActive(System.nanoTime()) || sprintWindow.shouldRestore(System.nanoTime());
        sprintWindow.clear();
        if (restoreSprintKey && hadSprintWindow && mc.thePlayer != null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }

    public enum AttackDecision {
        ATTACK(true, true, false),
        NORMAL_MISS(false, true, false),
        FAKE_SWING(false, false, true),
        CANCEL(false, false, false);

        private final boolean attack;
        private final boolean normalSwing;
        private final boolean localSwing;

        AttackDecision(boolean attack, boolean normalSwing, boolean localSwing) {
            this.attack = attack;
            this.normalSwing = normalSwing;
            this.localSwing = localSwing;
        }

        public boolean shouldAttack() { return attack; }
        public boolean shouldNormalSwing() { return normalSwing; }
        public boolean shouldLocalSwing() { return localSwing; }
    }

    private static final class StatusObservation {
        final S19PacketEntityStatus packet;
        final net.minecraft.client.multiplayer.WorldClient world;
        final long arrivalNanos;

        StatusObservation(S19PacketEntityStatus packet,
                          net.minecraft.client.multiplayer.WorldClient world,
                          long arrivalNanos) {
            this.packet = packet;
            this.world = world;
            this.arrivalNanos = arrivalNanos;
        }
    }
}

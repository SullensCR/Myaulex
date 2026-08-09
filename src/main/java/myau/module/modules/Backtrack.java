package myau.module.modules;

import myau.Myau;
import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.AttackEvent;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3DPacketDisplayScoreboard;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;

/**
 * LiquidBounce-style modern backtrack. It keeps the target at an older client
 * position by delaying inbound play packets while independently tracking the
 * target's latest server position.
 */
public final class Backtrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long MANUAL_TARGET_TIMEOUT_MS = 3_000L;
    private static final double MAX_SERVER_DISTANCE = 6.0D;

    public final IntProperty nextDelay = new IntProperty("next-delay-ms", 0, 0, 2000);
    public final IntProperty minDelay = new IntProperty("min-delay-ms", 80, 0, 2000);
    public final IntProperty maxDelay = new IntProperty("max-delay-ms", 80, 0, 2000);
    public final ModeProperty style = new ModeProperty("style", 1, new String[]{"PULSE", "SMOOTH"});
    public final FloatProperty minDistance = new FloatProperty("min-distance", 2.0F, 0.0F, 6.0F);
    public final FloatProperty maxDistance = new FloatProperty("max-distance", 3.0F, 0.0F, 6.0F);
    public final BooleanProperty smart = new BooleanProperty("smart", true);
    public final ModeProperty esp = new ModeProperty(
            "esp", 1, new String[]{"NONE", "BOX", "MODEL", "WIREFRAME"});
    public final FloatProperty wireframeWidth = new FloatProperty(
            "wireframe-width", 1.0F, 0.5F, 5.0F, () -> esp.getValue() == 3).childOf(esp);
    public final ColorProperty espColor = new ColorProperty(
            "esp-color", new Color(0, 255, 0).getRGB(), () -> esp.getValue() != 0).childOf(esp);

    private final ConcurrentLinkedQueue<TimedPacket> packets = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PositionSnapshot> positions = new ConcurrentLinkedQueue<>();
    private final ServerPositionTracker serverPosition = new ServerPositionTracker();

    private volatile EntityLivingBase target;
    private volatile EntityLivingBase manualTarget;
    private volatile long manualTargetTime;
    private volatile boolean pendingFlush;
    private volatile boolean pendingTargetReset;

    private volatile int selectedDelay = 80;
    private volatile long cycleStarted;
    private volatile long nextCycleAllowed;
    private volatile boolean cycleActive;
    private volatile boolean shouldRender;
    private Vec3 renderedServerPosition;

    public Backtrack() {
        super("Backtrack", false, false, "Attacks targets at delayed server positions.");
    }

    @Override
    public void onEnabled() {
        resetWithoutReplay();
        selectedDelay = chooseDelay(minDelay.getValue(), maxDelay.getValue());
    }

    @Override
    public void onDisabled() {
        if (mc.getNetHandler() != null && mc.theWorld != null) {
            drainAllPackets();
        } else {
            packets.clear();
        }
        resetWithoutReplay();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled() || !(event.getTarget() instanceof EntityLivingBase)) return;
        manualTarget = (EntityLivingBase) event.getTarget();
        manualTargetTime = System.currentTimeMillis();
    }

    @EventTarget(Priority.LOW)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) return;

        if (hasConflictingInboundDelay()) {
            pendingFlush = true;
            return;
        }

        EntityLivingBase currentTarget = target;
        Packet<?> packet = event.getPacket();
        long now = System.currentTimeMillis();

        if (isWorldTransitionPacket(packet)) {
            // These packets must reach vanilla immediately. In particular,
            // S08PacketPlayerPosLook marks terrain loading as complete. Any
            // queued combat packets belong to the state before this packet.
            resetWithoutReplay();
            return;
        }

        if (currentTarget != null) {
            updateServerPosition(packet, currentTarget, now);
        }

        if (packetRequiresFlush(packet, currentTarget)) {
            pendingFlush = true;
            if (packetRemovesTarget(packet, currentTarget)) pendingTargetReset = true;
            return;
        }
        if (isImmediatePacket(packet, currentTarget)) return;

        if (packets.isEmpty() && !shouldBacktrack(currentTarget, now)) return;
        if (currentTarget == null || currentTarget != target || !shouldBacktrack(currentTarget, now)) return;

        if (!cycleActive) {
            cycleActive = true;
            cycleStarted = now;
            shouldRender = true;
        }
        event.setCancelled(true);
        packets.offer(new TimedPacket(packet, now, mc.theWorld, mc.getNetHandler()));
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
            packets.clear();
            positions.clear();
            return;
        }

        long now = System.currentTimeMillis();
        EntityLivingBase resolved = resolveTarget(now);
        if (pendingTargetReset) resolved = null;

        if (resolved != target) {
            drainAllPackets();
            resetCycle(now, false);
            target = resolved;
            initializeServerPosition(resolved);
            selectedDelay = chooseDelay(minDelay.getValue(), maxDelay.getValue());
        }

        if (pendingFlush || hasConflictingInboundDelay()) {
            drainAllPackets();
            resetCycle(now, true);
            pendingFlush = false;
            if (pendingTargetReset) {
                target = null;
                manualTarget = null;
                serverPosition.clear();
            }
            pendingTargetReset = false;
            return;
        }

        EntityLivingBase currentTarget = target;
        if (!shouldBacktrack(currentTarget, now)) {
            drainAllPackets();
            resetCycle(now, true);
            return;
        }

        Vec3 truePosition = serverPosition.current();
        if (truePosition == null) return;

        double trueDistance = mc.thePlayer.getDistance(
                truePosition.xCoord, truePosition.yCoord, truePosition.zCoord);
        double visibleDistance = mc.thePlayer.getDistanceToEntity(currentTarget);
        boolean useful = trueDistance <= MAX_SERVER_DISTANCE
                && (!smart.getValue() || trueDistance >= visibleDistance);
        if (!useful) {
            drainAllPackets();
            resetCycle(now, true);
            return;
        }

        if (style.getValue() == 0) {
            if (pulseExpired(cycleActive, cycleStarted, now, selectedDelay)) {
                drainAllPackets();
                resetCycle(now, true);
            }
            return;
        }

        double visibleBoxDistance = RotationUtil.distanceToBox(currentTarget.getEntityBoundingBox());
        if (inDistanceWindow(visibleBoxDistance, minDistance.getValue(), maxDistance.getValue())) {
            long cutoff = smoothCutoff(now, selectedDelay);
            releaseThrough(cutoff);
            discardPositionsBefore(cutoff);
        } else {
            long cutoff = findRangeCutoff(
                    positions, currentTarget, minDistance.getValue(), maxDistance.getValue());
            if (cutoff < 0L) {
                drainAllPackets();
                resetCycle(now, true);
                return;
            }
            releaseThrough(cutoff);
            discardPositionsBefore(cutoff);
        }

    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || esp.getValue() == 0 || !shouldRender) return;
        EntityLivingBase currentTarget = target;
        Vec3 truePosition = serverPosition.current();
        if (currentTarget == null || truePosition == null || !isValidTarget(currentTarget)) return;

        renderedServerPosition = interpolate(renderedServerPosition, truePosition, 0.35D);
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        double x = renderedServerPosition.xCoord - renderManager.getRenderPosX();
        double y = renderedServerPosition.yCoord - renderManager.getRenderPosY();
        double z = renderedServerPosition.zCoord - renderManager.getRenderPosZ();
        Color color = new Color(espColor.getValue(), true);

        if (esp.getValue() == 1) {
            AxisAlignedBB box = boxAt(currentTarget, renderedServerPosition)
                    .offset(-renderManager.getRenderPosX(), -renderManager.getRenderPosY(), -renderManager.getRenderPosZ());
            RenderUtil.enableRenderState();
            RenderUtil.drawFilledBox(box, color.getRed(), color.getGreen(), color.getBlue());
            RenderUtil.drawBoundingBox(box, color.getRed(), color.getGreen(), color.getBlue(), 180, 1.0F);
            RenderUtil.disableRenderState();
            return;
        }

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            mc.entityRenderer.disableLightmap();
            GL11.glColor4f(
                    color.getRed() / 255.0F,
                    color.getGreen() / 255.0F,
                    color.getBlue() / 255.0F,
                    color.getAlpha() / 255.0F);
            if (esp.getValue() == 3) {
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glLineWidth(wireframeWidth.getValue());
            }
            mc.getRenderManager().doRenderEntity(
                    currentTarget,
                    x,
                    y,
                    z,
                    currentTarget.prevRotationYaw
                            + (currentTarget.rotationYaw - currentTarget.prevRotationYaw) * event.getPartialTicks(),
                    event.getPartialTicks(),
                    true);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    @EventTarget
    public void onWorldChange(LoadWorldEvent event) {
        // Queued packets belong to the previous NetHandler and must never enter
        // a replacement world.
        resetWithoutReplay();
    }

    private EntityLivingBase resolveTarget(long now) {
        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (aura != null && aura.isEnabled() && isValidTarget(aura.getTarget())) {
            return aura.getTarget();
        }
        if (now - manualTargetTime <= MANUAL_TARGET_TIMEOUT_MS && isValidTarget(manualTarget)) {
            return manualTarget;
        }
        return null;
    }

    private boolean shouldBacktrack(EntityLivingBase candidate, long now) {
        return candidate != null
                && now >= nextCycleAllowed
                && !mc.isSingleplayer()
                && mc.getCurrentServerData() != null
                && mc.thePlayer != null
                && mc.thePlayer.getHealth() > 0.0F
                && mc.thePlayer.ticksExisted > 20
                && mc.playerController != null
                && mc.playerController.getCurrentGameType() != GameType.SPECTATOR
                && isValidTarget(candidate)
                && (candidate.getHealth() > 0.0F || Float.isNaN(candidate.getHealth()));
    }

    private boolean isValidTarget(EntityLivingBase candidate) {
        if (candidate == null || mc.thePlayer == null || mc.theWorld == null) return false;
        if (!TeamUtil.isAllowedTarget(candidate) || !TeamUtil.isEntityLoaded(candidate)) return false;
        if (candidate == mc.thePlayer || candidate == mc.thePlayer.ridingEntity || candidate.isDead
                || candidate.deathTime > 0) return false;
        Entity view = mc.getRenderViewEntity();
        if (candidate == view || view != null && candidate == view.ridingEntity) return false;
        if (candidate instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) candidate;
            return !TeamUtil.isFriend(player)
                    && !TeamUtil.isSameTeam(player)
                    && !TeamUtil.isBot(player);
        }
        return true;
    }

    private void initializeServerPosition(EntityLivingBase candidate) {
        serverPosition.clear();
        positions.clear();
        renderedServerPosition = null;
        if (candidate == null) return;
        serverPosition.setEncoded(candidate.serverPosX, candidate.serverPosY, candidate.serverPosZ);
        renderedServerPosition = serverPosition.current();
    }

    private void updateServerPosition(Packet<?> packet, EntityLivingBase currentTarget, long now) {
        if (packet instanceof S14PacketEntity) {
            S14PacketEntity movement = (S14PacketEntity) packet;
            if (movement.getEntity(mc.theWorld) != currentTarget) return;
            Vec3 updated = serverPosition.add(
                    movement.func_149062_c(), movement.func_149061_d(), movement.func_149064_e());
            positions.offer(new PositionSnapshot(updated, now));
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleport = (S18PacketEntityTeleport) packet;
            if (teleport.getEntityId() != currentTarget.getEntityId()) return;
            Vec3 updated = serverPosition.setEncoded(teleport.getX(), teleport.getY(), teleport.getZ());
            positions.offer(new PositionSnapshot(updated, now));
        }
    }

    private boolean hasConflictingInboundDelay() {
        if (Myau.delayManager != null
                && Myau.delayManager.getDelayModule() != DelayModules.NONE) return true;
        PacketDelay packetDelay = (PacketDelay) Myau.moduleManager.modules.get(PacketDelay.class);
        return packetDelay != null && packetDelay.isEnabled() && packetDelay.inbound.getValue();
    }

    static boolean isImmediatePacket(Packet<?> packet, EntityLivingBase currentTarget) {
        if (isWorldTransitionPacket(packet)
                || packet instanceof S3BPacketScoreboardObjective
                || packet instanceof S3CPacketUpdateScore
                || packet instanceof S3DPacketDisplayScoreboard
                || packet instanceof S3EPacketTeams
                || packet instanceof S38PacketPlayerListItem
                || isWorldStatePacket(packet)) return true;
        if (packet instanceof S02PacketChat || packet instanceof S01PacketPong) return true;
        if (packet instanceof S29PacketSoundEffect) {
            String name = ((S29PacketSoundEffect) packet).getSoundName();
            if (name != null && (name.contains("game.player.hurt") || name.contains("game.player.die"))) {
                return true;
            }
        }
        if (packet instanceof S19PacketEntityStatus && currentTarget != null) {
            return ((S19PacketEntityStatus) packet).getEntity(mc.theWorld) == currentTarget;
        }
        return packet instanceof S1CPacketEntityMetadata
                && currentTarget != null
                && ((S1CPacketEntityMetadata) packet).getEntityId() == currentTarget.getEntityId();
    }

    static boolean packetRequiresFlush(Packet<?> packet, EntityLivingBase currentTarget) {
        if (isWorldTransitionPacket(packet)) return true;
        if (packet instanceof S06PacketUpdateHealth) {
            return ((S06PacketUpdateHealth) packet).getHealth() <= 0.0F;
        }
        if (packetRemovesTarget(packet, currentTarget)) return true;
        if (packet instanceof S1CPacketEntityMetadata && currentTarget != null) {
            S1CPacketEntityMetadata metadata = (S1CPacketEntityMetadata) packet;
            if (metadata.getEntityId() != currentTarget.getEntityId()) return false;
            for (DataWatcher.WatchableObject value : metadata.func_149376_c()) {
                if (value.getDataValueId() == 6 && value.getObject() instanceof Number) {
                    float health = ((Number) value.getObject()).floatValue();
                    if (!Float.isNaN(health) && health <= 0.0F) return true;
                }
            }
        }
        return false;
    }

    private static boolean isWorldTransitionPacket(Packet<?> packet) {
        return packet instanceof S01PacketJoinGame
                || packet instanceof S07PacketRespawn
                || packet instanceof S08PacketPlayerPosLook;
    }

    private static boolean isWorldStatePacket(Packet<?> packet) {
        return packet instanceof S21PacketChunkData
                || packet instanceof S22PacketMultiBlockChange
                || packet instanceof S23PacketBlockChange
                || packet instanceof S26PacketMapChunkBulk;
    }

    private static boolean packetRemovesTarget(Packet<?> packet, EntityLivingBase currentTarget) {
        if (!(packet instanceof S13PacketDestroyEntities) || currentTarget == null) return false;
        for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
            if (id == currentTarget.getEntityId()) return true;
        }
        return false;
    }

    private void releaseThrough(long cutoff) {
        TimedPacket next;
        while ((next = packets.peek()) != null && next.time <= cutoff) {
            packets.poll();
            replay(next);
        }
    }

    private void drainAllPackets() {
        TimedPacket next;
        while ((next = packets.poll()) != null) replay(next);
    }

    @SuppressWarnings("unchecked")
    private void replay(TimedPacket delayed) {
        if (mc.theWorld == delayed.world && mc.getNetHandler() == delayed.netHandler) {
            ((Packet<INetHandlerPlayClient>) delayed.packet).processPacket(delayed.netHandler);
        }
    }

    private void discardPositionsBefore(long cutoff) {
        PositionSnapshot next;
        while ((next = positions.peek()) != null && next.time < cutoff) positions.poll();
    }

    private long findRangeCutoff(
            Iterable<PositionSnapshot> history,
            EntityLivingBase currentTarget,
            double minimum,
            double maximum
    ) {
        return findFirstTimestampInWindow(
                history,
                snapshot -> RotationUtil.distanceToBox(boxAt(currentTarget, snapshot)),
                minimum,
                maximum);
    }

    private void resetCycle(long now, boolean applyCooldown) {
        boolean hadCycle = cycleActive;
        positions.clear();
        cycleStarted = 0L;
        cycleActive = false;
        shouldRender = false;
        if (applyCooldown && hadCycle) {
            nextCycleAllowed = now + Math.max(0, nextDelay.getValue());
            selectedDelay = chooseDelay(minDelay.getValue(), maxDelay.getValue());
        }
    }

    private void resetWithoutReplay() {
        packets.clear();
        positions.clear();
        serverPosition.clear();
        target = null;
        manualTarget = null;
        manualTargetTime = 0L;
        pendingFlush = false;
        pendingTargetReset = false;
        cycleStarted = 0L;
        nextCycleAllowed = 0L;
        cycleActive = false;
        shouldRender = false;
        renderedServerPosition = null;
    }

    static int chooseDelay(int first, int second) {
        int minimum = Math.min(first, second);
        int maximum = Math.max(first, second);
        if (minimum == maximum) return minimum;
        return ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
    }

    static boolean inDistanceWindow(double distance, double first, double second) {
        double minimum = Math.min(first, second);
        double maximum = Math.max(first, second);
        return distance >= minimum && distance <= maximum;
    }

    static boolean pulseExpired(boolean active, long started, long now, int delay) {
        return active && now - started >= Math.max(0, delay);
    }

    static long smoothCutoff(long now, int delay) {
        return now - Math.max(0, delay);
    }

    static long findFirstTimestampInWindow(
            Iterable<PositionSnapshot> history,
            ToDoubleFunction<Vec3> distance,
            double minimum,
            double maximum
    ) {
        for (PositionSnapshot snapshot : history) {
            if (inDistanceWindow(distance.applyAsDouble(snapshot.position), minimum, maximum)) {
                return snapshot.time;
            }
        }
        return -1L;
    }

    static Vec3 interpolate(Vec3 current, Vec3 target, double factor) {
        if (current == null) return target;
        return new Vec3(
                current.xCoord + (target.xCoord - current.xCoord) * factor,
                current.yCoord + (target.yCoord - current.yCoord) * factor,
                current.zCoord + (target.zCoord - current.zCoord) * factor);
    }

    private static AxisAlignedBB boxAt(EntityLivingBase entity, Vec3 position) {
        return entity.getEntityBoundingBox().offset(
                position.xCoord - entity.posX,
                position.yCoord - entity.posY,
                position.zCoord - entity.posZ);
    }

    @Override
    public void verifyValue(String value) {
        if (minDelay.getName().equals(value) && minDelay.getValue() > maxDelay.getValue()) {
            maxDelay.setValue(minDelay.getValue());
        } else if (maxDelay.getName().equals(value) && maxDelay.getValue() < minDelay.getValue()) {
            minDelay.setValue(maxDelay.getValue());
        } else if (minDistance.getName().equals(value) && minDistance.getValue() > maxDistance.getValue()) {
            maxDistance.setValue(minDistance.getValue());
        } else if (maxDistance.getName().equals(value) && maxDistance.getValue() < minDistance.getValue()) {
            minDistance.setValue(maxDistance.getValue());
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{selectedDelay + "ms"};
    }

    static final class ServerPositionTracker {
        private int x;
        private int y;
        private int z;
        private boolean initialized;

        synchronized Vec3 setEncoded(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            initialized = true;
            return currentUnsafe();
        }

        synchronized Vec3 add(int x, int y, int z) {
            this.x += x;
            this.y += y;
            this.z += z;
            initialized = true;
            return currentUnsafe();
        }

        synchronized Vec3 current() {
            return initialized ? currentUnsafe() : null;
        }

        synchronized void clear() {
            x = y = z = 0;
            initialized = false;
        }

        private Vec3 currentUnsafe() {
            return new Vec3(x / 32.0D, y / 32.0D, z / 32.0D);
        }
    }

    private static final class TimedPacket {
        private final Packet<?> packet;
        private final long time;
        private final WorldClient world;
        private final INetHandlerPlayClient netHandler;

        private TimedPacket(Packet<?> packet, long time, WorldClient world, INetHandlerPlayClient netHandler) {
            this.packet = packet;
            this.time = time;
            this.world = world;
            this.netHandler = netHandler;
        }
    }

    static final class PositionSnapshot {
        private final Vec3 position;
        private final long time;

        PositionSnapshot(Vec3 position, long time) {
            this.position = position;
            this.time = time;
        }
    }
}

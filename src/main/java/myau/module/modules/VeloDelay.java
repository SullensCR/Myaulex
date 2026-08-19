package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.PacketArrivalEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S47PacketPlayerListHeaderFooter;
import org.lwjgl.input.Mouse;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * GNUClient-style knockback delay. A session begins on self velocity and
 * temporarily freezes ordinary inbound play packets while the player is
 * airborne and has a valid nearby player target.
 */
public final class VeloDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Kept as delay-ms for compatibility; it is the maximum packet age. */
    public final IntProperty delay = new IntProperty("delay-ms", 200, 50, 1000);
    public final FloatProperty distance = new FloatProperty("distance", 6.0F, 3.0F, 12.0F);
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final BooleanProperty inAir = new BooleanProperty("in-air", true);
    public final BooleanProperty lookingAtPlayer = new BooleanProperty("looking-at-player", false);
    public final BooleanProperty requireLeftMouse = new BooleanProperty("require-left-mouse", false);

    private final ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<>();
    private final ThreadLocal<Boolean> replaying = ThreadLocal.withInitial(() -> false);
    private volatile boolean sessionActive;
    private boolean discardOnDisable;

    public VeloDelay() {
        super("VeloDelay", false, false, "Delays inbound packets during a knockback window.");
    }

    /** True while this module owns the inbound stream for an active session. */
    public static boolean isOwningInboundQueue() {
        VeloDelay module = activeInstance();
        return module != null && module.sessionActive;
    }

    /** Backtrack uses this as a fast conflict check before its own tick logic. */
    public static boolean isBlockingBacktrack() {
        return isOwningInboundQueue();
    }

    private static VeloDelay activeInstance() {
        if (Myau.moduleManager == null) return null;
        Module module = Myau.moduleManager.modules.get(VeloDelay.class);
        return module instanceof VeloDelay && module.isEnabled() ? (VeloDelay) module : null;
    }

    @Override
    public void onEnabled() {
        sessionActive = false;
        discardOnDisable = false;
        queue.clear();
    }

    @Override
    public void onDisabled() {
        if (discardOnDisable) {
            sessionActive = false;
            queue.clear();
            discardOnDisable = false;
            return;
        }
        flushInboundAndClear();
    }

    /**
     * S12 is observed before DelayManager can claim the inbound packet. This
     * lets a valid knockback session take ownership of the stream immediately.
     */
    @EventTarget(Priority.HIGHEST)
    public void onPacketArrival(PacketArrivalEvent event) {
        if (!isEnabled() || replaying.get() || sessionActive) return;
        if (!(event.getPacket() instanceof S12PacketEntityVelocity)) return;
        tryStartSession(event.getPacket(), event.getWorld());
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || replaying.get() || event.getType() != EventType.RECEIVE) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof S08PacketPlayerPosLook) {
            if (sessionActive) flushInboundAndClear();
            return;
        }

        if (isImmediatePacket(packet)) return;

        if (!sessionActive && packet instanceof S12PacketEntityVelocity) {
            tryStartSession(packet, mc.theWorld);
        }
        if (!sessionActive || mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) return;

        event.setCancelled(true);
        queue.offer(new Entry(packet, System.currentTimeMillis(), mc.theWorld, mc.getNetHandler()));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || !sessionActive) return;

        EntityPlayerSP player = mc.thePlayer;
        WorldClient world = mc.theWorld;
        if (player == null || world == null || mc.getNetHandler() == null || player.isDead) {
            flushInboundAndClear();
            return;
        }

        if (conditionsFailureReason(player, world) != null) {
            flushInboundAndClear();
            return;
        }

        long now = System.currentTimeMillis();
        Entry next;
        while ((next = queue.peek()) != null && now - next.created >= delay.getValue()) {
            queue.poll();
            replay(next);
        }
    }

    @EventTarget
    public void onWorldChange(LoadWorldEvent event) {
        discardOnDisable = true;
        sessionActive = false;
        queue.clear();
        if (isEnabled()) {
            setEnabled(false);
        } else {
            discardOnDisable = false;
        }
    }

    private boolean tryStartSession(Packet<?> packet, WorldClient arrivalWorld) {
        if (sessionActive || !(packet instanceof S12PacketEntityVelocity)) return false;

        EntityPlayerSP player = mc.thePlayer;
        WorldClient world = mc.theWorld;
        if (player == null || world == null || world != arrivalWorld || mc.getNetHandler() == null) return false;

        S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
        if (velocity.getEntityID() != player.getEntityId()) return false;
        if (conditionsFailureReason(player, world) != null) return false;
        if (chance.getValue() < 100 && Math.random() * 100.0 >= chance.getValue()) return false;

        startSession();
        return true;
    }

    private void startSession() {
        sessionActive = true;
        flushCompetingInboundQueues();
    }

    private void flushCompetingInboundQueues() {
        if (Myau.moduleManager != null) {
            PacketDelay packetDelay = (PacketDelay) Myau.moduleManager.modules.get(PacketDelay.class);
            if (packetDelay != null) packetDelay.flushInboundForVeloDelay();

            Backtrack backtrack = (Backtrack) Myau.moduleManager.modules.get(Backtrack.class);
            if (backtrack != null) backtrack.flushForVeloDelay();
        }

        if (Myau.delayManager != null && Myau.delayManager.getDelayModule() != myau.enums.DelayModules.NONE) {
            Myau.delayManager.setDelayState(false, Myau.delayManager.getDelayModule());
        }
    }

    private String conditionsFailureReason(EntityPlayerSP player, WorldClient world) {
        if (player == null || world == null) return "null";
        if (findTarget(distance.getValue()) == null) return "no target";
        if (inAir.getValue() && player.onGround) return "on ground";
        if (lookingAtPlayer.getValue() && getMouseOverTarget(distance.getValue()) == null) {
            return "not looking at player";
        }
        if (requireLeftMouse.getValue() && !Mouse.isButtonDown(0)) return "lmb";
        return null;
    }

    private EntityPlayer findTarget(double maxDistance) {
        EntityPlayer mouseOver = getMouseOverTarget(maxDistance);
        if (mouseOver != null) return mouseOver;
        if (mc.theWorld == null) return null;

        EntityPlayer closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (EntityPlayer candidate : mc.theWorld.playerEntities) {
            if (!isValidTarget(candidate, maxDistance)) continue;
            double candidateDistance = RotationUtil.distanceToEntity(candidate);
            if (candidateDistance < closestDistance) {
                closestDistance = candidateDistance;
                closest = candidate;
            }
        }
        return closest;
    }

    private EntityPlayer getMouseOverTarget(double maxDistance) {
        if (mc.objectMouseOver == null || !(mc.objectMouseOver.entityHit instanceof EntityPlayer)) return null;
        EntityPlayer candidate = (EntityPlayer) mc.objectMouseOver.entityHit;
        return isValidTarget(candidate, maxDistance) ? candidate : null;
    }

    private boolean isValidTarget(EntityPlayer candidate, double maxDistance) {
        return candidate != null
                && candidate != mc.thePlayer
                && !candidate.isDead
                && candidate.deathTime == 0
                && TeamUtil.isEntityLoaded(candidate)
                && !TeamUtil.isFriend(candidate)
                && !TeamUtil.isSameTeam(candidate)
                && !TeamUtil.isBot(candidate)
                && RotationUtil.distanceToEntity(candidate) <= maxDistance;
    }

    /** Packets that must remain live while a session is active. */
    static boolean isImmediatePacket(Packet<?> packet) {
        return packet instanceof S01PacketJoinGame
                || packet instanceof S07PacketRespawn
                || packet instanceof S08PacketPlayerPosLook
                || packet instanceof S38PacketPlayerListItem
                || packet instanceof S47PacketPlayerListHeaderFooter
                || packet instanceof S21PacketChunkData
                || packet instanceof S22PacketMultiBlockChange
                || packet instanceof S23PacketBlockChange
                || packet instanceof S26PacketMapChunkBulk;
    }

    /** Compatibility/test helper: ordinary inbound packets are delayable. */
    static boolean shouldDelayInbound(Packet<?> packet) {
        return !isImmediatePacket(packet);
    }

    static boolean isSelfVelocity(Packet<?> packet, int entityId) {
        return packet instanceof S12PacketEntityVelocity
                && ((S12PacketEntityVelocity) packet).getEntityID() == entityId;
    }

    private void flushInboundAndClear() {
        sessionActive = false;
        Entry entry;
        while ((entry = queue.poll()) != null) replay(entry);
    }

    @SuppressWarnings("unchecked")
    private void replay(Entry entry) {
        if (mc.theWorld != entry.world || mc.getNetHandler() != entry.netHandler) return;
        replaying.set(true);
        try {
            ((Packet<INetHandlerPlayClient>) entry.packet).processPacket(entry.netHandler);
        } finally {
            replaying.set(false);
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{delay.getValue() + "ms"};
    }

    private static final class Entry {
        private final Packet<?> packet;
        private final long created;
        private final WorldClient world;
        private final INetHandlerPlayClient netHandler;

        private Entry(Packet<?> packet, long created, WorldClient world, INetHandlerPlayClient netHandler) {
            this.packet = packet;
            this.created = created;
            this.world = world;
            this.netHandler = netHandler;
        }
    }
}

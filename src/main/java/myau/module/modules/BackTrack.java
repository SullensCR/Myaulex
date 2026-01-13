package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.module.modules.KillAura;
import myau.module.modules.Scaffold;
import myau.module.modules.HUD;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.PacketUtil;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.*;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty legit = new BooleanProperty("legit", false);
    public final BooleanProperty releaseOnHit = new BooleanProperty("release-on-hit", true, this.legit::getValue);
    public final IntProperty delay = new IntProperty("delay", 400, 0, 1000);
    public final FloatProperty hitRange = new FloatProperty("range", 3.0F, 3.0F, 10.0F);
    public final BooleanProperty adaptive = new BooleanProperty("adaptive", true);
    public final BooleanProperty useKillAura = new BooleanProperty("use-killaura", true);
    public final BooleanProperty botCheck = new BooleanProperty("bot-check", true);
    public final BooleanProperty teams = new BooleanProperty("teams", true);
    public final ModeProperty showPosition = new ModeProperty("show-position", 1, new String[]{"NONE", "DEFAULT", "HUD"});

    private final Queue<Packet> incomingPackets = new LinkedList<>();
    private final Queue<Packet> outgoingPackets = new LinkedList<>();
    private final Map<Integer, Vec3> realPositions = new HashMap<>();

    private KillAura killAura;
    private EntityLivingBase target;
    private Vec3 lastRealPos;
    private Vec3 currentRealPos;
    private long lastReleaseTime;

    public BackTrack() {
        super("BackTrack", false);
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (entityLivingBase == mc.thePlayer || entityLivingBase == mc.thePlayer.ridingEntity) {
            return false;
        } else if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
            return false;
        } else if (entityLivingBase.deathTime > 0) {
            return false;
        } else if (entityLivingBase instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entityLivingBase;
            
            if (TeamUtil.isFriend(player)) {
                return false;
            } else {
                return (!this.teams.getValue() || !TeamUtil.isSameTeam(player)) 
                    && (!this.botCheck.getValue() || !TeamUtil.isBot(player));
            }
        } else {
            return true;
        }
    }

    private EntityLivingBase getTargetEntity() {
        if (this.useKillAura.getValue() && this.killAura != null) {
            return this.killAura.getTarget();
        }
        
        EntityLivingBase closest = null;
        double closestDist = this.hitRange.getValue();
        
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityLivingBase)) {
                continue;
            }
            
            EntityLivingBase entity = (EntityLivingBase) obj;
            
            if (!this.isValidTarget(entity)) {
                continue;
            }
            
            double dist = mc.thePlayer.getDistanceToEntity(entity);
            if (dist < closestDist) {
                closest = entity;
                closestDist = dist;
            }
        }
        
        return closest;
    }

    private boolean shouldQueue() {
        if (this.target == null) {
            return false;
        }

        Vec3 real = this.realPositions.get(this.target.getEntityId());
        if (real == null) {
            return false;
        }

        if (!this.adaptive.getValue()) {
            double distReal = mc.thePlayer.getDistance(
                    real.xCoord, real.yCoord, real.zCoord);
            double distCurrent = mc.thePlayer.getDistanceToEntity(this.target);

            return distReal + 0.15 < distCurrent
                    && System.currentTimeMillis() - this.lastReleaseTime <= this.delay.getValue();
        }

        double distReal = mc.thePlayer.getDistance(
                real.xCoord, real.yCoord, real.zCoord
        );
        double distCurrent = mc.thePlayer.getDistanceToEntity(this.target);

        return distReal < distCurrent;
    }

    private void releaseIncoming() {
        if (mc.getNetHandler() == null) {
            return;
        }

        while (!this.incomingPackets.isEmpty()) {
            this.incomingPackets.poll().processPacket(mc.getNetHandler());
        }
        this.lastReleaseTime = System.currentTimeMillis();
    }

    private void releaseOutgoing() {
        while (!this.outgoingPackets.isEmpty()) {
            PacketUtil.sendPacketNoEvent(this.outgoingPackets.poll());
        }
        this.lastReleaseTime = System.currentTimeMillis();
    }

    private void releaseAll() {
        this.releaseIncoming();
        this.releaseOutgoing();
    }

    private boolean blockIncoming(Packet<?> p) {
        if (!this.adaptive.getValue()) {
            if (p instanceof S12PacketEntityVelocity
                    || p instanceof S27PacketExplosion) {
                return false;
            }

            return p instanceof S14PacketEntity
                    || p instanceof S18PacketEntityTeleport
                    || p instanceof S19PacketEntityHeadLook
                    || p instanceof S0FPacketSpawnMob;
        }

        return p instanceof S12PacketEntityVelocity
                || p instanceof S27PacketExplosion
                || p instanceof S14PacketEntity
                || p instanceof S18PacketEntityTeleport
                || p instanceof S19PacketEntityHeadLook
                || p instanceof S0FPacketSpawnMob;
    }

    private boolean blockOutgoing(Packet<?> p) {
        return p instanceof C03PacketPlayer
                || p instanceof C02PacketUseEntity
                || p instanceof C0APacketAnimation
                || p instanceof C0BPacketEntityAction
                || p instanceof C08PacketPlayerBlockPlacement
                || p instanceof C07PacketPlayerDigging
                || p instanceof C09PacketHeldItemChange
                || p instanceof C00PacketKeepAlive
                || p instanceof C01PacketPing;
    }

    private void handleIncoming(PacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            Entity e = p.getEntity(mc.theWorld);
            if (e == null) {
                return;
            }

            int id = e.getEntityId();
            Vec3 pos = this.realPositions.getOrDefault(id, new Vec3(0, 0, 0));

            this.realPositions.put(
                    id,
                    pos.addVector(
                            p.func_149062_c() / 32.0,
                            p.func_149061_d() / 32.0,
                            p.func_149064_e() / 32.0
                    )
            );
        }

        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
            this.realPositions.put(
                    p.getEntityId(),
                    new Vec3(p.getX() / 32.0, p.getY() / 32.0, p.getZ() / 32.0)
            );
        }

        if (this.shouldQueue()) {
            if (this.blockIncoming(packet)) {
                this.incomingPackets.add(packet);
                event.setCancelled(true);
            }
        } else {
            this.releaseIncoming();
        }
    }

    private void handleOutgoing(PacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (!this.legit.getValue()) {
            return;
        }

        if (this.shouldQueue()) {
            if (this.blockOutgoing(packet)) {
                this.outgoingPackets.add(packet);
                event.setCancelled(true);
            }
        } else {
            this.releaseOutgoing();
        }
    }

    @Override
    public void onEnabled() {
        Module m = Myau.moduleManager.getModule(KillAura.class);
        if (m instanceof KillAura) {
            this.killAura = (KillAura) m;
        }

        this.incomingPackets.clear();
        this.outgoingPackets.clear();
        this.realPositions.clear();
        this.lastRealPos = null;
        this.currentRealPos = null;
        this.lastReleaseTime = System.currentTimeMillis();
    }

    @Override
    public void onDisabled() {
        this.releaseAll();
        this.incomingPackets.clear();
        this.outgoingPackets.clear();
        this.realPositions.clear();
        this.lastRealPos = null;
        this.currentRealPos = null;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        Module scaffold = Myau.moduleManager.getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            this.releaseAll();
            this.incomingPackets.clear();
            this.outgoingPackets.clear();
            return;
        }

        if (this.useKillAura.getValue() && this.killAura != null) {
            this.target = this.killAura.getTarget();
        }

        if (event.getType() == EventType.RECEIVE) {
            this.handleIncoming(event);
        } else if (event.getType() == EventType.SEND) {
            this.handleOutgoing(event);
        }
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }

        switch (event.getType()) {
            case PRE:
                EntityLivingBase newTarget = this.getTargetEntity();
                
                if (this.target != newTarget) {
                    this.releaseAll();
                    this.lastRealPos = null;
                    this.currentRealPos = null;
                }
                
                this.target = newTarget;

                if (this.target == null) {
                    return;
                }

                Vec3 real = this.realPositions.get(this.target.getEntityId());
                if (real == null) {
                    return;
                }

                double distReal = mc.thePlayer.getDistance(real.xCoord, real.yCoord, real.zCoord);
                double distCurrent = mc.thePlayer.getDistanceToEntity(this.target);

                if (mc.thePlayer.maxHurtTime > 0 && mc.thePlayer.hurtTime == mc.thePlayer.maxHurtTime) {
                    this.releaseAll();
                }

                if (distReal > this.hitRange.getValue() || System.currentTimeMillis() - this.lastReleaseTime > this.delay.getValue()) {
                    this.releaseAll();
                }

                if (this.adaptive.getValue()) {
                    if (distCurrent <= distReal) {
                        this.releaseAll();
                    }

                    if (this.lastRealPos != null) {
                        double lastDist = mc.thePlayer.getDistance(
                                this.lastRealPos.xCoord,
                                this.lastRealPos.yCoord,
                                this.lastRealPos.zCoord
                        );

                        if (distReal < lastDist) {
                            this.releaseAll();
                        }
                    }
                }

                if (this.legit.getValue() && this.releaseOnHit.getValue() && this.target.hurtTime == 1) {
                    this.releaseAll();
                }
                break;
            case POST:
                Vec3 savedPosition = this.realPositions.get(this.target != null ? this.target.getEntityId() : -1);
                if (this.currentRealPos == null) {
                    this.lastRealPos = savedPosition;
                } else {
                    this.lastRealPos = this.currentRealPos;
                }
                this.currentRealPos = savedPosition;
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        if (this.showPosition.getValue() == 0) {
            return;
        }

        if (this.target == null) {
            return;
        }

        Vec3 real = this.realPositions.get(this.target.getEntityId());
        if (real == null || this.lastRealPos == null || this.currentRealPos == null) {
            return;
        }

        Color color = new Color(-1);
        switch (this.showPosition.getValue()) {
            case 1:
                if (this.target instanceof EntityPlayer) {
                    color = TeamUtil.getTeamColor((EntityPlayer) this.target, 1.0F);
                } else {
                    color = new Color(255, 0, 0);
                }
                break;
            case 2:
                color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
        }

        double x = RenderUtil.lerpDouble(this.currentRealPos.xCoord, this.lastRealPos.xCoord, event.getPartialTicks());
        double y = RenderUtil.lerpDouble(this.currentRealPos.yCoord, this.lastRealPos.yCoord, event.getPartialTicks());
        double z = RenderUtil.lerpDouble(this.currentRealPos.zCoord, this.lastRealPos.zCoord, event.getPartialTicks());

        float size = this.target.getCollisionBorderSize();
        AxisAlignedBB aabb = new AxisAlignedBB(
                x - (double) this.target.width / 2.0,
                y,
                z - (double) this.target.width / 2.0,
                x + (double) this.target.width / 2.0,
                y + (double) this.target.height,
                z + (double) this.target.width / 2.0
        )
                .expand(size, size, size)
                .offset(
                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                );

        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtil.disableRenderState();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%dms", this.delay.getValue())};
    }
}
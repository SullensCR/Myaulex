package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.PacketEvent;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.PacketUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;

public final class Criticals extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{
            "Packet", "NCPPacket", "OldBlocksMC", "OldBlocksMC2", "NoGround", "Hop", "TPHop",
            "Jump", "LowJump", "CustomMotion", "Visual"});
    public final IntProperty delay = new IntProperty("delay", 0, 0, 500);
    public final IntProperty hurtTime = new IntProperty("hurt-time", 10, 0, 10);
    public final FloatProperty customMotionY = new FloatProperty("custom-y", 0.2F, 0.01F, 0.42F);
    private final TimerUtil timer = new TimerUtil();

    public Criticals() {
        super("Criticals", false);
        this.markUnsafe();
    }

    @Override
    public void onEnabled() {
        if (this.mode.getValue() == 4 && mc.thePlayer != null) mc.thePlayer.jump();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isOperational() || mc.thePlayer == null || mc.theWorld == null
                || !(event.getTarget() instanceof EntityLivingBase)) return;
        EntityLivingBase target = (EntityLivingBase) event.getTarget();
        if (!mc.thePlayer.onGround || mc.thePlayer.isUsingItem() || mc.thePlayer.isInWater()
                || mc.thePlayer.isInLava() || mc.thePlayer.ridingEntity != null
                || target.hurtTime > this.hurtTime.getValue() || !this.timer.hasTimeElapsed(this.delay.getValue())) return;
        Fly fly = (Fly) Myau.moduleManager.getModule(Fly.class);
        if (fly != null && fly.isOperational()) return;

        double x = mc.thePlayer.posX;
        double y = mc.thePlayer.posY;
        double z = mc.thePlayer.posZ;
        switch (this.mode.getValue()) {
            case 0:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0625F, z, true));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                mc.thePlayer.attackTargetEntityWithCurrentItem(target);
                break;
            case 1:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.11D, z, false));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.1100013579D, z, false));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 1.3579E-6D, z, false));
                mc.thePlayer.attackTargetEntityWithCurrentItem(target);
                break;
            case 2:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.001091981D, z, true));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                break;
            case 3:
                if (mc.thePlayer.ticksExisted % 4 == 0) {
                    PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0011D, z, true));
                    PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                }
                break;
            case 5:
                mc.thePlayer.motionY = 0.1D;
                mc.thePlayer.fallDistance = 0.1F;
                mc.thePlayer.onGround = false;
                break;
            case 6:
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.02D, z, false));
                PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.01D, z, false));
                mc.thePlayer.setPosition(x, y + 0.01D, z);
                break;
            case 7: mc.thePlayer.motionY = 0.42D; break;
            case 8: mc.thePlayer.motionY = 0.3425D; break;
            case 9: mc.thePlayer.motionY = this.customMotionY.getValue(); break;
            case 10: mc.thePlayer.attackTargetEntityWithCurrentItem(target); break;
            default: break;
        }
        this.timer.reset();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isOperational() && event.getType() == EventType.SEND && this.mode.getValue() == 4
                && event.getPacket() instanceof C03PacketPlayer) {
            ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(false);
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}

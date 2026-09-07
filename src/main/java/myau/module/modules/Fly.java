package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.StrafeEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import net.minecraft.client.Minecraft;

public final class Fly extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private double verticalMotion;
    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 1.0F, 0.0F, 100.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 1.0F, 0.0F, 100.0F);

    public Fly() {
        super("Fly", false);
        this.markUnsafe();
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isOperational() || mc.thePlayer == null) return;
        if (mc.thePlayer.posY % 1.0D != 0.0D) mc.thePlayer.motionY = this.verticalMotion;
        MoveUtil.setSpeed(0.0D);
        event.setFriction((float) MoveUtil.getBaseMoveSpeed() * this.hSpeed.getValue());
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isOperational() || event.getType() != EventType.PRE || mc.thePlayer == null) return;
        this.verticalMotion = 0.0D;
        if (mc.currentScreen == null) {
            if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                this.verticalMotion += this.vSpeed.getValue() * 0.42F;
            }
            if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
                this.verticalMotion -= this.vSpeed.getValue() * 0.42F;
            }
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null) mc.thePlayer.motionY = 0.0D;
        MoveUtil.setSpeed(0.0D);
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
    }
}

package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;

/** Hylex Regen port of the supplied Rise script. */
public final class Regen extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty minimumHealth = new IntProperty("minimum-health", 15, 1, 20);
    public final IntProperty packetsPerTick = new IntProperty("packets-per-tick", 40, 10, 80);
    public final BooleanProperty yOffset = new BooleanProperty("y-offset", false);

    public Regen() {
        super("Regen", false, false, "Sends grounded position reports while below the selected health.");
        this.markUnsafe();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isOperational() || event.getType() != EventType.PRE
                || mc.thePlayer == null || mc.theWorld == null
                || !shouldRegenerate(mc.thePlayer.getHealth(), this.minimumHealth.getValue())) {
            return;
        }

        double y = mc.thePlayer.posY + (this.yOffset.getValue() ? 1.0E-8D : 0.0D);
        for (int i = 0; i < this.packetsPerTick.getValue(); i++) {
            PacketUtil.sendPacket(new C04PacketPlayerPosition(
                    mc.thePlayer.posX, y, mc.thePlayer.posZ, true));
        }
    }

    static boolean shouldRegenerate(float health, int minimumHealth) {
        return health < minimumHealth;
    }
}

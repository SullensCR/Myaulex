package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"DEFAULT", "Experimental"});
    public final PercentProperty slowdown = new PercentProperty("slowdown", 0);
    public final BooleanProperty groundOnly = new BooleanProperty("ground-only", false);
    public final BooleanProperty reachOnly = new BooleanProperty("reach-only", false);

    public KeepSprint() {
        super("Keep Sprint", false);
    }

    public boolean shouldKeepSprint() {
        if (this.groundOnly.getValue() && !mc.thePlayer.onGround) {
            return false;
        } else {
            return !this.reachOnly.getValue() || mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F)) > 3.0;
        }
    }

    public double getMotionMultiplier(double vanillaMultiplier) {
        return this.mode.getValue() == 1
                ? vanillaMultiplier
                : vanillaMultiplier + (1.0 - vanillaMultiplier) * (1.0 - this.slowdown.getValue().doubleValue() / 100.0);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}

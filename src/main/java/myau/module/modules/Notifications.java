package myau.module.modules;

import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;

/** User controls for noncritical Myaulex notifications. */
public final class Notifications extends Module {
    public final ModeProperty position = new ModeProperty("position", 0,
            new String[]{"BOTTOM_RIGHT", "TOP_RIGHT", "BOTTOM_LEFT", "TOP_LEFT"});
    public final IntProperty duration = new IntProperty("duration-ms", 2500, 500, 8000);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);

    public Notifications() {
        super("Notifications", true, true, "Controls client notifications.");
    }
}

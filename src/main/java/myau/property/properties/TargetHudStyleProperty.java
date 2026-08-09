package myau.property.properties;

import com.google.gson.JsonObject;

/** Keeps existing TargetHUD profiles readable after the style catalogue cleanup. */
public final class TargetHudStyleProperty extends ModeProperty {
    public TargetHudStyleProperty() {
        super("style", 0, new String[]{"MYAULEX", "Classic Blur", "Classic"});
    }

    @Override
    public boolean parseString(String value) {
        if ("RAVENBS-MODERN".equalsIgnoreCase(value)) {
            return this.setValue(1);
        }
        if ("RAVENBS-LEGACY".equalsIgnoreCase(value)) {
            return this.setValue(2);
        }
        return super.parseString(value);
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        if (jsonObject == null || !jsonObject.has(this.getName())) {
            return false;
        }
        if (this.parseString(jsonObject.get(this.getName()).getAsString())) {
            return true;
        }
        // Removed renderers intentionally become the replacement MYAULEX HUD,
        // even when this property instance was previously set to a Classic mode.
        return this.setValue(0);
    }
}

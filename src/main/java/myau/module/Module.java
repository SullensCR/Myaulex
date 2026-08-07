package myau.module;

import myau.Myau;
import myau.config.Config;
import myau.module.modules.GuiModule;
import myau.module.modules.HUD;
import myau.util.KeyBindUtil;

public abstract class Module {
    protected final String name;
    protected final String description;
    protected final boolean defaultEnabled;
    protected final int defaultKey;
    protected final boolean defaultHidden;
    protected boolean enabled;
    protected int key;
    protected boolean hidden;

    public Module(String name, boolean enabled) {
        this(name, enabled, false, "");
    }

    public Module(String name, boolean enabled, boolean hidden) {
        this(name, enabled, hidden, "");
    }

    public Module(String name, boolean enabled, boolean hidden, String description) {
        this.name = name;
        this.description = description;
        this.enabled = this.defaultEnabled = enabled;
        this.key = this.defaultKey = 0;
        this.hidden = this.defaultHidden = hidden;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public String formatModule() {
        return String.format(
                "%s%s &r(%s&r)",
                this.key == 0 ? "" : String.format("&l[%s] &r", KeyBindUtil.getKeyName(this.key)),
                this.name,
                this.enabled ? "&a&lON" : "&c&lOFF"
        );
    }

    public String[] getSuffix() {
        return new String[0];
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                this.onEnabled();
            } else {
                this.onDisabled();
            }
            Config.markDirty(this);
        }
    }

    public boolean toggle() {
        boolean enabled = !this.enabled;
        this.setEnabled(enabled);
        if (this.enabled == enabled) {
            if (((HUD) Myau.moduleManager.modules.get(HUD.class)).toggleSound.getValue()) {
                Myau.moduleManager.playSound();
            }
            HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
            if (hud != null && hud.toggleAlerts.getValue() && Myau.notificationManager != null && !(this instanceof GuiModule)) {
                int color = this.enabled ? hud.notificationEnabledColor.getValue() : hud.notificationDisabledColor.getValue();
                Myau.notificationManager.add(this.name + (this.enabled ? " enabled" : " disabled"), hud.notificationDuration.getValue(), color);
            }
            return true;
        } else {
            return false;
        }
    }

    public int getKey() {
        return this.key;
    }

    public void setKey(int integer) {
        if (this.key != integer) {
            this.key = integer;
            Config.markKeybindDirty();
        }
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean boolean1) {
        if (this.hidden != boolean1) {
            this.hidden = boolean1;
            Config.markDirty(this);
        }
    }

    public void onEnabled() {
    }

    public void onDisabled() {
    }

    public void verifyValue(String string) {
    }
}

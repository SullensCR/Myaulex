package myau.module;

import myau.Myau;
import myau.config.Config;
import myau.module.modules.GuiModule;
import myau.module.modules.HUD;
import myau.module.modules.Notifications;
import myau.management.UnsafeModuleAccess;
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
    private boolean unsafe;
    private boolean unsafeOperational;

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

    /** True only while this module is both requested and permitted to act. */
    public final boolean isOperational() {
        return this.unsafe ? this.unsafeOperational : this.enabled;
    }

    public final boolean isUnsafe() {
        return this.unsafe;
    }

    /** Marks a module whose effects are controlled by Disabler or the Client override. */
    protected final void markUnsafe() {
        this.unsafe = true;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (this.unsafe) {
                this.refreshUnsafeOperationalState();
            } else {
                if (enabled) {
                    this.onEnabled();
                } else {
                    this.onDisabled();
                }
            }
            Config.markDirty(this);
        }
    }

    /** Called by ModuleManager before module tick handlers run. */
    public final void refreshUnsafeOperationalState() {
        if (!this.unsafe) return;
        boolean next = this.enabled && UnsafeModuleAccess.isAllowed();
        if (next == this.unsafeOperational) return;
        this.unsafeOperational = next;
        if (next) {
            this.onEnabled();
        } else {
            this.onDisabled();
        }
    }

    public boolean toggle() {
        boolean enabled = !this.enabled;
        this.setEnabled(enabled);
        if (this.enabled == enabled) {
            if (((HUD) Myau.moduleManager.modules.get(HUD.class)).toggleSound.getValue()) {
                Myau.moduleManager.playSound(this.enabled);
            }
            if (Myau.notificationManager != null
                    && !(this instanceof GuiModule)
                    && !(this instanceof Notifications)) {
                Myau.notificationManager.addToggle(this.name, this.enabled);
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

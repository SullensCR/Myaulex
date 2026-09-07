package myau.management;

import myau.Myau;
import myau.module.modules.Disabler;

/** Central authorization check for modules tagged unsafe. */
public final class UnsafeModuleAccess {
    private UnsafeModuleAccess() {
    }

    public static boolean isAllowed() {
        if (Myau.clientSettings != null && Myau.clientSettings.isUnsafeModuleOverride()) {
            return true;
        }
        if (Myau.moduleManager == null) return false;
        Disabler disabler = (Disabler) Myau.moduleManager.getModule(Disabler.class);
        return disabler != null && disabler.isEnabled() && disabler.isSuccessful();
    }
}

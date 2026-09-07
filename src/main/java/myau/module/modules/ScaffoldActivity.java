package myau.module.modules;

import myau.Myau;
import myau.module.Module;

/** Shared activity for the mutually exclusive scaffold modules. */
public final class ScaffoldActivity {
    private ScaffoldActivity() {
    }

    public static Scaffold regular() {
        if (Myau.moduleManager == null) return null;
        Module module = Myau.moduleManager.getModule(Scaffold.class);
        return module instanceof Scaffold ? (Scaffold) module : null;
    }

    public static LegitScaffold legit() {
        if (Myau.moduleManager == null) return null;
        Module module = Myau.moduleManager.getModule(LegitScaffold.class);
        return module instanceof LegitScaffold ? (LegitScaffold) module : null;
    }

    public static void disableOther(Module requester) {
        Module other = requester instanceof LegitScaffold ? regular() : legit();
        if (other != null && other.isEnabled()) other.setEnabled(false);
    }

    public static boolean isAnyEnabled() {
        Scaffold regular = regular();
        LegitScaffold legit = legit();
        return regular != null && regular.isEnabled() || legit != null && legit.isEnabled();
    }

    public static boolean blocksSprint() {
        Scaffold regular = regular();
        LegitScaffold legit = legit();
        return regular != null && regular.blocksSprint() || legit != null && legit.blocksSprint();
    }

    public static Module activeModule() {
        LegitScaffold legit = legit();
        if (legit != null && legit.isEnabled()) return legit;
        Scaffold regular = regular();
        return regular != null && regular.isEnabled() ? regular : null;
    }

    public static int visualSlot() {
        Scaffold regular = regular();
        if (regular != null && regular.isEnabled() && regular.itemSpoof.getValue()) return regular.getSlot();
        return -1;
    }
}

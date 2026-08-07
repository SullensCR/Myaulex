package myau.module.modules;

import myau.module.Module;

public class Stasis extends Module {
    public Stasis() {
        super("Stasis", false);
    }

    public boolean isFreezing() {
        return this.isEnabled();
    }
}

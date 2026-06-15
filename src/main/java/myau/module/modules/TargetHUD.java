package myau.module.modules;

import myau.module.Module;

/**
 * Minimal stub of TargetHUD to allow the project to build.
 *
 * The original TargetHUD implementation in this workspace references many
 * optional rendering utilities and shaders that aren't present or are in the
 * process of being refactored. To produce a working build quickly we provide
 * this small placeholder implementation. If you want the full TargetHUD UI
 * restored, I can import the full implementation and add the missing
 * rendering utilities in a follow-up change.
 */
public class TargetHUD extends Module {
    public TargetHUD() {
        super("TargetHUD", false, true);
    }
}

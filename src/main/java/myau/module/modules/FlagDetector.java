package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.management.NotificationManager;
import myau.module.Module;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

/** Disables flag-prone modules after a server position correction. */
public final class FlagDetector extends Module {
    static final long AURA_DISABLE_DURATION_MILLIS = 3000L;

    private long auraReenableAtMillis = -1L;

    public FlagDetector() {
        super("FlagDetector", false, false,
                "Temporarily disables Aura or Scaffold after a server teleport correction.");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE
                || !isPlayerTeleportPacket(event.getPacket())) {
            return;
        }

        KillAura aura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
        if (aura != null && aura.isEnabled()) {
            aura.setEnabled(false);
            this.auraReenableAtMillis = System.currentTimeMillis() + AURA_DISABLE_DURATION_MILLIS;
            this.notifyDisabled(aura);
        }

        Scaffold scaffold = (Scaffold) Myau.moduleManager.getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            scaffold.setEnabled(false);
            Stasis stasis = (Stasis) Myau.moduleManager.getModule(Stasis.class);
            if (stasis != null) stasis.setEnabled(true);
            this.notifyDisabled(scaffold);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE || this.auraReenableAtMillis < 0L
                || System.currentTimeMillis() < this.auraReenableAtMillis) {
            return;
        }

        this.auraReenableAtMillis = -1L;
        KillAura aura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
        if (aura != null) aura.setEnabled(true);
    }

    static boolean isPlayerTeleportPacket(Packet<?> packet) {
        return packet instanceof S08PacketPlayerPosLook;
    }

    private void notifyDisabled(Module module) {
        if (Myau.notificationManager == null) return;
        Myau.notificationManager.add(
                NotificationManager.NotificationType.WARNING,
                "flag-detector-" + module.getName().toLowerCase(),
                "FlagDetector",
                "Anticheat flag detected, disabling " + module.getName(),
                false
        );
    }
}

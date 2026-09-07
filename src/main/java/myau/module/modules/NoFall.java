package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import net.minecraft.network.play.client.C03PacketPlayer;

/** Reports every player movement packet as grounded. */
public final class NoFall extends Module {
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"SpoofGround"});

    public NoFall() {
        super("NoFall", false, false, "Reports player movement as on ground.");
        this.markUnsafe();
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!this.isOperational() || event.getType() != EventType.SEND
                || !(event.getPacket() instanceof C03PacketPlayer)) {
            return;
        }
        ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(true);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}

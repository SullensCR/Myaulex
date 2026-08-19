package myau.events;

import myau.event.events.Event;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Fired after an external velocity impulse has actually been applied to a player.
 * Delayed packets therefore emit this event when they are replayed, not when they
 * first arrive.
 */
public final class AppliedMotionEvent implements Event {
    public enum Source {
        VELOCITY,
        EXPLOSION
    }

    private final EntityPlayer player;
    private final double motionX;
    private final double motionY;
    private final double motionZ;
    private final Source source;

    public AppliedMotionEvent(
            EntityPlayer player,
            double motionX,
            double motionY,
            double motionZ,
            Source source
    ) {
        this.player = player;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.source = source;
    }

    public EntityPlayer getPlayer() {
        return this.player;
    }

    public double getMotionX() {
        return this.motionX;
    }

    public double getMotionY() {
        return this.motionY;
    }

    public double getMotionZ() {
        return this.motionZ;
    }

    public Source getSource() {
        return this.source;
    }
}

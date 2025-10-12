package myau.events;

import net.minecraft.entity.Entity;

public class AttackEvent {
    private final Entity target;
    private boolean cancelled;

    public AttackEvent(Entity target) {
        this.target = target;
        this.cancelled = false;
    }

    public Entity getTarget() {
        return this.target;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
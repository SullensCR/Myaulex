package myau.module.modules;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.Vec3;

import java.util.function.Consumer;

/** Keeps a successful attack's interaction context until its PRE or POST reblock. */
final class ExpoAutoBlockReblock {
    private Entity attackedTarget;
    private Vec3 relativeHit;
    private boolean movementAfterAttack;
    private boolean deferred;

    void recordAttackResult(boolean attacked, Entity target, Vec3 hit) {
        this.reset();
        if (attacked) {
            this.attackedTarget = target;
            this.relativeHit = hit;
        }
    }

    Entity getAttackedTarget() {
        return this.attackedTarget;
    }

    void onMovement() {
        this.movementAfterAttack = true;
    }

    boolean isDeferred() {
        return this.deferred;
    }

    /**
     * OpenExpo AutoBlock.Q sends INTERACT_AT and INTERACT before C08 after a
     * successful hit. Keep the original hit rather than using POST's restored
     * camera rotation. A range-only Aura hit may have no ray intersection; in
     * that case wait for a real movement boundary before using the item alone.
     */
    boolean sendBlock(ItemStack heldItem, boolean targetStillValid, Consumer<Packet<?>> send) {
        if (this.attackedTarget != null) {
            if (targetStillValid && this.relativeHit != null) {
                send.accept(new C02PacketUseEntity(this.attackedTarget, this.relativeHit));
                send.accept(new C02PacketUseEntity(this.attackedTarget, C02PacketUseEntity.Action.INTERACT));
            } else if (!this.movementAfterAttack) {
                this.deferred = true;
                return false;
            }
        }
        send.accept(new C08PacketPlayerBlockPlacement(heldItem));
        this.reset();
        return true;
    }

    void reset() {
        this.attackedTarget = null;
        this.relativeHit = null;
        this.movementAfterAttack = false;
        this.deferred = false;
    }
}

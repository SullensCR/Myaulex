package myau.mixin;

import myau.Myau;
import myau.event.EventManager;
import myau.events.AppliedMotionEvent;
import myau.events.KnockbackEvent;
import myau.events.SafeWalkEvent;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {Entity.class}, priority = 9999)
public abstract class MixinEntity {
    @Unique
    private boolean myau$motionEventEmittedAtHead;
    @Unique
    private double myau$motionBeforeX;
    @Unique
    private double myau$motionBeforeY;
    @Unique
    private double myau$motionBeforeZ;

    @Shadow
    public World worldObj;
    @Shadow
    public double posX;
    @Shadow
    public double posY;
    @Shadow
    public double posZ;
    @Shadow
    public double motionX;
    @Shadow
    public double motionY;
    @Shadow
    public double motionZ;
    @Shadow
    public float rotationYaw;
    @Shadow
    public float rotationPitch;
    @Shadow
    public float prevRotationYaw;
    @Shadow
    public float prevRotationPitch;
    @Shadow
    public boolean onGround;

    @Shadow
    public boolean isRiding() {
        return false;
    }

    @Inject(
            method = {"setVelocity"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void setVelocity(double double1, double double2, double double3, CallbackInfo callbackInfo) {
        this.myau$motionEventEmittedAtHead = false;
        this.myau$motionBeforeX = this.motionX;
        this.myau$motionBeforeY = this.motionY;
        this.myau$motionBeforeZ = this.motionZ;
        if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
            KnockbackEvent event = new KnockbackEvent(double1, double2, double3);
            EventManager.call(event);
            if (event.isCancelled()) {
                callbackInfo.cancel();
                this.motionX = event.getX();
                this.motionY = event.getY();
                this.motionZ = event.getZ();
                if (this.myau$motionChanged()) {
                    this.myau$motionEventEmittedAtHead = true;
                    EventManager.call(new AppliedMotionEvent(
                            (EntityPlayer) (Object) this,
                            this.motionX,
                            this.motionY,
                            this.motionZ,
                            AppliedMotionEvent.Source.VELOCITY
                    ));
                }
            }
        }
    }

    @Inject(method = {"setVelocity"}, at = {@At("RETURN")})
    private void afterSetVelocity(double x, double y, double z, CallbackInfo callbackInfo) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof EntityPlayer && !this.myau$motionEventEmittedAtHead && this.myau$motionChanged()) {
            EventManager.call(new AppliedMotionEvent(
                    (EntityPlayer) entity,
                    this.motionX,
                    this.motionY,
                    this.motionZ,
                    AppliedMotionEvent.Source.VELOCITY
            ));
        }
        this.myau$motionEventEmittedAtHead = false;
    }

    @Unique
    private boolean myau$motionChanged() {
        return Math.abs(this.motionX - this.myau$motionBeforeX) > 1.0E-8D
                || Math.abs(this.motionY - this.myau$motionBeforeY) > 1.0E-8D
                || Math.abs(this.motionZ - this.myau$motionBeforeZ) > 1.0E-8D;
    }

    @Inject(
            method = {"setAngles"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void setAngles(CallbackInfo callbackInfo) {
        if ((Entity) ((Object) this) instanceof EntityPlayerSP && Myau.rotationManager != null && Myau.rotationManager.isRotated()) {
            callbackInfo.cancel();
        }
    }

    @ModifyVariable(
            method = {"moveEntity"},
            ordinal = 0,
            at = @At("STORE"),
            name = {"flag"}
    )
    private boolean moveEntity(boolean boolean1) {
        if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
            SafeWalkEvent event = new SafeWalkEvent(boolean1);
            EventManager.call(event);
            return event.isSafeWalk();
        } else {
            return boolean1;
        }
    }
}

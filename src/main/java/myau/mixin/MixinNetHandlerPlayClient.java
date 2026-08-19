package myau.mixin;

import myau.event.EventManager;
import myau.events.AppliedMotionEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the terrain overlay as soon as Minecraft accepts the first position
 * packet for a newly joined world. Vanilla still processes the packet normally
 * and remains responsible for marking terrain loading as complete.
 */
@SideOnly(Side.CLIENT)
@Mixin(value = NetHandlerPlayClient.class, priority = 9999)
public abstract class MixinNetHandlerPlayClient {
    @Shadow private Minecraft gameController;
    @Shadow public boolean doneLoadingTerrain;

    @Redirect(
            method = "handlePlayerPosLook",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V"
            )
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void skipTerrainScreen(
            Packet packet,
            INetHandler netHandler,
            IThreadListener threadListener
    ) {
        PacketThreadUtil.checkThreadAndEnqueue(packet, netHandler, threadListener);

        if (!this.doneLoadingTerrain && this.gameController.currentScreen instanceof GuiDownloadTerrain) {
            this.gameController.displayGuiScreen(null);
            this.gameController.setIngameFocus();
        }
    }

    @Inject(method = "handleExplosion", at = @At("RETURN"))
    private void afterExplosionMotion(S27PacketExplosion packet, CallbackInfo callbackInfo) {
        if (this.gameController.thePlayer == null) return;
        if (packet.func_149149_c() == 0.0F
                && packet.func_149144_d() == 0.0F
                && packet.func_149147_e() == 0.0F) {
            return;
        }
        EventManager.call(new AppliedMotionEvent(
                this.gameController.thePlayer,
                this.gameController.thePlayer.motionX,
                this.gameController.thePlayer.motionY,
                this.gameController.thePlayer.motionZ,
                AppliedMotionEvent.Source.EXPLOSION
        ));
    }
}

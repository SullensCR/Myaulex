package myau.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
}

package myau.mixin;

import myau.Myau;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Handles inherited screen mouse callbacks that GuiChat does not override. */
@SideOnly(Side.CLIENT)
@Mixin(value = {GuiScreen.class}, priority = 9999)
public abstract class MixinGuiScreen {
    @Inject(method = {"mouseClickMove(IIIJ)V"}, at = {@At("HEAD")}, cancellable = true)
    private void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
                                long timeSinceLastClick, CallbackInfo callbackInfo) {
        if ((Object) this instanceof GuiChat && clickedMouseButton == 0 && Myau.hudEditManager != null
                && Myau.hudEditManager.handleMouseDragged(mouseX, mouseY)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = {"mouseReleased(III)V"}, at = {@At("HEAD")}, cancellable = true)
    private void mouseReleased(int mouseX, int mouseY, int state, CallbackInfo callbackInfo) {
        if ((Object) this instanceof GuiChat && Myau.hudEditManager != null
                && Myau.hudEditManager.handleMouseReleased()) {
            callbackInfo.cancel();
        }
    }
}

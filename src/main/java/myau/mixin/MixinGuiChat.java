package myau.mixin;

import myau.Myau;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes chat-screen mouse input to the temporary HUD editor when applicable. */
@SideOnly(Side.CLIENT)
@Mixin(value = {GuiChat.class}, priority = 9999)
public abstract class MixinGuiChat {
    @Inject(method = {"drawScreen"}, at = {@At("RETURN")})
    private void drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        if (Myau.hudEditManager != null) Myau.hudEditManager.renderOverlay(mouseX, mouseY);
    }

    @Inject(method = {"mouseClicked"}, at = {@At("HEAD")}, cancellable = true)
    private void mouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo callbackInfo) {
        if (Myau.hudEditManager != null
                && Myau.hudEditManager.handleMouseClicked(mouseX, mouseY, mouseButton)) {
            callbackInfo.cancel();
        }
    }

}

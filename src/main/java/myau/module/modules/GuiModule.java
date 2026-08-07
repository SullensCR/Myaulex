package myau.module.modules;

import myau.Myau;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.ui.ClickGui;
import myau.ui.ModernClickGui;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private ClickGui clickGui;
    private ModernClickGui modernClickGui;
    public final ModeProperty clickGuiStyle = new ModeProperty("clickgui-style", 1, new String[]{"OLD", "MODERN"});
    public final FloatProperty clickGuiScale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);

    public GuiModule() {
        super("ClickGui", false, true, "Opens Myaulex client settings.");
        setKey(Keyboard.KEY_RSHIFT);
    }

    public GuiScreen getSelectedGui() {
        String style = Myau.clientSettings == null
                ? this.clickGuiStyle.getModeString() : Myau.clientSettings.getClickGuiStyle();
        if ("OLD".equalsIgnoreCase(style)) {
            if (this.clickGui == null) {
                this.clickGui = new ClickGui();
            }
            return this.clickGui;
        }
        if (this.modernClickGui == null) {
            this.modernClickGui = new ModernClickGui();
        }
        return this.modernClickGui;
    }

    public void openSelectedGui() {
        mc.displayGuiScreen(this.getSelectedGui());
    }

    public void openOldGuiAfterModernFailure() {
        ChatUtil.sendFormatted(Myau.clientName + "&cModern ClickGUI could not start; opened the old GUI. Check the client log for details.&r");
        if (this.clickGui == null) {
            this.clickGui = new ClickGui();
        }
        mc.displayGuiScreen(this.clickGui);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        this.openSelectedGui();
    }
}

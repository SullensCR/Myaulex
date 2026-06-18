package myau.module.modules;

import myau.module.Module;
import myau.property.properties.ModeProperty;
import myau.ui.ClickGui;
import myau.ui.ModernClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private ClickGui clickGui;
    private ModernClickGui modernClickGui;
    public final ModeProperty clickGuiStyle = new ModeProperty("clickgui-style", 1, new String[]{"OLD", "MODERN"});

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    public GuiScreen getSelectedGui() {
        if (this.clickGuiStyle.getValue() == 0) {
            if (this.clickGui == null) {
                this.clickGui = new ClickGui();
            }
            return this.clickGui;
        }
        if (this.modernClickGui == null) {
            this.modernClickGui = ModernClickGui.getInstance();
        }
        return this.modernClickGui;
    }

    public void openSelectedGui() {
        mc.displayGuiScreen(this.getSelectedGui());
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        this.openSelectedGui();
    }
}

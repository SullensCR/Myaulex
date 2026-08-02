package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.modules.GuiModule;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class ClickGuiCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public ClickGuiCommand() {
        super(new ArrayList<>(Arrays.asList("clickgui", "gui")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        GuiModule guiModule = (GuiModule) Myau.moduleManager.getModule(GuiModule.class);
        if (guiModule == null) {
            ChatUtil.sendFormatted(String.format("%sClickGui module not found&r", Myau.clientName));
            return;
        }

        String command = args.get(0).toLowerCase(Locale.ROOT);
        if (args.size() < 2) {
            ChatUtil.sendFormatted(String.format("%sUsage: .%s open | .%s clickgui-style <old/modern>&r", Myau.clientName, command, command));
            return;
        }

        String subCommand = args.get(1).toLowerCase(Locale.ROOT);
        if (subCommand.equals("open")) {
            guiModule.openSelectedGui();
            return;
        }

        if (subCommand.equals("clickgui-style") || subCommand.equals("style")) {
            if (args.size() < 3) {
                ChatUtil.sendFormatted(String.format("%sCurrent ClickGUI style: &o%s&r", Myau.clientName, guiModule.clickGuiStyle.getModeString()));
                return;
            }

            if (!guiModule.clickGuiStyle.parseString(args.get(2))) {
                ChatUtil.sendFormatted(String.format("%sInvalid ClickGUI style (&o%s&r). Use old or modern&r", Myau.clientName, args.get(2)));
                return;
            }

            ChatUtil.sendFormatted(String.format("%sClickGUI style set to &o%s&r", Myau.clientName, guiModule.clickGuiStyle.getModeString()));
            if (mc.currentScreen instanceof myau.ui.ClickGui || mc.currentScreen instanceof myau.ui.ModernClickGui) {
                guiModule.openSelectedGui();
            }
            return;
        }

        ChatUtil.sendFormatted(String.format("%sInvalid argument (&o%s&r)&r", Myau.clientName, args.get(1)));
    }
}

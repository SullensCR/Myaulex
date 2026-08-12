package myau.module.modules;

import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.utils.NameGenerator;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Session;

import java.util.Locale;

public final class AutoRejoin extends Module {
    static final int TRIGGER_HOTBAR_SLOT = 5;
    private static final int HYLEX = 0;
    private static final int USERNAME_GENERATION_ATTEMPTS = 5;
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", HYLEX, new String[]{"Hylex"});

    public AutoRejoin() {
        super("AutoRejoin", false, false,
                "Changes to a random offline account and reconnects when Hylex signals an unban.");
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!this.isEnabled()
                || event.getType() != EventType.POST
                || mc.theWorld == null
                || mc.thePlayer == null
                || mc.isIntegratedServerRunning()) {
            return;
        }

        ServerData previousServer = mc.getCurrentServerData();
        if (previousServer == null
                || !isTriggerItem(TRIGGER_HOTBAR_SLOT,
                mc.thePlayer.inventory.getStackInSlot(TRIGGER_HOTBAR_SLOT))) {
            return;
        }

        String previousUsername = SessionManager.get().getUsername();
        mc.loadWorld(null);

        String username = generateDifferentUsername(previousUsername);
        if (username == null) {
            showMultiplayerScreen();
            return;
        }

        SessionManager.set(SessionManager.offline(username));
        Session currentSession = SessionManager.get();
        if (currentSession == null
                || previousUsername.equals(currentSession.getUsername())
                || !username.equals(currentSession.getUsername())) {
            showMultiplayerScreen();
            return;
        }

        mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(new GuiMainMenu()), mc, previousServer));
    }

    static boolean isTriggerItem(int hotbarSlot, ItemStack stack) {
        return matchesTrigger(
                hotbarSlot,
                stack != null && stack.getItem() == Items.name_tag,
                stack == null ? null : stack.getDisplayName()
        );
    }

    static boolean matchesTrigger(int hotbarSlot, boolean nameTag, String formattedDisplayName) {
        if (hotbarSlot != TRIGGER_HOTBAR_SLOT || !nameTag || formattedDisplayName == null) return false;

        String displayName = EnumChatFormatting.getTextWithoutFormattingCodes(formattedDisplayName);
        return displayName != null && displayName.toLowerCase(Locale.ROOT).contains("unban");
    }

    private static String generateDifferentUsername(String previousUsername) {
        for (int attempt = 0; attempt < USERNAME_GENERATION_ATTEMPTS; attempt++) {
            String username = NameGenerator.randomUsername();
            if (!previousUsername.equals(username)) return username;
        }
        return null;
    }

    private static void showMultiplayerScreen() {
        mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}

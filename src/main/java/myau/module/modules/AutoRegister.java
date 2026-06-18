package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.TextProperty;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.network.play.server.S02PacketChat;

public class AutoRegister extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final TextProperty password = new TextProperty("password", "a1b2c3d4f5");

    private static final String[] AUTH_REGISTER = {"register", "registrar"};
    private static final String[] AUTH_LOGIN = {"login", "logar"};

    private String lastTitleText = "";

    public AutoRegister() {
        super("AutoRegister", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE) return;

        if (event.getPacket() instanceof S02PacketChat) {
            S02PacketChat packet = (S02PacketChat) event.getPacket();
            try {
                String chatText = packet.getChatComponent().getUnformattedText().toLowerCase();
                processAuthText(chatText);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled()) return;

        try {
            // Check for title and subtitle in GuiIngame
            GuiIngame guiIngame = mc.ingameGUI;
            if (guiIngame != null) {
                String titleText = getTitleText(guiIngame);
                if (titleText != null && !titleText.isEmpty() && !titleText.equals(lastTitleText)) {
                    lastTitleText = titleText;
                    String lowerTitle = titleText.toLowerCase();
                    processAuthText(lowerTitle);
                }
            }
        } catch (Exception e) {
            // Silently handle reflection errors
        }
    }

    private String getTitleText(GuiIngame guiIngame) {
        try {
            // Try to get title text using reflection
            java.lang.reflect.Field[] fields = GuiIngame.class.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(guiIngame);
                if (value instanceof String) {
                    String str = (String) value;
                    if (!str.isEmpty()) {
                        return str;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }
        return null;
    }

    private void processAuthText(String text) {
        if (text == null || text.isEmpty()) return;

        boolean isRegister = containsKeyword(text, AUTH_REGISTER);
        boolean isLogin = containsKeyword(text, AUTH_LOGIN);

        if (isRegister) {
            String pwd = this.password.getValue();
            String command = String.format("/register %s %s", pwd, pwd);
            mc.thePlayer.sendChatMessage(command);
            ChatUtil.sendFormatted(String.format("%s&aAutoRegister: Sent registration command&r", Myau.clientName));
        } else if (isLogin) {
            String pwd = this.password.getValue();
            String command = String.format("/login %s", pwd);
            mc.thePlayer.sendChatMessage(command);
            ChatUtil.sendFormatted(String.format("%s&aAutoRegister: Sent login command&r", Myau.clientName));
        }
    }

    private boolean containsKeyword(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}







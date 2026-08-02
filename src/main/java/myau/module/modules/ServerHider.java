package myau.module.modules;

import myau.enums.ChatColors;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.TextProperty;
import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerHider extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "(?i)\\b(?:(?:[a-z0-9-]+\\.)+[a-z]{2,}|(?:\\d{1,3}\\.){3}\\d{1,3})(?::\\d{1,5})?\\b"
    );

    public final TextProperty replacement = new TextProperty("replacement", "Hidden IP");
    public final BooleanProperty scoreboard = new BooleanProperty("scoreboard", true);
    public final BooleanProperty chat = new BooleanProperty("chat", true);
    public final BooleanProperty tab = new BooleanProperty("tab", true);

    public ServerHider() {
        super("ServerHider", false);
    }

    public String replaceServer(String input) {
        if (input == null || (!this.scoreboard.getValue() && !this.chat.getValue() && !this.tab.getValue())) {
            return input;
        }

        String replacementText = Matcher.quoteReplacement(ChatColors.formatColor(this.replacement.getValue()));
        String output = input;
        String currentServer = this.getCurrentServer();
        if (currentServer != null && !currentServer.isEmpty()) {
            output = output.replaceAll("(?i)" + Pattern.quote(currentServer), replacementText);
            String serverWithoutPort = currentServer.replaceFirst(":\\d{1,5}$", "");
            if (!serverWithoutPort.equals(currentServer)) {
                output = output.replaceAll("(?i)" + Pattern.quote(serverWithoutPort), replacementText);
            }
        }
        return ADDRESS_PATTERN.matcher(output).replaceAll(replacementText);
    }

    private String getCurrentServer() {
        if (mc.isIntegratedServerRunning() || mc.getCurrentServerData() == null) {
            return null;
        }
        return mc.getCurrentServerData().serverIP;
    }
}

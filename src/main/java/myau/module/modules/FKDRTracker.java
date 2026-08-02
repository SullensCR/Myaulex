package myau.module.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.config.Config;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import myau.util.font.IFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.EnumChatFormatting;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

public class FKDRTracker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File STATS_DIR = new File(Config.CONFIG_DIR, "stats");

    public final IntProperty positionX = new IntProperty("position-x", 8, -5000, 5000);
    public final IntProperty positionY = new IntProperty("position-y", 120, -5000, 5000);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final IntProperty width = new IntProperty("width", 128, 96, 240);
    public final IntProperty padding = new IntProperty("padding", 8, 4, 24);
    public final ColorProperty backgroundColor = new ColorProperty("background-color", new Color(12, 14, 18, 172).getRGB());
    public final ColorProperty accentColor = new ColorProperty("accent-color", new Color(60, 162, 253).getRGB());
    public final ColorProperty textColor = new ColorProperty("text-color", Color.WHITE.getRGB());
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty showServer = new BooleanProperty("show-server", false);

    private String loadedServerKey = "";
    private int sessionKills;
    private int sessionDeaths;
    private int lifetimeFinalKills;
    private int lifetimeFinalDeaths;
    private String lastCountedMessage = "";
    private long lastCountedAt;

    public FKDRTracker() {
        super("FKDRTracker", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || mc.thePlayer == null || !(event.getPacket() instanceof S02PacketChat)) {
            return;
        }

        S02PacketChat packet = (S02PacketChat) event.getPacket();
        String rawMessage = packet.getChatComponent().getUnformattedText();
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return;
        }

        this.ensureStatsLoaded();
        String message = EnumChatFormatting.getTextWithoutFormattingCodes(rawMessage).trim();
        if (this.isDuplicate(message)) {
            return;
        }

        String playerName = mc.thePlayer.getName();
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        boolean finalKill = lowerMessage.endsWith("final kill")
                || lowerMessage.endsWith("final kill!")
                || lowerMessage.endsWith("abate final")
                || lowerMessage.endsWith("abate final!");
        String normalized = this.stripFinalMarker(message).replaceAll("\\s+", " ").trim();

        if (this.isPlayerKill(normalized, playerName)) {
            if (finalKill) {
                this.lifetimeFinalKills++;
                this.saveStats();
            } else {
                this.sessionKills++;
            }
            this.rememberMessage(message);
        } else if (this.isPlayerDeath(normalized, playerName)) {
            if (finalKill) {
                this.lifetimeFinalDeaths++;
                this.saveStats();
            } else {
                this.sessionDeaths++;
            }
            this.rememberMessage(message);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.showDebugInfo) {
            return;
        }

        this.ensureStatsLoaded();
        FontManager.initializeFonts();
        IFont font = FontManager.nunito18 != null ? FontManager.nunito18 : FontManager.getMinecraft();
        float scaledX = this.positionX.getValue() / this.scale.getValue();
        float scaledY = this.positionY.getValue() / this.scale.getValue();
        float panelWidth = this.width.getValue();
        float lineHeight = (float) font.height() + 3.0F;
        int lines = this.showServer.getValue() ? 6 : 5;
        float panelHeight = this.padding.getValue() * 2.0F + lineHeight * lines + 2.0F;

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
        this.drawPanel(scaledX, scaledY, panelWidth, panelHeight);

        float textX = scaledX + this.padding.getValue();
        float textY = scaledY + this.padding.getValue();
        int accent = this.accentColor.getValue();
        int text = this.textColor.getValue();

        font.drawString("Session Stats", textX, textY, accent, this.shadow.getValue());
        textY += lineHeight + 1.0F;
        if (this.showServer.getValue()) {
            font.drawString("Server: " + this.getServerKey(), textX, textY, text, this.shadow.getValue());
            textY += lineHeight;
        }
        font.drawString("Kills: " + this.sessionKills, textX, textY, text, this.shadow.getValue());
        textY += lineHeight;
        font.drawString("Deaths: " + this.sessionDeaths, textX, textY, text, this.shadow.getValue());
        textY += lineHeight;
        font.drawString(String.format(Locale.US, "Finals: %d/%d", this.lifetimeFinalKills, this.lifetimeFinalDeaths), textX, textY, text, this.shadow.getValue());
        textY += lineHeight;
        font.drawString(String.format(Locale.US, "FKDR: %.2f", this.getFkdr()), textX, textY, accent, this.shadow.getValue());
        GlStateManager.popMatrix();
    }

    private void drawPanel(float x, float y, float width, float height) {
        RenderUtil.enableRenderState();
        int bg = this.backgroundColor.getValue();
        RenderUtil.drawRoundedRect(x + 2.0F, y + 3.0F, width, height, 8.0F, RenderUtil.mergeAlpha(Color.BLACK.getRGB(), 70));
        RenderUtil.drawRoundedGradientRect(x, y, width, height, 8.0F, bg, RenderUtil.mergeAlpha(bg, 220));
        RenderUtil.drawRoundedRect(x, y, 3.0F, height, 1.5F, RenderUtil.mergeAlpha(this.accentColor.getValue(), 185));
        RenderUtil.disableRenderState();
    }

    private boolean isPlayerKill(String message, String playerName) {
        return message.matches("(?i)^.+\\b(was killed by|was slain by|was shot by|was blown up by|was fireballed by)\\s+\\Q" + playerName + "\\E$")
                || message.matches("(?i)^.+\\bwas killed by\\s+\\Q" + playerName + "\\E\\s+using magic$");
    }

    private boolean isPlayerDeath(String message, String playerName) {
        return message.matches("(?i)^\\Q" + playerName + "\\E\\s+(was killed by|was slain by|was shot by|was blown up by|was fireballed by)\\s+.+$")
                || message.matches("(?i)^\\Q" + playerName + "\\E\\s+was killed by\\s+.+\\s+using magic$");
    }

    private String stripFinalMarker(String message) {
        return message
                .replaceAll("(?i)\\s+FINAL KILL!?$", "")
                .replaceAll("(?i)\\s+ABATE FINAL!?$", "");
    }

    private boolean isDuplicate(String message) {
        long now = System.currentTimeMillis();
        return message.equals(this.lastCountedMessage) && now - this.lastCountedAt < 1500L;
    }

    private void rememberMessage(String message) {
        this.lastCountedMessage = message;
        this.lastCountedAt = System.currentTimeMillis();
    }

    private float getFkdr() {
        return this.lifetimeFinalDeaths <= 0 ? this.lifetimeFinalKills : (float) this.lifetimeFinalKills / (float) this.lifetimeFinalDeaths;
    }

    private void ensureStatsLoaded() {
        String serverKey = this.getServerKey();
        if (!serverKey.equals(this.loadedServerKey)) {
            this.loadedServerKey = serverKey;
            this.sessionKills = 0;
            this.sessionDeaths = 0;
            this.loadStats();
        }
    }

    private String getServerKey() {
        if (mc.isIntegratedServerRunning() || mc.getCurrentServerData() == null || mc.getCurrentServerData().serverIP == null) {
            return "singleplayer";
        }
        return mc.getCurrentServerData().serverIP.toLowerCase(Locale.ROOT);
    }

    private File getStatsFile() {
        String safeName = this.loadedServerKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeName.isEmpty()) {
            safeName = "unknown";
        }
        return new File(STATS_DIR, safeName + ".json");
    }

    private void loadStats() {
        this.lifetimeFinalKills = 0;
        this.lifetimeFinalDeaths = 0;
        File statsFile = this.getStatsFile();
        if (!statsFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(statsFile))) {
            JsonObject object = new JsonParser().parse(reader).getAsJsonObject();
            if (object.has("lifetimeFinalKills")) {
                this.lifetimeFinalKills = object.get("lifetimeFinalKills").getAsInt();
            }
            if (object.has("lifetimeFinalDeaths")) {
                this.lifetimeFinalDeaths = object.get("lifetimeFinalDeaths").getAsInt();
            }
        } catch (Exception ignored) {
            this.lifetimeFinalKills = 0;
            this.lifetimeFinalDeaths = 0;
        }
    }

    private void saveStats() {
        if (!STATS_DIR.exists()) {
            STATS_DIR.mkdirs();
        }

        JsonObject object = new JsonObject();
        object.addProperty("server", this.loadedServerKey);
        object.addProperty("lifetimeFinalKills", this.lifetimeFinalKills);
        object.addProperty("lifetimeFinalDeaths", this.lifetimeFinalDeaths);
        try (FileWriter writer = new FileWriter(this.getStatsFile())) {
            GSON.toJson(object, writer);
        } catch (IOException ignored) {
        }
    }
}

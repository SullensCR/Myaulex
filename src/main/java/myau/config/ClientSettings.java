package myau.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import myau.Myau;
import myau.module.Module;
import myau.module.modules.GuiModule;
import myau.property.Property;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Persistent client-owned state. This object intentionally is not a Module and
 * is never exposed through the ordinary module registry or named profiles.
 */
public final class ClientSettings {
    private static final java.util.Set<String> INTEGRATED_MODULES = new HashSet<>(Arrays.asList(
            "ClickGui", "Animations", "HUD", "TargetHUD",
            "Fpscounter", "FloatingIsland", "DynamicIsland", "Capes", "Fullbright",
            "NoHurtCam", "NoHitDelay", "NoJumpDelay", "AntiObfuscate", "NickHider",
            "ServerHider"
    ));
    private float clickGuiScale = 1.0F;
    private String clickGuiStyle = "MODERN";
    private String clickGuiCategory = "COMBAT";
    private boolean verifyTcpNoDelay = true;
    private boolean indicator = true;
    private int moveFixMode = 1;
    private int botFilterMode = 0;
    private int teamsMode = 0;
    private boolean targetPlayers = true;
    private boolean targetMobs;
    private boolean targetAnimals;
    private final Map<myau.ui.modern.ClickGuiCategory, Float> scrollPositions =
            new EnumMap<>(myau.ui.modern.ClickGuiCategory.class);

    public ClientSettings() {
        for (myau.ui.modern.ClickGuiCategory category : myau.ui.modern.ClickGuiCategory.values()) {
            scrollPositions.put(category, 0.0F);
        }
    }

    public float getClickGuiScale() {
        return clickGuiScale;
    }

    public void setClickGuiScale(float value) {
        float safe = Math.max(0.5F, Math.min(2.0F, value));
        if (safe != clickGuiScale) {
            clickGuiScale = safe;
            Config.markClientDirty();
        }
    }

    public String getClickGuiStyle() {
        return clickGuiStyle;
    }

    public void setClickGuiStyle(String style) {
        String safe = "OLD".equalsIgnoreCase(style) ? "OLD" : "MODERN";
        if (!safe.equals(clickGuiStyle)) {
            clickGuiStyle = safe;
            Config.markClientDirty();
        }
    }

    public myau.ui.modern.ClickGuiCategory getClickGuiCategory() {
        try {
            return myau.ui.modern.ClickGuiCategory.valueOf(clickGuiCategory);
        } catch (IllegalArgumentException ignored) {
            return myau.ui.modern.ClickGuiCategory.COMBAT;
        }
    }

    public void setClickGuiCategory(myau.ui.modern.ClickGuiCategory category) {
        if (category != null && !category.name().equals(clickGuiCategory)) {
            clickGuiCategory = category.name();
            Config.markClientDirty();
        }
    }

    public float getScroll(myau.ui.modern.ClickGuiCategory category) {
        Float value = scrollPositions.get(category);
        return value == null ? 0.0F : Math.max(0.0F, value);
    }

    public void setScroll(myau.ui.modern.ClickGuiCategory category, float value) {
        if (category == null) return;
        float safe = Math.max(0.0F, value);
        Float old = scrollPositions.put(category, safe);
        if (old == null || Math.abs(old - safe) > 0.01F) Config.markClientDirty();
    }

    public boolean isVerifyTcpNoDelay() {
        return verifyTcpNoDelay;
    }

    public static boolean isIntegratedModuleName(String name) {
        return INTEGRATED_MODULES.contains(name);
    }

    public void setVerifyTcpNoDelay(boolean value) {
        if (verifyTcpNoDelay != value) {
            verifyTcpNoDelay = value;
            Config.markClientDirty();
        }
    }

    public boolean isIndicatorEnabled() {
        return indicator;
    }

    public void setIndicatorEnabled(boolean value) {
        if (indicator != value) {
            indicator = value;
            Config.markClientDirty();
        }
    }

    public int getMoveFixMode() { return moveFixMode; }
    public int getBotFilterMode() { return botFilterMode; }
    public int getTeamsMode() { return teamsMode; }
    public boolean isTargetPlayers() { return targetPlayers; }
    public boolean isTargetMobs() { return targetMobs; }
    public boolean isTargetAnimals() { return targetAnimals; }

    public void cycleMoveFixMode() {
        moveFixMode = (moveFixMode + 1) % 3;
        Config.markClientDirty();
    }

    public void cycleBotFilterMode() {
        botFilterMode = (botFilterMode + 1) % 3;
        Config.markClientDirty();
    }

    public void cycleTeamsMode() {
        teamsMode = (teamsMode + 1) % 3;
        Config.markClientDirty();
    }

    public void setTargetPlayers(boolean value) {
        if (targetPlayers != value) {
            targetPlayers = value;
            Config.markClientDirty();
        }
    }

    public void setTargetMobs(boolean value) {
        if (targetMobs != value) {
            targetMobs = value;
            Config.markClientDirty();
        }
    }

    public void setTargetAnimals(boolean value) {
        if (targetAnimals != value) {
            targetAnimals = value;
            Config.markClientDirty();
        }
    }

    public void read(JsonObject root) {
        JsonObject client = root.has("client") && root.get("client").isJsonObject()
                ? root.getAsJsonObject("client") : root;
        if (client.has("clickgui") && client.get("clickgui").isJsonObject()) {
            JsonObject gui = client.getAsJsonObject("clickgui");
            if (gui.has("scale")) clickGuiScale = Math.max(0.5F, Math.min(2.0F, gui.get("scale").getAsFloat()));
            if (gui.has("style")) setStyleQuiet(gui.get("style").getAsString());
            if (gui.has("category")) setCategoryQuiet(gui.get("category").getAsString());
            if (gui.has("scroll") && gui.get("scroll").isJsonObject()) {
                JsonObject scroll = gui.getAsJsonObject("scroll");
                for (myau.ui.modern.ClickGuiCategory category : myau.ui.modern.ClickGuiCategory.values()) {
                    JsonElement value = scroll.get(category.name().toLowerCase(Locale.ROOT));
                    if (value != null && value.isJsonPrimitive()) {
                        scrollPositions.put(category, Math.max(0.0F, value.getAsFloat()));
                    }
                }
            }
        }
        if (client.has("features") && client.get("features").isJsonObject()) {
            readIntegratedFeatures(client.getAsJsonObject("features"));
        }
        if (client.has("network") && client.get("network").isJsonObject()) {
            JsonObject network = client.getAsJsonObject("network");
            if (network.has("verify-tcp-no-delay")) {
                verifyTcpNoDelay = network.get("verify-tcp-no-delay").getAsBoolean();
            }
        }
        if (client.has("visuals") && client.get("visuals").isJsonObject()) {
            JsonObject visuals = client.getAsJsonObject("visuals");
            if (visuals.has("indicator")) indicator = visuals.get("indicator").getAsBoolean();
        }
        if (client.has("targeting") && client.get("targeting").isJsonObject()) {
            JsonObject targeting = client.getAsJsonObject("targeting");
            moveFixMode = readIndex(targeting, "move-fix", moveFixMode, 3);
            botFilterMode = readIndex(targeting, "bot-filter", botFilterMode, 3);
            teamsMode = readIndex(targeting, "teams", teamsMode, 3);
            if (targeting.has("players")) targetPlayers = targeting.get("players").getAsBoolean();
            if (targeting.has("mobs")) targetMobs = targeting.get("mobs").getAsBoolean();
            if (targeting.has("animals")) targetAnimals = targeting.get("animals").getAsBoolean();
        }
    }

    public void write(JsonObject root) {
        JsonObject client = new JsonObject();
        JsonObject gui = new JsonObject();
        gui.addProperty("scale", clickGuiScale);
        gui.addProperty("style", clickGuiStyle);
        gui.addProperty("category", clickGuiCategory);
        JsonObject scroll = new JsonObject();
        for (Map.Entry<myau.ui.modern.ClickGuiCategory, Float> entry : scrollPositions.entrySet()) {
            scroll.addProperty(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
        }
        gui.add("scroll", scroll);
        client.add("clickgui", gui);
        client.add("features", writeIntegratedFeatures());
        JsonObject network = new JsonObject();
        network.addProperty("verify-tcp-no-delay", verifyTcpNoDelay);
        client.add("network", network);
        JsonObject visuals = new JsonObject();
        visuals.addProperty("indicator", indicator);
        client.add("visuals", visuals);
        JsonObject targeting = new JsonObject();
        targeting.addProperty("move-fix", moveFixMode);
        targeting.addProperty("bot-filter", botFilterMode);
        targeting.addProperty("teams", teamsMode);
        targeting.addProperty("players", targetPlayers);
        targeting.addProperty("mobs", targetMobs);
        targeting.addProperty("animals", targetAnimals);
        client.add("targeting", targeting);
        root.add("client", client);
    }

    public void importLegacyModules(JsonObject legacy) {
        readIntegratedFeatures(legacy);
        Module guiBase = Myau.moduleManager == null ? null : Myau.moduleManager.modules.get(GuiModule.class);
        if (guiBase instanceof GuiModule) {
            GuiModule gui = (GuiModule) guiBase;
            clickGuiScale = gui.clickGuiScale.getValue();
            clickGuiStyle = gui.clickGuiStyle.getModeString();
        }
    }

    private void setStyleQuiet(String style) {
        clickGuiStyle = "OLD".equalsIgnoreCase(style) ? "OLD" : "MODERN";
    }

    private void setCategoryQuiet(String value) {
        try {
            clickGuiCategory = myau.ui.modern.ClickGuiCategory.valueOf(value.toUpperCase(Locale.ROOT)).name();
        } catch (RuntimeException ignored) {
            clickGuiCategory = "COMBAT";
        }
    }

    private JsonObject writeIntegratedFeatures() {
        JsonObject features = new JsonObject();
        if (Myau.moduleManager == null || Myau.propertyManager == null) return features;
        for (Module module : Myau.moduleManager.modules.values()) {
            if (!INTEGRATED_MODULES.contains(module.getName())) continue;
            JsonObject data = new JsonObject();
            data.addProperty("enabled", module.isEnabled());
            JsonObject properties = new JsonObject();
            List<Property<?>> list = Myau.propertyManager.properties.get(module.getClass());
            if (list != null) {
                for (Property<?> property : list) {
                    try {
                        property.write(properties);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            data.add("properties", properties);
            features.add(module.getName(), data);
        }
        return features;
    }

    private void readIntegratedFeatures(JsonObject features) {
        if (Myau.moduleManager == null || Myau.propertyManager == null) return;
        for (Module module : Myau.moduleManager.modules.values()) {
            if (!INTEGRATED_MODULES.contains(module.getName())) continue;
            JsonElement raw = findIgnoreCase(features, module.getName());
            if (raw == null || !raw.isJsonObject()) continue;
            JsonObject data = raw.getAsJsonObject();
            JsonObject properties = data.has("properties") && data.get("properties").isJsonObject()
                    ? data.getAsJsonObject("properties") : data;
            List<Property<?>> list = Myau.propertyManager.properties.get(module.getClass());
            if (list != null) {
                for (Property<?> property : list) {
                    try {
                        if (properties.has(property.getName())) property.read(properties);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            if (data.has("enabled") && data.get("enabled").isJsonPrimitive()) {
                module.setEnabled(data.get("enabled").getAsBoolean());
            }
        }
    }

    private JsonElement findIgnoreCase(JsonObject object, String name) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static int readIndex(JsonObject object, String name, int fallback, int size) {
        if (!object.has(name)) return fallback;
        try {
            return Math.max(0, Math.min(size - 1, object.get(name).getAsInt()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}

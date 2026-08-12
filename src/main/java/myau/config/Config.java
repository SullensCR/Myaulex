package myau.config;

import com.google.gson.*;
import myau.Myau;
import myau.management.NotificationManager;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.property.Property;
import myau.ui.modern.ModuleCatalog;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Versioned persistence for ordinary modules, keybinds and client-owned state.
 *
 * Named Config instances deliberately contain no keybind or Client state.
 */
public class Config {
    public static final int CONFIG_VERSION = 1;
    public static final File CONFIG_DIR = new File("./config/Myaulex/");
    public static final File LATEST_FILE = new File(CONFIG_DIR, "latest.json");
    public static final File KEYBINDS_FILE = new File(CONFIG_DIR, "keybinds.json");
    public static final File CLIENT_FILE = new File(CONFIG_DIR, "client.json");
    private static final File LEGACY_DEFAULT_FILE = new File(CONFIG_DIR, "default.json");
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "latest", "keybinds", "client", "default"
    ));
    private static final Map<String, String[]> LEGACY_ALIASES = buildAliases();

    public static final Minecraft mc = Minecraft.getMinecraft();
    public static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public static String lastConfig;

    private static boolean loading;
    private static boolean latestDirty;
    private static boolean keybindsDirty;
    private static boolean clientDirty;

    public final String name;
    public final File file;
    private final boolean valid;

    public Config(String name, boolean newConfig) {
        String normalized = normalizeName(name);
        this.valid = normalized != null;
        this.name = valid ? normalized : "";
        this.file = valid ? new File(CONFIG_DIR, this.name + ".json") : new File(CONFIG_DIR, "_invalid.json");
        if (valid) lastConfig = this.name;
        ensureDirectory();
    }

    /** Loads persistent state, performing the one-time default.json migration. */
    public static void initialize() {
        ensureDirectory();
        loading = true;
        try {
            if (!LATEST_FILE.exists() && LEGACY_DEFAULT_FILE.exists()) {
                JsonObject legacy = readObject(LEGACY_DEFAULT_FILE, true);
                if (legacy != null) {
                    loadModules(legacy, true, true);
                    if (Myau.clientSettings != null) Myau.clientSettings.importLegacyModules(legacy);
                    backup(LEGACY_DEFAULT_FILE, "legacy");
                    writeLatest();
                    writeKeybinds();
                    writeClient();
                    notifyUser(NotificationManager.NotificationType.CONFIG_SUCCESS,
                            "default", "Configuration imported successfully");
                }
            } else {
                JsonObject latest = readObject(LATEST_FILE, true);
                if (latest != null) loadModules(moduleRoot(latest), true, false);
                JsonObject keybinds = readObject(KEYBINDS_FILE, true);
                if (keybinds != null) loadKeybinds(keybinds);
                JsonObject client = readObject(CLIENT_FILE, true);
                if (client != null && Myau.clientSettings != null) Myau.clientSettings.read(client);
            }

            if (!LATEST_FILE.exists()) writeLatest();
            if (!KEYBINDS_FILE.exists()) writeKeybinds();
            if (!CLIENT_FILE.exists()) writeClient();
        } finally {
            loading = false;
            latestDirty = keybindsDirty = clientDirty = false;
        }
    }

    /** Loads a named ordinary-module profile. Keybinds and Client state are untouched. */
    public void load() {
        if (!requireValid()) return;
        if (!file.exists()) {
            notifyUser(NotificationManager.NotificationType.CONFIG_ERROR,
                    this.name, "Config file not found");
            return;
        }
        JsonObject object = readObject(file, true);
        if (object == null) return;
        loading = true;
        try {
            loadModules(moduleRoot(object), false, false);
            latestDirty = true;
        } finally {
            loading = false;
        }
        notifyUser(NotificationManager.NotificationType.CONFIG_SUCCESS,
                this.name, "Config loaded successfully");
    }

    /** Saves a named ordinary-module profile. */
    public void save() {
        if (!requireValid()) return;
        JsonObject root = baseRoot();
        root.add("modules", serializeModules(false));
        if (writeAtomic(file, root)) {
            notifyUser(NotificationManager.NotificationType.CONFIG_SUCCESS,
                    this.name, "Config saved successfully");
        } else {
            notifyUser(NotificationManager.NotificationType.CONFIG_ERROR,
                    this.name, "Error when saving config");
        }
    }

    public static boolean isReservedName(String name) {
        return name != null && RESERVED.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isValidProfileName(String name) {
        return normalizeName(name) != null;
    }

    public static boolean isNamedConfigFile(File candidate) {
        if (candidate == null || !candidate.isFile()) return false;
        String filename = candidate.getName();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".json")) return false;
        return !isReservedName(filename.substring(0, filename.length() - 5));
    }

    public static void markDirty() {
        if (!loading) latestDirty = true;
    }

    public static void markDirty(Module owner) {
        if (loading) return;
        if (owner != null && !ModuleCatalog.isOrdinary(owner)) {
            clientDirty = true;
        } else {
            latestDirty = true;
        }
    }

    public static void markKeybindDirty() {
        if (!loading) keybindsDirty = true;
    }

    public static void markClientDirty() {
        if (!loading) clientDirty = true;
    }

    /** Compatibility entry point; no longer called by ClickGUI close. */
    public static void saveActive() {
        savePersistent();
    }

    public static synchronized void savePersistent() {
        if (latestDirty) writeLatest();
        if (keybindsDirty) writeKeybinds();
        if (clientDirty) writeClient();
    }

    private static void writeLatest() {
        JsonObject root = baseRoot();
        root.add("modules", serializeModules(false));
        if (writeAtomic(LATEST_FILE, root)) latestDirty = false;
    }

    private static void writeKeybinds() {
        JsonObject root = baseRoot();
        JsonObject bindings = new JsonObject();
        if (Myau.moduleManager != null) {
            for (Module module : Myau.moduleManager.ordinaryModules()) {
                bindings.addProperty(module.getName(), module.getKey());
            }
        }
        root.add("keybinds", bindings);
        if (writeAtomic(KEYBINDS_FILE, root)) keybindsDirty = false;
    }

    private static void writeClient() {
        JsonObject root = baseRoot();
        if (Myau.clientSettings != null) Myau.clientSettings.write(root);
        if (writeAtomic(CLIENT_FILE, root)) clientDirty = false;
    }

    private static JsonObject serializeModules(boolean includeKeys) {
        JsonObject modules = new JsonObject();
        if (Myau.moduleManager == null) return modules;
        for (Module module : Myau.moduleManager.ordinaryModules()) {
            JsonObject data = new JsonObject();
            data.addProperty("enabled", module.isEnabled());
            data.addProperty("hidden", module.isHidden());
            if (includeKeys) data.addProperty("key", module.getKey());
            JsonObject properties = new JsonObject();
            List<Property<?>> list = Myau.propertyManager.properties.get(module.getClass());
            if (list != null) {
                for (Property<?> property : list) {
                    try {
                        property.write(properties);
                    } catch (RuntimeException error) {
                        logger().warn("Failed to save property " + property.getName() + " for " + module.getName());
                    }
                }
            }
            data.add("properties", properties);
            modules.add(module.getName(), data);
        }
        return modules;
    }

    private static void loadModules(JsonObject root, boolean includeKeys, boolean legacyFlat) {
        if (root == null || Myau.moduleManager == null) return;
        for (Module module : Myau.moduleManager.ordinaryModules()) {
            JsonObject data = findModule(root, module.getName());
            if (data == null) continue;
            JsonObject properties = data.has("properties") && data.get("properties").isJsonObject()
                    ? data.getAsJsonObject("properties") : data;
            List<Property<?>> list = Myau.propertyManager.properties.get(module.getClass());
            if (list != null) {
                for (Property<?> property : list) {
                    JsonObject propertySource = propertySource(properties, property);
                    if (propertySource == null) continue;
                    try {
                        if (!property.read(propertySource)) {
                            logger().warn("Rejected property " + property.getName() + " for " + module.getName());
                        }
                    } catch (RuntimeException error) {
                        logger().warn("Skipped invalid property " + property.getName() + " for " + module.getName());
                    }
                }
            }
            readEnabled(data, module);
            if (data.has("hidden") && data.get("hidden").isJsonPrimitive()) {
                module.setHidden(data.get("hidden").getAsBoolean());
            }
            if (includeKeys && data.has("key") && data.get("key").isJsonPrimitive()) {
                module.setKey(data.get("key").getAsInt());
            }
        }
    }

    private static void readEnabled(JsonObject data, Module module) {
        JsonElement enabled = data.has("enabled") ? data.get("enabled") : data.get("toggled");
        if (enabled != null && enabled.isJsonPrimitive()) module.setEnabled(enabled.getAsBoolean());
    }

    private static void loadKeybinds(JsonObject root) {
        JsonObject bindings = root.has("keybinds") && root.get("keybinds").isJsonObject()
                ? root.getAsJsonObject("keybinds") : root;
        for (Module module : Myau.moduleManager.ordinaryModules()) {
            JsonElement value = findElement(bindings, module.getName());
            if (value != null && value.isJsonPrimitive()) {
                try {
                    module.setKey(value.getAsInt());
                } catch (RuntimeException ignored) {
                    logger().warn("Skipped invalid keybind for " + module.getName());
                }
            }
        }
    }

    private static JsonObject findModule(JsonObject root, String name) {
        JsonElement direct = findElement(root, name);
        String[] aliases = LEGACY_ALIASES.get(name.toLowerCase(Locale.ROOT));
        if ((direct == null || !direct.isJsonObject()) && aliases == null) return null;

        List<JsonObject> sources = new ArrayList<>();
        if (aliases != null) {
            for (String alias : aliases) {
                JsonElement candidate = findElement(root, alias);
                if (candidate != null && candidate.isJsonObject()) sources.add(candidate.getAsJsonObject());
            }
        }
        JsonObject canonical = direct != null && direct.isJsonObject() ? direct.getAsJsonObject() : null;
        if (canonical == null && sources.isEmpty()) return null;
        if (sources.isEmpty()) return canonical;

        JsonObject merged = new JsonObject();
        JsonObject properties = new JsonObject();
        // Earlier documented sources have precedence. Canonical values always win.
        for (JsonObject source : sources) mergeMissing(properties, propertyObject(source));
        if (canonical != null) {
            JsonObject canonicalProperties = propertyObject(canonical);
            for (Map.Entry<String, JsonElement> entry : canonicalProperties.entrySet()) {
                String key = entry.getKey();
                if ("enabled".equalsIgnoreCase(key) || "toggled".equalsIgnoreCase(key)
                        || "hidden".equalsIgnoreCase(key) || "key".equalsIgnoreCase(key)
                        || "properties".equalsIgnoreCase(key)) continue;
                properties.add(entry.getKey(), entry.getValue());
            }
        }
        merged.add("properties", properties);

        boolean enabled = false;
        boolean sawEnabled = false;
        boolean hidden = true;
        boolean sawHidden = false;
        int key = 0;
        List<JsonObject> stateSources = new ArrayList<>();
        if (canonical != null) stateSources.add(canonical);
        stateSources.addAll(sources);
        for (JsonObject source : stateSources) {
            JsonElement enabledValue = source.has("enabled") ? source.get("enabled") : source.get("toggled");
            if (enabledValue != null && enabledValue.isJsonPrimitive()) {
                enabled |= enabledValue.getAsBoolean();
                sawEnabled = true;
            }
            if (source.has("hidden") && source.get("hidden").isJsonPrimitive()) {
                hidden &= source.get("hidden").getAsBoolean();
                sawHidden = true;
            }
            if (key == 0 && source.has("key") && source.get("key").isJsonPrimitive()) {
                int candidate = source.get("key").getAsInt();
                if (candidate != 0) key = candidate;
            }
        }
        if (sawEnabled) merged.addProperty("enabled", enabled);
        if (sawHidden) merged.addProperty("hidden", hidden);
        if (key != 0) merged.addProperty("key", key);
        return merged;
    }

    private static JsonObject propertySource(JsonObject properties, Property<?> property) {
        if (properties.has(property.getName())) return properties;
        JsonElement targetCps = legacyTargetCpsValue(properties, property.getName());
        if (targetCps != null) {
            JsonObject migrated = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                migrated.add(entry.getKey(), entry.getValue());
            }
            migrated.add(property.getName(), targetCps);
            return migrated;
        }
        String legacyName = legacyPropertyName(property.getName());
        if (legacyName == null || !properties.has(legacyName)) return null;

        JsonObject migrated = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            migrated.add(entry.getKey(), entry.getValue());
        }
        migrated.add(property.getName(), properties.get(legacyName));
        return migrated;
    }

    private static JsonElement legacyTargetCpsValue(JsonObject properties, String propertyName) {
        String minimumName;
        String maximumName;
        if ("target-cps".equals(propertyName)) {
            minimumName = "min-aps";
            maximumName = "max-aps";
        } else if ("auto-block-target-cps".equals(propertyName)) {
            minimumName = "auto-block-min-aps";
            maximumName = "auto-block-max-aps";
        } else {
            return null;
        }

        JsonElement minimum = findElement(properties, minimumName);
        JsonElement maximum = findElement(properties, maximumName);
        if (minimum == null && maximum == null) return null;
        try {
            if (minimum == null) return new JsonPrimitive(maximum.getAsInt());
            if (maximum == null) return new JsonPrimitive(minimum.getAsInt());
            return new JsonPrimitive((int) Math.round((minimum.getAsDouble() + maximum.getAsDouble()) / 2.0D));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String legacyPropertyName(String propertyName) {
        if ("consumables-mode".equals(propertyName)) return "food-mode";
        if ("consumables-motion".equals(propertyName)) return "food-motion";
        if ("consumables-sprint".equals(propertyName)) return "food-sprint";
        return null;
    }

    private static JsonObject propertyObject(JsonObject data) {
        return data.has("properties") && data.get("properties").isJsonObject()
                ? data.getAsJsonObject("properties") : data;
    }

    private static void mergeMissing(JsonObject target, JsonObject source) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = entry.getKey();
            if ("enabled".equalsIgnoreCase(key) || "toggled".equalsIgnoreCase(key)
                    || "hidden".equalsIgnoreCase(key) || "key".equalsIgnoreCase(key)
                    || "properties".equalsIgnoreCase(key)) continue;
            if (findElement(target, key) == null) target.add(key, entry.getValue());
        }
    }

    private static JsonElement findElement(JsonObject root, String name) {
        if (root.has(name)) return root.get(name);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static JsonObject moduleRoot(JsonObject root) {
        return root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : root;
    }

    private static JsonObject readObject(File source, boolean checkVersion) {
        if (source == null || !source.exists()) return null;
        try (Reader reader = new BufferedReader(new FileReader(source))) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) throw new JsonSyntaxException("Root is not an object");
            JsonObject root = parsed.getAsJsonObject();
            if (checkVersion && root.has("config-version")) {
                int version = root.get("config-version").getAsInt();
                if (version != CONFIG_VERSION) {
                    backup(source, "v" + version);
                    notifyUser(NotificationManager.NotificationType.CONFIG_EDIT,
                            source.getName(), "Config version differs; importing recognized values.");
                }
            }
            return root;
        } catch (Exception error) {
            logger().error("Failed to read config " + source.getName(), error);
            notifyUser(NotificationManager.NotificationType.CONFIG_ERROR,
                    source.getName(), "Error when loading config; existing state was preserved.");
            return null;
        }
    }

    private static boolean writeAtomic(File destination, JsonObject object) {
        ensureDirectory();
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (Writer writer = new BufferedWriter(new FileWriter(temporary))) {
            gson.toJson(object, writer);
        } catch (IOException error) {
            logger().error("Failed to write temporary config " + temporary.getName(), error);
            return false;
        }
        try {
            try {
                Files.move(temporary.toPath(), destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException error) {
            logger().error("Failed to replace config " + destination.getName(), error);
            return false;
        }
    }

    private static void backup(File source, String reason) {
        if (source == null || !source.exists()) return;
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File backup = new File(CONFIG_DIR, source.getName() + "." + reason + "." + timestamp + ".bak");
        try {
            Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            logger().error("Failed to back up " + source.getName(), error);
        }
    }

    private boolean requireValid() {
        if (valid) return true;
        notifyUser(NotificationManager.NotificationType.CONFIG_ERROR,
                "Configuration", "Config names cannot be blank, reserved, or contain path characters.");
        return false;
    }

    private static String normalizeName(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.isEmpty() || isReservedName(value)) return null;
        if (!value.matches("[A-Za-z0-9_-]+")) return null;
        return value;
    }

    private static JsonObject baseRoot() {
        JsonObject root = new JsonObject();
        root.addProperty("config-version", CONFIG_VERSION);
        return root;
    }

    private static void ensureDirectory() {
        if (!CONFIG_DIR.exists() && !CONFIG_DIR.mkdirs()) {
            logger().warn("Could not create config directory " + CONFIG_DIR.getPath());
        }
    }

    private static void notifyUser(NotificationManager.NotificationType type, String title, String message) {
        if (Myau.notificationManager != null && Myau.notificationManager.isEnabled()) {
            Myau.notificationManager.add(type, "config:" + title, title, message, false);
        } else {
            ChatUtil.sendFormatted(Myau.clientName + title + ": " + message);
        }
        logger().info(message);
    }

    private static org.apache.logging.log4j.Logger logger() {
        try {
            return ((IAccessorMinecraft) mc).getLogger();
        } catch (Throwable ignored) {
            return org.apache.logging.log4j.LogManager.getLogger("Myaulex-Config");
        }
    }

    private static Map<String, String[]> buildAliases() {
        Map<String, String[]> aliases = new HashMap<>();
        aliases.put("aura", new String[]{"KillAura"});
        aliases.put("fastkb", new String[]{"MoreKB", "WTap"});
        aliases.put("entityesp", new String[]{"ESP", "ESP2D", "Chams", "ItemESP"});
        aliases.put("autogapple", new String[]{"AutoHeal"});
        aliases.put("inventorymanager", new String[]{"InvManager"});
        aliases.put("inventorymove", new String[]{"InvWalk"});
        aliases.put("fastbreak", new String[]{"SpeedMine"});
        aliases.put("bedbreaker", new String[]{"BedNuker"});
        aliases.put("particles", new String[]{"HitParticleEffects"});
        aliases.put("autoauthentication", new String[]{"AutoRegister"});
        aliases.put("packetdelay", new String[]{"FakeLag"});
        aliases.put("nametags", new String[]{"NameTags"});
        aliases.put("keep sprint", new String[]{"KeepSprint"});
        return aliases;
    }
}

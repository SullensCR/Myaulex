package myau.ui.modern;

import myau.module.Module;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ModuleCatalog {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-UI");
    private static final Set<String> WARNED_UNCATEGORIZED = Collections.synchronizedSet(new HashSet<String>());
    private static final Map<String, ClickGuiCategory> CATEGORIES;

    static {
        Map<String, ClickGuiCategory> categories = new HashMap<>();
        add(categories, ClickGuiCategory.COMBAT,
                "AimAssist", "AutoClicker", "AntiFireball", "Aura", "Hit Select", "Backtrack", "LagRange",
                "FastKB", "Velocity", "VeloDelay");
        add(categories, ClickGuiCategory.MOVEMENT,
                "AntiVoid", "Blink", "Keep Sprint", "NoSlow", "InventoryMove", "Sprint", "Stasis");
        add(categories, ClickGuiCategory.VISUALS,
                "Ambience", "Particles", "Indicators", "EntityESP", "BlockOverlay",
                "Nametags", "Trajectories", "MotionPath", "AntiDebuff", "Bedplates");
        add(categories, ClickGuiCategory.PLAYER,
                "AutoGapple", "AutoTool", "ChestStealer", "InventoryManager", "Scaffold",
                "BridgeAssist", "FastBreak", "BedBreaker");
        add(categories, ClickGuiCategory.UTILITIES,
                "AutoCaptcha", "AutoAuthentication", "AutoRejoin", "TransactionAnalyzer", "Notifications", "PacketDelay",
                "FastQueue", "FlagDetector");
        CATEGORIES = Collections.unmodifiableMap(categories);
    }

    private ModuleCatalog() {
    }

    private static void add(Map<String, ClickGuiCategory> target, ClickGuiCategory category, String... names) {
        for (String name : Arrays.asList(names)) {
            if (target.put(name, category) != null) {
                throw new IllegalStateException("Duplicate ClickGUI category for " + name);
            }
        }
    }

    public static ClickGuiCategory category(Module module) {
        return CATEGORIES.get(module.getName());
    }

    public static boolean isOrdinary(Module module) {
        return module != null && CATEGORIES.containsKey(module.getName());
    }

    public static Set<String> ordinaryNames() {
        return CATEGORIES.keySet();
    }

    public static void validate(Iterable<Module> modules) {
        Map<String, Integer> counts = new HashMap<>();
        for (Module module : modules) {
            if (isOrdinary(module)) counts.put(module.getName(), counts.getOrDefault(module.getName(), 0) + 1);
        }
        for (String expected : CATEGORIES.keySet()) {
            int count = counts.getOrDefault(expected, 0);
            if (count != 1) {
                throw new IllegalStateException("Ordinary module catalog requires exactly one '" + expected
                        + "' registration, found " + count);
            }
        }
    }
}

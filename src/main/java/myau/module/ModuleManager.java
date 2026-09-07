package myau.module;

import myau.Myau;
import myau.config.Config;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.modules.GuiModule;
import myau.util.SoundUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import myau.ui.modern.ModuleCatalog;

public class ModuleManager {
    private String sound;
    public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<>();
    /**
     * Lazily-built lookup caches. The module set is fixed once {@code Myau.init()}
     * finishes registering modules, so caching lookups here is behavior-identical
     * to streaming over the registry on every call. Index maps use
     * {@code putIfAbsent} to preserve the original "first match wins" ordering.
     */
    private Map<String, Module> nameIndex;
    private Map<String, Module> ordinaryNameIndex;
    private Collection<Module> ordinaryModulesCache;

    public Module getModule(String string) {
        if (string == null) {
            return null;
        }
        return this.nameIndex().get(string.toLowerCase(Locale.ROOT));
    }

    public Module getOrdinaryModule(String name) {
        if (name == null) {
            return null;
        }
        return this.ordinaryNameIndex().get(name.toLowerCase(Locale.ROOT));
    }

    public Module getModule(Class<?> clazz){
        return this.modules.get(clazz);
    }

    public Collection<Module> ordinaryModules() {
        Collection<Module> cache = this.ordinaryModulesCache;
        if (cache == null) {
            ArrayList<Module> list = new ArrayList<>();
            for (Module module : this.modules.values()) {
                if (ModuleCatalog.isOrdinary(module)) {
                    list.add(module);
                }
            }
            cache = Collections.unmodifiableList(list);
            this.ordinaryModulesCache = cache;
        }
        return cache;
    }

    private Map<String, Module> nameIndex() {
        Map<String, Module> index = this.nameIndex;
        if (index == null) {
            index = new HashMap<>();
            for (Module module : this.modules.values()) {
                index.putIfAbsent(module.getName().toLowerCase(Locale.ROOT), module);
            }
            this.nameIndex = index;
        }
        return index;
    }

    private Map<String, Module> ordinaryNameIndex() {
        Map<String, Module> index = this.ordinaryNameIndex;
        if (index == null) {
            index = new HashMap<>();
            for (Module module : this.ordinaryModules()) {
                index.putIfAbsent(module.getName().toLowerCase(Locale.ROOT), module);
            }
            this.ordinaryNameIndex = index;
        }
        return index;
    }

    public void playSound(boolean enabled) {
        this.sound = enabled ? SoundUtil.TOGGLE_ON_SOUND : SoundUtil.TOGGLE_OFF_SOUND;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        for (Module module : this.modules.values()) {
            if (module.getKey() != event.getKey()) {
                continue;
            }
            boolean shouldNotify = module.toggle();
            if(module instanceof GuiModule){
                shouldNotify = false;
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            for (Module module : this.modules.values()) {
                module.refreshUnsafeOperationalState();
            }
            Config.autosaveTick();
            if (this.sound != null) {
                String sound = this.sound;
                this.sound = null;
                SoundUtil.playSound(sound);
            }
        }
    }
}

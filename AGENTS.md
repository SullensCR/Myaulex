# AGENTS.md - Myau Minecraft Mod Development Guide

## Project Overview

**Myau** is a Minecraft 1.8.9 Forge mod (`myau` package, version 250910+9) structured as a **modular cheat/utility client** with 74 feature modules (see `Myau.init()` registration in `src/main/java/myau/Myau.java`). Built with Gradle/Kotlin DSL and Loom for Minecraft development, it uses **Mixins** for bytecode transformation to hook into Minecraft internals.

### Architecture Philosophy
The codebase follows a **plugin/module architecture** where almost all features are self-contained modules, coordinated by a central event bus. This design maximizes code isolation and makes adding new features straightforward.

## Critical Knowledge for AI Agents

### 1. The Module System (Foundation)

Every feature is a **Module** (`myau/module/Module.java`). Understand this pattern first.

```java
public class MyFeature extends Module {
    public MyFeature() {
        super("MyFeature", false); // name, defaultEnabled
    }
    
    @EventTarget  // Register for events via reflection
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) return;
        // Feature logic here
    }
}
```

**Key Module Characteristics:**
- Lifecycle: `onEnabled()` / `onDisabled()` methods called when toggled
- Properties: Publicly exposed `Property<?>` fields are auto-discovered, saved to JSON config, and validated
- Key Binding: Each module has optional key binding (integer, 0 = disabled)
- Hidden flag: Determines if module appears in HUD

**Modules are registered in `Myau.java` init()** in two ways:
1. Added to `ModuleManager.modules` (LinkedHashMap to preserve order)
2. All Properties auto-discovered via reflection from declared fields

### 2. Event System (Glue Between Modules)

Custom event bus at `myau/event/EventManager.java`. Uses **reflection** to discover methods marked `@EventTarget`.

**Key Events** (located in `myau/events/`):
- `TickEvent` - Game tick (PRE/POST phases)
- `KeyEvent` - Player key press
- `PacketEvent` - Network packets (SEND/POST phases)
- `MotionEvent` - Player movement updates

**Event Flow:**
```java
@EventTarget(Priority.HIGHEST)  // Lower value = called first
public void onTick(TickEvent event) {
    if (event.getType() == EventType.PRE) {
        // Called before game tick
    }
}
```

**Important:** Events registered on ANY object via `EventManager.register(object)`. ModuleManager, all managers, and modules are registered during `Myau.init()`.

### 3. Property/Config System (Persistence)

**Properties** are typed configuration values with validation and visibility logic. Auto-persisted to JSON.

**Property Types** (`myau/property/properties/`):
- `BooleanProperty` - true/false toggle (constructor: name, defaultValue, visibilitySupplier)
- `FloatProperty` - float with min/max bounds
- `IntProperty` - integer with min/max
- `PercentProperty` - 0-100 range
- `ModeProperty` - enumerated mode selection

**Pattern in Modules:**
```java
public class AimAssist extends Module {
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 8.0F);
    public final BooleanProperty weaponOnly = new BooleanProperty("weapons-only", true);
    // Conditional visibility: only show allowTools if weaponOnly is true
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, 
        this.weaponOnly::getValue);
}
```

**Storage:** `PropertyManager` maintains map of Module.class → List<Property<?>>. Config system auto-saves via `Config.save()` to `./config/Myau/{configName}.json`.

### 4. Manager System (Core Services)

Singleton managers initialized in `Myau.init()` and registered with EventManager:

**Critical Managers:**
- `RotationManager` - Player rotation/look angles (used by aimbot, kill aura, etc.)
- `TargetManager` - Current player/entity target selection
- `PlayerStateManager` - Local player state (sprinting, falling, etc.)
- `FriendManager` - Friend list (persisted file)
- `ModuleManager` - Module registry and lifecycle
- `PropertyManager` - Property registry by module class
 - `FloatManager` - Tracks/predicts player "floating" state and per-module float activity (used by modules that alter vertical motion). See `myau/management/FloatManager.java`.
 - `BlinkManager` - Buffers outbound packets for "blink" functionality and flushes them on disable or network events. See `myau/management/BlinkManager.java`.
 - `DelayManager` - Holds or delays incoming/outgoing packets for timed replay/delay features. See `myau/management/DelayManager.java`.
 - `LagManager` - Queues and flushes packets to simulate or handle lag delays; tracks last player positions for movement packet replay. See `myau/management/LagManager.java`.

**Access Pattern:** Static references on `Myau` class, e.g., `Myau.rotationManager.lookAngle()`.

### 5. Mixin/Bytecode Injection Pattern

Located in `myau/mixin/`. Two types:

**Mixin Classes** (e.g., `MixinMinecraft.java`):
- Modify Minecraft classes at bytecode level
- Use `@Inject`, `@Redirect`, `@Modify` annotations from Spongepowered Mixin library
- Fire custom events from injected hooks

**Accessor Interfaces** (e.g., `IAccessorEntityPlayer.java`):
- Access private fields in Minecraft classes
- Use `@Accessor` annotation to generate getters

**Declared in** `src/main/resources/mixins.myau.json` (templated):
```json
{
  "package": "${basePackage}.mixin",
  "plugin": "${basePackage}.init.FMLLoadingPlugin",
  "refmap": "mixins.${modid}.refmap.json",
  "minVersion": "0.7",
  "compatibilityLevel": "JAVA_8"
}
```

FMLLoadingPlugin dynamically loads mixin configuration at game startup. Note: this project uses a templated mixin config (`mixins.${modid}.json`) which is expanded during Gradle `processResources` (see `build.gradle.kts` `filesMatching(...){ expand(...) }`). The mixin template also sets `compatibilityLevel` to `JAVA_8`.

### 6. Command System

Commands are intercepted from chat messages starting with `.` (dot).

**ChatEvent hook** in CommandManager detects `.command args` pattern, dispatches to Command subclasses.

**Pattern:**
```java
public class MyCommand extends Command {
    public MyCommand() {
        super(new String[]{"mycommand", "mc"}, "Description");
    }
    
    @Override
    public void runCommand(List<String> args) {
        // args[0] = command name, args[1:] = parameters
    }
}
```

**Registration:** Add to `commandManager.commands` list in `Myau.init()`.

## Developer Workflow

### Building
```bash
./gradlew build
# Output: build/intermediates/Myau.jar
```

Note: This repo uses `gg.essential.loom` (configured in `settings.gradle.kts` / `build.gradle.kts`) and includes a shadowed Mixin dependency (`org.spongepowered:mixin:0.7.11-SNAPSHOT`) plus optional access transformer support (`src/main/resources/accesstransformer.cfg`). The mixin config name is templated (`mixins.$modid.json`) and expanded during `processResources`.

### Understanding Existing Modules
1. Open a module (e.g., `AimAssist.java`) - starts with properties/fields
2. Find `@EventTarget` methods that implement feature logic
3. Check which managers/utils it uses (rotation, target, etc.)
4. Properties auto-saved; check config loading logic

### Adding a New Module
1. Create class extending `Module` in `myau/module/modules/`
2. Add public `Property<?>` fields for config
3. Implement `@EventTarget` event handler(s)
4. Register in `Myau.init()` alongside other modules
5. Test and verify config saves to `config/Myau/default.json`

### Adding a New Property Type
Extend `Property<T>` in `myau/property/properties/`, implement abstract methods.

### Accessing Minecraft Classes
Use **Accessor interfaces** (`IAccessor*`) to access private fields. If accessor needed doesn't exist:
1. Create interface in `myau/mixin/` with `@Accessor` methods
2. Reference in mixin class via casting

## File Structure Reference

```
src/main/java/myau/
├── Myau.java                    # Initialization, manager setup
├── module/
│   ├── Module.java              # Base class for all features
│   ├── ModuleManager.java       # Module registry & key dispatch
│   └── modules/                 # 74 concrete module implementations (registered in `Myau.init()`)
├── event/
│   ├── EventManager.java        # Reflection-based event bus
│   ├── EventTarget.java         # Annotation for event handlers
│   ├── events/                  # Event base classes
│   └── types/Priority.java      # Priority enum for handler ordering
├── events/                      # Concrete event classes (TickEvent, etc.)
├── property/
│   ├── Property.java            # Base property class
│   ├── PropertyManager.java     # Property registry
│   └── properties/              # Boolean, Float, Int, Percent, Mode
├── config/
│   └── Config.java              # GSON-based JSON persistence
├── command/
│   ├── CommandManager.java      # Command dispatch
│   └── commands/                # Command implementations
├── management/                  # Singleton managers
├── mixin/                       # Bytecode injection classes & accessors
├── util/                        # Helper utilities (Chat, Render, Rotation, etc.)
└── data/                        # Data structures
```

## Common Patterns & Gotchas

### Pattern: Conditional Visibility
Properties can be hidden based on other properties:
```java
public final BooleanProperty enabled = new BooleanProperty("enabled", true);
public final FloatProperty speed = new FloatProperty("speed", 1.0F, 
    enabled::getValue);  // Only visible if enabled is true
```

### Pattern: Event Priority Ordering
Lower priority value = called earlier. Use `@EventTarget(Priority.HIGHEST)` to run before other handlers, critical for cancellable events that shouldn't double-process.

### Gotcha: Reflection Field Discovery
Properties must be:
1. Public final fields
2. Actual `Property<?>` instances (not raw types)
3. Declared in module subclass (not parent)

### Gotcha: Minecraft 1.8.9 Specifics
- Use `Minecraft.getMinecraft().thePlayer` for local player
- Packets use old MC net classes (no Netty-based naming)
- Mixin syntax is Spongepowered 0.7 era (may differ from newer versions)

### Common Utility Imports
```java
import myau.util.RotationUtil;      // Aim calculations
import myau.util.PlayerUtil;        // Player state checks
import myau.util.ChatUtil;          // Send formatted messages
import myau.util.TeamUtil;          // Friend/team detection
```

## Testing & Debugging

- Config auto-saves on JVM shutdown (shutdown hook in init)
- Test by toggling module via key bind, verifying `.module name` command works
- Configs stored in `./config/Myau/` - delete to reset to defaults
- Use ChatUtil.sendFormatted() for in-game feedback (supports Minecraft color codes with `&` prefix)

---

## Quick Start for New Contributors

1. **Read** `Myau.java` init() - see manager/module initialization order
2. **Pick a simple module** like `KeepSprint` or `Sprint` - understand its structure
3. **Trace an event** (e.g., `TickEvent`) through EventManager → Module handler
4. **Add a property** to existing module and verify config save/load works
5. **Create a simple new module** following the pattern above

This modular, event-driven architecture makes Myau easy to extend without breaking existing code.


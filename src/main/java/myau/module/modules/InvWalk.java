package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorC0DPacketCloseWindow;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.ui.ClickGuiScreen;
import myau.util.KeyBindUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvWalk extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private boolean keysPressed = false;
    private C16PacketClientStatus pendingStatus = null;
    private int delayTicks = 0;
    private int openDelayTicks = -1;
    private int closeDelayTicks = -1;
    private int sprintPauseTicks;
    private boolean serverInventoryOpen;
    private boolean clickGuiMovementActive;
    private final Map<KeyBinding, Boolean> movementKeys = new HashMap<KeyBinding, Boolean>(8) {{
        put(mc.gameSettings.keyBindForward, false);
        put(mc.gameSettings.keyBindBack, false);
        put(mc.gameSettings.keyBindLeft, false);
        put(mc.gameSettings.keyBindRight, false);
        put(mc.gameSettings.keyBindJump, false);
        put(mc.gameSettings.keyBindSneak, false);
        put(mc.gameSettings.keyBindSprint, false);
    }};

    public final ModeProperty mode = new ModeProperty("mode", 1,
            new String[]{"VANILLA", "LEGIT", "HYPIXEL", "LEGIT+", "SPOOF"});
    public final BooleanProperty sprint = new BooleanProperty("sprint", false);
    public final BooleanProperty clickSlowdown = new BooleanProperty("click-slowdown", true,
            sprint::getValue).childOf(sprint);
    public final BooleanProperty reopenOnClick = new BooleanProperty("reopen-on-click", true,
            () -> mode.getValue() == 4).childOf(mode);
    public final IntProperty openDelay = new IntProperty("open-delay", 0, 0, 20, () -> mode.getValue() == 3);
    public final IntProperty closeDelay = new IntProperty("close-delay", 4, 0, 20, () -> mode.getValue() == 3);
    public final BooleanProperty lockMoveKey = new BooleanProperty("lock-move-dey", false);

    public InvWalk() {
        super("InventoryMove", false);
    }

    public void pressMovementKeys(boolean skipSneak) {
        this.movementKeys.keySet().stream()
                .filter(key -> !skipSneak || key != mc.gameSettings.keyBindSneak)
                .forEach(key -> KeyBindUtil.updateKeyState(key.getKeyCode()));
        if (shouldForceSprintKey()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        } else if (isContainerOpen() && (!sprint.getValue() || sprintPauseTicks > 0)) {
            stopSprintingNow();
        }
        this.keysPressed = true;
    }

    public void resetMovementKeys() {
        this.movementKeys.replaceAll((k, v) -> false);
    }

    public boolean isSetMovementKeys() {
        return this.movementKeys.values().stream().anyMatch(Boolean::booleanValue);
    }

    public void storeMovementKeys() {
        this.movementKeys.replaceAll((k, v) -> KeyBindUtil.isKeyDown(k.getKeyCode()));
    }

    public void restoreMovementKeys() {
        for (Map.Entry<KeyBinding, Boolean> keyBinding : movementKeys.entrySet()) {
            KeyBindUtil.setKeyBindState(keyBinding.getKey().getKeyCode(), keyBinding.getValue());
        }
        if (shouldForceSprintKey()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        } else if (isContainerOpen() && (!sprint.getValue() || sprintPauseTicks > 0)) {
            stopSprintingNow();
        }
        this.keysPressed = true;
    }

    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer)) return false;
        if (mc.currentScreen instanceof GuiContainerCreative) return false;

        switch (this.mode.getValue()) {
            case 0: // Vanilla
                return true;
            case 1: // Legit
                if (!(mc.currentScreen instanceof GuiInventory)) return false;
                return this.pendingStatus != null && this.clickQueue.isEmpty();
            case 2: // Hypixel
                return this.delayTicks == 0 && this.clickQueue.isEmpty();
            case 3: // Legit+
                if (!(mc.currentScreen instanceof GuiInventory)) return false;
                return this.closeDelayTicks == -1 && this.clickQueue.isEmpty();
            case 4: // Spoof
                return true;
            default:
                return false;
        }
    }

    /** True while InventoryMove owns sprint and the Sprint option is off. */
    public boolean blocksSprint() {
        return isEnabled() && isContainerOpen() && !sprint.getValue();
    }

    private boolean isContainerOpen() {
        return mc.currentScreen instanceof GuiContainer
                && !(mc.currentScreen instanceof GuiContainerCreative);
    }

    private boolean shouldForceSprintKey() {
        Sprint sprintModule = (Sprint) Myau.moduleManager.modules.get(Sprint.class);
        return sprint.getValue() && sprintPauseTicks <= 0
                && sprintModule != null && sprintModule.isEnabled()
                && !isBlockedByScaffold();
    }

    private boolean isBlockedByScaffold() {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
        return scaffold != null && scaffold.blocksSprint();
    }

    private void stopSprintingNow() {
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        if (mc.thePlayer != null) mc.thePlayer.setSprinting(false);
    }

    /**
     * ClickGUI movement is a client UI behavior rather than an InventoryMove
     * module feature, so it must be handled even when this module is disabled.
     */
    private boolean updateClickGuiMovement() {
        if (!(mc.currentScreen instanceof ClickGuiScreen)) {
            if (clickGuiMovementActive) releaseClickGuiMovement();
            return false;
        }

        ClickGuiScreen screen = (ClickGuiScreen) mc.currentScreen;
        if (screen.isTextInputFocused()) {
            releaseClickGuiMovement();
        } else {
            if (isBlockedByScaffold()) {
                stopSprintingNow();
            } else {
                pressMovementKeys(true);
            }
            clickGuiMovementActive = true;
        }
        return true;
    }

    private void releaseClickGuiMovement() {
        KeyBinding.unPressAllKeys();
        clickGuiMovementActive = false;
        keysPressed = false;
    }

    public boolean temporaryStackIsEmpty() {
        if (mc.thePlayer.inventory.getItemStack() != null) return false;
        if (mc.thePlayer.inventoryContainer instanceof ContainerPlayer) {
            ContainerPlayer containerPlayer = (ContainerPlayer)mc.thePlayer.inventoryContainer;
            for (int i = 0; i < containerPlayer.craftMatrix.getSizeInventory(); i++) {
                ItemStack stack = containerPlayer.craftMatrix.getStackInSlot(i);
                if (stack != null) {
                    return false;
                }
            }
        }
        return true;
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.sprintPauseTicks > 0) this.sprintPauseTicks--;
            if (this.openDelayTicks >= 0) {
                this.openDelayTicks--;
                return;
            }
            if (this.delayTicks == 0) {
                while (!this.clickQueue.isEmpty()) {
                    PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
                }
                if (this.mode.getValue() == 4 && this.serverInventoryOpen && !this.reopenOnClick.getValue()) {
                    PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
                    this.serverInventoryOpen = false;
                }
            } else {
                this.delayTicks--;
            }
            if (this.closeDelayTicks > 0) {
                if (this.temporaryStackIsEmpty()) {
                    this.closeDelayTicks--;
                }
            } else if (this.closeDelayTicks == 0) {
                if (mc.currentScreen instanceof GuiInventory)
                    PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
                this.closeDelayTicks = -1;
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;

        if (updateClickGuiMovement()) return;
        if (!this.isEnabled()) return;

        if (isContainerOpen() && (!sprint.getValue() || sprintPauseTicks > 0)) {
            stopSprintingNow();
        }

        if (this.canInvWalk()) {
            if (this.isSetMovementKeys() && this.lockMoveKey.getValue()) {
                this.restoreMovementKeys();
            } else {
                this.pressMovementKeys(true);
            }
        } else {
            if (this.keysPressed) {
                if (mc.currentScreen != null) {
                    KeyBinding.unPressAllKeys();
                } else if (this.isSetMovementKeys()) {
                    this.resetMovementKeys();
                    this.pressMovementKeys(false);
                }
                this.keysPressed = false;
            }
            if (this.pendingStatus != null) {
                PacketUtil.sendPacketNoEvent(this.pendingStatus);
                this.pendingStatus = null;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND) return;

        if (event.getPacket() instanceof C16PacketClientStatus) {
            this.storeMovementKeys();
            if (this.mode.getValue() == 1 || this.mode.getValue() == 3) {
                C16PacketClientStatus packet = (C16PacketClientStatus) event.getPacket();
                if (packet.getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                    if (this.mode.getValue() == 4) {
                        event.setCancelled(true);
                        this.serverInventoryOpen = false;
                        return;
                    }
                    this.serverInventoryOpen = true;
                    event.setCancelled(true);
                    if (this.mode.getValue() == 1){
                        this.pendingStatus = packet;
                    }
                }
            }
        } else if (!(event.getPacket() instanceof C0EPacketClickWindow)) {
            if (event.getPacket() instanceof C0DPacketCloseWindow) {
                C0DPacketCloseWindow packet = (C0DPacketCloseWindow) event.getPacket();
                if (((IAccessorC0DPacketCloseWindow) packet).getWindowId() == 0) {
                    this.serverInventoryOpen = false;
                    if (this.mode.getValue() == 4) {
                        event.setCancelled(true);
                        this.clickQueue.clear();
                        return;
                    }
                    if (this.mode.getValue() == 3) {
                        if (!this.clickQueue.isEmpty()) {
                            this.clickQueue.clear();
                        }
                        if (this.openDelayTicks >= 0) {
                            this.openDelayTicks = -1;
                        }
                        if (this.closeDelayTicks >= 0) {
                            this.closeDelayTicks = -1;
                        } else {
                            event.setCancelled(true);
                        }
                    } else if (this.pendingStatus != null) {
                        this.pendingStatus = null;
                        event.setCancelled(true);
                    }
                } else {
                    if (!this.clickQueue.isEmpty()) {
                        this.clickQueue.clear();
                    }
                    if (this.openDelayTicks >= 0) {
                        this.openDelayTicks = -1;
                    }
                    if (this.closeDelayTicks >= 0) {
                        this.closeDelayTicks = -1;
                    }
                }
            }
        } else {
            C0EPacketClickWindow packet = (C0EPacketClickWindow) event.getPacket();
            if (sprint.getValue() && clickSlowdown.getValue()
                    && isContainerOpen()) {
                stopSprintingNow();
                event.setCancelled(true);
                this.clickQueue.offer(packet);
                this.delayTicks = Math.max(this.delayTicks, 2);
                this.sprintPauseTicks = Math.max(this.sprintPauseTicks, 2);
                return;
            }
            switch (this.mode.getValue()) {
                case 1: // Legit
                    if (packet.getWindowId() == 0) {
                        if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                            event.setCancelled(true);
                            return;
                        }
                        if (this.pendingStatus != null) {
                            KeyBinding.unPressAllKeys();
                            event.setCancelled(true);
                            this.clickQueue.offer(packet);
                        }
                    }
                    break;
                case 4: // Spoof
                    event.setCancelled(true);
                    if (!this.serverInventoryOpen || this.reopenOnClick.getValue()) {
                        PacketUtil.sendPacketNoEvent(new C16PacketClientStatus(EnumState.OPEN_INVENTORY_ACHIEVEMENT));
                        this.serverInventoryOpen = true;
                    }
                    this.clickQueue.offer(packet);
                    this.delayTicks = Math.max(this.delayTicks, 1);
                    break;
                case 2: // Hypixel
                    if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                        event.setCancelled(true);
                    } else {
                        KeyBinding.unPressAllKeys();
                        event.setCancelled(true);
                        this.clickQueue.offer(packet);
                        this.delayTicks = 8;
                    }
                    break;
                case 3: // Legit+
                    if (packet.getWindowId() == 0) { // inventory
                        if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                            event.setCancelled(true);
                            return;
                        }
                        KeyBinding.unPressAllKeys();
                        event.setCancelled(true);
                        this.clickQueue.offer(packet);
                        if (this.closeDelayTicks < 0 && this.openDelayTicks < 0){
                            this.pendingStatus = new C16PacketClientStatus(EnumState.OPEN_INVENTORY_ACHIEVEMENT);
                            this.openDelayTicks = openDelay.getValue();
                        }
                        this.closeDelayTicks = closeDelay.getValue();
                    }
                    break;
            }
            if (this.pendingStatus != null) {
                PacketUtil.sendPacketNoEvent(this.pendingStatus);
                this.pendingStatus = null;
            }
        }
    }

    @Override
    public void onDisabled() {
        if (this.keysPressed) {
            if (mc.currentScreen != null) {
                KeyBinding.unPressAllKeys();
            }
            this.keysPressed = false;
        }
        if (this.pendingStatus != null) {
            PacketUtil.sendPacketNoEvent(this.pendingStatus);
            this.pendingStatus = null;
        }
        this.delayTicks = 0;
        this.sprintPauseTicks = 0;
        while (!this.clickQueue.isEmpty()) PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
        if (this.serverInventoryOpen && mc.getNetHandler() != null) {
            PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
        }
        this.serverInventoryOpen = false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}

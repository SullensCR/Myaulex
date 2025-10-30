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
import myau.property.properties.IntProperty;
import myau.util.ChatUtil;
import myau.util.KeyBindUtil;
import myau.util.PacketUtil;
import myau.property.properties.ModeProperty;
import myau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.network.play.server.S2EPacketCloseWindow;

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
    private final Map<KeyBinding, Boolean> movementKeys = new HashMap<KeyBinding, Boolean>(8) {{
        put(mc.gameSettings.keyBindForward, false);
        put(mc.gameSettings.keyBindBack, false);
        put(mc.gameSettings.keyBindLeft, false);
        put(mc.gameSettings.keyBindRight, false);
        put(mc.gameSettings.keyBindJump, false);
        put(mc.gameSettings.keyBindSneak, false);
        put(mc.gameSettings.keyBindSprint, false);
    }};

    public final ModeProperty mode = new ModeProperty("mode", 1, new String[]{"VANILLA", "LEGIT", "HYPIXEL", "KEEPMOVE"});
    public final BooleanProperty guiEnabled = new BooleanProperty("ClickGUI", true);
    public final IntProperty openDelay = new IntProperty("openDelay", 6, 0, 20, () -> mode.getValue() == 3);
    public final IntProperty closeDelay = new IntProperty("closeDelay", 4, 0, 20, () -> mode.getValue() == 3);
    public final BooleanProperty lockMoveKey = new BooleanProperty("lockMoveKey", false);

    public InvWalk() {
        super("InvWalk", false);
    }

    public void pressMovementKeys() {
        for (KeyBinding keyBinding : movementKeys.keySet()) {
            KeyBindUtil.updateKeyState(keyBinding.getKeyCode());
        }
        if (Myau.moduleManager.modules.get(Sprint.class).isEnabled()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        this.keysPressed = true;
    }

    public void resetMovementKeys() {
        for (Map.Entry<KeyBinding, Boolean> keyBinding : movementKeys.entrySet()) {
            keyBinding.setValue(false);
        }
    }

    public boolean isSetMovementKeys() {
        for (Boolean keyBinding : movementKeys.values()) {
            if (keyBinding) return true;
        }
        return false;
    }

    public void storeMovementKeys() {
        for (Map.Entry<KeyBinding, Boolean> keyBinding : movementKeys.entrySet()) {
            keyBinding.setValue(KeyBindUtil.isKeyDown(keyBinding.getKey().getKeyCode()));
        }
    }

    public void restoreMovementKeys() {
        for (Map.Entry<KeyBinding, Boolean> keyBinding : movementKeys.entrySet()) {
            KeyBindUtil.setKeyBindState(keyBinding.getKey().getKeyCode(), keyBinding.getValue());
        }
        if (Myau.moduleManager.modules.get(Sprint.class).isEnabled()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
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
                return this.clickQueue.isEmpty();
            case 3: // KeepMove
                if (!(mc.currentScreen instanceof GuiInventory)) return false;
                return this.closeDelayTicks == -1 && this.clickQueue.isEmpty();
            default:
                return false;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.openDelayTicks >= 0) {
                this.openDelayTicks--;
                return;
            }
            while (!this.clickQueue.isEmpty()) {
                PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
                ChatUtil.sendFormatted(String.format("%s%s: &csend clickQueue&r", Myau.clientName, this.getName()));
            }
            if (this.closeDelayTicks > 0) {
                if (mc.thePlayer.inventory.getItemStack() == null) {
                    this.closeDelayTicks--;
                }
            } else if (this.closeDelayTicks == 0) {
                if (mc.currentScreen instanceof GuiInventory)
                    PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
                this.closeDelayTicks = -1;
                ChatUtil.sendFormatted(String.format("%s%s: &cclose inv&r", Myau.clientName, this.getName()));
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        if (mc.currentScreen instanceof myau.ui.ClickGui && this.guiEnabled.getValue()) {
            pressMovementKeys();
            return;
        }

        if (this.canInvWalk() && this.delayTicks == 0) {
            if (this.isSetMovementKeys() && this.lockMoveKey.getValue()) {
                this.restoreMovementKeys();
            } else {
                this.pressMovementKeys();
            }
        } else {
            if (this.keysPressed) {
                if (mc.currentScreen != null) {
                    KeyBinding.unPressAllKeys();
                } else if (this.isSetMovementKeys()) {
                    this.resetMovementKeys();
                    this.pressMovementKeys();
                }
                this.keysPressed = false;
            }
            if (this.pendingStatus != null) {
                PacketUtil.sendPacketNoEvent(this.pendingStatus);
                this.pendingStatus = null;
                ChatUtil.sendFormatted(String.format("%s%s: &copen inv&r", Myau.clientName, this.getName()));
            }
            if (this.delayTicks > 0) {
                this.delayTicks--;
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
                    event.setCancelled(true);
                    if (this.mode.getValue() == 1){
                        this.pendingStatus = packet;
                    }
                }
            } else if (((C16PacketClientStatus) event.getPacket()).getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                ChatUtil.sendFormatted(String.format("%s%s: &copen inv native2&r", Myau.clientName, this.getName()));
            }
        } else if (!(event.getPacket() instanceof C0EPacketClickWindow)) {
            if (event.getPacket() instanceof C0DPacketCloseWindow) {
                C0DPacketCloseWindow packet = (C0DPacketCloseWindow) event.getPacket();
                if (((IAccessorC0DPacketCloseWindow) packet).getWindowId() == 0) {
                    if (this.mode.getValue() == 3) {
                        if (!this.clickQueue.isEmpty()) {
                            this.clickQueue.clear();
                        }
                        if (this.openDelayTicks >= 0) {
                            this.openDelayTicks = -1;
                        }
                        if (this.closeDelayTicks >= 0) {
                            ChatUtil.sendFormatted(String.format("%s%s: &cclose inv native 1&r", Myau.clientName, this.getName()));
                            this.closeDelayTicks = -1;
                        } else {
                            event.setCancelled(true);
                        }
                    } else if (this.pendingStatus != null) {
                        this.pendingStatus = null;
                        event.setCancelled(true);
                    } else {
                        ChatUtil.sendFormatted(String.format("%s%s: &cclose inv native 2&r", Myau.clientName, this.getName()));
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
                    ChatUtil.sendFormatted(String.format("%s%s: &cC0DPacketCloseWindow %d&r", Myau.clientName, this.getName(), ((IAccessorC0DPacketCloseWindow) packet).getWindowId()));
                }
            }
        } else {
            C0EPacketClickWindow packet = (C0EPacketClickWindow) event.getPacket();
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
                case 3: // KeepMove
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
                ChatUtil.sendFormatted(String.format("%s%s: &copen inv native1&r", Myau.clientName, this.getName()));
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
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}

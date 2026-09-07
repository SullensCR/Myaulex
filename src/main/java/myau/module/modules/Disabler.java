package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.management.NotificationManager;
import myau.mixin.IAccessorMinecraft;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.util.KeyBindUtil;
import myau.util.ServerUtil;
import net.minecraft.block.BlockCarpet;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Performs the Hylex carpet interaction sequence once per server instance. */
public final class Disabler extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int HYLEX = 0;
    private static final Pattern INSTANCE_LINE = Pattern.compile(
            "^\\s*\\d{2}/\\d{2}/\\d{2,4}\\s*\\|\\s*(.+?)\\s*$");

    private enum Phase {
        SEARCHING,
        WAITING_TO_SELECT,
        WAITING_TO_USE,
        WAITING_FOR_MOUNT,
        CROUCHING,
        SUCCESS
    }

    public final ModeProperty mode = new ModeProperty("mode", HYLEX, new String[]{"Hylex"});
    public final BooleanProperty slotSpoofing = new BooleanProperty("slot-spoofing", true,
            () -> this.mode.getValue() == HYLEX);

    private Phase phase = Phase.SEARCHING;
    private int selectedSlot = -1;
    private int originalSlot = -1;
    private int delayTicks;
    private int mountGraceTicks;
    private int crouchTicks;
    private boolean successful;
    private boolean awaitingInstance;
    private String lastInstance;

    public Disabler() {
        super("Disabler", false, false, "Runs the Hylex Grim-disabling carpet sequence.");
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public boolean isSlotSpoofActive() {
        return this.isEnabled() && this.slotSpoofing.getValue() && this.originalSlot >= 0
                && this.phase != Phase.SEARCHING && this.phase != Phase.SUCCESS;
    }

    public int getVisualSlot() {
        return this.isSlotSpoofActive() ? this.originalSlot : -1;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE || !this.isEnabled() || this.mode.getValue() != HYLEX) return;
        this.updateInstance();
        if (this.successful || mc.thePlayer == null || mc.theWorld == null) return;

        switch (this.phase) {
            case SEARCHING:
                int slot = findTriggerSlot();
                if (slot >= 0) {
                    this.originalSlot = mc.thePlayer.inventory.currentItem;
                    this.selectedSlot = slot;
                    this.delayTicks = 1;
                    this.phase = Phase.WAITING_TO_SELECT;
                }
                break;
            case WAITING_TO_SELECT:
                if (waitOneTick()) break;
                if (!isTriggerItem(this.selectedSlot, mc.thePlayer.inventory.getStackInSlot(this.selectedSlot))) {
                    this.cancelAttempt();
                    break;
                }
                mc.thePlayer.inventory.currentItem = this.selectedSlot;
                syncSelectedSlot();
                this.delayTicks = 1;
                this.phase = Phase.WAITING_TO_USE;
                break;
            case WAITING_TO_USE:
                if (waitOneTick()) break;
                ((IAccessorMinecraft) mc).callRightClickMouse();
                this.mountGraceTicks = 2;
                this.phase = Phase.WAITING_FOR_MOUNT;
                break;
            case WAITING_FOR_MOUNT:
                if (mc.thePlayer.ridingEntity != null) {
                    this.crouchTicks = 2;
                    this.phase = Phase.CROUCHING;
                } else if (--this.mountGraceTicks <= 0) {
                    this.cancelAttempt();
                }
                break;
            case CROUCHING:
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
                if (this.crouchTicks-- <= 0) this.completeAttempt();
                break;
            default:
                break;
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (!this.isEnabled()) return;
        this.restorePlayerState();
        this.phase = this.successful ? Phase.SUCCESS : Phase.SEARCHING;
        this.awaitingInstance = true;
    }

    @Override
    public void onEnabled() {
        this.successful = false;
        this.phase = Phase.SEARCHING;
        this.awaitingInstance = true;
        this.updateInstance();
    }

    @Override
    public void onDisabled() {
        this.successful = false;
        this.awaitingInstance = false;
        this.lastInstance = null;
        this.restorePlayerState();
        this.phase = Phase.SEARCHING;
    }

    private boolean waitOneTick() {
        if (this.delayTicks <= 0) return false;
        this.delayTicks--;
        return this.delayTicks > 0;
    }

    private int findTriggerSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (isTriggerItem(slot, mc.thePlayer.inventory.getStackInSlot(slot))) return slot;
        }
        return -1;
    }

    static boolean isTriggerItem(int slot, ItemStack stack) {
        boolean carpet = stack != null && stack.getItem() instanceof ItemBlock
                && ((ItemBlock) stack.getItem()).getBlock() instanceof BlockCarpet;
        return matchesTrigger(slot, carpet, stack == null ? null : stack.getDisplayName());
    }

    static boolean matchesTrigger(int slot, boolean carpet, String formattedDisplayName) {
        if (slot < 0 || slot > 8 || !carpet || formattedDisplayName == null) return false;
        String name = EnumChatFormatting.getTextWithoutFormattingCodes(formattedDisplayName);
        return "Asa Delta".equals(name == null ? null : name.trim());
    }

    static String extractInstance(String formattedLine) {
        if (formattedLine == null) return null;
        String line = EnumChatFormatting.getTextWithoutFormattingCodes(formattedLine);
        if (line == null) return null;
        Matcher matcher = INSTANCE_LINE.matcher(line);
        if (!matcher.matches()) return null;
        String instance = matcher.group(1).trim();
        return instance.isEmpty() ? null : instance;
    }

    private void updateInstance() {
        List<String> lines = ServerUtil.getScoreboardLines();
        String instance = null;
        for (String line : lines) {
            instance = extractInstance(line);
            if (instance != null) break;
        }
        if (instance == null) return;

        if (this.awaitingInstance && this.lastInstance != null && !this.lastInstance.equals(instance)) {
            this.successful = false;
            this.phase = Phase.SEARCHING;
            notifyUser(NotificationManager.NotificationType.WARNING, "disabler-instance",
                    "Waiting to disable Grim again");
        }
        this.lastInstance = instance;
        this.awaitingInstance = false;
    }

    private void completeAttempt() {
        this.restorePlayerState();
        this.successful = true;
        this.phase = Phase.SUCCESS;
        notifyUser(NotificationManager.NotificationType.ENABLED, "disabler-success",
                "Grim disabled successfully!");
    }

    private void cancelAttempt() {
        this.restorePlayerState();
        this.phase = Phase.SEARCHING;
    }

    private void restorePlayerState() {
        if (mc.thePlayer != null && this.originalSlot >= 0) {
            mc.thePlayer.inventory.currentItem = this.originalSlot;
            syncSelectedSlot();
        }
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
        this.selectedSlot = -1;
        this.originalSlot = -1;
        this.delayTicks = 0;
        this.mountGraceTicks = 0;
        this.crouchTicks = 0;
    }

    private void syncSelectedSlot() {
        if (mc.playerController != null) {
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        }
    }

    private static void notifyUser(NotificationManager.NotificationType type, String slot, String message) {
        if (Myau.notificationManager != null) {
            Myau.notificationManager.add(type, slot, "Disabler", message, false);
        }
    }
}

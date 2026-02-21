package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.RightClickMouseEvent;
import myau.module.Module;
import myau.util.ItemUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings.GameType;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.LinkedList;

public class DoubleClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Robot bot;
    private boolean ignNL = false;
    private boolean ignNR = false;

    private final LinkedList<Long> leftClickTimes = new LinkedList<>();
    private final LinkedList<Long> rightClickTimes = new LinkedList<>();

    public final BooleanProperty leftClick = new BooleanProperty("left-click", true);
    public final FloatProperty chanceLeft = new FloatProperty("chance-left", 80.0F, 0.0F, 100.0F, this.leftClick::getValue);
    public final BooleanProperty weaponOnly = new BooleanProperty("weapon-only", true, this.leftClick::getValue);
    public final BooleanProperty onlyWhileTargeting = new BooleanProperty("only-while-targeting", false, this.leftClick::getValue);
    public final BooleanProperty aboveCPSLeft = new BooleanProperty("above-5-cps", false, this.leftClick::getValue);

    public final BooleanProperty rightClick = new BooleanProperty("right-click", false);
    public final FloatProperty chanceRight = new FloatProperty("chance-right", 80.0F, 0.0F, 100.0F, this.rightClick::getValue);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true, this.rightClick::getValue);
    public final BooleanProperty aboveCPSRight = new BooleanProperty("above-5-cps-right", false, this.rightClick::getValue);

    public final BooleanProperty disableInCreative = new BooleanProperty("disable-in-creative", true);

    public DoubleClicker() {
        super("DoubleClicker", false);
    }

    private int getLeftCPS() {
        long now = System.currentTimeMillis();
        leftClickTimes.removeIf(t -> now - t > 1000L);
        return leftClickTimes.size();
    }

    private int getRightCPS() {
        long now = System.currentTimeMillis();
        rightClickTimes.removeIf(t -> now - t > 1000L);
        return rightClickTimes.size();
    }

    @Override
    public void onEnabled() {
        try {
            this.bot = new Robot();
        } catch (AWTException e) {
            this.bot = null;
        }
        this.ignNL = false;
        this.ignNR = false;
        this.leftClickTimes.clear();
        this.rightClickTimes.clear();
    }

    @Override
    public void onDisabled() {
        this.ignNL = false;
        this.ignNR = false;
        this.bot = null;
        this.leftClickTimes.clear();
        this.rightClickTimes.clear();
    }

    @EventTarget(Priority.HIGH)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (!this.isEnabled() || this.bot == null) return;
        if (this.disableInCreative.getValue() && mc.playerController.getCurrentGameType() == GameType.CREATIVE) return;
        if (mc.currentScreen != null) return;

        if (this.ignNL) {
            this.ignNL = false;
            return;
        }

        leftClickTimes.add(System.currentTimeMillis());

        if (!this.leftClick.getValue() || this.chanceLeft.getValue() == 0.0F) return;
        if (this.weaponOnly.getValue() && !ItemUtil.isHoldingSword()) return;
        if (this.onlyWhileTargeting.getValue() && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) return;

        if (this.aboveCPSLeft.getValue() && getLeftCPS() <= 5) return;

        if (this.chanceLeft.getValue() < 100.0F) {
            if (Math.random() >= this.chanceLeft.getValue() / 100.0F) {
                if (!org.lwjgl.input.Mouse.isButtonDown(0)) {
                    this.bot.mouseRelease(InputEvent.BUTTON1_MASK);
                }
                return;
            }
        }

        this.bot.mouseRelease(InputEvent.BUTTON1_MASK);
        this.bot.mousePress(InputEvent.BUTTON1_MASK);
        this.ignNL = true;
    }

    @EventTarget(Priority.HIGH)
    public void onRightClick(RightClickMouseEvent event) {
        if (!this.isEnabled() || this.bot == null) return;
        if (this.disableInCreative.getValue() && mc.playerController.getCurrentGameType() == GameType.CREATIVE) return;
        if (mc.currentScreen != null) return;

        if (this.ignNR) {
            this.ignNR = false;
            return;
        }

        rightClickTimes.add(System.currentTimeMillis());

        if (!this.rightClick.getValue() || this.chanceRight.getValue() == 0.0F) return;

        if (this.blocksOnly.getValue()) {
            ItemStack item = mc.thePlayer.getHeldItem();
            if (item == null || !(item.getItem() instanceof ItemBlock)) {
                if (!org.lwjgl.input.Mouse.isButtonDown(1)) {
                    this.bot.mouseRelease(InputEvent.BUTTON3_MASK);
                }
                return;
            }
        }

        if (this.aboveCPSRight.getValue() && getRightCPS() <= 5) return;

        if (this.chanceRight.getValue() < 100.0F) {
            if (Math.random() >= this.chanceRight.getValue() / 100.0F) {
                if (!org.lwjgl.input.Mouse.isButtonDown(1)) {
                    this.bot.mouseRelease(InputEvent.BUTTON3_MASK);
                }
                return;
            }
        }

        this.bot.mouseRelease(InputEvent.BUTTON3_MASK);
        this.bot.mousePress(InputEvent.BUTTON3_MASK);
        this.ignNR = true;
    }
}
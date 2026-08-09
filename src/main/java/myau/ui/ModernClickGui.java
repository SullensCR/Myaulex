package myau.ui;

import myau.Myau;
import myau.module.modules.GuiModule;
import myau.render.ui.UiRenderer;
import myau.render.ui.UiTransform;
import myau.ui.modern.ClickGuiTheme;
import myau.ui.modern.ClickGuiView;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thin Minecraft screen adapter for the reusable modern UI and renderer.
 */
public final class ModernClickGui extends GuiScreen implements ClickGuiScreen {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-UI");

    private UiRenderer renderer;
    private ClickGuiView view;
    private UiTransform transform;
    private boolean fallbackRequested;
    private long openedAt;
    private long closingAt = -1L;
    private int transitionType;

    @Override
    public void initGui() {
        fallbackRequested = false;
        openedAt = System.currentTimeMillis();
        closingAt = -1L;
        transitionType = ThreadLocalRandom.current().nextInt(3);
        try {
            if (renderer == null) renderer = new UiRenderer();
            if (!renderer.isSupported()) {
                throw new IllegalStateException("OpenGL 2.0 framebuffers and GLSL 1.20 are required");
            }
            if (view == null) view = new ClickGuiView();
            view.resetEntrance();
            updateTransform();
        } catch (Throwable failure) {
            fallback(failure);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (fallbackRequested || renderer == null || view == null) return;
        updateTransform();
        float designMouseX = transform.mouseX(mouseX);
        float designMouseY = transform.mouseY(mouseY);
        UiRenderer activeRenderer = renderer;
        boolean frameStarted = false;
        Throwable renderFailure = null;
        try {
            activeRenderer.beginFrame(transform, 25.0F);
            frameStarted = true;
            float progress = screenProgress();
            view.render(activeRenderer, designMouseX, designMouseY, progress, transitionType);
        } catch (Throwable failure) {
            renderFailure = failure;
        } finally {
            if (frameStarted) activeRenderer.endFrame();
        }
        if (renderFailure != null) fallback(renderFailure);
        if (closingAt >= 0L && screenProgress() <= 0.001F) {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!acceptsInput()) return;
        if (view != null && transform != null
                && view.mouseClicked(transform.mouseX(mouseX), transform.mouseY(mouseY), mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!acceptsInput()) return;
        if (view != null && transform != null) {
            view.mouseDragged(transform.mouseX(mouseX), transform.mouseY(mouseY), clickedMouseButton);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (view != null) view.mouseReleased();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (view != null && acceptsInput()) view.scroll(Mouse.getEventDWheel());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (view != null) view.keyTyped(typedChar, keyCode);
            beginClose();
            return;
        }
        if (!acceptsInput()) return;
        if (view != null && view.keyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        if (view != null) view.mouseReleased();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public boolean isTextInputFocused() {
        return view != null && view.isTextInputFocused();
    }

    private void updateTransform() {
        float userScale = Myau.clientSettings == null ? 1.0F : Myau.clientSettings.getClickGuiScale();
        transform = new UiTransform(
                mc,
                ClickGuiTheme.DESIGN_WIDTH,
                ClickGuiTheme.DESIGN_HEIGHT,
                userScale,
                10.0F
        );
    }

    private void beginClose() {
        if (closingAt < 0L) closingAt = System.currentTimeMillis();
    }

    private float screenProgress() {
        if (closingAt >= 0L) {
            return Math.max(0.0F, 1.0F - (System.currentTimeMillis() - closingAt) / 220.0F);
        }
        return Math.min(1.0F, (System.currentTimeMillis() - openedAt) / 220.0F);
    }

    private boolean acceptsInput() {
        return closingAt < 0L && screenProgress() >= 0.98F;
    }

    private void fallback(Throwable failure) {
        if (fallbackRequested) return;
        fallbackRequested = true;
        LOGGER.error(
                "Modern ClickGUI failed. vendor={} renderer={} OpenGL={} GLSL={} physical={}x{} guiScale={} "
                        + "uiScale={} logicalOrigin=({}, {}) glError={}",
                safeGlString(GL11.GL_VENDOR),
                safeGlString(GL11.GL_RENDERER),
                safeGlString(GL11.GL_VERSION),
                safeGlString(org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION),
                mc.displayWidth,
                mc.displayHeight,
                transform == null ? "unknown" : transform.getGuiScale(),
                transform == null ? "unknown" : transform.getPhysicalScale(),
                transform == null ? "unknown" : transform.getLogicalX(),
                transform == null ? "unknown" : transform.getLogicalY(),
                GL11.glGetError(),
                failure
        );
        disposeRenderer();
        GuiModule gui = (GuiModule) Myau.moduleManager.getModule(GuiModule.class);
        if (gui != null) gui.openOldGuiAfterModernFailure();
    }

    private void disposeRenderer() {
        if (renderer != null) {
            try {
                renderer.delete();
            } catch (Throwable cleanupFailure) {
                LOGGER.error("Failed to dispose modern UI renderer; glError={}", GL11.glGetError(), cleanupFailure);
            }
        }
        renderer = null;
        view = null;
        transform = null;
    }

    private static String safeGlString(int name) {
        try {
            String value = GL11.glGetString(name);
            return value == null ? "unknown" : value;
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }
}

package myau.ui;

import myau.Myau;
import myau.config.Config;
import myau.module.modules.GuiModule;
import myau.module.modules.TransactionAnalyzer;
import myau.render.ui.*;
import myau.media.MprisService;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.achievement.GuiStats;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.fml.client.GuiModList;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Figma-derived replacement for Minecraft's in-game pause screen. */
public final class MyauPauseScreen extends GuiScreen {
    private static final float DESIGN_WIDTH = 510.0F;
    private static final float DESIGN_HEIGHT = 568.0F;
    private static final UiBounds[] BUTTONS = {
            new UiBounds(55, 330, 400, 41),
            new UiBounds(55, 379, 400, 41),
            new UiBounds(55, 428, 192, 41),
            new UiBounds(263, 428, 192, 41),
            new UiBounds(55, 477, 192, 41),
            new UiBounds(263, 477, 192, 41),
            new UiBounds(55, 526, 192, 41),
            new UiBounds(263, 526, 192, 41)
    };
    private static final String[] LABELS = {
            "Resume", "Server list", "Statistics", "Configs",
            "Options", "Forge Mods", "Reconnect", "Disconnect"
    };
    private static UiRenderer sharedRenderer;
    private static final UiBounds PREVIOUS = new UiBounds(351, 267, 24, 24);
    private static final UiBounds PLAY_PAUSE = new UiBounds(386, 263, 32, 32);
    private static final UiBounds NEXT = new UiBounds(429, 267, 24, 24);
    private UiTransform transform;
    private long openedAt;

    @Override
    public void initGui() {
        openedAt = System.currentTimeMillis();
        if (sharedRenderer == null) sharedRenderer = new UiRenderer();
        updateTransform();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateTransform();
        float mx = transform.mouseX(mouseX);
        float my = transform.mouseY(mouseY);
        sharedRenderer.beginFrame(transform, 15.5F);
        float progress = Math.min(1.0F, (System.currentTimeMillis() - openedAt) / 220.0F);
        progress = 1.0F - (1.0F - progress) * (1.0F - progress);
        float scale = 0.96F + 0.04F * progress;
        GL11.glTranslatef(DESIGN_WIDTH / 2.0F, DESIGN_HEIGHT / 2.0F, 0);
        GL11.glScalef(scale, scale, 1);
        GL11.glTranslatef(-DESIGN_WIDTH / 2.0F, -DESIGN_HEIGHT / 2.0F, 0);
        renderPanel(mx, my);
        sharedRenderer.endFrame();
    }

    private void renderPanel(float mouseX, float mouseY) {
        sharedRenderer.shadow(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT, 20, 0, 3, 10, 5, 0x63000000);
        sharedRenderer.backdrop(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT, 20, 0x801A1A24);
        UiFont logo = sharedRenderer.fonts().snPro(58, UiFonts.BLACK);
        UiFont regular = sharedRenderer.fonts().snPro(23, UiFonts.REGULAR);
        UiFont bold = sharedRenderer.fonts().snPro(23, UiFonts.SEMIBOLD);

        logo.draw("Myaulex", (DESIGN_WIDTH - logo.width("Myaulex")) / 2.0F, 18, 0xFF76FFCF, true);
        TransactionAnalyzer analyzer = (TransactionAnalyzer) Myau.moduleManager.modules.get(TransactionAnalyzer.class);
        String ac = analyzer == null ? "Unknown" : analyzer.getDetected();
        regular.draw("AC: " + ac, 270, 106, 0xFFB0A7F7, true);
        UiFont meta = sharedRenderer.fonts().mojang(13);
        ServerData currentServer = mc.getCurrentServerData();
        String connection = mc.isIntegratedServerRunning() ? "Singleplayer"
                : currentServer == null ? "Disconnected" : currentServer.serverIP;
        String build = Myau.version == null ? "dev" : Myau.version;
        meta.draw(ellipsis("Minecraft 1.8.9  •  Myaulex " + build + "  •  " + connection,
                meta, 400), 55, 177, 0xFFC6C3D7, true);
        sharedRenderer.roundedRect(2, 198, 506, 3, 1.5F, 0xFFDCDFFF);
        renderMedia(mouseX, mouseY, regular, bold);

        for (int i = 0; i < BUTTONS.length; i++) {
            UiBounds button = BUTTONS[i];
            boolean hovered = button.contains(mouseX, mouseY);
            sharedRenderer.shadow(button.x, button.y, button.width, button.height, 5, 0, 4, 5, 0, 0x40000000);
            sharedRenderer.roundedRect(button.x, button.y, button.width, button.height, 5,
                    hovered ? 0xB3464E64 : 0x801A1A24);
            float textX = button.x + (button.width - regular.width(LABELS[i])) / 2.0F;
            regular.draw(LABELS[i], textX, button.y + 5, 0xFFFFFFFF, true);
        }
    }

    private void renderMedia(float mouseX, float mouseY, UiFont regular, UiFont bold) {
        MprisService.Snapshot media = MprisService.getInstance().snapshot();
        if (!media.available) return;
        sharedRenderer.shadow(55, 216, 400, 96, 10, 0, 3, 7, 0, 0x40000000);
        sharedRenderer.roundedRect(55, 216, 400, 96, 10, 0x8A252635);
        sharedRenderer.roundedRect(69, 230, 66, 66, 8, 0xFF3D4054);
        UiFont note = sharedRenderer.fonts().snPro(32, UiFonts.SEMIBOLD);
        note.draw("♪", 91, 244, 0xFF76FFCF, true);
        bold.draw(ellipsis(media.title, bold, 183), 151, 229, 0xFFFFFFFF, true);
        regular.draw(ellipsis(media.artist.isEmpty() ? media.album : media.artist, regular, 183),
                151, 260, 0xFFB9B6CB, true);
        drawControl(PREVIOUS, mouseX, mouseY, "‹", media.canPrevious, regular);
        drawControl(PLAY_PAUSE, mouseX, mouseY,
                "Playing".equals(media.playbackStatus) ? "Ⅱ" : "▶", media.canPlayPause, regular);
        drawControl(NEXT, mouseX, mouseY, "›", media.canNext, regular);
    }

    private void drawControl(UiBounds bounds, float mouseX, float mouseY, String label,
                             boolean enabled, UiFont font) {
        int color = enabled ? (bounds.contains(mouseX, mouseY) ? 0xFF76FFCF : 0xFFFFFFFF) : 0xFF666776;
        font.draw(label, bounds.x + (bounds.width - font.width(label)) / 2.0F, bounds.y, color, true);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0 || transform == null) return;
        float mx = transform.mouseX(mouseX);
        float my = transform.mouseY(mouseY);
        MprisService.Snapshot media = MprisService.getInstance().snapshot();
        if (media.available) {
            if (media.canPrevious && PREVIOUS.contains(mx, my)) {
                MprisService.getInstance().previous();
                return;
            }
            if (media.canPlayPause && PLAY_PAUSE.contains(mx, my)) {
                MprisService.getInstance().playPause();
                return;
            }
            if (media.canNext && NEXT.contains(mx, my)) {
                MprisService.getInstance().next();
                return;
            }
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            if (BUTTONS[i].contains(mx, my)) {
                activate(i);
                return;
            }
        }
    }

    private void activate(int index) {
        switch (index) {
            case 0:
                mc.displayGuiScreen(null);
                mc.setIngameFocus();
                break;
            case 1:
                mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case 2:
                mc.displayGuiScreen(new GuiStats(this, mc.thePlayer.getStatFileWriter()));
                break;
            case 3:
                GuiModule gui = (GuiModule) Myau.moduleManager.modules.get(GuiModule.class);
                if (gui != null) gui.openSelectedGui();
                break;
            case 4:
                mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
                break;
            case 5:
                mc.displayGuiScreen(new GuiModList(this));
                break;
            case 6:
                ServerData server = mc.getCurrentServerData();
                if (server != null) {
                    Config.savePersistent();
                    mc.loadWorld(null);
                    mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(new GuiMainMenu()), mc, server));
                }
                break;
            case 7:
                boolean singleplayer = mc.isIntegratedServerRunning();
                Config.savePersistent();
                mc.loadWorld(null);
                mc.displayGuiScreen(singleplayer ? new GuiMainMenu() : new GuiMultiplayer(new GuiMainMenu()));
                break;
            default:
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    private void updateTransform() {
        transform = new UiTransform(mc, DESIGN_WIDTH, DESIGN_HEIGHT, 1.0F, 10.0F);
    }

    private static String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }

    private static String ellipsis(String value, UiFont font, float maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String result = value;
        while (!result.isEmpty() && font.width(result + "…") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }
}

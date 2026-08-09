package myau.ui.modern.component;

import myau.Myau;
import myau.module.Module;
import myau.render.ui.UiBounds;
import myau.render.ui.UiFont;
import myau.render.ui.UiFonts;
import myau.render.ui.UiRenderer;
import myau.ui.modern.ClickGuiTheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Real non-module Client settings navigator. Section rows open working detail
 * pages backed by the same property controls used by ordinary modules.
 */
public final class ClientSettingsView {
    public static final UiBounds GEAR = new UiBounds(163, 18, 32, 34);
    private static final float X = ClickGuiTheme.CONTENT_X;
    private static final float Y = ClickGuiTheme.MODULE_TOP;
    private static final float WIDTH = ClickGuiTheme.CONTENT_WIDTH;
    private static final float HEIGHT = ClickGuiTheme.MODULE_BOTTOM - ClickGuiTheme.MODULE_TOP;
    private static final String[] SECTIONS = {
            "Interface", "HUD", "Visuals", "Gameplay", "Targeting", "Privacy"
    };
    private static final String[][] MODULES = {
            {"ClickGui"},
            {"HUD", "TargetHUD", "Fpscounter", "FloatingIsland", "DynamicIsland"},
            {"Animations", "Capes", "Fullbright", "NoHurtCam"},
            {"NoHitDelay", "NoJumpDelay", "AntiObfuscate"},
            {},
            {"NickHider", "ServerHider"}
    };

    private final Map<String, ModuleCard> cards = new HashMap<>();
    private final Map<String, Float> toggleAnimations = new HashMap<>();
    private int section = -1;
    private ModuleCard expanded;
    private float scroll;
    private float targetScroll;
    private float maxScroll;
    private long lastFrameNanos = System.nanoTime();
    private float frameDelta;

    public ClientSettingsView() {
        if (Myau.moduleManager != null) {
            for (Module module : Myau.moduleManager.modules.values()) {
                if (Myau.clientSettings != null
                        && myau.config.ClientSettings.isIntegratedModuleName(module.getName())) {
                    cards.put(module.getName(), new ModuleCard(module));
                }
            }
        }
    }

    public void renderGear(UiRenderer renderer, boolean open) {
        renderer.shadow(GEAR.x, GEAR.y, GEAR.width, GEAR.height, 10, 0, 2, 4, 0, 0x4D000000);
        renderer.roundedRect(GEAR.x, GEAR.y, GEAR.width, GEAR.height, 10,
                open ? ClickGuiTheme.TOGGLE_ON : ClickGuiTheme.SEARCH);
        UiFont font = renderer.fonts().google(18, UiFonts.SEMIBOLD);
        font.draw("⚙", GEAR.x + (GEAR.width - font.width("⚙")) / 2.0F, GEAR.y + 5, 0xFFFFFFFF, true);
    }

    public void render(UiRenderer renderer, float mouseX, float mouseY) {
        updateTime();
        renderer.shadow(X, Y, WIDTH, HEIGHT, 8, 0, 2, 6, 1, 0x70000000);
        renderer.roundedRect(X, Y, WIDTH, HEIGHT, 8, ClickGuiTheme.MODULE_SETTINGS);
        UiFont title = renderer.fonts().google(24, UiFonts.SEMIBOLD);
        UiFont text = renderer.fonts().google(18, UiFonts.REGULAR);

        if (section < 0) {
            title.draw("Client Settings", X + 18, Y + 14, 0xFFFFFFFF, true);
            float sectionY = Y + 58;
            for (String name : SECTIONS) {
                boolean hovered = new UiBounds(X + 12, sectionY, WIDTH - 24, 48).contains(mouseX, mouseY);
                renderer.roundedRect(X + 12, sectionY, WIDTH - 24, 48, 5,
                        hovered ? 0xFF35394D : ClickGuiTheme.MODULE);
                text.draw(name, X + 24, sectionY + 11, 0xFFFFFFFF, true);
                text.draw("›", X + WIDTH - 38, sectionY + 11, 0xFFB9B6CB, true);
                sectionY += 56;
            }
            return;
        }

        text.draw("‹", X + 16, Y + 14, 0xFFFFFFFF, true);
        title.draw(SECTIONS[section], X + 43, Y + 12, 0xFFFFFFFF, true);
        renderDirectControls(renderer, text);
        renderCards(renderer, mouseX, mouseY);
    }

    private void renderDirectControls(UiRenderer renderer, UiFont text) {
        if (section == 0) {
            float row = Y + 66;
            label(text, "ClickGUI scale", row);
            drawButton(renderer, X + 238, row - 3, 34, 28, "-");
            String scale = String.format(Locale.ROOT, "%.2f", Myau.clientSettings.getClickGuiScale());
            text.draw(scale, X + 279, row, 0xFFFFFFFF);
            drawButton(renderer, X + 337, row - 3, 34, 28, "+");
            row += 43;
            label(text, "Renderer style", row);
            drawButton(renderer, X + 238, row - 3, 133, 30, Myau.clientSettings.getClickGuiStyle());
        } else if (section == 3) {
            float row = Y + 66;
            label(text, "Verify TCP_NODELAY", row);
            drawToggle(renderer, "tcp", X + 333, row, Myau.clientSettings.isVerifyTcpNoDelay());
        } else if (section == 2) {
            float row = Y + 66;
            label(text, "Indicator", row);
            drawToggle(renderer, "indicator", X + 333, row, Myau.clientSettings.isIndicatorEnabled());
        } else if (section == 4) {
            float row = Y + 66;
            modeRow(renderer, text, "Move correction", moveFixName(), row);
            row += 43;
            modeRow(renderer, text, "Bot filter", botFilterName(), row);
            row += 43;
            modeRow(renderer, text, "Teams", teamsName(), row);
            row += 49;
            booleanRow(renderer, text, "Players", "players", Myau.clientSettings.isTargetPlayers(), row);
            row += 43;
            booleanRow(renderer, text, "Mobs", "mobs", Myau.clientSettings.isTargetMobs(), row);
            row += 43;
            booleanRow(renderer, text, "Animals", "animals", Myau.clientSettings.isTargetAnimals(), row);
        }
    }

    private void renderCards(UiRenderer renderer, float mouseX, float mouseY) {
        List<ModuleCard> visible = visibleCards();
        if (visible.isEmpty()) return;
        float top = cardsTop();
        float contentHeight = 0;
        for (ModuleCard card : visible) contentHeight += card.height() + 9;
        maxScroll = Math.max(0, contentHeight - (Y + HEIGHT - top));
        targetScroll = clamp(targetScroll, 0, maxScroll);
        float blend = ease(Math.min(1.0F, frameDelta / 0.18F));
        scroll += (targetScroll - scroll) * blend;
        UiBounds clip = new UiBounds(X, top, WIDTH, Y + HEIGHT - top);
        renderer.pushClip(clip);
        float cardY = top - scroll;
        for (ModuleCard card : visible) {
            float height = card.height();
            if (cardY + height >= top && cardY <= Y + HEIGHT) {
                card.render(renderer, X, cardY, mouseX, mouseY);
            }
            cardY += height + 9;
        }
        renderer.popClip();
        if (expanded != null && expanded.hasOpenPopup()) expanded.renderPopup(renderer);
    }

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0 && button != 1) return false;
        if (section < 0) {
            float sectionY = Y + 58;
            for (int i = 0; i < SECTIONS.length; i++) {
                if (new UiBounds(X + 12, sectionY, WIDTH - 24, 48).contains(mouseX, mouseY)) {
                    section = i;
                    scroll = targetScroll = 0;
                    collapseExpanded();
                    return true;
                }
                sectionY += 56;
            }
            return new UiBounds(X, Y, WIDTH, HEIGHT).contains(mouseX, mouseY);
        }
        if (new UiBounds(X + 8, Y + 7, 36, 38).contains(mouseX, mouseY) && button == 0) {
            section = -1;
            collapseExpanded();
            return true;
        }
        if (expanded != null && expanded.hasOpenPopup()) {
            return expanded.handlePopupClick(mouseX, mouseY, button);
        }
        if (button == 0 && handleDirectClick(mouseX, mouseY)) return true;

        float top = cardsTop();
        float cardY = top - scroll;
        for (ModuleCard card : visibleCards()) {
            float height = card.height();
            if (mouseY >= cardY && mouseY <= cardY + height) {
                ModuleCard.ClickResult result = card.mouseClicked(mouseX, mouseY, button);
                if (result == ModuleCard.ClickResult.EXPAND) {
                    if (expanded != null && expanded != card) expanded.setExpanded(false);
                    boolean next = !card.isExpanded();
                    card.setExpanded(next);
                    expanded = next ? card : null;
                    return true;
                }
                return result == ModuleCard.ClickResult.CONSUMED;
            }
            cardY += height + 9;
        }
        return new UiBounds(X, Y, WIDTH, HEIGHT).contains(mouseX, mouseY);
    }

    private boolean handleDirectClick(float mouseX, float mouseY) {
        if (section == 0) {
            float row = Y + 66;
            if (new UiBounds(X + 238, row - 3, 34, 28).contains(mouseX, mouseY)) {
                Myau.clientSettings.setClickGuiScale(Myau.clientSettings.getClickGuiScale() - 0.05F);
                return true;
            }
            if (new UiBounds(X + 337, row - 3, 34, 28).contains(mouseX, mouseY)) {
                Myau.clientSettings.setClickGuiScale(Myau.clientSettings.getClickGuiScale() + 0.05F);
                return true;
            }
            row += 43;
            if (new UiBounds(X + 238, row - 3, 133, 30).contains(mouseX, mouseY)) {
                Myau.clientSettings.setClickGuiStyle(
                        "MODERN".equals(Myau.clientSettings.getClickGuiStyle()) ? "OLD" : "MODERN");
                return true;
            }
        } else if (section == 2) {
            if (toggleAt(Y + 66).contains(mouseX, mouseY)) {
                Myau.clientSettings.setIndicatorEnabled(!Myau.clientSettings.isIndicatorEnabled());
                return true;
            }
        } else if (section == 3) {
            if (new UiBounds(X + 325, Y + 59, 54, 35).contains(mouseX, mouseY)) {
                Myau.clientSettings.setVerifyTcpNoDelay(!Myau.clientSettings.isVerifyTcpNoDelay());
                return true;
            }
        } else if (section == 4) {
            float row = Y + 66;
            if (buttonAt(row).contains(mouseX, mouseY)) { Myau.clientSettings.cycleMoveFixMode(); return true; }
            row += 43;
            if (buttonAt(row).contains(mouseX, mouseY)) { Myau.clientSettings.cycleBotFilterMode(); return true; }
            row += 43;
            if (buttonAt(row).contains(mouseX, mouseY)) { Myau.clientSettings.cycleTeamsMode(); return true; }
            row += 49;
            if (toggleAt(row).contains(mouseX, mouseY)) {
                Myau.clientSettings.setTargetPlayers(!Myau.clientSettings.isTargetPlayers()); return true;
            }
            row += 43;
            if (toggleAt(row).contains(mouseX, mouseY)) {
                Myau.clientSettings.setTargetMobs(!Myau.clientSettings.isTargetMobs()); return true;
            }
            row += 43;
            if (toggleAt(row).contains(mouseX, mouseY)) {
                Myau.clientSettings.setTargetAnimals(!Myau.clientSettings.isTargetAnimals()); return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(float mouseX, float mouseY, int button) {
        return expanded != null && expanded.mouseDragged(mouseX, mouseY, button);
    }

    public void mouseReleased() {
        for (ModuleCard card : cards.values()) card.mouseReleased();
    }

    public boolean keyTyped(char character, int keyCode) {
        for (ModuleCard card : visibleCards()) {
            if (card.hasInputCapture() && card.keyTyped(character, keyCode)) return true;
        }
        return false;
    }

    public boolean isTextInputFocused() {
        for (ModuleCard card : cards.values()) {
            if (card.isTextInputFocused()) return true;
        }
        return false;
    }

    public void discardTextEditors() {
        for (ModuleCard card : cards.values()) card.discardTextEditor();
    }

    public void scroll(int wheel) {
        if (wheel == 0 || section < 0) return;
        targetScroll = clamp(targetScroll + (wheel < 0 ? 48 : -48), 0, maxScroll);
    }

    public void close() {
        section = -1;
        collapseExpanded();
        for (ModuleCard card : cards.values()) card.discardEditor();
    }

    private List<ModuleCard> visibleCards() {
        List<ModuleCard> result = new ArrayList<>();
        if (section < 0) return result;
        for (String name : MODULES[section]) {
            ModuleCard card = cards.get(name);
            if (card != null) result.add(card);
        }
        return result;
    }

    private float cardsTop() {
        if (section == 0) return Y + 161;
        if (section == 2) return Y + 111;
        if (section == 3) return Y + 111;
        if (section == 4) return Y + HEIGHT;
        return Y + 58;
    }

    private void collapseExpanded() {
        if (expanded != null) expanded.setExpanded(false);
        expanded = null;
    }

    private void modeRow(UiRenderer renderer, UiFont text, String label, String value, float y) {
        label(text, label, y);
        drawButton(renderer, X + 238, y - 3, 133, 30, value);
    }

    private void booleanRow(UiRenderer renderer, UiFont text, String label, String key, boolean value, float y) {
        label(text, label, y);
        drawToggle(renderer, key, X + 333, y, value);
    }

    private void drawToggle(UiRenderer renderer, String key, float x, float y, boolean enabled) {
        float state = toggleAnimations.containsKey(key) ? toggleAnimations.get(key) : (enabled ? 1.0F : 0.0F);
        state += ((enabled ? 1.0F : 0.0F) - state) * ease(Math.min(1.0F, frameDelta / 0.16F));
        toggleAnimations.put(key, state);
        renderer.roundedRect(x, y, 38, 21, 10.5F,
                mix(ClickGuiTheme.TRACK, ClickGuiTheme.TOGGLE_ON, state));
        renderer.roundedRect(x + 4 + state * 17, y + 3, 15, 15, 7.5F, 0xFFD6D9E8);
    }

    private static void label(UiFont text, String value, float y) {
        text.draw(value, X + 24, y, 0xFFFFFFFF, true);
    }

    private static UiBounds buttonAt(float row) { return new UiBounds(X + 238, row - 3, 133, 30); }
    private static UiBounds toggleAt(float row) { return new UiBounds(X + 325, row - 5, 54, 35); }
    private static String moveFixName() { return new String[]{"Off", "Silent", "Strict"}[Myau.clientSettings.getMoveFixMode()]; }
    private static String botFilterName() { return new String[]{"Tablist", "Static", "Off"}[Myau.clientSettings.getBotFilterMode()]; }
    private static String teamsName() { return new String[]{"Armor", "Tab", "Off"}[Myau.clientSettings.getTeamsMode()]; }

    private void updateTime() {
        long now = System.nanoTime();
        frameDelta = Math.min(0.1F, Math.max(0, (now - lastFrameNanos) / 1_000_000_000.0F));
        lastFrameNanos = now;
    }

    private static void drawButton(UiRenderer renderer, float x, float y, float width, float height, String label) {
        renderer.roundedRect(x, y, width, height, 4, ClickGuiTheme.FIELD);
        UiFont font = renderer.fonts().google(16, UiFonts.SEMIBOLD);
        font.draw(label, x + (width - font.width(label)) / 2.0F, y + 3, 0xFFFFFFFF);
    }

    private static float ease(float value) {
        float t = clamp(value, 0, 1);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int mix(int from, int to, float progress) {
        float t = clamp(progress, 0, 1);
        int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = (int) (((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
        int g = (int) (((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }
}

package myau.ui.modern;

import myau.Myau;
import myau.module.Module;
import myau.render.ui.UiBounds;
import myau.render.ui.UiClock;
import myau.render.ui.UiFont;
import myau.render.ui.UiFonts;
import myau.render.ui.UiRenderer;
import myau.ui.modern.component.CategoryBar;
import myau.ui.modern.component.ClientSettingsView;
import myau.ui.modern.component.ModuleCard;
import myau.ui.modern.component.SearchField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.lwjgl.opengl.GL11;

public final class ClickGuiView {
    private static final UiBounds VIEWPORT = new UiBounds(
            ClickGuiTheme.CONTENT_X,
            ClickGuiTheme.MODULE_TOP,
            ClickGuiTheme.CONTENT_WIDTH,
            ClickGuiTheme.MODULE_BOTTOM - ClickGuiTheme.MODULE_TOP
    );

    private final UiClock clock = new UiClock();
    private final SearchField search = new SearchField();
    private final CategoryBar categories;
    private final ClientSettingsView clientSettings = new ClientSettingsView();
    private final List<ModuleCard> cards = new ArrayList<>();
    private ModuleCard expanded;
    private float scroll;
    private float targetScroll;
    private float maxScroll;
    private ClickGuiCategory lastCategory;
    private boolean clientMenuOpen;
    private long entranceStartedAt = System.currentTimeMillis();

    public ClickGuiView() {
        ClickGuiCategory initial = Myau.clientSettings == null
                ? ClickGuiCategory.COMBAT : Myau.clientSettings.getClickGuiCategory();
        categories = new CategoryBar(initial);
        lastCategory = initial;
        scroll = Myau.clientSettings == null ? 0.0F : Myau.clientSettings.getScroll(initial);
        targetScroll = scroll;
        for (Module module : Myau.moduleManager.ordinaryModules()) cards.add(new ModuleCard(module));
        cards.sort(Comparator.comparing(card -> card.module().getName().toLowerCase(Locale.ROOT)));
    }

    public void render(UiRenderer renderer, float mouseX, float mouseY) {
        render(renderer, mouseX, mouseY, 1.0F, 2);
    }

    public void render(UiRenderer renderer, float mouseX, float mouseY,
                       float screenProgress, int transitionType) {
        clock.tick();
        boolean searching = !search.value().trim().isEmpty();
        categories.setSelectionVisible(!searching);
        renderer.shadow(0, 0, ClickGuiTheme.DESIGN_WIDTH, ClickGuiTheme.PANEL_HEIGHT,
                10, 0, 0, 6, 2, ClickGuiTheme.SHADOW);
        renderer.backdrop(0, 0, ClickGuiTheme.DESIGN_WIDTH, ClickGuiTheme.PANEL_HEIGHT, 10, ClickGuiTheme.PANEL);

        GL11.glPushMatrix();
        applyContentTransition(screenProgress, transitionType);
        renderLogo(renderer);
        clientSettings.renderGear(renderer, clientMenuOpen);
        search.render(renderer, mouseX, mouseY);

        if (lastCategory != categories.selected()) {
            if (Myau.clientSettings != null) Myau.clientSettings.setScroll(lastCategory, targetScroll);
            lastCategory = categories.selected();
            scroll = Myau.clientSettings == null ? 0.0F : Myau.clientSettings.getScroll(lastCategory);
            targetScroll = scroll;
            if (Myau.clientSettings != null) Myau.clientSettings.setClickGuiCategory(lastCategory);
            collapseExpanded();
        }

        if (clientMenuOpen) {
            clientSettings.render(renderer, mouseX, mouseY);
        } else {
            List<ModuleCard> visible = visibleCards();
            float contentHeight = 0;
            for (ModuleCard card : visible) contentHeight += card.height() + 9;
            maxScroll = Math.max(0, contentHeight - VIEWPORT.height);
            targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
            float blend = Math.min(1.0F, clock.deltaSeconds() / 0.18F);
            blend = 1.0F - (1.0F - blend) * (1.0F - blend);
            scroll += (targetScroll - scroll) * blend;
            scroll = Math.max(0, Math.min(scroll, maxScroll));

            renderer.pushClip(VIEWPORT);
            float y = VIEWPORT.y - scroll;
            for (ModuleCard card : visible) {
                float height = card.height();
                if (y + height >= VIEWPORT.y && y <= VIEWPORT.y + VIEWPORT.height) {
                    card.render(renderer, VIEWPORT.x, y, mouseX, mouseY);
                }
                y += height + 9;
            }
            renderer.popClip();
            if (expanded != null && expanded.hasOpenPopup()) expanded.renderPopup(renderer);
        }
        GL11.glPopMatrix();
        // The category bar is intentionally stationary during screen open and
        // close, matching the requested motion hierarchy.
        categories.render(renderer, mouseX, mouseY);
    }

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        for (ModuleCard card : cards) card.cancelBinding();
        for (ModuleCard card : cards) card.discardTextEditor();
        clientSettings.discardTextEditors();

        if (ClientSettingsView.GEAR.contains(mouseX, mouseY) && button == 0) {
            clientMenuOpen = !clientMenuOpen;
            if (!clientMenuOpen) clientSettings.close();
            search.blur();
            return true;
        }
        if (clientMenuOpen) {
            ClickGuiCategory before = categories.selected();
            if (categories.mouseClicked(mouseX, mouseY, button)) {
                clientMenuOpen = false;
                clientSettings.close();
                applyCategorySelection(before);
                return true;
            }
            return clientSettings.mouseClicked(mouseX, mouseY, button);
        }
        if (expanded != null && expanded.hasOpenPopup()) {
            return expanded.handlePopupClick(mouseX, mouseY, button);
        }
        if (search.mouseClicked(mouseX, mouseY, button)) return true;
        search.blur();
        ClickGuiCategory before = categories.selected();
        if (categories.mouseClicked(mouseX, mouseY, button)) {
            applyCategorySelection(before);
            return true;
        }

        if (!VIEWPORT.contains(mouseX, mouseY)) {
            if (expanded != null) expanded.mouseClicked(mouseX, mouseY, button);
            return false;
        }

        float y = VIEWPORT.y - scroll;
        for (ModuleCard card : visibleCards()) {
            float height = card.height();
            if (mouseY >= y && mouseY <= y + height) {
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
            y += height + 9;
        }
        return false;
    }

    public boolean mouseDragged(float mouseX, float mouseY, int button) {
        if (clientMenuOpen) return clientSettings.mouseDragged(mouseX, mouseY, button);
        return expanded != null && expanded.mouseDragged(mouseX, mouseY, button);
    }

    public void mouseReleased() {
        clientSettings.mouseReleased();
        for (ModuleCard card : cards) card.mouseReleased();
    }

    public boolean keyTyped(char character, int keyCode) {
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            search.blur();
            for (ModuleCard card : cards) card.discardEditor();
            clientSettings.discardTextEditors();
            return false;
        }
        if (clientMenuOpen && clientSettings.keyTyped(character, keyCode)) return true;
        if (search.keyTyped(character, keyCode)) return true;
        for (ModuleCard card : cards) {
            if (card.hasInputCapture() && card.keyTyped(character, keyCode)) return true;
        }
        return false;
    }

    public boolean isTextInputFocused() {
        if (search.isFocused()) return true;
        if (clientMenuOpen && clientSettings.isTextInputFocused()) return true;
        for (ModuleCard card : cards) {
            if (card.isTextInputFocused()) return true;
        }
        return false;
    }

    public boolean collapseExpanded() {
        if (expanded == null) return false;
        expanded.setExpanded(false);
        expanded = null;
        return true;
    }

    private void applyCategorySelection(ClickGuiCategory before) {
        if (before == categories.selected()) return;
        if (Myau.clientSettings != null) Myau.clientSettings.setScroll(before, targetScroll);
        lastCategory = categories.selected();
        scroll = Myau.clientSettings == null ? 0.0F : Myau.clientSettings.getScroll(lastCategory);
        targetScroll = scroll;
        if (Myau.clientSettings != null) Myau.clientSettings.setClickGuiCategory(lastCategory);
        collapseExpanded();
    }

    public void resetEntrance() {
        entranceStartedAt = System.currentTimeMillis();
        categories.resetEntrance();
    }

    public void scroll(int wheel) {
        if (wheel == 0) return;
        if (clientMenuOpen) {
            clientSettings.scroll(wheel);
            return;
        }
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll + (wheel < 0 ? 48 : -48)));
        if (Myau.clientSettings != null) Myau.clientSettings.setScroll(categories.selected(), targetScroll);
    }

    private List<ModuleCard> visibleCards() {
        String query = search.value().trim().toLowerCase(Locale.ROOT);
        List<ModuleCard> result = new ArrayList<>();
        for (ModuleCard card : cards) {
            Module module = card.module();
            if (query.isEmpty() && ModuleCatalog.category(module) != categories.selected()) continue;
            if (ModuleCatalog.category(module) == null) continue;
            if (!query.isEmpty()) {
                String name = module.getName().toLowerCase(Locale.ROOT);
                String description = module.getDescription() == null ? "" : module.getDescription().toLowerCase(Locale.ROOT);
                if (!name.contains(query) && !description.contains(query)) continue;
            }
            result.add(card);
        }
        if (expanded != null && !result.contains(expanded)) collapseExpanded();
        return result;
    }

    private static void applyContentTransition(float progress, int transitionType) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        t = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        if (transitionType == 0) {
            GL11.glTranslatef(0, -72.0F * (1.0F - t), 0);
        } else if (transitionType == 1) {
            GL11.glTranslatef(0, 72.0F * (1.0F - t), 0);
        } else {
            float scale = 0.90F + 0.10F * t;
            GL11.glTranslatef(ClickGuiTheme.DESIGN_WIDTH / 2.0F,
                    ClickGuiTheme.PANEL_HEIGHT / 2.0F, 0);
            GL11.glScalef(scale, scale, 1);
            GL11.glTranslatef(-ClickGuiTheme.DESIGN_WIDTH / 2.0F,
                    -ClickGuiTheme.PANEL_HEIGHT / 2.0F, 0);
        }
    }

    private void renderLogo(UiRenderer renderer) {
        UiFont logo = renderer.fonts().minecraft(38);
        String value = "MYAULEX";
        float x = 22;
        long elapsed = System.currentTimeMillis() - entranceStartedAt;
        for (int i = 0; i < value.length(); i++) {
            String letter = value.substring(i, i + 1);
            float progress = Math.max(0.0F, Math.min(1.0F, (elapsed - i * 35L) / 360.0F));
            progress = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
            int alpha = Math.max(0, Math.min(255, Math.round(progress * 255.0F)));
            logo.draw(letter, x, 18 - 18 * (1.0F - progress), alpha << 24 | 0x00FFFFFF, true);
            x += logo.width(letter);
        }
    }
}

package myau.ui.modern.component;

import myau.render.ui.UiBounds;
import myau.render.ui.UiComponent;
import myau.render.ui.UiFont;
import myau.render.ui.UiFonts;
import myau.render.ui.UiRenderer;
import myau.ui.modern.ClickGuiTheme;
import org.lwjgl.input.Keyboard;

public final class SearchField implements UiComponent {
    private static final UiBounds BOUNDS = new UiBounds(203, 18, 197, 34);
    private final StringBuilder text = new StringBuilder();
    private boolean focused;

    @Override
    public void render(UiRenderer renderer, float mouseX, float mouseY) {
        renderer.shadow(BOUNDS.x, BOUNDS.y, BOUNDS.width, BOUNDS.height, 17, 0, 3, 6, 1, 0x4D000000);
        renderer.roundedRect(BOUNDS.x, BOUNDS.y, BOUNDS.width, BOUNDS.height, 17, ClickGuiTheme.SEARCH);
        renderer.imageContained("search", BOUNDS.x + 13, BOUNDS.y + 8, 18, 18, 0xFFFFFFFF);

        UiFont font = renderer.fonts().google(20, UiFonts.REGULAR);
        String shown = text.length() == 0 && !focused ? "Search" : text.toString();
        int color = text.length() == 0 && !focused ? 0xB3FFFFFF : ClickGuiTheme.TEXT;
        while (font.width(shown) > 151 && shown.length() > 1) shown = shown.substring(1);
        font.draw(shown, BOUNDS.x + 39, BOUNDS.y + 5, color, true);
        if (focused && (System.currentTimeMillis() / 500L & 1L) == 0L) {
            float cursor = BOUNDS.x + 39 + font.width(shown) + 1;
            renderer.rect(cursor, BOUNDS.y + 7, 1, 20, 0xCCFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        boolean hit = BOUNDS.contains(mouseX, mouseY);
        focused = hit && button == 0;
        return hit;
    }

    @Override
    public boolean keyTyped(char character, int keyCode) {
        if (!focused) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            focused = false;
            return false;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            focused = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK && text.length() > 0) {
            text.deleteCharAt(text.length() - 1);
            return true;
        }
        if (character >= 32 && character != 127 && text.length() < 64) {
            text.append(character);
            return true;
        }
        return true;
    }

    public String value() {
        return text.toString();
    }

    public void blur() {
        focused = false;
    }

    public boolean isFocused() {
        return focused;
    }
}

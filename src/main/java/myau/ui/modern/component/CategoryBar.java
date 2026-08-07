package myau.ui.modern.component;

import myau.render.ui.UiBounds;
import myau.render.ui.UiComponent;
import myau.render.ui.UiFont;
import myau.render.ui.UiFonts;
import myau.render.ui.UiRenderer;
import myau.ui.modern.ClickGuiCategory;
import myau.ui.modern.ClickGuiTheme;

public final class CategoryBar implements UiComponent {
    private static final UiBounds BAR = new UiBounds(49, 780, 320, 66);
    private ClickGuiCategory selected;
    private ClickGuiCategory previous;
    private long selectionChangedAt;
    private boolean selectionVisible = true;
    private float visibleProgress = 1.0F;
    private long lastFrameNanos = System.nanoTime();
    private long entranceStartedAt = System.currentTimeMillis();

    public CategoryBar() {
        this(ClickGuiCategory.COMBAT);
    }

    public CategoryBar(ClickGuiCategory selected) {
        this.selected = selected == null ? ClickGuiCategory.COMBAT : selected;
    }

    @Override
    public void render(UiRenderer renderer, float mouseX, float mouseY) {
        long nowNanos = System.nanoTime();
        float delta = Math.min(0.1F, Math.max(0.0F, (nowNanos - lastFrameNanos) / 1_000_000_000.0F));
        lastFrameNanos = nowNanos;
        visibleProgress = approach(visibleProgress, selectionVisible ? 1.0F : 0.0F, delta / 0.18F);
        float change = selectionChangedAt == 0L ? 1.0F
                : Math.min(1.0F, (System.currentTimeMillis() - selectionChangedAt) / 180.0F);
        renderer.shadow(BAR.x, BAR.y, BAR.width, BAR.height, 10, 0, 3, 4, 0, 0x80000000);
        renderer.backdrop(BAR.x, BAR.y, BAR.width, BAR.height, 10, ClickGuiTheme.PANEL);
        renderer.roundedRect(BAR.x, BAR.y + 56, BAR.width, 10, 0, 0, 10, 10, ClickGuiTheme.UNFOCUSED);

        ClickGuiCategory hovered = null;
        ClickGuiCategory[] values = ClickGuiCategory.values();
        for (int i = 0; i < values.length; i++) {
            ClickGuiCategory category = values[i];
            float x = BAR.x + 10 + i * 60;
            UiBounds item = new UiBounds(x, BAR.y, 60, 66);
            if (item.contains(mouseX, mouseY)) hovered = category;
            if (category == previous && change < 1.0F) {
                renderer.gradientRect(x, BAR.y + 56, 60, 10,
                        withAlpha(ClickGuiTheme.CYAN, (1.0F - change) * visibleProgress),
                        withAlpha(0xFF4A8499, 0.0F));
            }
            if (category == selected && visibleProgress > 0.01F) {
                renderer.gradientRect(x, BAR.y + 56, 60, 10,
                        withAlpha(ClickGuiTheme.CYAN, change * visibleProgress),
                        withAlpha(0xFF4A8499, 0.0F));
            }
            float selectedAmount = category == selected ? change * visibleProgress
                    : category == previous ? (1.0F - change) * visibleProgress : 0.0F;
            float iconEntrance = Math.max(0.0F, Math.min(1.0F,
                    (System.currentTimeMillis() - entranceStartedAt - i * 30L) / 320.0F));
            iconEntrance = 1.0F - (1.0F - iconEntrance) * (1.0F - iconEntrance) * (1.0F - iconEntrance);
            float baseAlpha = category == hovered ? 1.0F : 0.70F + 0.30F * selectedAmount;
            int alpha = withAlpha(0xFFFFFFFF, baseAlpha * iconEntrance);
            renderer.imageContained(
                    category.icon(),
                    x + category.iconX(),
                    BAR.y + category.iconY() + 18 * (1.0F - iconEntrance),
                    category.iconWidth(),
                    category.iconHeight(),
                    alpha
            );
        }

        if (hovered != null) {
            UiFont tooltip = renderer.fonts().google(14, UiFonts.REGULAR);
            float width = tooltip.width(hovered.displayName()) + 14;
            float center = BAR.x + 40 + hovered.ordinal() * 60;
            renderer.shadow(center - width / 2, BAR.y - 25, width, 19, 4, 0, 2, 3, 0, 0x66000000);
            renderer.roundedRect(center - width / 2, BAR.y - 25, width, 19, 4, 0xE62B2E3D);
            tooltip.draw(hovered.displayName(), center - tooltip.width(hovered.displayName()) / 2, BAR.y - 24, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0 || !BAR.contains(mouseX, mouseY)) return false;
        int index = Math.min(4, Math.max(0, (int) ((mouseX - BAR.x - 10) / 60)));
        ClickGuiCategory next = ClickGuiCategory.values()[index];
        if (next != selected) {
            previous = selected;
            selected = next;
            selectionChangedAt = System.currentTimeMillis();
        }
        return true;
    }

    public ClickGuiCategory selected() {
        return selected;
    }

    public void select(ClickGuiCategory category) {
        if (category != null && category != selected) {
            previous = selected;
            selected = category;
            selectionChangedAt = System.currentTimeMillis();
        }
    }

    public void setSelectionVisible(boolean visible) {
        selectionVisible = visible;
    }

    public void resetEntrance() {
        entranceStartedAt = System.currentTimeMillis();
    }

    private static float approach(float value, float target, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        t = 1.0F - (1.0F - t) * (1.0F - t);
        return value + (target - value) * t;
    }

    private static int withAlpha(int color, float alpha) {
        int value = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return color & 0x00FFFFFF | value << 24;
    }
}

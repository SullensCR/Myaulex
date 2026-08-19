package myau.ui.modern.component;

import myau.Myau;
import myau.config.Config;
import myau.management.NotificationManager;
import myau.module.Module;
import myau.property.Property;
import myau.property.RecommendedRange;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.DragProperty;
import myau.property.properties.FileProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.KeyBindProperty;
import myau.property.properties.LongProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.TextProperty;
import myau.render.ui.UiBounds;
import myau.render.ui.UiFont;
import myau.render.ui.UiFonts;
import myau.render.ui.UiRenderer;
import myau.ui.modern.ClickGuiTheme;
import myau.ui.dataset.SliderStops;
import myau.util.KeyBindUtil;
import org.lwjgl.input.Keyboard;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModuleCard {
    private static final int MAX_VISIBLE_POPUP_ENTRIES = 5;
    private static final float POPUP_ENTRY_HEIGHT = 20.0F;
    private static final float POPUP_PADDING = 4.0F;

    private final Module module;
    private float x;
    private float y;
    private boolean expanded;
    private float expansionProgress;
    private float toggleProgress;
    private long lastAnimationNanos = System.nanoTime();
    private boolean binding;
    private KeyBindProperty bindingProperty;
    private Property<?> openMode;
    private Property<?> draggingNumber;
    private Property<?> editing;
    private EditKind editKind;
    private String editorValue = "";
    private float popupX;
    private float popupY;
    private float popupWidth;
    private int popupScroll;
    private final Map<Property<?>, UiBounds> numericTracks = new HashMap<>();
    private final Map<Property<?>, Float> booleanAnimations = new HashMap<>();
    private float animationDelta;

    private enum EditKind { NUMBER, TEXT, COLOR, DRAG_X, DRAG_Y }

    public ModuleCard(Module module) {
        this.module = module;
        this.toggleProgress = module.isEnabled() ? 1.0F : 0.0F;
    }

    public Module module() {
        return module;
    }

    public float height() {
        updateAnimations();
        return 47.0F + settingsHeight() * expansionProgress;
    }

    /**
     * Client-only cards with no visible properties do not need an expandable
     * module panel. Keep those internal features as compact toggles instead.
     */
    public boolean isToggleOnly() {
        for (Property<?> property : properties()) {
            if (property.isVisible()) return false;
        }
        return true;
    }

    public float clientHeight() {
        return isToggleOnly() ? 38.0F : height();
    }

    public void renderClient(UiRenderer renderer, float x, float y, float mouseX, float mouseY) {
        if (!isToggleOnly()) {
            render(renderer, x, y, mouseX, mouseY);
            return;
        }

        updateAnimations();
        this.x = x;
        this.y = y;
        int titleColor = mix(ClickGuiTheme.DISABLED, ClickGuiTheme.ACCENT, toggleProgress);
        renderer.shadow(x, y, ClickGuiTheme.CONTENT_WIDTH, 38, 5, 0, 0, 4, 1, 0x59000000);
        renderer.roundedRect(x, y, ClickGuiTheme.CONTENT_WIDTH, 38, 5, ClickGuiTheme.MODULE);
        renderer.roundedRect(x, y, 5, 38, 5, 0, 0, 5, titleColor);
        renderer.imageContained("module", x + 12, y + 9, 20, 20, titleColor);

        UiFont title = renderer.fonts().google(18, UiFonts.SEMIBOLD);
        UiFont description = renderer.fonts().google(14, UiFonts.REGULAR);
        title.draw(trim(title, module.getName(), 235), x + 42, y + 3, titleColor, true);
        if (module.getDescription() != null && !module.getDescription().isEmpty()) {
            description.draw(trim(description, module.getDescription(), 235), x + 42, y + 21,
                    ClickGuiTheme.MUTED, true);
        }

        float toggleX = x + 334;
        float state = toggleProgress;
        renderer.shadow(toggleX, y + 9, 38, 21, 10.5F, 0, 2, 4, 0, 0x40000000);
        renderer.roundedRect(toggleX, y + 9, 38, 21, 10.5F,
                mix(ClickGuiTheme.TRACK, ClickGuiTheme.TOGGLE_ON, state));
        float thumbX = toggleX + 4 + 17 * state;
        renderer.shadow(thumbX, y + 12, 15, 15, 7.5F, 0, 2, 3, 0, 0x59000000);
        renderer.roundedRect(thumbX, y + 12, 15, 15, 7.5F, 0xFFD6D9E8);
    }

    public ClickResult mouseClickedClient(float mouseX, float mouseY, int button) {
        if (!isToggleOnly()) return mouseClicked(mouseX, mouseY, button);
        if (button == 0 && new UiBounds(x, y, ClickGuiTheme.CONTENT_WIDTH, 38).contains(mouseX, mouseY)) {
            module.toggle();
            return ClickResult.CONSUMED;
        }
        return ClickResult.NONE;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        if (!expanded) {
            openMode = null;
            popupScroll = 0;
            cancelEditor();
            binding = false;
            bindingProperty = null;
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public boolean isTextInputFocused() {
        return editing != null;
    }

    public boolean hasInputCapture() {
        return editing != null || binding || bindingProperty != null;
    }

    public void discardTextEditor() {
        cancelEditor();
    }

    public void render(UiRenderer renderer, float x, float y, float mouseX, float mouseY) {
        updateAnimations();
        this.x = x;
        this.y = y;
        renderHeader(renderer);
        if (expansionProgress > 0.01F) renderSettings(renderer);
    }

    private void renderHeader(UiRenderer renderer) {
        int indicator = mix(0xFF2F3244, ClickGuiTheme.ACCENT, toggleProgress);
        int titleColor = mix(ClickGuiTheme.DISABLED, ClickGuiTheme.ACCENT, toggleProgress);

        renderer.shadow(x, y, ClickGuiTheme.CONTENT_WIDTH, 47, 5, 0, 0, 4, 1, 0x59000000);
        renderer.roundedRect(x, y, ClickGuiTheme.CONTENT_WIDTH, 47,
                5, 5, expanded ? 0 : 5, expanded ? 0 : 5, ClickGuiTheme.MODULE);
        renderer.roundedRect(x, y, 5, 47, 5, 0, expanded ? 0 : 0, expanded ? 0 : 5, indicator);
        renderer.imageContained("module", x + 15, y + 12, 20, 20, titleColor);

        UiFont title = renderer.fonts().google(20, UiFonts.SEMIBOLD);
        UiFont description = renderer.fonts().google(15, UiFonts.REGULAR);
        title.draw(trim(title, module.getName(), 220), x + 44, y + 3, titleColor, true);
        if (module.getDescription() != null && !module.getDescription().isEmpty()) {
            description.draw(trim(description, module.getDescription(), 220), x + 44, y + 24, ClickGuiTheme.MUTED, true);
        }

        float keyX = x + 283;
        renderer.shadow(keyX, y + 9, 45, 26, 2, 0, 2, 2, 0, 0x4D000000);
        renderer.roundedRect(keyX, y + 9, 45, 26, 2, 0xFF2B2E3D);
        String key = binding ? "..." : module.getKey() == 0 ? "" : KeyBindUtil.getKeyName(module.getKey());
        UiFont keyFont = renderer.fonts().google(20, UiFonts.BLACK);
        keyFont.draw(key, keyX + (45 - keyFont.width(key)) / 2, y + 8, 0xFFFFFFFF);

        boolean visible = !module.isHidden();
        renderer.imageContained(visible ? "eye-on-bg" : "eye-off-bg", x + 343, y + 4, 41, 40, 0xFFFFFFFF);
        if (visible) renderer.imageContained("eye-on", x + 349, y + 13, 27, 19, 0xFFFFFFFF);
        else renderer.imageContained("eye-off", x + 350, y + 12, 23, 20, 0xFFFFFFFF);
    }

    private void renderSettings(UiRenderer renderer) {
        float settingsY = y + 47;
        float height = settingsHeight() * expansionProgress;
        renderer.shadow(x + 3, settingsY, 382, height, 5, 0, 0, 4, 1, 0x59000000);
        renderer.roundedRect(x + 3, settingsY, 382, height, 0, 0, 5, 5, ClickGuiTheme.MODULE_SETTINGS);
        renderer.pushClip(new UiBounds(x + 3, settingsY, 382, height));

        float rowY = settingsY + 13;
        numericTracks.clear();
        for (Property<?> property : properties()) {
            if (!property.isVisible()) continue;
            renderProperty(renderer, property, rowY);
            rowY += 30;
        }
        renderer.popClip();
    }

    private void renderProperty(UiRenderer renderer, Property<?> property, float rowY) {
        UiFont labelFont = renderer.fonts().google(18, UiFonts.REGULAR);
        UiFont valueFont = renderer.fonts().google(14, UiFonts.REGULAR);
        String label = displayLabel(property);
        float rowX = property.getParent() == null ? x + 9 : x + 21;
        if (property.getParent() != null) {
            renderer.rect(x + 6, rowY - 4, 376, 28, 0x143B3F57);
            renderer.rect(x + 13, rowY - 1, 2, 21, ClickGuiTheme.UNFOCUSED);
        }
        labelFont.draw(label, rowX, rowY - 2, 0xFFFFFFFF, true);
        float labelEnd = rowX + labelFont.width(label);

        if (property instanceof BooleanProperty) {
            float toggleX = Math.min(x + 337, labelEnd + 12);
            boolean enabled = ((BooleanProperty) property).getValue();
            float state = booleanAnimations.containsKey(property)
                    ? booleanAnimations.get(property) : (enabled ? 1.0F : 0.0F);
            state = approach(state, enabled ? 1.0F : 0.0F, animationDelta / 0.16F);
            booleanAnimations.put(property, state);
            renderer.shadow(toggleX, rowY, 38, 21, 10.5F, 0, 2, 4, 0, 0x40000000);
            renderer.roundedRect(toggleX, rowY, 38, 21, 10.5F,
                    mix(ClickGuiTheme.TRACK, ClickGuiTheme.TOGGLE_ON, state));
            float thumbX = toggleX + 4 + 17 * state;
            renderer.shadow(thumbX, rowY + 3, 15, 15, 7.5F, 0, 2, 3, 0, 0x59000000);
            renderer.roundedRect(thumbX, rowY + 3, 15, 15, 7.5F, 0xFFD6D9E8);
            return;
        }

        if (property instanceof ModeProperty) {
            ModeProperty mode = (ModeProperty) property;
            float fieldX = Math.min(x + 297, Math.max(x + 62, labelEnd + 10));
            float width = Math.min(110, x + 376 - fieldX);
            drawField(renderer, fieldX, rowY, width, 22, mode.getModeString(), valueFont, property == openMode);
            renderer.imageContained("dropdown", fieldX + width - 14, rowY + 8, 9, 5, 0xFFFFFFFF);
            if (property == openMode) {
                popupX = fieldX;
                popupY = rowY + 24;
                popupWidth = width;
                float popupHeight = popupHeight(mode);
                if (popupY + popupHeight > ClickGuiTheme.MODULE_BOTTOM) {
                    popupY = rowY - popupHeight - 2.0F;
                }
            }
            return;
        }

        if (property instanceof KeyBindProperty) {
            float fieldX = x + 250;
            String key = bindingProperty == property ? "..." : KeyBindUtil.getKeyName(((KeyBindProperty) property).getValue());
            drawField(renderer, fieldX, rowY, 129, 22, key, valueFont, bindingProperty == property);
            return;
        }

        if (isNumeric(property)) {
            float valueX = x + 333;
            float trackX = Math.min(x + 214, Math.max(x + 56, labelEnd + 10));
            float trackWidth = Math.max(30, valueX - trackX - 12);
            numericTracks.put(property, new UiBounds(trackX, rowY + 2, trackWidth, 17));
            float fraction = numericFraction(property);
            renderer.shadow(trackX, rowY + 8, trackWidth, 5, 2.5F, 0, 2, 4, 0, 0x40000000);
            renderer.roundedRect(trackX, rowY + 8, trackWidth, 5, 2.5F, ClickGuiTheme.TRACK);
            renderer.roundedRect(trackX, rowY + 8, Math.max(3, trackWidth * fraction), 5, 2.5F, ClickGuiTheme.CYAN);
            drawSliderStops(renderer, property, trackX, rowY + 8, trackWidth);
            float thumbX = trackX + trackWidth * fraction - 4.5F;
            renderer.shadow(thumbX, rowY + 4, 9, 9, 4.5F, 0, 2, 3, 0, 0x59000000);
            renderer.roundedRect(thumbX, rowY + 4, 9, 9, 4.5F, 0xFFF3F5FF);
            drawField(renderer, valueX, rowY, 46, 22, displayValue(property), valueFont, editing == property);
            return;
        }

        if (property instanceof TextProperty) {
            drawField(renderer, x + 150, rowY, 229, 22,
                    editing == property ? editorValue : ((TextProperty) property).getValue(), valueFont, editing == property);
        } else if (property instanceof ColorProperty) {
            int color = ((ColorProperty) property).getValue();
            renderer.roundedRect(x + 248, rowY + 1, 20, 20, 3, color);
            drawField(renderer, x + 274, rowY, 105, 22,
                    editing == property ? editorValue : String.format("%08X", color), valueFont, editing == property);
        } else if (property instanceof DragProperty) {
            DragProperty drag = (DragProperty) property;
            String valueX = editing == property && editKind == EditKind.DRAG_X ? editorValue : String.format(Locale.ROOT, "%.1f", drag.position.x);
            String valueY = editing == property && editKind == EditKind.DRAG_Y ? editorValue : String.format(Locale.ROOT, "%.1f", drag.position.y);
            drawField(renderer, x + 219, rowY, 61, 22, "X " + valueX, valueFont, editing == property && editKind == EditKind.DRAG_X);
            drawField(renderer, x + 285, rowY, 61, 22, "Y " + valueY, valueFont, editing == property && editKind == EditKind.DRAG_Y);
            drawField(renderer, x + 351, rowY, 28, 22, "R", valueFont, false);
        } else if (property instanceof FileProperty) {
            drawField(renderer, x + 294, rowY, 85, 22, "Open file", valueFont, false);
        }
    }

    public void renderPopup(UiRenderer renderer) {
        if (!(openMode instanceof ModeProperty)) return;
        ModeProperty mode = (ModeProperty) openMode;
        UiFont font = renderer.fonts().google(14, UiFonts.REGULAR);
        int visibleEntries = visiblePopupEntries(mode);
        float height = popupHeight(mode);
        renderer.shadow(popupX, popupY, popupWidth, height, 2, 0, 2, 4, 0, 0x66000000);
        renderer.roundedRect(popupX, popupY, popupWidth, height, 2, 0xFF20222E);
        for (int row = 0; row < visibleEntries; row++) {
            int index = popupScroll + row;
            if (index == mode.getValue()) {
                renderer.rect(popupX + 2, popupY + 2 + row * POPUP_ENTRY_HEIGHT,
                        popupWidth - 4, POPUP_ENTRY_HEIGHT, 0x40545A83);
            }
            font.draw(trim(font, mode.getModes()[index], popupWidth - 12),
                    popupX + 5, popupY + row * POPUP_ENTRY_HEIGHT, 0xFFFFFFFF);
        }
        if (mode.getModes().length > MAX_VISIBLE_POPUP_ENTRIES) {
            float trackHeight = visibleEntries * POPUP_ENTRY_HEIGHT;
            float thumbHeight = Math.max(10.0F, trackHeight * visibleEntries / mode.getModes().length);
            float thumbY = popupY + 2 + (trackHeight - thumbHeight) * popupScroll / maxPopupScroll(mode);
            renderer.roundedRect(popupX + popupWidth - 4, thumbY, 2, thumbHeight, 1, ClickGuiTheme.CYAN);
        }
    }

    private void drawField(UiRenderer renderer, float fieldX, float fieldY, float width, float height,
                           String value, UiFont font, boolean focused) {
        renderer.shadow(fieldX, fieldY, width, height, 2, 0, 2, 4, 0, 0x40000000);
        renderer.roundedRect(fieldX, fieldY, width, height, 2, focused ? 0xFF2F3348 : ClickGuiTheme.FIELD);
        String shown = trim(font, value == null ? "" : value, width - 10);
        font.draw(shown, fieldX + 5, fieldY, 0xFFFFFFFF);
        if (focused && (System.currentTimeMillis() / 500L & 1L) == 0L) {
            renderer.rect(fieldX + 5 + font.width(shown) + 1, fieldY + 4, 1, 14, 0xCCFFFFFF);
        }
    }

    public ClickResult mouseClicked(float mouseX, float mouseY, int button) {
        if (bindingProperty != null) {
            if (button != 0) {
                bindingProperty.setValue(button - 100);
                bindingProperty = null;
            }
            return ClickResult.CONSUMED;
        }
        if (editing != null) cancelEditor();

        UiBounds header = new UiBounds(x, y, ClickGuiTheme.CONTENT_WIDTH, 47);
        if (header.contains(mouseX, mouseY)) {
            if (button == 0 && new UiBounds(x + 339, y + 3, 49, 41).contains(mouseX, mouseY)) {
                module.setHidden(!module.isHidden());
                return ClickResult.CONSUMED;
            }
            if (button == 0 && new UiBounds(x + 278, y + 5, 55, 34).contains(mouseX, mouseY)) {
                binding = true;
                return ClickResult.CONSUMED;
            }
            if (button == 1) return ClickResult.EXPAND;
            if (button == 0) {
                module.toggle();
                return ClickResult.CONSUMED;
            }
        }
        if (!expanded || button != 0) return ClickResult.NONE;

        float rowY = y + 60;
        for (Property<?> property : properties()) {
            if (!property.isVisible()) continue;
            if (new UiBounds(x + 6, rowY - 2, 376, 26).contains(mouseX, mouseY)) {
                handlePropertyClick(property, rowY, mouseX, mouseY);
                return ClickResult.CONSUMED;
            }
            rowY += 30;
        }
        openMode = null;
        return ClickResult.NONE;
    }

    private void handlePropertyClick(Property<?> property, float rowY, float mouseX, float mouseY) {
        if (property instanceof BooleanProperty) {
            ((BooleanProperty) property).setValue(!((BooleanProperty) property).getValue());
        } else if (property instanceof KeyBindProperty) {
            bindingProperty = (KeyBindProperty) property;
        } else if (property instanceof ModeProperty) {
            if (openMode == property) {
                openMode = null;
                popupScroll = 0;
            } else {
                ModeProperty mode = (ModeProperty) property;
                openMode = property;
                popupScroll = Math.min(Math.max(0, mode.getValue() - MAX_VISIBLE_POPUP_ENTRIES + 1), maxPopupScroll(mode));
            }
        } else if (isNumeric(property)) {
            if (mouseX >= x + 327) startEditing(property, EditKind.NUMBER, displayValue(property));
            else {
                UiBounds track = numericTracks.get(property);
                if (track == null) return;
                setNumericFromMouse(property, (mouseX - track.x) / track.width);
                draggingNumber = property;
            }
        } else if (property instanceof TextProperty) {
            startEditing(property, EditKind.TEXT, ((TextProperty) property).getValue());
        } else if (property instanceof ColorProperty) {
            startEditing(property, EditKind.COLOR, String.format("%08X", ((ColorProperty) property).getValue()));
        } else if (property instanceof DragProperty) {
            DragProperty drag = (DragProperty) property;
            if (mouseX >= x + 349) {
                drag.position.x = drag.targetPosition.x = drag.getValue().x;
                drag.position.y = drag.targetPosition.y = drag.getValue().y;
                Config.markDirty();
            } else if (mouseX < x + 283) startEditing(property, EditKind.DRAG_X, String.valueOf(drag.position.x));
            else startEditing(property, EditKind.DRAG_Y, String.valueOf(drag.position.y));
        } else if (property instanceof FileProperty) {
            ((FileProperty) property).openFile();
        }
    }

    public boolean mouseDragged(float mouseX, float mouseY, int button) {
        if (button != 0 || draggingNumber == null) return false;
        UiBounds track = numericTracks.get(draggingNumber);
        if (track == null) return false;
        setNumericFromMouse(draggingNumber, (mouseX - track.x) / track.width);
        return true;
    }

    public void mouseReleased() {
        draggingNumber = null;
    }

    public boolean keyTyped(char character, int keyCode) {
        if (bindingProperty != null) {
            bindingProperty.setValue(keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK ? 0 : keyCode);
            bindingProperty = null;
            return true;
        }
        if (binding) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) module.setKey(0);
            else module.setKey(keyCode);
            binding = false;
            return true;
        }
        if (editing == null) return false;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            applyEditor();
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelEditor();
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK && !editorValue.isEmpty()) {
            editorValue = editorValue.substring(0, editorValue.length() - 1);
            return true;
        }
        if (character >= 32 && character != 127 && editorValue.length() < 64) {
            editorValue += character;
        }
        return true;
    }

    public void cancelBinding() {
        binding = false;
        bindingProperty = null;
    }

    public boolean hasOpenPopup() {
        return openMode instanceof ModeProperty;
    }

    /** Modal popup input is consumed even when the click lands outside it. */
    public boolean handlePopupClick(float mouseX, float mouseY, int button) {
        if (!(openMode instanceof ModeProperty)) return false;
        ModeProperty mode = (ModeProperty) openMode;
        UiBounds popup = new UiBounds(popupX, popupY, popupWidth, popupHeight(mode));
        if (button == 0 && popup.contains(mouseX, mouseY)) {
            int index = popupScroll + Math.min(visiblePopupEntries(mode) - 1,
                    Math.max(0, (int) ((mouseY - popupY - 2) / POPUP_ENTRY_HEIGHT)));
            mode.setValue(index);
        }
        openMode = null;
        popupScroll = 0;
        return true;
    }

    /** Scrolls an open mode popup one entry at a time and consumes the wheel event. */
    public boolean scrollPopup(int wheel) {
        if (!(openMode instanceof ModeProperty) || wheel == 0) return false;
        ModeProperty mode = (ModeProperty) openMode;
        popupScroll = Math.max(0, Math.min(maxPopupScroll(mode), popupScroll + (wheel < 0 ? 1 : -1)));
        return true;
    }

    public void discardEditor() {
        cancelEditor();
        binding = false;
        bindingProperty = null;
        openMode = null;
        popupScroll = 0;
    }

    private void startEditing(Property<?> property, EditKind kind, String value) {
        editing = property;
        editKind = kind;
        editorValue = value == null ? "" : value;
    }

    private void applyEditor() {
        if (editing == null) return;
        String value = editorValue.trim();
        try {
            if (editKind == EditKind.NUMBER) {
                double parsed = Double.parseDouble(value.replace("%", ""));
                setNumeric(editing, parsed);
                if (editing instanceof RecommendedRange
                        && !((RecommendedRange) editing).isRecommended(parsed)
                        && Myau.notificationManager != null) {
                    Myau.notificationManager.add(
                            NotificationManager.NotificationType.WARNING,
                            "recommended-range-" + displayLabel(editing),
                            "Warning!",
                            displayLabel(editing) + " is outside its recommended range and could trigger anticheat flags.",
                            false
                    );
                }
            }
            else if (editKind == EditKind.TEXT) ((TextProperty) editing).setValue(editorValue);
            else if (editKind == EditKind.COLOR) ((ColorProperty) editing).parseString(value);
            else if (editing instanceof DragProperty) {
                DragProperty drag = (DragProperty) editing;
                double coordinate = Double.parseDouble(value);
                if (editKind == EditKind.DRAG_X) drag.position.x = drag.targetPosition.x = coordinate;
                else drag.position.y = drag.targetPosition.y = coordinate;
                Config.markDirty();
            }
        } catch (RuntimeException ignored) {
            // Invalid input intentionally preserves the previous value.
        }
        cancelEditor();
    }

    private void cancelEditor() {
        editing = null;
        editKind = null;
        editorValue = "";
    }

    private static int visiblePopupEntries(ModeProperty mode) {
        return Math.min(MAX_VISIBLE_POPUP_ENTRIES, mode.getModes().length);
    }

    private static int maxPopupScroll(ModeProperty mode) {
        return Math.max(0, mode.getModes().length - visiblePopupEntries(mode));
    }

    private static float popupHeight(ModeProperty mode) {
        return visiblePopupEntries(mode) * POPUP_ENTRY_HEIGHT + POPUP_PADDING;
    }

    private float settingsHeight() {
        int visible = 0;
        for (Property<?> property : properties()) if (property.isVisible()) visible++;
        return 16.0F + visible * 30.0F;
    }

    private List<Property<?>> properties() {
        List<Property<?>> properties = Myau.propertyManager.properties.get(module.getClass());
        return properties == null ? Collections.<Property<?>>emptyList() : properties;
    }

    private static boolean isNumeric(Property<?> property) {
        return property instanceof FloatProperty || property instanceof IntProperty
                || property instanceof LongProperty || property instanceof PercentProperty;
    }

    private static double numericValue(Property<?> property) {
        if (property instanceof FloatProperty) return ((FloatProperty) property).getValue();
        if (property instanceof IntProperty) return ((IntProperty) property).getValue();
        if (property instanceof LongProperty) return ((LongProperty) property).getValue();
        return ((PercentProperty) property).getValue();
    }

    private static double numericMin(Property<?> property) {
        if (property instanceof FloatProperty) return ((FloatProperty) property).getMinimum();
        if (property instanceof IntProperty) return ((IntProperty) property).getMinimum();
        if (property instanceof LongProperty) return ((LongProperty) property).getMinimum();
        return ((PercentProperty) property).getMinimum();
    }

    private static double numericMax(Property<?> property) {
        if (property instanceof FloatProperty) return ((FloatProperty) property).getMaximum();
        if (property instanceof IntProperty) return ((IntProperty) property).getMaximum();
        if (property instanceof LongProperty) return ((LongProperty) property).getMaximum();
        return ((PercentProperty) property).getMaximum();
    }

    private static float numericFraction(Property<?> property) {
        double min = numericMin(property);
        double max = numericMax(property);
        if (max <= min) return 0;
        return (float) Math.max(0, Math.min(1, (numericValue(property) - min) / (max - min)));
    }

    private static void setNumericFromMouse(Property<?> property, float fraction) {
        double clamped = Math.max(0, Math.min(1, fraction));
        setNumeric(property, numericMin(property) + (numericMax(property) - numericMin(property)) * clamped);
    }

    private static void drawSliderStops(UiRenderer renderer, Property<?> property, float trackX, float trackY, float trackWidth) {
        double minimum = numericMin(property);
        double maximum = numericMax(property);
        double increment = numericStep(property);
        int stopCount = SliderStops.count(minimum, maximum, increment);
        for (int i = 0; i < stopCount; i++) {
            double stopValue = SliderStops.valueAt(minimum, maximum, increment, i, stopCount);
            float stopX = trackX + trackWidth * (float) SliderStops.fraction(stopValue, minimum, maximum);
            renderer.rect(stopX, trackY, 1, 5, 0xAA202020);
        }
    }

    private static void setNumeric(Property<?> property, double value) {
        double minimum = numericMin(property);
        double maximum = numericMax(property);
        value = SliderStops.snap(value, minimum, maximum, numericStep(property));
        if (property instanceof FloatProperty) {
            FloatProperty floatProperty = (FloatProperty) property;
            floatProperty.setValue((float) value);
        }
        else if (property instanceof IntProperty) ((IntProperty) property).setValue((int) Math.round(value));
        else if (property instanceof LongProperty) ((LongProperty) property).setValue(Math.round(value));
        else ((PercentProperty) property).setValue((int) Math.round(value));
    }

    private static double numericStep(Property<?> property) {
        if (property instanceof FloatProperty) {
            double step = ((FloatProperty) property).getStep();
            return step > 0.0D ? step : 0.1D;
        }
        return 1.0D;
    }

    private static String displayValue(Property<?> property) {
        if (property instanceof FloatProperty) return String.format(Locale.ROOT, "%.2f", numericValue(property)).replaceAll("0+$", "").replaceAll("\\.$", "");
        if (property instanceof PercentProperty) return Math.round(numericValue(property)) + "%";
        return String.valueOf(Math.round(numericValue(property)));
    }

    private static String label(String raw) {
        String[] words = raw.replace('_', '-').split("-");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String displayLabel(Property<?> property) {
        return property.getDisplayName() != null
                ? property.getDisplayName()
                : label(property.getName());
    }

    private static String trim(UiFont font, String value, float width) {
        if (value == null) return "";
        if (font.width(value) <= width) return value;
        String result = value;
        while (result.length() > 1 && font.width(result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private void updateAnimations() {
        long now = System.nanoTime();
        float delta = Math.min(0.1F, Math.max(0.0F, (now - lastAnimationNanos) / 1_000_000_000.0F));
        // height() and render() are intentionally called in the same frame.
        // Preserve the real frame delta instead of replacing it with ~0 on
        // the second call, otherwise property toggle thumbs never advance.
        if (delta < 0.001F) return;
        animationDelta = delta;
        lastAnimationNanos = now;
        expansionProgress = approach(expansionProgress, expanded ? 1.0F : 0.0F, delta / 0.20F);
        toggleProgress = approach(toggleProgress, module.isEnabled() ? 1.0F : 0.0F, delta / 0.16F);
    }

    private static float approach(float value, float target, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        t = 1.0F - (1.0F - t) * (1.0F - t);
        return value + (target - value) * t;
    }

    private static int mix(int from, int to, float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = (int) (((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
        int g = (int) (((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public enum ClickResult {
        NONE,
        CONSUMED,
        EXPAND
    }
}

package myau.ui.modern;

public enum ClickGuiCategory {
    COMBAT("Combat", "combat", 12.00F, 14.06F, 35.00F, 38.00F),
    MOVEMENT("Movement", "movement", 5.58F, 13.95F, 47.84F, 38.22F),
    VISUALS("Visuals", "visuals", 10.08F, 17.38F, 38.84F, 31.37F),
    PLAYER("Player", "player", 13.41F, 15.47F, 32.18F, 35.18F),
    UTILITIES("Utilities", "utilities", 13.41F, 15.47F, 32.18F, 35.18F);

    private final String displayName;
    private final String icon;
    private final float iconX;
    private final float iconY;
    private final float iconWidth;
    private final float iconHeight;

    ClickGuiCategory(String displayName, String icon,
                     float iconX, float iconY, float iconWidth, float iconHeight) {
        this.displayName = displayName;
        this.icon = icon;
        this.iconX = iconX;
        this.iconY = iconY;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
    }

    public String displayName() {
        return displayName;
    }

    public String icon() {
        return icon;
    }

    public float iconX() {
        return iconX;
    }

    public float iconY() {
        return iconY;
    }

    public float iconWidth() {
        return iconWidth;
    }

    public float iconHeight() {
        return iconHeight;
    }
}

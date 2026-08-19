package myau.render;

/** JSON-defined dimensions for the modern HUD arraylist cards. */
public final class ArraylistLayout {
    public static final float CARD_HEIGHT = 42.0F;
    public static final float CARD_RADIUS = 5.0F;
    public static final float CARD_PADDING = 5.0F;
    public static final float ROW_GAP = 7.0F;
    public static final float ACCENT_WIDTH = 5.0F;
    public static final float ACCENT_HEIGHT = 24.0F;
    public static final float CONTENT_GAP = 6.0F;
    public static final float FONT_SIZE = 32.0F;

    private ArraylistLayout() {
    }

    public static float cardWidth(float nameWidth, float suffixWidth) {
        float width = CARD_PADDING * 2.0F + ACCENT_WIDTH + CONTENT_GAP + Math.max(0.0F, nameWidth);
        if (suffixWidth > 0.0F) width += CONTENT_GAP + suffixWidth;
        return (float) Math.ceil(width);
    }

    public static float textX(float cardX, float scale) {
        return cardX + (CARD_PADDING + ACCENT_WIDTH + CONTENT_GAP) * scale;
    }

    public static float accentY(float cardY, float scale) {
        return cardY + (CARD_HEIGHT - ACCENT_HEIGHT) * 0.5F * scale;
    }

    public static float nextTopCursor(float cursor, float scale, float visibility) {
        return nextTopCursor(cursor, scale, visibility, ROW_GAP);
    }

    public static float nextTopCursor(float cursor, float scale, float visibility, float rowGap) {
        return cursor + (CARD_HEIGHT + Math.max(0.0F, rowGap)) * scale * clamp01(visibility);
    }

    public static float nextBottomCursor(float cursor, float scale, float visibility) {
        return nextBottomCursor(cursor, scale, visibility, ROW_GAP);
    }

    public static float nextBottomCursor(float cursor, float scale, float visibility, float rowGap) {
        return cursor - (CARD_HEIGHT + Math.max(0.0F, rowGap)) * scale * clamp01(visibility);
    }

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}

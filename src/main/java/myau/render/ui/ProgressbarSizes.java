package myau.render.ui;

/**
 * The progressbar's editable visual measurements.
 *
 * Values use the same 1920x1080 design space as the modern UI renderer. The
 * exported PNGs include five pixels of effect overflow on every side, so the
 * nominal Figma component remains 241x29 while the exported canvas is 251x39.
 * Change USER_SCALE or the offsets first when testing a different size or
 * position. The geometry constants below are exposed for fine tuning.
 */
public final class ProgressbarSizes {
    private ProgressbarSizes() {
    }

    public static final float DESIGN_WIDTH = 1920.0F;
    public static final float DESIGN_HEIGHT = 1080.0F;

    // Main user controls: one scale changes the complete component uniformly.
    public static final float USER_SCALE = 1.0F;
    public static final float OFFSET_X = 0.0F;
    public static final float OFFSET_Y = -10F;

    // Figma component bounds before shadow/blur effect overflow.
    public static final float COMPONENT_WIDTH = 241.0F;
    public static final float COMPONENT_HEIGHT = 29.0F;

    // The exported box canvas includes effect overflow. Its path is aligned
    // back to the nominal JSON frame with these independent offsets.
    public static final float BOX_EXPORT_OFFSET_X = -5.0F;
    public static final float BOX_EXPORT_OFFSET_Y = -2.0F;
    public static final float EXPORT_WIDTH = 251.0F;
    public static final float EXPORT_HEIGHT = 39.0F;

    // Progressbar box geometry in the nominal JSON component.
    public static final float BOX_X = 5.0F;
    public static final float BOX_Y = 5.0F;
    public static final float BOX_WIDTH = 231.0F;
    public static final float BOX_HEIGHT = 19.0F;
    public static final float BOX_RADIUS = 9.0F;

    // Track geometry. Track@2x.png has two pixels of shadow overflow vertically
    // and four pixels horizontally around this actual 221x9 track.
    public static final float TRACK_ASSET_X = 6.0F;
    public static final float TRACK_ASSET_Y = 8.0F;
    public static final float TRACK_ASSET_WIDTH = 229.0F;
    public static final float TRACK_ASSET_HEIGHT = 17.0F;
    public static final float TRACK_X = 10.0F;
    public static final float TRACK_Y = 10.0F;
    public static final float TRACK_WIDTH = 221.0F;
    public static final float TRACK_HEIGHT = 9.0F;
    public static final float TRACK_RADIUS = 4.5F;

    // Design effects from Progressbar.json.
    public static final float BACKDROP_BLUR_RADIUS = 31.0F;
    public static final float FADE_SPEED = 0.22F;
    public static final float PROGRESS_ANIMATION_SPEED = 9.0F;
    public static final int MAX_ALPHA = 255;

    // Material gradients. Hex values are ARGB integers for the OpenGL UI API.
    public static final int ICE_START = 0xFF67ECC6;
    public static final int ICE_END = 0xFF65C3EC;
    public static final int WOOD_START = 0xFFF2B45F;
    public static final int WOOD_END = 0xFFEC8D65;
    public static final int OTHER_START = 0xFFEC6767;
    public static final int OTHER_END = 0xFFEC8D65;
}

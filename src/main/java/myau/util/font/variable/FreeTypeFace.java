package myau.util.font.variable;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

/** One configured FreeType face and its variable-axis coordinates. */
public final class FreeTypeFace implements AutoCloseable {
    private final FreeTypeLibrary.Api api;
    private final Memory fontMemory;
    private final Pointer library;
    private final Pointer face;
    private final int size;

    private FreeTypeFace(FreeTypeLibrary.Api api, Memory fontMemory, Pointer library, Pointer face, int size) {
        this.api = api;
        this.fontMemory = fontMemory;
        this.library = library;
        this.face = face;
        this.size = size;
    }

    public static FreeTypeFace open(OpenTypeVariableFont font, FontAxes axes, int size) {
        FreeTypeLibrary.Api api = FreeTypeLibrary.get();
        if (api == null) return null;
        Memory memory = FreeTypeLibrary.nativeMemory(font.bytes());
        PointerByReference libraryRef = new PointerByReference();
        PointerByReference faceRef = new PointerByReference();
        Pointer library = null;
        Pointer face = null;
        try {
            check(api.FT_Init_FreeType(libraryRef), "FT_Init_FreeType");
            library = libraryRef.getValue();
            check(api.FT_New_Memory_Face(library, memory, new NativeLong(memory.size()), new NativeLong(0), faceRef),
                    "FT_New_Memory_Face");
            face = faceRef.getValue();
            int[] coordinates = font.designCoordinates(axes);
            if (coordinates.length > 0) {
                Memory coordinateMemory = new Memory(coordinates.length * 4L);
                for (int i = 0; i < coordinates.length; i++) coordinateMemory.setInt(i * 4L, coordinates[i]);
                check(api.FT_Set_Var_Design_Coordinates(face, coordinates.length, coordinateMemory),
                        "FT_Set_Var_Design_Coordinates");
                coordinateMemory.clear();
            }
            check(api.FT_Set_Pixel_Sizes(face, 0, Math.max(1, size)), "FT_Set_Pixel_Sizes");
            return new FreeTypeFace(api, memory, library, face, size);
        } catch (Throwable failure) {
            if (face != null) api.FT_Done_Face(face);
            if (library != null) api.FT_Done_FreeType(library);
            memory.clear();
            return null;
        }
    }

    public GlyphBitmap glyph(int codePoint) {
        try {
            check(api.FT_Load_Char(face, new NativeLong(codePoint), FreeTypeLibrary.LOAD_DEFAULT), "FT_Load_Char");
            FacePrefix faceData = new FacePrefix(face);
            faceData.read();
            if (faceData.glyph == null) return GlyphBitmap.empty();
            GlyphSlotPrefix glyph = new GlyphSlotPrefix(faceData.glyph);
            glyph.read();
            check(api.FT_Render_Glyph(faceData.glyph, FreeTypeLibrary.RENDER_MODE_NORMAL), "FT_Render_Glyph");
            glyph.read();
            Bitmap bitmap = glyph.bitmap;
            int width = Math.max(0, bitmap.width);
            int height = Math.max(0, bitmap.rows);
            byte[] alpha = new byte[width * height];
            if (bitmap.buffer != null && width > 0 && height > 0) {
                int pitch = bitmap.pitch;
                int rowStride = Math.abs(pitch);
                byte[] row = new byte[rowStride];
                for (int y = 0; y < height; y++) {
                    long rowOffset = pitch >= 0 ? (long) y * rowStride : (long) (height - 1 - y) * rowStride;
                    bitmap.buffer.read(rowOffset, row, 0, row.length);
                    System.arraycopy(row, 0, alpha, y * width, Math.min(width, row.length));
                }
            }
            return new GlyphBitmap(width, height, glyph.bitmapLeft, glyph.bitmapTop,
                    glyph.metrics.horiAdvance.longValue() / 64.0F, alpha);
        } catch (Throwable ignored) {
            return GlyphBitmap.empty();
        }
    }

    public float lineHeight() {
        FacePrefix faceData = new FacePrefix(face);
        faceData.read();
        return faceData.height / (float) Math.max(1, faceData.unitsPerEM) * size;
    }

    public float ascent() {
        FacePrefix faceData = new FacePrefix(face);
        faceData.read();
        return faceData.ascender / (float) Math.max(1, faceData.unitsPerEM) * size;
    }

    @Override
    public void close() {
        if (face != null) api.FT_Done_Face(face);
        if (library != null) api.FT_Done_FreeType(library);
        fontMemory.clear();
    }

    private static void check(int error, String operation) {
        if (error != 0) throw new IllegalStateException(operation + " failed with error " + error);
    }

    public static final class GlyphBitmap {
        public final int width;
        public final int height;
        public final int left;
        public final int top;
        public final float advance;
        public final byte[] alpha;

        GlyphBitmap(int width, int height, int left, int top, float advance, byte[] alpha) {
            this.width = width;
            this.height = height;
            this.left = left;
            this.top = top;
            this.advance = advance;
            this.alpha = alpha;
        }

        static GlyphBitmap empty() {
            return new GlyphBitmap(0, 0, 0, 0, 0.0F, new byte[0]);
        }
    }

    public static class OrderedStructure extends Structure {
        public OrderedStructure(Pointer pointer, String[] order) {
            super(pointer);
            setFieldOrder(order);
        }
    }

    public static final class Generic extends OrderedStructure {
        public Pointer data;
        public Pointer finalizer;

        public Generic() {
            super(null, new String[]{"data", "finalizer"});
        }

        public Generic(Pointer pointer) {
            super(pointer, new String[]{"data", "finalizer"});
        }
    }

    public static final class Vector extends OrderedStructure {
        public NativeLong x;
        public NativeLong y;

        public Vector() {
            super(null, new String[]{"x", "y"});
        }
    }

    public static final class BBox extends OrderedStructure {
        public NativeLong xMin;
        public NativeLong yMin;
        public NativeLong xMax;
        public NativeLong yMax;

        public BBox() {
            super(null, new String[]{"xMin", "yMin", "xMax", "yMax"});
        }
    }

    public static final class Metrics extends OrderedStructure {
        public NativeLong width;
        public NativeLong height;
        public NativeLong horiBearingX;
        public NativeLong horiBearingY;
        public NativeLong horiAdvance;
        public NativeLong vertBearingX;
        public NativeLong vertBearingY;
        public NativeLong vertAdvance;

        public Metrics() {
            super(null, new String[]{"width", "height", "horiBearingX", "horiBearingY", "horiAdvance",
                    "vertBearingX", "vertBearingY", "vertAdvance"});
        }
    }

    public static final class Bitmap extends OrderedStructure {
        public int rows;
        public int width;
        public int pitch;
        public Pointer buffer;
        public short numGrays;
        public byte pixelMode;
        public byte paletteMode;
        public Pointer palette;

        public Bitmap() {
            super(null, new String[]{"rows", "width", "pitch", "buffer", "numGrays", "pixelMode", "paletteMode", "palette"});
        }
    }

    public static final class FacePrefix extends OrderedStructure {
        public NativeLong numFaces;
        public NativeLong faceIndex;
        public NativeLong faceFlags;
        public NativeLong styleFlags;
        public NativeLong numGlyphs;
        public Pointer familyName;
        public Pointer styleName;
        public int numFixedSizes;
        public Pointer availableSizes;
        public int numCharmaps;
        public Pointer charmaps;
        public Generic generic;
        public BBox bbox;
        public short unitsPerEM;
        public short ascender;
        public short descender;
        public short height;
        public short maxAdvanceWidth;
        public short maxAdvanceHeight;
        public short underlinePosition;
        public short underlineThickness;
        public Pointer glyph;

        public FacePrefix(Pointer pointer) {
            super(pointer, new String[]{"numFaces", "faceIndex", "faceFlags", "styleFlags", "numGlyphs",
                    "familyName", "styleName", "numFixedSizes", "availableSizes", "numCharmaps", "charmaps",
                    "generic", "bbox", "unitsPerEM", "ascender", "descender", "height", "maxAdvanceWidth",
                    "maxAdvanceHeight", "underlinePosition", "underlineThickness", "glyph"});
        }
    }

    public static final class GlyphSlotPrefix extends OrderedStructure {
        public Pointer library;
        public Pointer face;
        public Pointer next;
        public int glyphIndex;
        public Generic generic;
        public Metrics metrics;
        public NativeLong linearHoriAdvance;
        public NativeLong linearVertAdvance;
        public Vector advance;
        public int format;
        public Bitmap bitmap;
        public int bitmapLeft;
        public int bitmapTop;

        public GlyphSlotPrefix(Pointer pointer) {
            super(pointer, new String[]{"library", "face", "next", "glyphIndex", "generic", "metrics",
                    "linearHoriAdvance", "linearVertAdvance", "advance", "format", "bitmap", "bitmapLeft", "bitmapTop"});
        }
    }
}

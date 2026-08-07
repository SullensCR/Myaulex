package myau.util.font.variable;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Minimal JNA binding for the FreeType calls required by the font atlas renderers. */
final class FreeTypeLibrary {
    static final int LOAD_DEFAULT = 0;
    static final int RENDER_MODE_NORMAL = 0;

    interface Api extends Library {
        int FT_Init_FreeType(PointerByReference library);

        int FT_Done_FreeType(Pointer library);

        int FT_New_Memory_Face(Pointer library, Pointer fileBase, NativeLong fileSize,
                               NativeLong faceIndex, PointerByReference face);

        int FT_Done_Face(Pointer face);

        int FT_Set_Pixel_Sizes(Pointer face, int pixelWidth, int pixelHeight);

        int FT_Set_Var_Design_Coordinates(Pointer face, int coordinateCount, Pointer coordinates);

        int FT_Load_Char(Pointer face, NativeLong charCode, int loadFlags);

        int FT_Render_Glyph(Pointer glyphSlot, int renderMode);
    }

    private static volatile Api instance;
    private static volatile boolean attempted;
    private static File extractedNative;

    private FreeTypeLibrary() {
    }

    static Api get() {
        if (attempted) return instance;
        synchronized (FreeTypeLibrary.class) {
            if (attempted) return instance;
            attempted = true;
            try {
                String bundled = extractBundledNative();
                if (bundled != null) {
                    instance = (Api) Native.loadLibrary(bundled, Api.class);
                }
            } catch (Throwable ignored) {
                try {
                    instance = (Api) Native.loadLibrary("freetype", Api.class);
                } catch (Throwable ignoredAgain) {
                    instance = null;
                }
            }
            return instance;
        }
    }

    private static String extractBundledNative() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64") || arch.contains("armv8");
        boolean x64 = !arm && (arch.contains("amd64") || arch.contains("x86_64") || arch.equals("x64"));
        String resource;
        String suffix;
        if (os.contains("win") && x64) {
            resource = "windows/x64/org/lwjgl/freetype/freetype.dll";
            suffix = ".dll";
        } else if (os.contains("mac") && x64) {
            resource = "macos/x64/org/lwjgl/freetype/libfreetype.dylib";
            suffix = ".dylib";
        } else if (os.contains("linux") && x64) {
            resource = "linux/x64/org/lwjgl/freetype/libfreetype.so";
            suffix = ".so";
        } else {
            return null;
        }

        InputStream input = FreeTypeLibrary.class.getClassLoader().getResourceAsStream(resource);
        if (input == null) input = FreeTypeLibrary.class.getResourceAsStream("/" + resource);
        if (input == null) return null;
        extractedNative = File.createTempFile("myaulex-freetype-", suffix);
        extractedNative.deleteOnExit();
        try (InputStream stream = input; FileOutputStream output = new FileOutputStream(extractedNative)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return extractedNative.getAbsolutePath();
    }

    static Memory nativeMemory(byte[] bytes) {
        Memory memory = new Memory(bytes.length);
        memory.write(0, bytes, 0, bytes.length);
        return memory;
    }
}

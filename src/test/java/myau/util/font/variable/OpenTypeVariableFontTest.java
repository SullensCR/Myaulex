package myau.util.font.variable;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OpenTypeVariableFontTest {
    @Test
    public void discoversGoogleSansFlexAxesAndClampsCoordinates() throws IOException {
        OpenTypeVariableFont font = loadGoogleSans();
        assertTrue(font.axes().size() >= 3);
        assertNotNull(font.axis("wght"));
        assertNotNull(font.axis("wdth"));

        FontAxes clamped = font.clamp(FontAxes.builder()
                .put("wght", -500.0F)
                .put("wdth", 500.0F)
                .build());
        assertEquals(font.axis("wght").minimum(), clamped.value("wght", 0.0F), 0.001F);
        assertEquals(font.axis("wdth").maximum(), clamped.value("wdth", 0.0F), 0.001F);
    }

    @Test
    public void freetypeRendersDifferentAxisInstances() throws IOException {
        OpenTypeVariableFont font = loadGoogleSans();
        FreeTypeFace regular = FreeTypeFace.open(font, FontAxes.builder()
                .put("wght", 100.0F).put("wdth", 100.0F).put("opsz", 14.0F).build(), 32);
        FreeTypeFace black = FreeTypeFace.open(font, FontAxes.builder()
                .put("wght", 900.0F).put("wdth", 100.0F).put("opsz", 14.0F).build(), 32);
        assertNotNull(regular);
        assertNotNull(black);
        try {
            FreeTypeFace.GlyphBitmap regularGlyph = regular.glyph('A');
            FreeTypeFace.GlyphBitmap blackGlyph = black.glyph('A');
            assertTrue(regularGlyph.advance > 0.0F);
            assertTrue(blackGlyph.advance > 0.0F);
            assertTrue(alphaSum(regularGlyph.alpha) > 0);
            assertTrue(alphaSum(blackGlyph.alpha) > 0);
            assertTrue(regularGlyph.width != blackGlyph.width
                    || regularGlyph.height != blackGlyph.height
                    || !Arrays.equals(regularGlyph.alpha, blackGlyph.alpha));
        } finally {
            regular.close();
            black.close();
        }
    }

    private static OpenTypeVariableFont loadGoogleSans() throws IOException {
        try (InputStream input = OpenTypeVariableFontTest.class.getResourceAsStream(
                "/assets/myau/ui/font/GoogleSansFlex.ttf")) {
            if (input == null) throw new IOException("Google Sans Flex test resource is missing");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return OpenTypeVariableFont.fromBytes("ui/font/GoogleSansFlex.ttf", output.toByteArray());
        }
    }

    private static int alphaSum(byte[] alpha) {
        int sum = 0;
        for (byte value : alpha) sum += value & 0xFF;
        return sum;
    }
}

package myau.util.font.variable;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed, immutable bytes and axis metadata for a variable TrueType/OpenType font. */
public final class OpenTypeVariableFont {
    private final String resourcePath;
    private final byte[] bytes;
    private final List<VariableFontAxis> axes;

    private OpenTypeVariableFont(String resourcePath, byte[] bytes, List<VariableFontAxis> axes) {
        this.resourcePath = resourcePath;
        this.bytes = bytes;
        this.axes = Collections.unmodifiableList(new ArrayList<>(axes));
    }

    public static OpenTypeVariableFont fromBytes(String resourcePath, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Font data is empty");
        return new OpenTypeVariableFont(resourcePath, bytes.clone(), parseAxes(bytes));
    }

    public static OpenTypeVariableFont load(String resourcePath) throws IOException {
        String normalized = normalizeResourcePath(resourcePath);
        InputStream stream = OpenTypeVariableFont.class.getResourceAsStream("/assets/myau/" + normalized);
        if (stream == null) {
            ClassLoader loader = OpenTypeVariableFont.class.getClassLoader();
            if (loader != null) stream = loader.getResourceAsStream("assets/myau/" + normalized);
        }
        if (stream == null) {
            try {
                stream = Minecraft.getMinecraft().getResourceManager()
                        .getResource(new ResourceLocation("myau", normalized)).getInputStream();
            } catch (Throwable ignored) {
            }
        }
        if (stream == null) throw new IOException("Variable font resource not found: " + normalized);
        try (InputStream input = stream) {
            return fromBytes(normalized, readAll(input));
        }
    }

    public String resourcePath() {
        return resourcePath;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public List<VariableFontAxis> axes() {
        return axes;
    }

    public VariableFontAxis axis(String tag) {
        String normalized = FontAxes.normalizeTag(tag);
        for (VariableFontAxis axis : axes) {
            if (axis.tag().equals(normalized)) return axis;
        }
        return null;
    }

    public FontAxes clamp(FontAxes requested) {
        FontAxes.Builder result = FontAxes.builder();
        for (VariableFontAxis axis : axes) {
            result.put(axis.tag(), axis.clamp(requested.value(axis.tag(), axis.defaultValue())));
        }
        return result.build();
    }

    /** Returns FreeType's signed 16.16 design-coordinate values in fvar order. */
    public int[] designCoordinates(FontAxes requested) {
        FontAxes clamped = clamp(requested);
        int[] coordinates = new int[axes.size()];
        for (int i = 0; i < axes.size(); i++) {
            coordinates[i] = Math.round(clamped.value(axes.get(i).tag(), axes.get(i).defaultValue()) * 65536.0F);
        }
        return coordinates;
    }

    private static List<VariableFontAxis> parseAxes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (buffer.remaining() < 12) return Collections.emptyList();
        buffer.position(4);
        int tableCount = u16(buffer);
        buffer.position(12);
        Map<String, Table> tables = new LinkedHashMap<>();
        for (int i = 0; i < tableCount && buffer.remaining() >= 16; i++) {
            String tag = tag(buffer.getInt());
            buffer.getInt();
            long offset = u32(buffer);
            long length = u32(buffer);
            if (offset <= bytes.length && length <= bytes.length - offset) {
                tables.put(tag, new Table((int) offset, (int) length));
            }
        }
        Table fvar = tables.get("fvar");
        if (fvar == null || fvar.length < 16) return Collections.emptyList();
        buffer.position(fvar.offset + 4);
        int axesOffset = u16(buffer);
        buffer.getShort();
        int axisCount = u16(buffer);
        int axisSize = u16(buffer);
        buffer.getShort();
        buffer.getShort();
        if (axisSize < 20) return Collections.emptyList();
        int axisStart = fvar.offset + axesOffset;
        List<VariableFontAxis> result = new ArrayList<>();
        for (int i = 0; i < axisCount; i++) {
            int position = axisStart + i * axisSize;
            if (position < 0 || position + 20 > fvar.offset + fvar.length || position + 20 > bytes.length) break;
            buffer.position(position);
            String tag = tag(buffer.getInt());
            float minimum = fixed16(buffer.getInt());
            float defaultValue = fixed16(buffer.getInt());
            float maximum = fixed16(buffer.getInt());
            result.add(new VariableFontAxis(tag, minimum, defaultValue, maximum));
        }
        return result;
    }

    private static String normalizeResourcePath(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.startsWith("assets/myau/")) normalized = normalized.substring("assets/myau/".length());
        return normalized;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = input.read(chunk)) != -1) output.write(chunk, 0, count);
        return output.toByteArray();
    }

    private static int u16(ByteBuffer buffer) {
        return buffer.getShort() & 0xFFFF;
    }

    private static long u32(ByteBuffer buffer) {
        return buffer.getInt() & 0xFFFFFFFFL;
    }

    private static String tag(int value) {
        byte[] bytes = new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static float fixed16(int value) {
        return value / 65536.0F;
    }

    private static final class Table {
        final int offset;
        final int length;

        Table(int offset, int length) {
            this.offset = offset;
            this.length = length;
        }
    }
}

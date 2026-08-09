package myau.render.ui;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import myau.util.font.variable.FontAxes;
import myau.util.font.variable.OpenTypeVariableFont;

public final class UiFonts {
    public static final int REGULAR = 400;
    public static final int SEMIBOLD = 600;
    public static final int BOLD = 700;
    public static final int BLACK = 900;

    private final Map<String, UiFont> cache = new HashMap<>();
    private Font googleSans;
    private Font minecraftTen;
    private Font snPro;
    private Font mojang;
    private OpenTypeVariableFont googleVariable;
    private OpenTypeVariableFont snProVariable;

    public UiFont google(float size, int weight) {
        return google(size, FontAxes.weight(weight));
    }

    public UiFont google(float size, FontAxes axes) {
        return variable("google", loadGoogleVariable(), size, axes, loadGoogle());
    }

    public UiFont minecraft(float size) {
        return font("minecraft", loadMinecraft(), size, REGULAR);
    }

    public UiFont snPro(float size, int weight) {
        return snPro(size, FontAxes.weight(weight));
    }

    public UiFont snPro(float size, FontAxes axes) {
        return variable("snpro", loadSnProVariable(), size, axes, loadSnProFallback());
    }

    public UiFont variable(String family, OpenTypeVariableFont variableFont, float size, FontAxes axes) {
        return variable(family, variableFont, size, axes,
                new Font("SansSerif", Font.PLAIN, Math.max(1, Math.round(size))));
    }

    public UiFont mojang(float size) {
        if (mojang == null) mojang = load("ui/font/Mojang-Regular.ttf");
        return font("mojang", mojang, size, REGULAR);
    }

    private UiFont font(String family, Font base, float size, int weight) {
        String key = family + ':' + size + ':' + weight;
        UiFont existing = cache.get(key);
        if (existing != null) return existing;

        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.SIZE, size);
        attributes.put(TextAttribute.WEIGHT, awtWeight(weight));
        UiFont created = new UiFont(base.deriveFont(attributes));
        cache.put(key, created);
        return created;
    }

    private Font loadGoogle() {
        if (googleSans == null) googleSans = load("ui/font/GoogleSansFlex.ttf");
        return googleSans;
    }

    private Font loadSnProFallback() {
        if (snPro == null) snPro = load("ui/font/SNPro-VariableFont_wght.ttf");
        return snPro;
    }

    private OpenTypeVariableFont loadGoogleVariable() {
        if (googleVariable == null) googleVariable = loadVariable("ui/font/GoogleSansFlex.ttf");
        return googleVariable;
    }

    private OpenTypeVariableFont loadSnProVariable() {
        if (snProVariable == null) snProVariable = loadVariable("ui/font/SNPro-VariableFont_wght.ttf");
        return snProVariable;
    }

    private UiFont variable(String family, OpenTypeVariableFont variableFont, float size,
                            FontAxes axes, Font fallback) {
        FontAxes clamped = variableFont.clamp(axes);
        String key = family + ':' + size + ':' + clamped.cacheKey();
        UiFont existing = cache.get(key);
        if (existing != null) return existing;
        UiFont created = new UiFont(variableFont, size, clamped, fallback);
        cache.put(key, created);
        return created;
    }

    private OpenTypeVariableFont loadVariable(String path) {
        try {
            return OpenTypeVariableFont.load(path);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load variable UI font /assets/myau/" + path, e);
        }
    }

    private Font loadMinecraft() {
        if (minecraftTen == null) minecraftTen = load("ui/font/MinecraftTen.ttf");
        return minecraftTen;
    }

    private Font load(String path) {
        try (InputStream input = UiResource.open(path)) {
            return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load UI font /assets/myau/" + path, e);
        }
    }

    private static Float awtWeight(int weight) {
        if (weight >= BLACK) return TextAttribute.WEIGHT_ULTRABOLD;
        if (weight >= BOLD) return TextAttribute.WEIGHT_BOLD;
        if (weight >= SEMIBOLD) return TextAttribute.WEIGHT_SEMIBOLD;
        return TextAttribute.WEIGHT_REGULAR;
    }

    public void delete() {
        for (UiFont font : cache.values()) font.delete();
        cache.clear();
    }
}

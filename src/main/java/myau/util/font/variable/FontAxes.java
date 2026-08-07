package myau.util.font.variable;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable OpenType variation-axis coordinates. */
public final class FontAxes {
    private final Map<String, Float> values;

    private FontAxes(Map<String, Float> values) {
        this.values = Collections.unmodifiableMap(new TreeMap<>(values));
    }

    public static FontAxes none() {
        return new FontAxes(Collections.<String, Float>emptyMap());
    }

    public static FontAxes of(String tag, float value) {
        return builder().put(tag, value).build();
    }

    public static FontAxes weight(float value) {
        return of("wght", value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Float> values() {
        return values;
    }

    public float value(String tag, float fallback) {
        Float value = values.get(normalizeTag(tag));
        return value == null ? fallback : value;
    }

    public String cacheKey() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Float> entry : values.entrySet()) {
            if (result.length() > 0) result.append(';');
            result.append(entry.getKey()).append('=').append(Float.toString(entry.getValue()));
        }
        return result.toString();
    }

    static String normalizeTag(String tag) {
        if (tag == null) throw new IllegalArgumentException("Axis tag cannot be null");
        String normalized = tag.trim();
        if (normalized.length() != 4) {
            throw new IllegalArgumentException("OpenType axis tags must contain four characters: " + tag);
        }
        return normalized;
    }

    public static final class Builder {
        private final Map<String, Float> values = new TreeMap<>();

        public Builder put(String tag, float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("Axis value must be finite: " + value);
            }
            values.put(normalizeTag(tag), value);
            return this;
        }

        public FontAxes build() {
            return new FontAxes(values);
        }
    }
}

package myau.util.font.variable;

/** Metadata for one axis declared by an OpenType variable font. */
public final class VariableFontAxis {
    private final String tag;
    private final float minimum;
    private final float defaultValue;
    private final float maximum;

    VariableFontAxis(String tag, float minimum, float defaultValue, float maximum) {
        this.tag = tag;
        this.minimum = minimum;
        this.defaultValue = defaultValue;
        this.maximum = maximum;
    }

    public String tag() {
        return tag;
    }

    public float minimum() {
        return minimum;
    }

    public float defaultValue() {
        return defaultValue;
    }

    public float maximum() {
        return maximum;
    }

    public float clamp(float value) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

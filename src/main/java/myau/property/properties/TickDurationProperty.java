package myau.property.properties;

/** Integer ticks with a human-readable duration; edited and persisted as ticks. */
public final class TickDurationProperty extends IntProperty {
    public TickDurationProperty(String name, int value, int minimum, int maximum) {
        super(name, value, minimum, maximum);
    }

    @Override
    public boolean setValue(Object value) {
        return value instanceof Integer && (Integer) value >= getMinimum() && (Integer) value <= getMaximum()
                && super.setValue(value);
    }

    public String displayDuration() { return getValue() + "t (" + getValue() * 50 + " ms)"; }

    @Override
    public String formatValue() { return "&e" + displayDuration(); }
}

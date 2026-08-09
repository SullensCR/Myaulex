package myau.property.properties;

import com.google.gson.JsonObject;
import myau.property.Property;
import myau.property.RecommendedRange;

import java.util.function.BooleanSupplier;

public class FloatProperty extends Property<Float> implements RecommendedRange {
    private final Float minimum;
    private final Float maximum;

    public FloatProperty(String name, Float value, Float minimum, Float maximum) {
        this(name, value, minimum, maximum, null);
    }

    public FloatProperty(String string, Float value, Float minimum, Float maximum, BooleanSupplier check) {
        super(string, value, floatV -> floatV != null && Float.isFinite(floatV)
                && Math.abs(floatV) <= 1_000_000.0F, check);
        this.minimum = minimum;
        this.maximum = maximum;
    }

    @Override
    public String getValuePrompt() {
        return String.format("%s-%s", this.minimum, this.maximum);
    }

    @Override
    public String formatValue() {
        return String.format("&6%s", this.getValue());
    }

    @Override
    public boolean parseString(String string) {
        return this.setValue(Float.parseFloat(string));
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        return this.setValue(jsonObject.get(this.getName()).getAsNumber().floatValue());
    }

    @Override
    public void write(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), this.getValue());
    }

    public Float getMinimum() {
        return minimum;
    }

    public Float getMaximum() {
        return maximum;
    }

    /**
     * Returns the slider step, or zero when the property is continuously draggable.
     */
    public double getStep() {
        return 0.0D;
    }

    @Override
    public double getRecommendedMinimum() { return minimum; }

    @Override
    public double getRecommendedMaximum() { return maximum; }
}

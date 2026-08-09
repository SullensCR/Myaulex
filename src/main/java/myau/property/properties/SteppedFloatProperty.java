package myau.property.properties;

import java.util.function.BooleanSupplier;

/**
 * A float property with hard bounds and a fixed UI/configuration step.
 */
public class SteppedFloatProperty extends FloatProperty {
    private static final float EPSILON = 0.0001F;
    private final float step;

    public SteppedFloatProperty(
            String name, Float value, Float minimum, Float maximum, Float step, BooleanSupplier check
    ) {
        super(name, value, minimum, maximum, check);
        if (step == null || !Float.isFinite(step) || step <= 0.0F) {
            throw new IllegalArgumentException("step must be finite and positive");
        }
        this.step = step;
    }

    @Override
    public boolean setValue(Object object) {
        if (!(object instanceof Number)) return false;
        float value = ((Number) object).floatValue();
        if (!Float.isFinite(value)
                || value < this.getMinimum() - EPSILON
                || value > this.getMaximum() + EPSILON) {
            return false;
        }

        float steps = (value - this.getMinimum()) / this.step;
        if (Math.abs(steps - Math.round(steps)) > EPSILON) return false;
        return super.setValue(value);
    }

    @Override
    public boolean parseString(String string) {
        try {
            return this.setValue(Float.parseFloat(string));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public double getStep() {
        return this.step;
    }
}

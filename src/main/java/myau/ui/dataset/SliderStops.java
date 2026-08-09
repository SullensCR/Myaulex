package myau.ui.dataset;

/** Shared stop positions and snapping rules for numeric ClickGUI sliders. */
public final class SliderStops {
    private static final int MAX_VISIBLE_STOPS = 11;

    private SliderStops() {
    }

    public static int count(double minimum, double maximum, double increment) {
        if (!validRange(minimum, maximum) || !Double.isFinite(increment) || increment <= 0.0D) {
            return 2;
        }

        long steps = stepCount(minimum, maximum, increment);
        return steps <= MAX_VISIBLE_STOPS - 1 ? (int) steps + 1 : MAX_VISIBLE_STOPS;
    }

    public static double valueAt(double minimum, double maximum, double increment, int index, int count) {
        if (count <= 1 || index <= 0) return minimum;
        if (index >= count - 1) return maximum;

        long steps = stepCount(minimum, maximum, increment);
        if (steps <= MAX_VISIBLE_STOPS - 1) {
            return minimum + index * increment;
        }

        long stopStep = Math.round((double) index * steps / (count - 1));
        return minimum + stopStep * increment;
    }

    public static double fraction(double value, double minimum, double maximum) {
        if (!validRange(minimum, maximum)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, (value - minimum) / (maximum - minimum)));
    }

    public static double snap(double value, double minimum, double maximum, double increment) {
        if (!validRange(minimum, maximum)) return minimum;
        double clamped = Math.max(minimum, Math.min(maximum, value));
        if (!Double.isFinite(increment) || increment <= 0.0D) return clamped;
        double snapped = minimum + Math.round((clamped - minimum) / increment) * increment;
        return Math.max(minimum, Math.min(maximum, snapped));
    }

    private static long stepCount(double minimum, double maximum, double increment) {
        return Math.max(1L, Math.round((maximum - minimum) / increment));
    }

    private static boolean validRange(double minimum, double maximum) {
        return Double.isFinite(minimum) && Double.isFinite(maximum) && maximum > minimum;
    }
}

package myau.property;

/** Numeric metadata used by sliders and anticheat-safety warnings. */
public interface RecommendedRange {
    double getRecommendedMinimum();
    double getRecommendedMaximum();

    default boolean isRecommended(double value) {
        return value >= getRecommendedMinimum() && value <= getRecommendedMaximum();
    }
}

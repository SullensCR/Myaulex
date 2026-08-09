package myau.render;

import java.awt.Color;
import java.util.Random;

/** Pure timing and source-selection rules for the central Aura indicator. */
public final class IndicatorState {
    public static final long BLINK_DEFAULT_WINDOW_MILLIS = 500L;

    private IndicatorState() {
    }

    public enum Source {
        HIDDEN,
        AURA,
        BLINK,
        LAG_RANGE
    }

    public static final class Frame {
        private final Source source;
        private final float sweep;

        private Frame(Source source, float sweep) {
            this.source = source;
            this.sweep = sweep;
        }

        public Source getSource() {
            return source;
        }

        public float getSweep() {
            return sweep;
        }
    }

    public static Frame resolve(boolean indicatorEnabled, boolean auraEnabled,
                                boolean blinkActive, boolean blinkPulse, long blinkStartedAtMillis,
                                boolean lagRangeActive, int lagRangeDelayMillis, long lagRangeStartedAtMillis,
                                long nowMillis) {
        if (!indicatorEnabled || !auraEnabled) return new Frame(Source.HIDDEN, 0.0F);

        long blinkElapsed = elapsed(nowMillis, blinkStartedAtMillis);
        if (blinkActive && blinkStartedAtMillis > 0L
                && (blinkPulse || blinkElapsed <= BLINK_DEFAULT_WINDOW_MILLIS)) {
            return new Frame(Source.BLINK, progress(blinkElapsed, BLINK_DEFAULT_WINDOW_MILLIS));
        }

        if (lagRangeActive && lagRangeDelayMillis > 0 && lagRangeStartedAtMillis > 0L) {
            return new Frame(Source.LAG_RANGE,
                    progress(elapsed(nowMillis, lagRangeStartedAtMillis), lagRangeDelayMillis));
        }

        return new Frame(Source.AURA, 1.0F);
    }

    public static Color randomVividColor(Random random) {
        return Color.getHSBColor(random.nextFloat(), 0.51F + random.nextFloat() * 0.49F,
                0.51F + random.nextFloat() * 0.49F);
    }

    private static long elapsed(long nowMillis, long startedAtMillis) {
        return Math.max(0L, nowMillis - startedAtMillis);
    }

    private static float progress(long elapsedMillis, long durationMillis) {
        if (durationMillis <= 0L) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, (float) elapsedMillis / (float) durationMillis));
    }
}

package myau.module.modules;

import java.util.ArrayDeque;
import java.util.Deque;

final class HitSelectState {
    static final long DAMAGE_COOLDOWN_NANOS = 500_000_000L;
    private static final long PING_ALLOWANCE_NANOS = 25_000_000L;

    private final Deque<Integer> recentPing = new ArrayDeque<>();
    private long safeAttackNanos;
    private long pendingAttackNanos;
    private double smoothedPingMs = -1.0;
    private int confirmedHits;

    void resetCombat() {
        safeAttackNanos = 0L;
        pendingAttackNanos = 0L;
        confirmedHits = 0;
    }

    void clearCombo() {
        pendingAttackNanos = 0L;
        confirmedHits = 0;
    }

    void resetAll() {
        resetCombat();
        recentPing.clear();
        smoothedPingMs = -1.0;
    }

    void samplePing(int pingMs) {
        if (pingMs <= 0) return;
        smoothedPingMs = smoothedPingMs < 0.0 ? pingMs : smoothedPingMs * 0.75 + pingMs * 0.25;
        recentPing.addLast(pingMs);
        while (recentPing.size() > 10) recentPing.removeFirst();
    }

    long correctionNanos() {
        if (recentPing.isEmpty()) return 0L;
        int minimum = Integer.MAX_VALUE;
        for (Integer sample : recentPing) minimum = Math.min(minimum, sample);
        return Math.max(0L, minimum * 1_000_000L - PING_ALLOWANCE_NANOS);
    }

    long halfSmoothedPingNanos() {
        return smoothedPingMs < 0.0 ? 0L : (long) (smoothedPingMs * 500_000.0);
    }

    long confirmationWindowNanos() {
        long pingNanos = smoothedPingMs < 0.0 ? 0L : (long) (smoothedPingMs * 1_000_000.0);
        return Math.max(250_000_000L, pingNanos + 150_000_000L);
    }

    void recordAttack(long sentNanos) {
        pendingAttackNanos = sentNanos;
        safeAttackNanos = Math.max(safeAttackNanos, sentNanos + DAMAGE_COOLDOWN_NANOS);
    }

    boolean recordDamageStatus(long arrivalNanos) {
        safeAttackNanos = Math.max(safeAttackNanos,
                arrivalNanos + DAMAGE_COOLDOWN_NANOS - correctionNanos());
        if (pendingAttackNanos != 0L
                && arrivalNanos >= pendingAttackNanos
                && arrivalNanos - pendingAttackNanos <= confirmationWindowNanos()) {
            pendingAttackNanos = 0L;
            confirmedHits++;
            return true;
        }
        return false;
    }

    void addHurtTimeFallback(long nowNanos, int hurtTime) {
        if (safeAttackNanos == 0L && hurtTime > 0) {
            safeAttackNanos = nowNanos + hurtTime * 50_000_000L;
        }
    }

    boolean isReady(long sendNanos) {
        return sendNanos >= safeAttackNanos;
    }

    long getSafeAttackNanos() {
        return safeAttackNanos;
    }

    int getConfirmedHits() {
        return confirmedHits;
    }

    static final class SprintWindow {
        private long restoreNanos;

        void begin(long nowNanos, int delayMs) {
            restoreNanos = nowNanos + delayMs * 1_000_000L;
        }

        boolean isActive(long nowNanos) {
            return restoreNanos != 0L && nowNanos < restoreNanos;
        }

        boolean shouldRestore(long nowNanos) {
            return restoreNanos != 0L && nowNanos >= restoreNanos;
        }

        void clear() {
            restoreNanos = 0L;
        }
    }

    static final class MotionHistory {
        private final Deque<Point> points = new ArrayDeque<>();

        void add(double x, double z) {
            Point previous = points.peekLast();
            if (previous != null && Math.hypot(x - previous.x, z - previous.z) > 3.0) points.clear();
            points.addLast(new Point(x, z));
            while (points.size() > 5) points.removeFirst();
        }

        double velocityX() {
            return velocity(true);
        }

        double velocityZ() {
            return velocity(false);
        }

        private double velocity(boolean xAxis) {
            if (points.size() < 2) return 0.0;
            Point[] values = points.toArray(new Point[0]);
            double total = 0.0;
            double weights = 0.0;
            int first = Math.max(1, values.length - 3);
            for (int i = first; i < values.length; i++) {
                double weight = i - first + 1;
                total += ((xAxis ? values[i].x : values[i].z)
                        - (xAxis ? values[i - 1].x : values[i - 1].z)) * weight;
                weights += weight;
            }
            return weights == 0.0 ? 0.0 : total / weights;
        }

        void clear() {
            points.clear();
        }

        private static final class Point {
            final double x;
            final double z;

            Point(double x, double z) {
                this.x = x;
                this.z = z;
            }
        }
    }
}

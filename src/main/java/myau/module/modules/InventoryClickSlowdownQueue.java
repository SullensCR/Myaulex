package myau.module.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Owns the click-slowdown timing independently from InventoryMove's mode-specific
 * packet delays. A slowdown batch keeps the sprint key blocked until its restore
 * window has completed.
 */
final class InventoryClickSlowdownQueue<T> {
    private enum Phase {
        IDLE,
        SLOWDOWN,
        RESTORE
    }

    static final class Advance<T> {
        private final List<T> releasedClicks;
        private final boolean restoreSprint;

        private Advance(List<T> releasedClicks, boolean restoreSprint) {
            this.releasedClicks = releasedClicks;
            this.restoreSprint = restoreSprint;
        }

        List<T> getReleasedClicks() {
            return this.releasedClicks;
        }

        boolean shouldRestoreSprint() {
            return this.restoreSprint;
        }
    }

    private final Queue<T> clicks = new ConcurrentLinkedQueue<>();
    private Phase phase = Phase.IDLE;
    private int slowdownTicksRemaining;
    private int restoreTicksRemaining;

    Advance<T> enqueue(T click, int slowdownTicks, int restoreTicks) {
        this.clicks.offer(click);
        if (this.phase != Phase.SLOWDOWN) {
            this.phase = Phase.SLOWDOWN;
            this.slowdownTicksRemaining = nonNegative(slowdownTicks);
        }

        if (this.slowdownTicksRemaining == 0) {
            return releaseBatch(restoreTicks);
        }
        return noAdvance();
    }

    Advance<T> tick(int restoreTicks) {
        if (this.phase == Phase.SLOWDOWN) {
            if (this.slowdownTicksRemaining > 0) {
                this.slowdownTicksRemaining--;
            }
            if (this.slowdownTicksRemaining == 0) {
                return releaseBatch(restoreTicks);
            }
        } else if (this.phase == Phase.RESTORE) {
            if (this.restoreTicksRemaining > 0) {
                this.restoreTicksRemaining--;
            }
            if (this.restoreTicksRemaining == 0) {
                resetTiming();
                return new Advance<>(Collections.emptyList(), true);
            }
        }
        return noAdvance();
    }

    boolean blocksSprint() {
        return this.phase != Phase.IDLE;
    }

    int pendingClicks() {
        return this.clicks.size();
    }

    List<T> drainAndReset() {
        List<T> pending = drainClicks();
        resetTiming();
        return pending;
    }

    void discardAndReset() {
        this.clicks.clear();
        resetTiming();
    }

    private Advance<T> releaseBatch(int restoreTicks) {
        List<T> released = drainClicks();
        this.slowdownTicksRemaining = 0;
        this.restoreTicksRemaining = nonNegative(restoreTicks);
        boolean restoreNow = this.restoreTicksRemaining == 0;
        this.phase = restoreNow ? Phase.IDLE : Phase.RESTORE;
        return new Advance<>(released, restoreNow);
    }

    private List<T> drainClicks() {
        if (this.clicks.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> pending = new ArrayList<>(this.clicks.size());
        T click;
        while ((click = this.clicks.poll()) != null) {
            pending.add(click);
        }
        return pending;
    }

    private void resetTiming() {
        this.phase = Phase.IDLE;
        this.slowdownTicksRemaining = 0;
        this.restoreTicksRemaining = 0;
    }

    private static int nonNegative(int ticks) {
        return Math.max(0, ticks);
    }

    private static <T> Advance<T> noAdvance() {
        return new Advance<>(Collections.emptyList(), false);
    }
}

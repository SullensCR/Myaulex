package myau.module.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tick-driven translation of OpenExpo's Aura/AutoBlock hand-off.  This class
 * deliberately contains no Minecraft state so the packet-stage ordering can
 * be verified without launching the game.
 */
final class ExpoAutoBlockState {
    static final int LEGIT = 7;
    static final int LAG_LEGIT = 9;
    static final int LAG = 10;
    static final int LAG_NEW = 11;

    private static final int TICKS_PER_SECOND = 20;
    private static final int MAX_LAG_LEGIT_BUFFER_TICKS = 20;

    enum Action {
        RELEASE,
        SPOOF_AWAY,
        SPOOF_BACK,
        ATTACK,
        BLOCK,
        FLUSH_BUFFER,
        START_BUFFER
    }

    static final class Step {
        private static final Step EMPTY = new Step(Collections.<Action>emptyList());
        private final List<Action> actions;

        private Step(List<Action> actions) {
            this.actions = actions;
        }

        static Step of(Action... actions) {
            if (actions.length == 0) {
                return EMPTY;
            }
            List<Action> ordered = new ArrayList<>(actions.length);
            Collections.addAll(ordered, actions);
            return new Step(Collections.unmodifiableList(ordered));
        }

        static Step of(List<Action> actions) {
            return actions.isEmpty() ? EMPTY : new Step(Collections.unmodifiableList(actions));
        }

        List<Action> getActions() {
            return this.actions;
        }

        boolean isEmpty() {
            return this.actions.isEmpty();
        }
    }

    private int activeMode = -1;
    private int attackProgress;
    private int bufferedTicks;
    private boolean firstAttempt = true;
    private boolean lagNewBlockPending;
    private boolean lagNewFlushPending;
    private boolean lagFlushPending;

    static boolean isExpoMode(int auraMode) {
        return auraMode == LEGIT || auraMode == LAG_LEGIT || auraMode == LAG || auraMode == LAG_NEW;
    }

    static boolean isLagMode(int auraMode) {
        return auraMode == LAG_LEGIT || auraMode == LAG || auraMode == LAG_NEW;
    }

    static int apsForIndex(int apsIndex) {
        switch (apsIndex) {
            case 0:
                return 3;
            case 1:
                return 5;
            case 2:
                return 7;
            case 3:
                return 10;
            case 4:
                return 14;
            default:
                return 3;
        }
    }

    Step pre(int auraMode, int aps, boolean blocking, boolean noSlowLive) {
        if (!isExpoMode(auraMode)) {
            this.reset();
            return Step.EMPTY;
        }
        if (this.activeMode != auraMode) {
            this.reset();
            this.activeMode = auraMode;
        }

        if (isLagMode(auraMode)) {
            this.bufferedTicks++;
        }
        if (this.lagNewBlockPending || this.lagNewFlushPending) {
            return Step.EMPTY;
        }

        boolean due;
        if (this.firstAttempt) {
            this.firstAttempt = false;
            due = true;
        } else {
            this.attackProgress += aps;
            due = this.attackProgress >= TICKS_PER_SECOND;
            if (due) {
                this.attackProgress -= TICKS_PER_SECOND;
            }
        }
        if (!due) {
            return Step.EMPTY;
        }

        List<Action> actions = new ArrayList<>();
        if (blocking) {
            actions.add(Action.RELEASE);
        }
        if (noSlowLive && isLagMode(auraMode)) {
            actions.add(Action.SPOOF_AWAY);
            actions.add(Action.SPOOF_BACK);
        }
        actions.add(Action.ATTACK);

        if (auraMode == LAG_NEW) {
            this.lagNewBlockPending = true;
        } else {
            actions.add(Action.BLOCK);
            if (auraMode == LAG) {
                this.lagFlushPending = true;
            }
        }
        return Step.of(actions);
    }

    Step post() {
        if (this.lagNewBlockPending) {
            this.lagNewBlockPending = false;
            this.lagNewFlushPending = true;
            return Step.of(Action.BLOCK);
        }
        if (this.lagNewFlushPending) {
            this.lagNewFlushPending = false;
            this.bufferedTicks = 0;
            return Step.of(Action.FLUSH_BUFFER, Action.START_BUFFER);
        }
        if (this.lagFlushPending) {
            this.lagFlushPending = false;
            this.bufferedTicks = 0;
            return Step.of(Action.FLUSH_BUFFER, Action.START_BUFFER);
        }
        if (this.activeMode == LAG_LEGIT && this.bufferedTicks >= MAX_LAG_LEGIT_BUFFER_TICKS) {
            this.bufferedTicks = 0;
            return Step.of(Action.FLUSH_BUFFER, Action.START_BUFFER);
        }
        return Step.EMPTY;
    }

    void reset() {
        this.activeMode = -1;
        this.attackProgress = 0;
        this.bufferedTicks = 0;
        this.firstAttempt = true;
        this.lagNewBlockPending = false;
        this.lagNewFlushPending = false;
        this.lagFlushPending = false;
    }
}

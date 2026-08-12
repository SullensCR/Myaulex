package myau.module.modules;

final class StasisActionState {
    static final int CLASSIC = 0;
    static final int EXPERIMENTAL_1 = 1;
    static final int EXPERIMENTAL_2 = 2;
    static final int EXPERIMENTAL_3 = 3;

    private int mode = CLASSIC;
    private int strictUpdatesRemaining;
    private boolean fastUpdateActive;
    private boolean pendingUse;
    private boolean rawUseArmed;
    private boolean replayingUse;

    void reset(int mode) {
        this.mode = mode;
        this.strictUpdatesRemaining = 0;
        this.fastUpdateActive = false;
        this.pendingUse = false;
        this.rawUseArmed = false;
        this.replayingUse = false;
    }

    int getMode() {
        return this.mode;
    }

    boolean isExperimental() {
        return this.mode != CLASSIC;
    }

    boolean isFreezing() {
        switch (this.mode) {
            case EXPERIMENTAL_1:
                return this.strictUpdatesRemaining <= 0;
            case EXPERIMENTAL_2:
                return !this.fastUpdateActive;
            case EXPERIMENTAL_3:
            case CLASSIC:
            default:
                return true;
        }
    }

    void armRawUse() {
        if (this.isExperimental()) {
            this.rawUseArmed = true;
        }
    }

    void clearRawUseArm() {
        this.rawUseArmed = false;
    }

    boolean captureRawUse() {
        if (!this.isExperimental() || !this.rawUseArmed || this.pendingUse || this.replayingUse) {
            return false;
        }
        this.rawUseArmed = false;
        this.pendingUse = true;
        this.startWakeWindow();
        return true;
    }

    boolean requestRotationWake() {
        if (!this.isExperimental()) {
            return false;
        }
        this.startWakeWindow();
        return this.mode == EXPERIMENTAL_1 || this.mode == EXPERIMENTAL_2;
    }

    private void startWakeWindow() {
        if (this.mode == EXPERIMENTAL_1) {
            this.strictUpdatesRemaining = 2;
        } else if (this.mode == EXPERIMENTAL_2) {
            this.fastUpdateActive = true;
        }
    }

    boolean pollStrictReplayAtTickStart() {
        if (this.mode == EXPERIMENTAL_1 && this.pendingUse && this.strictUpdatesRemaining == 1) {
            this.pendingUse = false;
            return true;
        }
        return false;
    }

    boolean pollLookReplayAtTickEnd() {
        if (this.mode == EXPERIMENTAL_3 && this.pendingUse) {
            this.pendingUse = false;
            return true;
        }
        return false;
    }

    boolean onPlayerUpdateCompleted() {
        if (this.mode == EXPERIMENTAL_1) {
            if (this.strictUpdatesRemaining > 0) {
                this.strictUpdatesRemaining--;
            }
            return false;
        }
        if (this.mode == EXPERIMENTAL_2 && this.fastUpdateActive) {
            boolean replay = this.pendingUse;
            this.pendingUse = false;
            this.fastUpdateActive = false;
            return replay;
        }
        return false;
    }

    void beginReplay() {
        this.replayingUse = true;
    }

    void endReplay() {
        this.replayingUse = false;
    }

    boolean isReplayingUse() {
        return this.replayingUse;
    }

    boolean hasPendingUse() {
        return this.pendingUse;
    }

    boolean hasActiveCycle() {
        return this.strictUpdatesRemaining > 0
                || this.fastUpdateActive
                || this.pendingUse
                || this.replayingUse;
    }
}

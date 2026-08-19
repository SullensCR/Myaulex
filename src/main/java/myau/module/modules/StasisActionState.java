package myau.module.modules;

final class StasisActionState {
    static final int MAX_BUFFERED_ROTATIONS = 3;
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
    private final float[] bufferedYaws = new float[MAX_BUFFERED_ROTATIONS];
    private final float[] bufferedPitches = new float[MAX_BUFFERED_ROTATIONS];
    private int bufferedRotationCount;

    void reset(int mode) {
        this.mode = mode;
        this.strictUpdatesRemaining = 0;
        this.fastUpdateActive = false;
        this.pendingUse = false;
        this.rawUseArmed = false;
        this.replayingUse = false;
        this.bufferedRotationCount = 0;
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

    void recordRotation(float yaw, float pitch) {
        if (!this.isExperimental()) {
            return;
        }
        if (this.bufferedRotationCount > 0) {
            int last = this.bufferedRotationCount - 1;
            if (Math.abs(this.bufferedYaws[last] - yaw) <= 1.0E-4F
                    && Math.abs(this.bufferedPitches[last] - pitch) <= 1.0E-4F) {
                return;
            }
        }
        if (this.bufferedRotationCount == MAX_BUFFERED_ROTATIONS) {
            System.arraycopy(this.bufferedYaws, 1, this.bufferedYaws, 0, MAX_BUFFERED_ROTATIONS - 1);
            System.arraycopy(this.bufferedPitches, 1, this.bufferedPitches, 0, MAX_BUFFERED_ROTATIONS - 1);
            this.bufferedRotationCount--;
        }
        this.bufferedYaws[this.bufferedRotationCount] = yaw;
        this.bufferedPitches[this.bufferedRotationCount] = pitch;
        this.bufferedRotationCount++;
    }

    int getBufferedRotationCount() {
        return this.bufferedRotationCount;
    }

    RotationSample consumeLatestRotation() {
        if (this.bufferedRotationCount == 0) {
            return null;
        }
        int last = this.bufferedRotationCount - 1;
        RotationSample sample = new RotationSample(this.bufferedYaws[last], this.bufferedPitches[last]);
        this.bufferedRotationCount = 0;
        return sample;
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

    static final class RotationSample {
        final float yaw;
        final float pitch;

        RotationSample(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}

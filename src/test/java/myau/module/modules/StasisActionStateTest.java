package myau.module.modules;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StasisActionStateTest {
    @Test
    public void classicAlwaysFreezesAndDoesNotCaptureUse() {
        StasisActionState state = state(StasisActionState.CLASSIC);

        state.armRawUse();

        assertTrue(state.isFreezing());
        assertFalse(state.captureRawUse());
        assertFalse(state.hasActiveCycle());
    }

    @Test
    public void syntheticUseWithoutRawHardwareArmIsIgnored() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_1);

        assertFalse(state.captureRawUse());
        assertTrue(state.isFreezing());
        assertFalse(state.hasPendingUse());
    }

    @Test
    public void strictModeReplaysAtTheNextTickStartAfterOneCompletedUpdate() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_1);

        state.armRawUse();
        assertTrue(state.captureRawUse());
        assertFalse(state.isFreezing());
        assertFalse(state.pollStrictReplayAtTickStart());

        assertFalse(state.onPlayerUpdateCompleted());
        assertTrue(state.pollStrictReplayAtTickStart());
        assertFalse(state.hasPendingUse());
        assertFalse(state.isFreezing());

        assertFalse(state.onPlayerUpdateCompleted());
        assertTrue(state.isFreezing());
        assertFalse(state.hasActiveCycle());
    }

    @Test
    public void strictModeKeepsOnlyOnePendingUse() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_1);

        state.armRawUse();
        assertTrue(state.captureRawUse());
        state.armRawUse();

        assertFalse(state.captureRawUse());
        assertTrue(state.hasPendingUse());
    }

    @Test
    public void cameraRotationOnlyBuffersAndDoesNotWakeStrictMode() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_1);

        state.recordRotation(10.0F, 20.0F);
        state.recordRotation(20.0F, 20.0F);

        assertTrue(state.isFreezing());
        assertFalse(state.hasPendingUse());
        assertTrue(state.getBufferedRotationCount() == 2);
    }

    @Test
    public void fastModeRequestsReplayImmediatelyAfterItsSingleUpdate() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_2);

        state.armRawUse();
        assertTrue(state.captureRawUse());
        assertFalse(state.isFreezing());

        assertTrue(state.onPlayerUpdateCompleted());
        assertTrue(state.isFreezing());
        assertFalse(state.hasPendingUse());
    }

    @Test
    public void fastRotationOnlyWindowCompletesWithoutUseReplay() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_2);

        state.recordRotation(10.0F, 20.0F);
        assertTrue(state.isFreezing());
        assertFalse(state.hasPendingUse());

        assertTrue(state.getBufferedRotationCount() == 1);
    }

    @Test
    public void lookModeNeverUnfreezesAndReplaysAtTickEnd() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_3);

        assertTrue(state.isFreezing());
        state.armRawUse();
        assertTrue(state.captureRawUse());
        assertTrue(state.isFreezing());
        assertTrue(state.getBufferedRotationCount() == 0);

        assertTrue(state.pollLookReplayAtTickEnd());
        assertFalse(state.pollLookReplayAtTickEnd());
        assertTrue(state.isFreezing());
    }

    @Test
    public void clearingRawArmPreventsHeldOrDelayedSyntheticRepeat() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_1);

        state.armRawUse();
        state.clearRawUseArm();

        assertFalse(state.captureRawUse());
        assertFalse(state.hasPendingUse());
    }

    @Test
    public void rotationBufferKeepsOnlyTheLatestFewSamples() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_3);

        state.recordRotation(1.0F, 1.0F);
        state.recordRotation(2.0F, 2.0F);
        state.recordRotation(3.0F, 3.0F);
        state.recordRotation(4.0F, 4.0F);

        assertTrue(state.getBufferedRotationCount() == StasisActionState.MAX_BUFFERED_ROTATIONS);
        StasisActionState.RotationSample latest = state.consumeLatestRotation();
        assertTrue(latest != null);
        assertTrue(latest.yaw == 4.0F);
        assertTrue(latest.pitch == 4.0F);
        assertTrue(state.getBufferedRotationCount() == 0);
    }

    @Test
    public void resetCancelsPendingWorkAndAppliesTheNewMode() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_1);
        state.armRawUse();
        assertTrue(state.captureRawUse());

        state.reset(StasisActionState.EXPERIMENTAL_3);

        assertTrue(state.isFreezing());
        assertFalse(state.hasPendingUse());
        assertFalse(state.hasActiveCycle());
        assertFalse(state.pollStrictReplayAtTickStart());
    }

    @Test
    public void replayGuardOwnsTheCycleUntilVanillaUseReturns() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_3);

        state.beginReplay();
        assertTrue(state.isReplayingUse());
        assertTrue(state.hasActiveCycle());

        state.endReplay();
        assertFalse(state.isReplayingUse());
        assertFalse(state.hasActiveCycle());
    }

    private static StasisActionState state(int mode) {
        StasisActionState state = new StasisActionState();
        state.reset(mode);
        return state;
    }
}

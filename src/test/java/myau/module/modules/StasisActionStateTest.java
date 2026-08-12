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
    public void newRotationRestartsStrictPreparationBeforeUseReplay() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_1);
        state.armRawUse();
        assertTrue(state.captureRawUse());
        state.onPlayerUpdateCompleted();

        assertTrue(state.requestRotationWake());
        assertFalse(state.pollStrictReplayAtTickStart());
        state.onPlayerUpdateCompleted();

        assertTrue(state.pollStrictReplayAtTickStart());
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

        assertTrue(state.requestRotationWake());
        assertFalse(state.isFreezing());

        assertFalse(state.onPlayerUpdateCompleted());
        assertTrue(state.isFreezing());
    }

    @Test
    public void lookModeNeverUnfreezesAndReplaysAtTickEnd() {
        StasisActionState state = state(StasisActionState.EXPERIMENTAL_3);

        assertFalse(state.requestRotationWake());
        assertTrue(state.isFreezing());
        state.armRawUse();
        assertTrue(state.captureRawUse());
        assertTrue(state.isFreezing());

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

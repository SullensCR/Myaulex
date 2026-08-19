package myau.module.modules;

import org.junit.Test;

import static org.junit.Assert.*;

public class HitSelectStateTest {
    @Test
    public void ownAttackUsesVanillaHalfSecondDamageWindow() {
        HitSelectState state = new HitSelectState();
        long sent = 1_000_000_000L;
        state.recordAttack(sent);

        assertFalse(state.isReady(sent + 499_999_999L));
        assertTrue(state.isReady(sent + 500_000_000L));
    }

    @Test
    public void serverStatusUsesRollingMinimumPingCorrection() {
        HitSelectState state = new HitSelectState();
        state.samplePing(100);
        state.samplePing(80);
        state.recordDamageStatus(1_000_000_000L);

        assertEquals(55_000_000L, state.correctionNanos());
        assertEquals(1_445_000_000L, state.getSafeAttackNanos());
    }

    @Test
    public void confirmationCountsOnlyOnceInsideLatencyWindow() {
        HitSelectState state = new HitSelectState();
        state.samplePing(100);
        state.recordAttack(1_000_000_000L);

        assertTrue(state.recordDamageStatus(1_200_000_000L));
        assertFalse(state.recordDamageStatus(1_220_000_000L));
        assertEquals(1, state.getConfirmedHits());
        state.clearCombo();
        assertEquals(0, state.getConfirmedHits());
    }

    @Test
    public void heldSprintStaysSuppressedUntilDeadline() {
        HitSelectState.SprintWindow window = new HitSelectState.SprintWindow();
        window.begin(1_000_000_000L, 100);

        assertTrue(window.isActive(1_099_999_999L));
        assertFalse(window.shouldRestore(1_099_999_999L));
        assertTrue(window.shouldRestore(1_100_000_000L));
    }

    @Test
    public void movementPredictionWeightsRecentVelocityAndRejectsTeleports() {
        HitSelectState.MotionHistory history = new HitSelectState.MotionHistory();
        history.add(0.0, 0.0);
        history.add(0.1, 0.0);
        history.add(0.3, 0.0);
        history.add(0.6, 0.0);
        assertEquals((0.1 + 0.4 + 0.9) / 6.0, history.velocityX(), 0.00001);

        history.add(10.0, 0.0);
        assertEquals(0.0, history.velocityX(), 0.0);
    }
}

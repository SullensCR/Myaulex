package myau.module.modules;

import org.junit.Test;

import java.util.Arrays;

import static myau.module.modules.ExpoAutoBlockState.Action.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExpoAutoBlockStateTest {
    @Test
    public void exposesExpoApsValues() {
        assertEquals(3, ExpoAutoBlockState.apsForIndex(0));
        assertEquals(5, ExpoAutoBlockState.apsForIndex(1));
        assertEquals(7, ExpoAutoBlockState.apsForIndex(2));
        assertEquals(10, ExpoAutoBlockState.apsForIndex(3));
        assertEquals(14, ExpoAutoBlockState.apsForIndex(4));
    }

    @Test
    public void legitReleasesAttacksAndReblocksWithoutBuffering() {
        ExpoAutoBlockState state = new ExpoAutoBlockState();

        assertActions(state.pre(ExpoAutoBlockState.LEGIT, 3, true, false), RELEASE, ATTACK, BLOCK);
        assertTrue(state.post().isEmpty());
    }

    @Test
    public void lagLegitUsesNoSlowSlotSpoofAndKeepsBufferUntilSafetyFlush() {
        ExpoAutoBlockState state = new ExpoAutoBlockState();

        assertActions(
                state.pre(ExpoAutoBlockState.LAG_LEGIT, 3, true, true),
                RELEASE, SPOOF_AWAY, SPOOF_BACK, ATTACK, BLOCK
        );
        assertTrue(state.post().isEmpty());
        for (int tick = 1; tick < 20; tick++) {
            state.pre(ExpoAutoBlockState.LAG_LEGIT, 3, true, true);
            if (tick < 19) {
                assertTrue(state.post().isEmpty());
            }
        }
        assertActions(state.post(), FLUSH_BUFFER, START_BUFFER);
    }

    @Test
    public void lagFlushesAndRestartsInPost() {
        ExpoAutoBlockState state = new ExpoAutoBlockState();

        assertActions(state.pre(ExpoAutoBlockState.LAG, 5, true, false), RELEASE, ATTACK, BLOCK);
        assertActions(state.post(), FLUSH_BUFFER, START_BUFFER);
    }

    @Test
    public void lagNewBlocksThenFlushesAcrossTwoPostStages() {
        ExpoAutoBlockState state = new ExpoAutoBlockState();

        assertActions(state.pre(ExpoAutoBlockState.LAG_NEW, 14, true, true), RELEASE, SPOOF_AWAY, SPOOF_BACK, ATTACK);
        assertActions(state.post(), BLOCK);
        assertActions(state.post(), FLUSH_BUFFER, START_BUFFER);
    }

    @Test
    public void eachApsProfileHasDeterministicTwentyTickLegitCadence() {
        int[] expected = {3, 5, 7, 10, 14};
        for (int aps : expected) {
            ExpoAutoBlockState state = new ExpoAutoBlockState();
            int attacks = 0;
            for (int tick = 0; tick < 20; tick++) {
                if (state.pre(ExpoAutoBlockState.LEGIT, aps, true, false).getActions().contains(ATTACK)) {
                    attacks++;
                }
                state.post();
            }
            assertEquals(aps, attacks);
        }
    }

    private static void assertActions(ExpoAutoBlockState.Step step, ExpoAutoBlockState.Action... actions) {
        assertEquals(Arrays.asList(actions), step.getActions());
    }
}

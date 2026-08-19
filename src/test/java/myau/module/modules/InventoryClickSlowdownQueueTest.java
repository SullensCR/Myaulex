package myau.module.modules;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InventoryClickSlowdownQueueTest {
    @Test
    public void defaultCycleReleasesBatchAfterTwoTicksAndRestoresAfterFiveMore() {
        InventoryClickSlowdownQueue<String> queue = new InventoryClickSlowdownQueue<>();

        assertReleased(queue.enqueue("first", 2, 5));
        assertReleased(queue.enqueue("second", 2, 5));
        assertTrue(queue.blocksSprint());
        assertEquals(2, queue.pendingClicks());

        assertReleased(queue.tick(5));
        assertReleased(queue.tick(5), "first", "second");
        assertTrue(queue.blocksSprint());

        for (int tick = 0; tick < 4; tick++) {
            InventoryClickSlowdownQueue.Advance<String> advance = queue.tick(5);
            assertReleased(advance);
            assertFalse(advance.shouldRestoreSprint());
            assertTrue(queue.blocksSprint());
        }

        InventoryClickSlowdownQueue.Advance<String> restore = queue.tick(5);
        assertReleased(restore);
        assertTrue(restore.shouldRestoreSprint());
        assertFalse(queue.blocksSprint());
    }

    @Test
    public void clickDuringRestoreStartsANewBatchAndRestartsRestoreDelay() {
        InventoryClickSlowdownQueue<String> queue = new InventoryClickSlowdownQueue<>();

        queue.enqueue("first", 1, 5);
        assertReleased(queue.tick(5), "first");
        queue.tick(5);
        queue.tick(5);

        assertReleased(queue.enqueue("second", 2, 5));
        assertReleased(queue.tick(5));
        assertReleased(queue.tick(5), "second");

        for (int tick = 0; tick < 4; tick++) {
            assertFalse(queue.tick(5).shouldRestoreSprint());
        }
        assertTrue(queue.tick(5).shouldRestoreSprint());
        assertFalse(queue.blocksSprint());
    }

    @Test
    public void zeroTicksReleaseAndRestoreImmediately() {
        InventoryClickSlowdownQueue<String> queue = new InventoryClickSlowdownQueue<>();

        InventoryClickSlowdownQueue.Advance<String> advance = queue.enqueue("click", 0, 0);

        assertReleased(advance, "click");
        assertTrue(advance.shouldRestoreSprint());
        assertFalse(queue.blocksSprint());
    }

    @Test
    public void zeroSlowdownReleasesImmediatelyButStillWaitsToRestore() {
        InventoryClickSlowdownQueue<String> queue = new InventoryClickSlowdownQueue<>();

        InventoryClickSlowdownQueue.Advance<String> release = queue.enqueue("click", 0, 5);

        assertReleased(release, "click");
        assertFalse(release.shouldRestoreSprint());
        assertTrue(queue.blocksSprint());
        for (int tick = 0; tick < 4; tick++) {
            assertFalse(queue.tick(5).shouldRestoreSprint());
        }
        assertTrue(queue.tick(5).shouldRestoreSprint());
    }

    @Test
    public void fiveTickBoundariesAreExact() {
        InventoryClickSlowdownQueue<String> queue = new InventoryClickSlowdownQueue<>();
        queue.enqueue("click", 5, 5);

        for (int tick = 0; tick < 4; tick++) {
            assertReleased(queue.tick(5));
        }
        assertReleased(queue.tick(5), "click");

        for (int tick = 0; tick < 4; tick++) {
            assertFalse(queue.tick(5).shouldRestoreSprint());
        }
        assertTrue(queue.tick(5).shouldRestoreSprint());
    }

    @Test
    public void drainFlushesInOrderWhileDiscardDropsTheBatch() {
        InventoryClickSlowdownQueue<String> queue = new InventoryClickSlowdownQueue<>();
        queue.enqueue("first", 5, 5);
        queue.enqueue("second", 5, 5);

        assertEquals(Arrays.asList("first", "second"), queue.drainAndReset());
        assertFalse(queue.blocksSprint());
        assertEquals(0, queue.pendingClicks());

        queue.enqueue("discarded", 5, 5);
        queue.discardAndReset();
        assertEquals(Collections.emptyList(), queue.drainAndReset());
        assertFalse(queue.blocksSprint());
    }

    private static void assertReleased(
            InventoryClickSlowdownQueue.Advance<String> advance, String... expected
    ) {
        assertEquals(Arrays.asList(expected), advance.getReleasedClicks());
    }
}

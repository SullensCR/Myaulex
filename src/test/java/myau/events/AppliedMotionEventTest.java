package myau.events;

import org.junit.Test;

import static org.junit.Assert.*;

public class AppliedMotionEventTest {
    @Test
    public void retainsResultingMotionAndSource() {
        AppliedMotionEvent event = new AppliedMotionEvent(
                null,
                0.25D,
                0.42D,
                -0.1D,
                AppliedMotionEvent.Source.EXPLOSION
        );

        assertNull(event.getPlayer());
        assertEquals(0.25D, event.getMotionX(), 0.0D);
        assertEquals(0.42D, event.getMotionY(), 0.0D);
        assertEquals(-0.1D, event.getMotionZ(), 0.0D);
        assertEquals(AppliedMotionEvent.Source.EXPLOSION, event.getSource());
    }
}

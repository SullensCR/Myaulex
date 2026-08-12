package myau.render;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RenderFrameTest {
    @Before
    public void resetBefore() {
        RenderFrame.reset();
    }

    @After
    public void resetAfter() {
        RenderFrame.reset();
    }

    @Test
    public void incrementsOnceForEachOuterRenderFrame() {
        assertEquals(0L, RenderFrame.current());
        assertEquals(1L, RenderFrame.begin());
        assertEquals(1L, RenderFrame.current());
        assertEquals(2L, RenderFrame.begin());
    }

    @Test
    public void resetInvalidatesTheCurrentFrameId() {
        RenderFrame.begin();
        RenderFrame.reset();
        assertEquals(0L, RenderFrame.current());
    }
}

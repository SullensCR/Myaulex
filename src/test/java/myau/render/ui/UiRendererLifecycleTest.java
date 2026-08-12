package myau.render.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UiRendererLifecycleTest {
    @Test
    public void startupInitializationOccursOnlyOnce() {
        UiRendererLifecycle lifecycle = new UiRendererLifecycle();

        assertTrue(lifecycle.beginInitialization());
        assertFalse(lifecycle.beginInitialization());
        assertTrue(lifecycle.isInitialized());
        assertEquals(1, lifecycle.initializationCount());
    }

    @Test
    public void resizeOnlyAdvancesFramebufferGeneration() {
        UiRendererLifecycle lifecycle = new UiRendererLifecycle();
        lifecycle.beginInitialization();

        assertEquals(1L, lifecycle.invalidateFramebuffers());
        assertEquals(2L, lifecycle.invalidateFramebuffers());
        assertEquals(2L, lifecycle.framebufferGeneration());
        assertEquals(1, lifecycle.initializationCount());
    }

    @Test
    public void nestedFramesAreRejectedAndTheNextFrameCanRecover() {
        UiRendererLifecycle lifecycle = new UiRendererLifecycle();
        lifecycle.beginFrame();
        try {
            lifecycle.beginFrame();
            fail("Expected a nested-frame rejection");
        } catch (IllegalStateException expected) {
            assertTrue(lifecycle.isFrameActive());
        }

        lifecycle.endFrame();
        assertFalse(lifecycle.isFrameActive());
        lifecycle.beginFrame();
        assertTrue(lifecycle.isFrameActive());
    }
}

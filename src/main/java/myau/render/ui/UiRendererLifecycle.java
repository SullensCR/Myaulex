package myau.render.ui;

/** Pure lifecycle state kept separate so it can be verified without an OpenGL context. */
final class UiRendererLifecycle {
    private boolean initialized;
    private int initializationCount;
    private long framebufferGeneration;
    private boolean frameActive;

    boolean beginInitialization() {
        if (initialized) return false;
        initialized = true;
        initializationCount++;
        return true;
    }

    boolean isInitialized() {
        return initialized;
    }

    int initializationCount() {
        return initializationCount;
    }

    long invalidateFramebuffers() {
        return ++framebufferGeneration;
    }

    long framebufferGeneration() {
        return framebufferGeneration;
    }

    void beginFrame() {
        if (frameActive) throw new IllegalStateException("UI frame already active");
        frameActive = true;
    }

    void endFrame() {
        frameActive = false;
    }

    boolean isFrameActive() {
        return frameActive;
    }
}

package myau.render.ui;

public final class UiClock {
    private long lastNanos = System.nanoTime();
    private float deltaSeconds;

    public void tick() {
        long now = System.nanoTime();
        deltaSeconds = Math.min(0.1F, Math.max(0.0F, (now - lastNanos) / 1_000_000_000.0F));
        lastNanos = now;
    }

    public float deltaSeconds() {
        return deltaSeconds;
    }
}

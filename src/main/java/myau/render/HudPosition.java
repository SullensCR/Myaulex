package myau.render;

/** Pure anchor and offset math shared by HUD renderers and the chat editor. */
public final class HudPosition {
    private HudPosition() {
    }

    public static float edgeX(int anchor, float canvasWidth, float componentWidth, float offset) {
        return anchor == 0 ? offset : canvasWidth - componentWidth - offset;
    }

    public static float edgeY(int anchor, float canvasHeight, float componentHeight, float offset) {
        return anchor == 0 ? offset : canvasHeight - componentHeight - offset;
    }

    public static float anchoredX(int anchor, float canvasWidth, float componentWidth, float offset) {
        if (anchor == 0) return offset;
        if (anchor == 2) return canvasWidth - componentWidth - offset;
        return (canvasWidth - componentWidth) * 0.5F + offset;
    }

    public static float anchoredY(int anchor, float canvasHeight, float componentHeight, float offset) {
        if (anchor == 0) return offset;
        if (anchor == 2) return canvasHeight - componentHeight - offset;
        return (canvasHeight - componentHeight) * 0.5F + offset;
    }

    public static int nearestEdgeX(float x, float componentWidth, float canvasWidth) {
        return x + componentWidth * 0.5F < canvasWidth * 0.5F ? 0 : 1;
    }

    public static int nearestEdgeY(float y, float componentHeight, float canvasHeight) {
        return y + componentHeight * 0.5F < canvasHeight * 0.5F ? 0 : 1;
    }

    public static int nearestAnchoredX(float x, float componentWidth, float canvasWidth) {
        float center = x + componentWidth * 0.5F;
        float left = componentWidth * 0.5F;
        float middle = canvasWidth * 0.5F;
        float right = canvasWidth - componentWidth * 0.5F;
        if (Math.abs(center - left) <= Math.abs(center - middle)
                && Math.abs(center - left) <= Math.abs(center - right)) return 0;
        if (Math.abs(center - right) <= Math.abs(center - middle)) return 2;
        return 1;
    }

    public static int nearestAnchoredY(float y, float componentHeight, float canvasHeight) {
        float center = y + componentHeight * 0.5F;
        float top = componentHeight * 0.5F;
        float middle = canvasHeight * 0.5F;
        float bottom = canvasHeight - componentHeight * 0.5F;
        if (Math.abs(center - top) <= Math.abs(center - middle)
                && Math.abs(center - top) <= Math.abs(center - bottom)) return 0;
        if (Math.abs(center - bottom) <= Math.abs(center - middle)) return 2;
        return 1;
    }

    public static float offsetForEdgeX(int anchor, float x, float canvasWidth, float componentWidth) {
        return anchor == 0 ? x : canvasWidth - componentWidth - x;
    }

    public static float offsetForEdgeY(int anchor, float y, float canvasHeight, float componentHeight) {
        return anchor == 0 ? y : canvasHeight - componentHeight - y;
    }

    public static float offsetForAnchoredX(int anchor, float x, float canvasWidth, float componentWidth) {
        if (anchor == 0) return x;
        if (anchor == 2) return canvasWidth - componentWidth - x;
        return x - (canvasWidth - componentWidth) * 0.5F;
    }

    public static float offsetForAnchoredY(int anchor, float y, float canvasHeight, float componentHeight) {
        if (anchor == 0) return y;
        if (anchor == 2) return canvasHeight - componentHeight - y;
        return y - (canvasHeight - componentHeight) * 0.5F;
    }

    public static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

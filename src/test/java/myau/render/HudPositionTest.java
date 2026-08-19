package myau.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HudPositionTest {
    @Test
    public void edgeAnchorsRoundTrip() {
        assertEquals(24.0F, HudPosition.edgeX(0, 1920.0F, 300.0F, 24.0F), 0.001F);
        assertEquals(1596.0F, HudPosition.edgeX(1, 1920.0F, 300.0F, 24.0F), 0.001F);
        assertEquals(24.0F, HudPosition.offsetForEdgeX(0, 24.0F, 1920.0F, 300.0F), 0.001F);
        assertEquals(24.0F, HudPosition.offsetForEdgeX(1, 1596.0F, 1920.0F, 300.0F), 0.001F);
        assertEquals(998.0F, HudPosition.edgeY(1, 1080.0F, 42.0F, 40.0F), 0.001F);
    }

    @Test
    public void nearestEdgeSwitchesAtCanvasMidpoint() {
        assertEquals(0, HudPosition.nearestEdgeX(100.0F, 200.0F, 1920.0F));
        assertEquals(1, HudPosition.nearestEdgeX(900.0F, 200.0F, 1920.0F));
        assertEquals(0, HudPosition.nearestEdgeY(100.0F, 100.0F, 1080.0F));
        assertEquals(1, HudPosition.nearestEdgeY(800.0F, 100.0F, 1080.0F));
    }

    @Test
    public void centeredAnchorsSupportSignedOffsets() {
        float x = HudPosition.anchoredX(1, 1920.0F, 250.0F, 40.0F);
        float y = HudPosition.anchoredY(1, 1080.0F, 72.0F, -20.0F);
        assertEquals(875.0F, x, 0.001F);
        assertEquals(484.0F, y, 0.001F);
        assertEquals(40.0F, HudPosition.offsetForAnchoredX(1, x, 1920.0F, 250.0F), 0.001F);
        assertEquals(-20.0F, HudPosition.offsetForAnchoredY(1, y, 1080.0F, 72.0F), 0.001F);
    }

    @Test
    public void nearestThreeWayAnchorChoosesClosestAnchor() {
        assertEquals(0, HudPosition.nearestAnchoredX(0.0F, 200.0F, 1920.0F));
        assertEquals(1, HudPosition.nearestAnchoredX(860.0F, 200.0F, 1920.0F));
        assertEquals(2, HudPosition.nearestAnchoredX(1720.0F, 200.0F, 1920.0F));
    }
}

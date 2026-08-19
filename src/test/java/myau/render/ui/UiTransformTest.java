package myau.render.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UiTransformTest {
    @Test
    public void scalesAgainstTheLogicalOverlayViewport() {
        UiTransform.Metrics small = UiTransform.calculateMetrics(
                960.0F, 540.0F, 1920, 1080,
                1920.0F, 1080.0F, 1.0F, 0.0F
        );
        UiTransform.Metrics large = UiTransform.calculateMetrics(
                1280.0F, 720.0F, 2560, 1440,
                1920.0F, 1080.0F, 1.0F, 0.0F
        );

        assertEquals(0.5F, small.logicalScale, 0.0001F);
        assertEquals(2.0F / 3.0F, large.logicalScale, 0.0001F);
        assertEquals(960.0F, 1920.0F * small.logicalScale, 0.0001F);
        assertEquals(1280.0F, 1920.0F * large.logicalScale, 0.0001F);
        assertEquals(0.0F, small.logicalX, 0.0001F);
        assertEquals(0.0F, large.logicalY, 0.0001F);
    }

    @Test
    public void centersLetterboxedDesignsInLogicalSpace() {
        UiTransform.Metrics metrics = UiTransform.calculateMetrics(
                1200.0F, 800.0F, 2400, 1600,
                1920.0F, 1080.0F, 1.0F, 0.0F
        );

        assertEquals(0.625F, metrics.logicalScale, 0.0001F);
        assertEquals(62.5F, metrics.logicalY, 0.0001F);
        assertEquals(125.0F, metrics.physicalY, 0.0001F);
    }

    @Test
    public void userScaleCannotPushTheDesignPastTheRequestedMargin() {
        UiTransform.Metrics metrics = UiTransform.calculateMetrics(
                960.0F, 540.0F, 1920, 1080,
                1920.0F, 1080.0F, 2.0F, 10.0F
        );

        assertEquals((540.0F - 20.0F) / 1080.0F, metrics.logicalScale, 0.0001F);
        assertEquals((960.0F - 1920.0F * metrics.logicalScale) * 0.5F,
                metrics.logicalX, 0.0001F);
        assertEquals(10.0F, metrics.logicalY, 0.0001F);
    }
}

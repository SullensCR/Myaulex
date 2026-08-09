package myau.ui;

import myau.ui.dataset.SliderStops;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SliderStopsTest {
    @Test
    public void smallRangesExposeEveryIncrementAndBothEndpoints() {
        assertEquals(5, SliderStops.count(0.0D, 4.0D, 1.0D));
        assertEquals(0.0D, SliderStops.valueAt(0.0D, 4.0D, 1.0D, 0, 5), 0.0D);
        assertEquals(2.0D, SliderStops.valueAt(0.0D, 4.0D, 1.0D, 2, 5), 0.0D);
        assertEquals(4.0D, SliderStops.valueAt(0.0D, 4.0D, 1.0D, 4, 5), 0.0D);
    }

    @Test
    public void denseRangesUseReadableValidMajorStops() {
        assertEquals(11, SliderStops.count(0.0D, 100.0D, 1.0D));
        assertEquals(50.0D, SliderStops.valueAt(0.0D, 100.0D, 1.0D, 5, 11), 0.0D);
        assertEquals(100.0D, SliderStops.valueAt(0.0D, 100.0D, 1.0D, 10, 11), 0.0D);
    }

    @Test
    public void snappingClampsAndUsesTheSliderIncrement() {
        assertEquals(0.0D, SliderStops.snap(-1.0D, 0.0D, 10.0D, 0.5D), 0.0D);
        assertEquals(3.5D, SliderStops.snap(3.4D, 0.0D, 10.0D, 0.5D), 0.0D);
        assertEquals(10.0D, SliderStops.snap(12.0D, 0.0D, 10.0D, 0.5D), 0.0D);
    }
}

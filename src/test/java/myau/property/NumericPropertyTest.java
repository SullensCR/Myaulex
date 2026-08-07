package myau.property;

import myau.property.properties.IntProperty;
import org.junit.Test;

import static org.junit.Assert.*;

public class NumericPropertyTest {
    @Test
    public void recommendationsDoNotActAsHardLimits() {
        IntProperty property = new IntProperty("delay", 200, 50, 1000);
        assertTrue(property.parseString("1250"));
        assertEquals(Integer.valueOf(1250), property.getValue());
        assertFalse(((RecommendedRange) property).isRecommended(property.getValue()));
    }

    @Test
    public void hardLimitsRejectUnsafeValuesAndKeepPreviousValue() {
        IntProperty property = new IntProperty("delay", 200, 50, 1000);
        assertFalse(property.setValue(1_000_000_001));
        assertEquals(Integer.valueOf(200), property.getValue());
    }

    @Test
    public void childMetadataIsExplicit() {
        IntProperty parent = new IntProperty("parent", 1, 0, 2);
        IntProperty child = new IntProperty("child", 1, 0, 2).childOf(parent);
        assertSame(parent, child.getParent());
    }
}

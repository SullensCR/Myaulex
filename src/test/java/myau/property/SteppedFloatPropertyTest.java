package myau.property;

import myau.property.properties.BooleanProperty;
import myau.property.properties.SteppedFloatProperty;
import org.junit.Test;

import static org.junit.Assert.*;

public class SteppedFloatPropertyTest {
    @Test
    public void acceptsOnlyHalfStepValuesInsideBounds() {
        SteppedFloatProperty property = new SteppedFloatProperty(
                "target-cps", 10.0F, 1.0F, 20.0F, 0.5F, null
        );

        assertTrue(property.parseString("1"));
        assertEquals(Float.valueOf(1.0F), property.getValue());
        assertTrue(property.parseString("1.5"));
        assertEquals(Float.valueOf(1.5F), property.getValue());
        assertTrue(property.parseString("20"));
        assertEquals(Float.valueOf(20.0F), property.getValue());
        assertFalse(property.parseString("1.25"));
        assertFalse(property.parseString("20.5"));
        assertFalse(property.parseString("0.5"));
        assertEquals(Float.valueOf(20.0F), property.getValue());
    }

    @Test
    public void visibilityTracksTheOwningClickToggle() {
        BooleanProperty clicks = new BooleanProperty("clicks", true);
        SteppedFloatProperty property = new SteppedFloatProperty(
                "target-cps", 10.0F, 1.0F, 20.0F, 0.5F, clicks::getValue
        );

        assertTrue(property.isVisible());
        clicks.setValue(false);
        assertFalse(property.isVisible());
    }
}

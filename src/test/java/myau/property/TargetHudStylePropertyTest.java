package myau.property;

import com.google.gson.JsonObject;
import myau.property.properties.TargetHudStyleProperty;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TargetHudStylePropertyTest {
    @Test
    public void migratesTheTwoRetainedRavenStyles() {
        TargetHudStyleProperty property = new TargetHudStyleProperty();
        JsonObject json = new JsonObject();
        json.addProperty("style", "RAVENBS-MODERN");
        assertTrue(property.read(json));
        assertEquals("Classic Blur", property.getModeString());

        json.addProperty("style", "RAVENBS-LEGACY");
        assertTrue(property.read(json));
        assertEquals("Classic", property.getModeString());
    }

    @Test
    public void removedStylesFallBackToTheNewMyaulexDesign() {
        TargetHudStyleProperty property = new TargetHudStyleProperty();
        JsonObject json = new JsonObject();
        json.addProperty("style", "ASTOLFO");
        assertTrue(property.read(json));
        assertEquals("MYAULEX", property.getModeString());
    }
}

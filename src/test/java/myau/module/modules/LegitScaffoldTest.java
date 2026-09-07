package myau.module.modules;

import com.google.gson.JsonObject;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.UpdateEvent;
import myau.property.properties.TickDurationProperty;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class LegitScaffoldTest {
    @Test public void startupSettingIsBoundedAndPersistsTicksWithReadableMilliseconds() {
        TickDurationProperty duration = new LegitScaffold().initialSneak;
        assertEquals(Integer.valueOf(6), duration.getValue());
        assertEquals("6t (300 ms)", duration.displayDuration());
        assertFalse(duration.setValue(1));
        assertFalse(duration.setValue(21));
        assertTrue(duration.setValue(20));
        JsonObject json = new JsonObject();
        duration.write(json);
        TickDurationProperty restored = new LegitScaffold().initialSneak;
        assertTrue(restored.read(json));
        assertEquals(Integer.valueOf(20), restored.getValue());
        assertEquals("20t (1000 ms)", restored.displayDuration());
    }

    @Test public void lateRotationTakeoverIsDetectedWithoutClearingTheForeignRequest() {
        UpdateEvent event = new UpdateEvent(EventType.PRE, 0, 0, 0, 0);
        event.setRotation(135, 78, 3); event.setPervRotation(135, 3);
        assertTrue(LegitScaffold.matchesRotation(event, 135, 78));
        event.setRotation(90, 60, 5);
        assertFalse(LegitScaffold.matchesRotation(event, 135, 78));
        assertEquals(90, event.getNewYaw(), 0);
        event.setPervRotation(90, 5);
        assertFalse(LegitScaffold.matchesRotation(event, 135, 78));
    }

    @Test public void placementWaitsForThePreviouslyReportedLookInThePreMovementPhase() {
        UpdateEvent settling = new UpdateEvent(EventType.PRE, 810, 60, 0, 0);
        settling.setRotation(855, 78, 3); settling.setPervRotation(855, 3);
        assertTrue(LegitScaffold.matchesRotation(settling, 855, 78));
        assertFalse(LegitScaffold.hasPlacementRotation(settling, 855, 78));

        UpdateEvent ready = new UpdateEvent(EventType.PRE, 855, 78, 0, 0);
        ready.setRotation(855, 78, 3); ready.setPervRotation(855, 3);
        assertTrue(LegitScaffold.hasPlacementRotation(ready, 855, 78));
        assertFalse(LegitScaffold.hasPlacementRotation(
                new UpdateEvent(EventType.POST, 855, 78, 855, 78), 855, 78));
        assertFalse(LegitScaffold.hasPlacementRotation(null, 855, 78));

        ready.setRotation(900, 78, 5);
        assertFalse(LegitScaffold.hasPlacementRotation(ready, 855, 78));
    }

    @Test public void equivalentWrappedHeadingCannotHideAnUnreportedNumericYawChange() {
        UpdateEvent wrapped = new UpdateEvent(EventType.PRE, 855, 78, 0, 0);
        wrapped.setRotation(135, 78, 3); wrapped.setPervRotation(135, 3);
        assertTrue(LegitScaffold.matchesRotation(wrapped, 135, 78));
        assertFalse(LegitScaffold.hasPlacementRotation(wrapped, 135, 78));
    }

    @Test public void worldChangeAndDisableClearTheOldSequenceAndSlots() throws Exception {
        LegitScaffold module = new LegitScaffold();
        Field cField = LegitScaffold.class.getDeclaredField("controller"); cField.setAccessible(true);
        Field iField = LegitScaffold.class.getDeclaredField("inventory"); iField.setAccessible(true);
        LegitScaffoldController c = (LegitScaffoldController) cField.get(module);
        LegitScaffoldInventory inventory = (LegitScaffoldInventory) iField.get(module);
        c.begin(0, 0, 0); inventory.begin(8); c.placed(5);
        module.onLoadWorld(new LoadWorldEvent());
        assertNull(c.side());
        assertEquals(LegitScaffoldController.Phase.IDLE, c.phase());
        assertEquals(-1, inventory.original());
        c.begin(1, 90, 0); inventory.begin(3);
        module.onDisabled();
        assertNull(c.side());
        assertEquals(-1, inventory.original());
    }
}

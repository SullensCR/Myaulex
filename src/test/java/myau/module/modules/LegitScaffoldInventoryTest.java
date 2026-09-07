package myau.module.modules;

import org.junit.Test;
import static org.junit.Assert.*;

public class LegitScaffoldInventoryTest {
    @Test public void keepsCurrentStackThenWrapsToNextUsableSlot() {
        LegitScaffoldInventory inventory = new LegitScaffoldInventory();
        inventory.begin(8);
        assertEquals(8, inventory.select(slot -> slot == 8 || slot == 1));
        assertEquals(1, inventory.select(slot -> slot == 1));
        assertEquals(1, inventory.select(slot -> slot == 1 || slot == 2));
        assertEquals(-1, inventory.select(slot -> false));
        assertEquals(8, inventory.original());
        assertEquals(2, inventory.select(slot -> slot == 2));
    }

    @Test public void newSessionNeverRestoresThePreviousPlayersSlot() {
        LegitScaffoldInventory inventory = new LegitScaffoldInventory();
        inventory.begin(8); inventory.select(slot -> slot == 2);
        inventory.reset();
        assertEquals(-1, inventory.original());
        assertEquals(-1, inventory.select(slot -> true));
        inventory.begin(4);
        assertEquals(4, inventory.original());
        assertEquals(4, inventory.selected());
    }
}

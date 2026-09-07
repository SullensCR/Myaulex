package myau.module.modules;

import java.util.function.IntPredicate;

/** Slot bookkeeping belongs to one player/world session, never to the renderer. */
final class LegitScaffoldInventory {
    private int original = -1;
    private int selected = -1;

    void begin(int current) { original = current; selected = current; }

    int select(IntPredicate usable) {
        if (selected < 0) return -1;
        for (int offset = 0; offset < 9; offset++) {
            int slot = (selected + offset) % 9;
            if (usable.test(slot)) return selected = slot;
        }
        return -1;
    }

    int original() { return original; }
    int selected() { return selected; }
    void reset() { original = -1; selected = -1; }
}

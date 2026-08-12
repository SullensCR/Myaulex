package myau.render.ui;

import java.util.HashMap;
import java.util.Map;

/** Small identity-preserving cache used by the startup-owned UI renderer. */
final class UiResourceCache<T> {
    interface Loader<T> {
        T load();
    }

    interface Disposer<T> {
        void dispose(T value);
    }

    private final Map<String, T> values = new HashMap<>();

    T get(String key, Loader<T> loader) {
        T value = values.get(key);
        if (value != null) return value;
        value = loader.load();
        values.put(key, value);
        return value;
    }

    int size() {
        return values.size();
    }

    void clear(Disposer<T> disposer) {
        for (T value : values.values()) disposer.dispose(value);
        values.clear();
    }
}

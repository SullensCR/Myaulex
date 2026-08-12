package myau.render.ui;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class UiResourceCacheTest {
    @Test
    public void repeatedLookupReturnsTheSameResource() {
        UiResourceCache<Object> cache = new UiResourceCache<>();
        AtomicInteger loads = new AtomicInteger();
        UiResourceCache.Loader<Object> loader = new UiResourceCache.Loader<Object>() {
            @Override
            public Object load() {
                loads.incrementAndGet();
                return new Object();
            }
        };

        Object first = cache.get("icon", loader);
        Object second = cache.get("icon", loader);

        assertSame(first, second);
        assertEquals(1, loads.get());
        assertEquals(1, cache.size());
    }

    @Test
    public void clearDisposesEachCachedResourceOnce() {
        UiResourceCache<Object> cache = new UiResourceCache<>();
        cache.get("first", Object::new);
        cache.get("second", Object::new);
        AtomicInteger disposals = new AtomicInteger();

        cache.clear(value -> disposals.incrementAndGet());

        assertEquals(2, disposals.get());
        assertEquals(0, cache.size());
    }
}

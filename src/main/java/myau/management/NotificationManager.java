package myau.management;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {
    private static final int MAX_ENTRIES = 32;
    private final List<NotificationEntry> entries = new ArrayList<>();

    public synchronized void add(String message, long durationMillis, int color) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        this.cleanupExpired();
        while (this.entries.size() >= MAX_ENTRIES) {
            this.entries.remove(0);
        }
        this.entries.add(new NotificationEntry(message, durationMillis, color));
    }

    public synchronized List<NotificationEntry> getActive() {
        this.cleanupExpired();
        return new ArrayList<>(this.entries);
    }

    public synchronized void clear() {
        this.entries.clear();
    }

    private void cleanupExpired() {
        Iterator<NotificationEntry> iterator = this.entries.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired()) {
                iterator.remove();
            }
        }
    }

    public static class NotificationEntry {
        public final String message;
        public final long startMillis;
        public final long durationMillis;
        public final int color;

        public NotificationEntry(String message, long durationMillis, int color) {
            this.message = message;
            this.durationMillis = durationMillis;
            this.color = color;
            this.startMillis = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return this.durationMillis >= 0L && this.getAge() >= this.durationMillis;
        }

        public long getAge() {
            return System.currentTimeMillis() - this.startMillis;
        }
    }
}

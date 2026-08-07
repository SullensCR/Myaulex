package myau.management;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {
    private static final int MAX_ENTRIES = 32;
    private final List<NotificationEntry> entries = new ArrayList<>();

    public synchronized void add(String message, long durationMillis, int color) {
        add(NotificationType.INFO, "", message, durationMillis, color, false);
    }

    public synchronized void add(NotificationType type, String title, String message,
                                 long durationMillis, boolean critical) {
        add(type, title, message, durationMillis, type.color, critical);
    }

    private synchronized void add(NotificationType type, String title, String message,
                                  long durationMillis, int color, boolean critical) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        this.cleanupExpired();
        while (this.entries.size() >= MAX_ENTRIES) {
            this.entries.remove(0);
        }
        this.entries.add(new NotificationEntry(type, title, message, durationMillis, color, critical));
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
        public final NotificationType type;
        public final String title;
        public final String message;
        public final long startMillis;
        public final long durationMillis;
        public final int color;
        public final boolean critical;

        public NotificationEntry(String message, long durationMillis, int color) {
            this(NotificationType.INFO, "", message, durationMillis, color, false);
        }

        public NotificationEntry(NotificationType type, String title, String message,
                                 long durationMillis, int color, boolean critical) {
            this.type = type == null ? NotificationType.INFO : type;
            this.title = title == null ? "" : title;
            this.message = message;
            this.durationMillis = durationMillis;
            this.color = color;
            this.startMillis = System.currentTimeMillis();
            this.critical = critical;
        }

        public boolean isExpired() {
            return this.durationMillis >= 0L && this.getAge() >= this.durationMillis;
        }

        public long getAge() {
            return System.currentTimeMillis() - this.startMillis;
        }
    }

    public enum NotificationType {
        INFO(0xFF65C3EC),
        WARNING(0xFFFFB84D),
        ERROR(0xFFFF5C6C),
        ANALYSIS(0xFFB0A7F7),
        CONFIG_ERROR(0xFFFF5C6C),
        CONFIG_SUCCESS(0xFF65D982),
        CONFIG_EDIT(0xFF8FA7FF),
        ENABLED(0xFF65D982),
        DISABLED(0xFFFF5C6C);

        public final int color;

        NotificationType(int color) {
            this.color = color;
        }
    }
}

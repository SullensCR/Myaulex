package myau.management;

import myau.util.SoundUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Owns notification data and timing independently from the HUD renderer. */
public class NotificationManager {
    public static final long ENTER_DURATION = 300L;
    public static final long DISPLAY_DURATION = 1200L;
    public static final long EXIT_DURATION = 300L;
    public static final String ANALYZER_SLOT = "transaction-analyzer";

    private static final int MAX_ENTRIES = 32;
    private final LongSupplier clock;
    private final Consumer<String> soundPlayer;
    private final List<NotificationEntry> entries = new ArrayList<>();
    private boolean enabled = true;

    public NotificationManager() {
        this(System::currentTimeMillis, SoundUtil::playSound);
    }

    NotificationManager(LongSupplier clock) {
        this(clock, SoundUtil::playSound);
    }

    NotificationManager(LongSupplier clock, Consumer<String> soundPlayer) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
        this.soundPlayer = soundPlayer == null ? SoundUtil::playSound : soundPlayer;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) this.entries.clear();
    }

    public synchronized boolean isEnabled() {
        return this.enabled;
    }

    /** Compatibility overload for callers that have not yet supplied a title. */
    public synchronized void add(String message, long durationMillis, int color) {
        if (!this.enabled) return;
        NotificationType type = NotificationType.fromColor(color);
        this.addWithSound(type, uniqueSlot(), "Notification", message,
                DISPLAY_DURATION, false, false, SoundUtil.NOTIFICATION_SOUND);
    }

    /** Compatibility overload retaining the existing typed API. */
    public synchronized void add(NotificationType type, String title, String message,
                                 long durationMillis, boolean critical) {
        if (!this.enabled) return;
        this.addWithSound(type, uniqueSlot(), title, message,
                DISPLAY_DURATION, critical, false, SoundUtil.NOTIFICATION_SOUND);
    }

    public synchronized NotificationEntry add(NotificationType type, String slotKey, String title,
                                              String message, boolean critical) {
        if (!this.enabled) return null;
        return this.addWithSound(type, slotKey, title, message,
                DISPLAY_DURATION, critical, false, SoundUtil.NOTIFICATION_SOUND);
    }

    public synchronized NotificationEntry addToggle(String moduleName, boolean enabled) {
        if (!this.enabled) return null;
        return this.addWithSound(
                enabled ? NotificationType.ENABLED : NotificationType.DISABLED,
                moduleName,
                moduleName,
                enabled ? "Enabled" : "Disabled",
                DISPLAY_DURATION,
                false,
                false,
                null
        );
    }

    public synchronized NotificationEntry addBedWhitelist() {
        this.playSound(SoundUtil.BED_WHITELIST_SOUND);
        if (!this.enabled) return null;
        return this.addInternal(
                NotificationType.INFO,
                "bedbreaker-whitelist",
                "BedBreaker",
                "Bed whitelisted",
                DISPLAY_DURATION,
                false,
                false
        );
    }

    public synchronized NotificationEntry beginAnalysis() {
        if (!this.enabled) return null;
        return this.addWithSound(
                NotificationType.ANALYSIS,
                ANALYZER_SLOT,
                "TransactionAnalyzer",
                "Analyzing transactions",
                3000L,
                false,
                true,
                SoundUtil.NOTIFICATION_SOUND
        );
    }

    public synchronized boolean completeAnalysis(String result) {
        if (!this.enabled) return false;
        for (int i = this.entries.size() - 1; i >= 0; i--) {
            NotificationEntry entry = this.entries.get(i);
            if (ANALYZER_SLOT.equals(entry.slotKey) && entry.progressActive) {
                entry.update(NotificationType.ANALYSIS, "TransactionAnalyzer", result,
                        DISPLAY_DURATION, false, false, this.clock.getAsLong());
                return true;
            }
        }
        return false;
    }

    public synchronized List<NotificationEntry> getActive() {
        this.cleanupExpired();
        return new ArrayList<>(this.entries);
    }

    public synchronized void clear() {
        this.entries.clear();
    }

    private NotificationEntry addWithSound(NotificationType type, String slotKey, String title,
                                            String message, long holdDuration, boolean critical,
                                            boolean progressActive, String soundName) {
        NotificationEntry entry = this.addInternal(type, slotKey, title, message,
                holdDuration, critical, progressActive);
        if (entry != null && soundName != null) this.playSound(soundName);
        return entry;
    }

    private void playSound(String soundName) {
        this.soundPlayer.accept(soundName);
    }

    private NotificationEntry addInternal(NotificationType type, String slotKey, String title,
                                           String message, long holdDuration, boolean critical,
                                           boolean progressActive) {
        if (message == null || message.trim().isEmpty()) return null;
        this.cleanupExpired();
        String safeSlot = slotKey == null || slotKey.trim().isEmpty() ? uniqueSlot() : slotKey;
        for (NotificationEntry entry : this.entries) {
            if (safeSlot.equals(entry.slotKey) && !entry.isExpired()) entry.beginExit(this.clock.getAsLong());
        }
        while (this.entries.size() >= MAX_ENTRIES) this.entries.remove(0);
        NotificationEntry entry = new NotificationEntry(
                type == null ? NotificationType.INFO : type,
                safeSlot,
                title,
                message,
                holdDuration,
                critical,
                progressActive,
                this.clock
        );
        this.entries.add(entry);
        return entry;
    }

    private void cleanupExpired() {
        Iterator<NotificationEntry> iterator = this.entries.iterator();
        while (iterator.hasNext()) {
            NotificationEntry entry = iterator.next();
            if (!entry.isExiting() && entry.isReadyForExit()) entry.beginExit(this.clock.getAsLong());
            if (entry.isExpired()) iterator.remove();
        }
    }

    private String uniqueSlot() {
        return "notification-" + this.clock.getAsLong() + "-" + this.entries.size();
    }

    public static final class NotificationEntry {
        private final LongSupplier clock;
        private final String slotKey;
        private final boolean critical;
        private long startMillis;
        private long holdDuration;
        private long exitStartedMillis = -1L;
        private boolean progressActive;
        private NotificationType type;
        private String title;
        private String message;

        private NotificationEntry(NotificationType type, String slotKey, String title, String message,
                                  long holdDuration, boolean critical, boolean progressActive,
                                  LongSupplier clock) {
            this.type = type;
            this.slotKey = slotKey;
            this.title = title == null ? "" : title;
            this.message = message;
            this.holdDuration = holdDuration;
            this.critical = critical;
            this.progressActive = progressActive;
            this.clock = clock;
            this.startMillis = clock.getAsLong();
        }

        public NotificationType getType() {
            return this.type;
        }

        public String getSlotKey() {
            return this.slotKey;
        }

        public String getTitle() {
            return this.title;
        }

        public String getMessage() {
            return this.message;
        }

        public long getHoldDuration() {
            return this.holdDuration;
        }

        public boolean isCritical() {
            return this.critical;
        }

        public boolean isProgressActive() {
            return this.progressActive;
        }

        public long getAge() {
            return Math.max(0L, this.clock.getAsLong() - this.startMillis);
        }

        public boolean isExiting() {
            return this.exitStartedMillis >= 0L;
        }

        public long getExitAge() {
            if (!this.isExiting()) return 0L;
            return Math.max(0L, this.clock.getAsLong() - this.exitStartedMillis);
        }

        public boolean isExpired() {
            return this.isExiting()
                    ? this.getExitAge() >= EXIT_DURATION
                    : this.getAge() >= ENTER_DURATION + this.holdDuration;
        }

        private boolean isReadyForExit() {
            return this.getAge() >= ENTER_DURATION + this.holdDuration;
        }

        public float getProgress() {
            if (!this.progressActive || this.holdDuration <= 0L) return 1.0F;
            return Math.max(0.0F, Math.min(1.0F, this.getAge() / (float) this.holdDuration));
        }

        private void beginExit(long now) {
            if (this.exitStartedMillis < 0L) this.exitStartedMillis = now;
        }

        private void update(NotificationType type, String title, String message, long holdDuration,
                            boolean critical, boolean progressActive, long now) {
            this.type = type == null ? NotificationType.INFO : type;
            this.title = title == null ? "" : title;
            this.message = message == null ? "" : message;
            this.holdDuration = holdDuration;
            this.progressActive = progressActive;
            this.startMillis = now;
            this.exitStartedMillis = -1L;
        }
    }

    public enum NotificationType {
        INFO("Info", 0xFF65C3EC),
        WARNING("Warning", 0xFFF8FF72),
        ERROR("Error", 0xFFC74444),
        ANALYSIS("Analyze", 0xFF65C3EC),
        CONFIG_ERROR("Config-Error", 0xFFC74444),
        CONFIG_SUCCESS("Config-Success", 0xFF81FF85),
        CONFIG_EDIT("Config-Edit", 0xFF9C95FF),
        ENABLED("Enabled", 0xFF81FF85),
        DISABLED("Disabled", 0xFFC74444);

        public final String iconName;
        public final int color;

        NotificationType(String iconName, int color) {
            this.iconName = iconName;
            this.color = color;
        }

        private static NotificationType fromColor(int color) {
            int rgb = color & 0x00FFFFFF;
            NotificationType closest = INFO;
            int distance = Integer.MAX_VALUE;
            for (NotificationType type : values()) {
                int candidate = type.color & 0x00FFFFFF;
                int current = Math.abs(((candidate >> 16) & 255) - ((rgb >> 16) & 255))
                        + Math.abs(((candidate >> 8) & 255) - ((rgb >> 8) & 255))
                        + Math.abs((candidate & 255) - (rgb & 255));
                if (current < distance) {
                    distance = current;
                    closest = type;
                }
            }
            return closest;
        }
    }
}

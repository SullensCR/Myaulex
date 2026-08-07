package myau.media;

import org.freedesktop.DBus;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Non-blocking MPRIS 2.2 bridge. All D-Bus access stays on a daemon worker so
 * a missing session bus or a disappearing player can never stall rendering.
 */
public final class MprisService {
    private static final String PATH = "/org/mpris/MediaPlayer2";
    private static final String PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player";
    private static final MprisService INSTANCE = new MprisService();

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Myaulex-MPRIS");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Snapshot snapshot = Snapshot.unavailable();
    private volatile DBusConnection connection;
    private volatile String activeService;

    private MprisService() {
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")) {
            worker.scheduleWithFixedDelay(this::refreshSafely, 0, 1, TimeUnit.SECONDS);
        }
    }

    public static MprisService getInstance() {
        return INSTANCE;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public void previous() {
        submit(Player::Previous);
    }

    public void playPause() {
        submit(Player::PlayPause);
    }

    public void next() {
        submit(Player::Next);
    }

    public void close() {
        worker.shutdownNow();
        DBusConnection current = connection;
        connection = null;
        if (current != null) current.disconnect();
    }

    private void submit(PlayerAction action) {
        worker.execute(() -> {
            try {
                String service = activeService;
                if (service == null) return;
                Player player = ensureConnection().getRemoteObject(service, PATH, Player.class);
                action.apply(player);
                refreshSafely();
            } catch (Exception ignored) {
                snapshot = Snapshot.unavailable();
            }
        });
    }

    private void refreshSafely() {
        try {
            DBusConnection bus = ensureConnection();
            DBus daemon = bus.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
            String selected = null;
            Snapshot selectedSnapshot = null;
            for (String name : daemon.ListNames()) {
                if (!name.startsWith("org.mpris.MediaPlayer2.")) continue;
                Snapshot candidate = read(bus, name);
                if (candidate == null) continue;
                if (selectedSnapshot == null || ("Playing".equals(candidate.playbackStatus)
                        && !"Playing".equals(selectedSnapshot.playbackStatus))) {
                    selected = name;
                    selectedSnapshot = candidate;
                }
            }
            activeService = selected;
            snapshot = selectedSnapshot == null ? Snapshot.unavailable() : selectedSnapshot;
        } catch (Exception error) {
            activeService = null;
            snapshot = Snapshot.unavailable();
            DBusConnection stale = connection;
            connection = null;
            if (stale != null) stale.disconnect();
        }
    }

    private DBusConnection ensureConnection() throws Exception {
        DBusConnection current = connection;
        if (current == null) {
            current = DBusConnection.newConnection(DBusConnection.DBusBusType.SESSION);
            connection = current;
        }
        return current;
    }

    private Snapshot read(DBusConnection bus, String service) {
        try {
            Properties properties = bus.getRemoteObject(service, PATH, Properties.class);
            Map<String, Variant<?>> values = properties.GetAll(PLAYER_INTERFACE);
            String status = stringValue(values.get("PlaybackStatus"), "Stopped");
            boolean canPrevious = booleanValue(values.get("CanGoPrevious"));
            boolean canPlay = booleanValue(values.get("CanPlay")) || booleanValue(values.get("CanPause"));
            boolean canNext = booleanValue(values.get("CanGoNext"));
            Object metadataValue = unwrap(values.get("Metadata"));
            Map<?, ?> metadata = metadataValue instanceof Map ? (Map<?, ?>) metadataValue : Collections.emptyMap();
            String title = metadataString(metadata, "xesam:title", "Unknown title");
            String album = metadataString(metadata, "xesam:album", "");
            String artist = metadataString(metadata, "xesam:artist", "");
            String artUrl = metadataString(metadata, "mpris:artUrl", "");
            return new Snapshot(true, service, status, title, artist, album, artUrl,
                    canPrevious, canPlay, canNext);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object unwrap(Object value) {
        return value instanceof Variant ? ((Variant<?>) value).getValue() : value;
    }

    private static boolean booleanValue(Object value) {
        Object unwrapped = unwrap(value);
        return unwrapped instanceof Boolean && (Boolean) unwrapped;
    }

    private static String stringValue(Object value, String fallback) {
        Object unwrapped = unwrap(value);
        return unwrapped == null ? fallback : String.valueOf(unwrapped);
    }

    private static String metadataString(Map<?, ?> metadata, String key, String fallback) {
        Object value = unwrap(metadata.get(key));
        if (value instanceof String[]) {
            String[] strings = (String[]) value;
            return strings.length == 0 ? fallback : join(strings);
        }
        if (value instanceof Iterable) {
            StringBuilder result = new StringBuilder();
            for (Object item : (Iterable<?>) value) {
                if (result.length() > 0) result.append(", ");
                result.append(item);
            }
            return result.length() == 0 ? fallback : result.toString();
        }
        return value == null ? fallback : String.valueOf(value);
    }

    private static String join(String[] strings) {
        StringBuilder result = new StringBuilder();
        for (String string : strings) {
            if (result.length() > 0) result.append(", ");
            result.append(string);
        }
        return result.toString();
    }

    private interface PlayerAction {
        void apply(Player player);
    }

    @DBusInterfaceName(PLAYER_INTERFACE)
    public interface Player extends DBusInterface {
        void Previous();
        void PlayPause();
        void Next();
    }

    public static final class Snapshot {
        public final boolean available;
        public final String service;
        public final String playbackStatus;
        public final String title;
        public final String artist;
        public final String album;
        public final String artUrl;
        public final boolean canPrevious;
        public final boolean canPlayPause;
        public final boolean canNext;

        private Snapshot(boolean available, String service, String playbackStatus, String title,
                         String artist, String album, String artUrl, boolean canPrevious,
                         boolean canPlayPause, boolean canNext) {
            this.available = available;
            this.service = service;
            this.playbackStatus = playbackStatus;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.artUrl = artUrl;
            this.canPrevious = canPrevious;
            this.canPlayPause = canPlayPause;
            this.canNext = canNext;
        }

        private static Snapshot unavailable() {
            return new Snapshot(false, "", "Stopped", "", "", "", "", false, false, false);
        }
    }
}

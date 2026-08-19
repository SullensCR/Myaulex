package myau.management;

import myau.Myau;
import myau.config.Config;
import myau.util.SoundUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationManagerTest {
    private long now;
    private NotificationManager manager;
    private List<String> playedSounds;

    @Before
    public void setUp() {
        this.now = 0L;
        this.playedSounds = new ArrayList<>();
        this.manager = new NotificationManager(() -> this.now, this.playedSounds::add);
    }

    @Test
    public void ordinaryEntryUsesEnterHoldAndExitPhases() {
        NotificationManager.NotificationEntry entry = this.manager.addToggle("Scaffold", true);

        this.now = NotificationManager.ENTER_DURATION - 1L;
        assertFalse(entry.isExiting());
        assertEquals(1, this.manager.getActive().size());

        this.now = NotificationManager.ENTER_DURATION + NotificationManager.DISPLAY_DURATION - 1L;
        assertFalse(entry.isExiting());
        assertEquals(1, this.manager.getActive().size());

        this.now = NotificationManager.ENTER_DURATION + NotificationManager.DISPLAY_DURATION;
        List<NotificationManager.NotificationEntry> exiting = this.manager.getActive();
        assertEquals(1, exiting.size());
        assertTrue(entry.isExiting());
        assertEquals(0L, entry.getExitAge());

        this.now += NotificationManager.EXIT_DURATION - 1L;
        assertEquals(1, this.manager.getActive().size());
        this.now += 1L;
        assertTrue(this.manager.getActive().isEmpty());
    }

    @Test
    public void sameModuleToggleReusesSlotAndCrossfades() {
        NotificationManager.NotificationEntry oldEntry = this.manager.addToggle("Scaffold", false);
        this.now = 40L;
        NotificationManager.NotificationEntry newEntry = this.manager.addToggle("Scaffold", true);

        assertEquals("Scaffold", oldEntry.getSlotKey());
        assertEquals(oldEntry.getSlotKey(), newEntry.getSlotKey());
        assertTrue(oldEntry.isExiting());
        assertEquals(0L, newEntry.getAge());
        assertEquals(2, this.manager.getActive().size());

        this.now = 40L + NotificationManager.EXIT_DURATION;
        assertEquals(1, this.manager.getActive().size());
        assertEquals("Enabled", this.manager.getActive().get(0).getMessage());
    }

    @Test
    public void genericNotificationsPlayOnlyTheGenericSound() {
        this.manager.add(NotificationManager.NotificationType.WARNING, "warning", "Warning", "Be careful", false);

        assertEquals(1, this.playedSounds.size());
        assertEquals(SoundUtil.NOTIFICATION_SOUND, this.playedSounds.get(0));
    }

    @Test
    public void toggleNotificationsDoNotPlayTheGenericSound() {
        this.manager.addToggle("Scaffold", true);

        assertTrue(this.playedSounds.isEmpty());
    }

    @Test
    public void bedWhitelistUsesItsDedicatedSoundEvenWithoutCards() {
        NotificationManager.NotificationEntry entry = this.manager.addBedWhitelist();

        assertEquals("Bed whitelisted", entry.getMessage());
        assertEquals(1, this.playedSounds.size());
        assertEquals(SoundUtil.BED_WHITELIST_SOUND, this.playedSounds.get(0));

        this.playedSounds.clear();
        this.manager.setEnabled(false);
        assertEquals(null, this.manager.addBedWhitelist());
        assertEquals(1, this.playedSounds.size());
        assertEquals(SoundUtil.BED_WHITELIST_SOUND, this.playedSounds.get(0));
    }

    @Test
    public void analyzerProgressUpdatesInPlaceAndRestartsResultHold() {
        NotificationManager.NotificationEntry active = this.manager.beginAnalysis();
        assertEquals(1, this.playedSounds.size());
        assertEquals(SoundUtil.NOTIFICATION_SOUND, this.playedSounds.get(0));
        this.now = 1500L;
        assertEquals(0.5F, active.getProgress(), 0.001F);
        assertTrue(active.isProgressActive());

        this.now = 3000L;
        assertTrue(this.manager.completeAnalysis("Grim"));
        List<NotificationManager.NotificationEntry> result = this.manager.getActive();
        assertEquals(1, result.size());
        assertEquals(active, result.get(0));
        assertEquals("TransactionAnalyzer", active.getTitle());
        assertEquals("Grim", active.getMessage());
        assertFalse(active.isProgressActive());
        assertEquals(0L, active.getAge());
        assertEquals(1, this.playedSounds.size());

        this.now = 3000L + NotificationManager.ENTER_DURATION + NotificationManager.DISPLAY_DURATION;
        this.manager.getActive();
        assertTrue(active.isExiting());
    }

    @Test
    public void notificationTypesMapToSuppliedIconNames() {
        assertEquals("Info", NotificationManager.NotificationType.INFO.iconName);
        assertEquals("Warning", NotificationManager.NotificationType.WARNING.iconName);
        assertEquals("Error", NotificationManager.NotificationType.ERROR.iconName);
        assertEquals("Analyze", NotificationManager.NotificationType.ANALYSIS.iconName);
        assertEquals("Config-Error", NotificationManager.NotificationType.CONFIG_ERROR.iconName);
        assertEquals("Config-Success", NotificationManager.NotificationType.CONFIG_SUCCESS.iconName);
        assertEquals("Config-Edit", NotificationManager.NotificationType.CONFIG_EDIT.iconName);
        assertEquals("Enabled", NotificationManager.NotificationType.ENABLED.iconName);
        assertEquals("Disabled", NotificationManager.NotificationType.DISABLED.iconName);
    }

    @Test
    public void disabledManagerSuppressesCardsAndCanBeReenabled() {
        this.manager.setEnabled(false);
        assertTrue(this.manager.getActive().isEmpty());
        assertEquals(null, this.manager.addToggle("Scaffold", true));
        this.manager.add(NotificationManager.NotificationType.WARNING, "warning", "Warning", "Be careful", false);
        assertTrue(this.playedSounds.isEmpty());

        this.manager.setEnabled(true);
        this.manager.addToggle("Scaffold", true);
        assertEquals(1, this.manager.getActive().size());
    }

    @Test
    public void invalidConfigFeedbackBecomesTypedCard() {
        NotificationManager previous = Myau.notificationManager;
        Myau.notificationManager = this.manager;
        try {
            new Config("", false).load();
            List<NotificationManager.NotificationEntry> active = this.manager.getActive();
            assertEquals(1, active.size());
            assertEquals(NotificationManager.NotificationType.CONFIG_ERROR, active.get(0).getType());
            assertEquals("Configuration", active.get(0).getTitle());
        } finally {
            Myau.notificationManager = previous;
        }
    }
}

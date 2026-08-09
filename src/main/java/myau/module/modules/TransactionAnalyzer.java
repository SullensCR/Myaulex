package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.ChatUtil;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Logs and classifies server confirm-transaction sequences. */
public final class TransactionAnalyzer extends Module {
    private final List<Short> samples = Collections.synchronizedList(new ArrayList<Short>());
    private final List<String> pendingChat = Collections.synchronizedList(new ArrayList<String>());
    private long analysisStarted;
    private boolean analyzing;
    private String detected = "Unknown";

    public TransactionAnalyzer() {
        super("TransactionAnalyzer", false, false, "Analyzes server transaction patterns.");
    }

    @Override
    public void onEnabled() {
        beginAnalysis();
    }

    @EventTarget
    public void onWorldChange(LoadWorldEvent event) {
        if (isEnabled()) beginAnalysis();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE
                || !(event.getPacket() instanceof S32PacketConfirmTransaction)) return;
        S32PacketConfirmTransaction packet = (S32PacketConfirmTransaction) event.getPacket();
        samples.add(packet.getActionNumber());
        pendingChat.add(String.format("&cTransaction&r (ID: %d, WindowID: %d, Accepted: %s)",
                (int) packet.getActionNumber(), packet.getWindowId(), packet.func_148888_e()));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        synchronized (pendingChat) {
            for (String line : pendingChat) ChatUtil.sendFormatted(Myau.clientName + line);
            pendingChat.clear();
        }
        if (analyzing && System.currentTimeMillis() - analysisStarted >= 3000L) {
            analyzing = false;
            List<Short> copy;
            synchronized (samples) {
                copy = new ArrayList<>(samples);
            }
            detected = classify(copy);
            if (Myau.notificationManager != null) {
                Myau.notificationManager.completeAnalysis(detected);
            }
        }
    }

    public String getDetected() {
        return detected;
    }

    private void beginAnalysis() {
        samples.clear();
        pendingChat.clear();
        detected = "Analyzing";
        analysisStarted = System.currentTimeMillis();
        analyzing = true;
        if (Myau.notificationManager != null) {
            Myau.notificationManager.beginAnalysis();
        }
    }

    static String classify(List<Short> values) {
        if (values == null || values.isEmpty()) return "Vanilla / No Anticheat";
        if (allEqual(values, (short) 23767)) return "Frequency";
        if (starts(values, 0, -1, -2, -3)) return "Grim";
        if (starts(values, -3000, -3001, -3002)) return "Karhu";
        if (starts(values, 1, 2, 3) && isStep(values, 1)) return "AGC";
        if (starts(values, 0, -1, -2) && isStep(values, -1)) return "Demon";
        if (starts(values, 0, 1, 2, 3)) return "Intave Old";
        if (values.get(0) < -30000 && isStep(values, -1)) return "Vulcan";
        if (hasPositiveAndNegativeTracks(values)) return "Sparky / Overflow";
        if (hasPeriodicOppositeSign(values, 18, 21)) return "Kraken / Lumos";
        if (looksMatrix(values)) return "Matrix";
        if (isStep(values, values.size() > 1 && values.get(1) > values.get(0) ? 1 : -1)) return "Verus / Buzz";
        if (mostlyDecreasingNegative(values)) return "Polar / Grim 3";
        return "Unknown";
    }

    private static boolean starts(List<Short> values, int... prefix) {
        if (values.size() < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (values.get(i) != (short) prefix[i]) return false;
        return true;
    }

    private static boolean allEqual(List<Short> values, short expected) {
        for (short value : values) if (value != expected) return false;
        return true;
    }

    private static boolean isStep(List<Short> values, int step) {
        if (values.size() < 3) return false;
        int matches = 0;
        for (int i = 1; i < values.size(); i++) if (values.get(i) - values.get(i - 1) == step) matches++;
        return matches >= Math.max(2, (values.size() - 1) * 3 / 4);
    }

    private static boolean hasPositiveAndNegativeTracks(List<Short> values) {
        int positive = 0, negative = 0;
        for (short value : values) {
            if (value > 0) positive++;
            if (value < 0) negative++;
        }
        return positive >= 2 && negative >= 2;
    }

    private static boolean hasPeriodicOppositeSign(List<Short> values, int min, int max) {
        int lastPositive = -1;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) > 0) {
                if (lastPositive >= 0 && i - lastPositive >= min && i - lastPositive <= max) return true;
                lastPositive = i;
            }
        }
        return false;
    }

    private static boolean looksMatrix(List<Short> values) {
        if (values.isEmpty() || values.get(0) != -1) return false;
        boolean low = false, high = false;
        for (short value : values) {
            if (value <= -14) low = true;
            if (value >= 101) high = true;
        }
        return low && high;
    }

    private static boolean mostlyDecreasingNegative(List<Short> values) {
        int matches = 0;
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) < 0 && values.get(i) < values.get(i - 1)) matches++;
        }
        return matches >= Math.max(2, values.size() * 2 / 3);
    }
}

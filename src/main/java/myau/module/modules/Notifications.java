package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.management.NotificationManager;
import myau.management.NotificationManager.NotificationEntry;
import myau.management.NotificationManager.NotificationType;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.render.HudPosition;
import myau.render.ui.ProgressbarRenderer;
import myau.render.ui.UiFont;
import myau.render.ui.UiFonts;
import myau.render.ui.UiRenderer;
import myau.render.ui.UiTexture;
import myau.render.ui.UiTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Independent lower-right notification renderer. */
public final class Notifications extends Module {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final float DESIGN_WIDTH = 1920.0F;
    private static final float DESIGN_HEIGHT = 1080.0F;
    private static final float RIGHT_MARGIN = 16.0F;
    private static final float BOTTOM_MARGIN = 16.0F;
    private static final float STACK_GAP = 8.0F;
    private static final float MAX_WIDTH = 420.0F;
    private static final float MIN_WIDTH = 220.0F;
    private static final float ICON_SIZE = 30.0F;
    private static final float TEXT_X = 58.0F;
    private static final float RIGHT_PADDING = 16.0F;
    private static final float TRAILING_PADDING = 5.0F;
    private static final float TEXT_GAP = 2.0F;
    private static final float TOP_PADDING = 9.0F;
    private static final float BOTTOM_PADDING = 9.0F;
    private static final float PROGRESS_GAP = 7.0F;
    private static final float PROGRESS_HEIGHT = 9.0F;
    private static final long STACK_REFLOW_DURATION_NANOS = 100_000_000L;
    private static final int CARD_BACKGROUND_ALPHA = 0x80;

    private final Map<String, VerticalAnimation> animatedY = new HashMap<>();
    private final Map<NotificationEntry, LayoutCache> layoutCache = new HashMap<>();

    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final ModeProperty positionX = new ModeProperty("position-x", 1, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty positionY = new ModeProperty("position-y", 1, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 16, 0, 5000);
    public final IntProperty offsetY = new IntProperty("offset-y", 16, 0, 5000);

    public Notifications() {
        super("Notifications", true, true, "Controls client notifications.");
    }

    @Override
    public void onEnabled() {
        if (Myau.notificationManager != null) Myau.notificationManager.setEnabled(true);
    }

    @Override
    public void onDisabled() {
        if (Myau.notificationManager != null) Myau.notificationManager.setEnabled(false);
        this.animatedY.clear();
        this.layoutCache.clear();
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || Myau.notificationManager == null || Myau.uiRenderer == null) return;
        List<NotificationEntry> active = Myau.notificationManager.getActive();
        if (active.isEmpty()) return;
        this.layoutCache.keySet().retainAll(active);

        List<List<NotificationEntry>> groups = this.groupEntries(active);
        Map<String, CardLayout> layouts = new LinkedHashMap<>();
        for (List<NotificationEntry> group : groups) {
            CardLayout layout = null;
            for (NotificationEntry entry : group) {
                CardLayout candidate = this.layout(entry);
                if (layout == null || candidate.totalHeight > layout.totalHeight) layout = candidate;
            }
            layouts.put(group.get(0).getSlotKey(), layout);
        }

        long now = System.nanoTime();

        UiTransform transform = new UiTransform(MC, DESIGN_WIDTH, DESIGN_HEIGHT, this.scale.getValue(), 0.0F);
        UiRenderer renderer = Myau.uiRenderer;
        boolean frameStarted = false;
        try {
            renderer.beginFrame("Notifications", transform, 31.0F);
            frameStarted = true;
            float cursor = this.positionY.getValue() == 0
                    ? this.offsetY.getValue()
                    : transform.getDesignHeight() - this.offsetY.getValue();
            Set<String> visibleSlots = new HashSet<>();
            for (int index = groups.size() - 1; index >= 0; index--) {
                List<NotificationEntry> group = groups.get(index);
                String slot = group.get(0).getSlotKey();
                CardLayout layout = layouts.get(slot);
                visibleSlots.add(slot);
                float targetY = this.positionY.getValue() == 0
                        ? cursor
                        : cursor - layout.totalHeight;
                VerticalAnimation animation = this.animatedY.get(slot);
                if (animation == null) {
                    animation = new VerticalAnimation(targetY, targetY, now);
                    this.animatedY.put(slot, animation);
                } else if (Math.abs(animation.targetY - targetY) > 0.01F) {
                    animation = new VerticalAnimation(animation.value(now), targetY, now);
                    this.animatedY.put(slot, animation);
                }
                float y = animation.value(now);
                for (NotificationEntry entry : group) {
                    this.renderEntry(entry, layout, y, transform.getDesignWidth());
                }
                cursor = this.positionY.getValue() == 0
                        ? targetY + layout.totalHeight + STACK_GAP
                        : targetY - STACK_GAP;
            }
            this.animatedY.keySet().removeIf(slot -> !visibleSlots.contains(slot));
        } catch (Throwable ignored) {
            // One notification frame must not prevent the other shared UI consumers from rendering.
        } finally {
            if (frameStarted) renderer.endFrame();
        }
    }

    private List<List<NotificationEntry>> groupEntries(List<NotificationEntry> entries) {
        Map<String, List<NotificationEntry>> grouped = new LinkedHashMap<>();
        for (NotificationEntry entry : entries) {
            List<NotificationEntry> group = grouped.get(entry.getSlotKey());
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(entry.getSlotKey(), group);
            }
            group.add(entry);
        }
        return new ArrayList<>(grouped.values());
    }

    private CardLayout layout(NotificationEntry entry) {
        String sourceTitle = entry.getTitle();
        String sourceMessage = entry.getMessage();
        boolean activeProgress = entry.isProgressActive();
        LayoutCache cached = this.layoutCache.get(entry);
        if (cached != null && cached.matches(sourceTitle, sourceMessage, activeProgress)) return cached.layout;

        UiFont titleFont = Myau.uiRenderer.fonts().snPro(30.0F, UiFonts.BOLD);
        UiFont bodyFont = Myau.uiRenderer.fonts().snPro(24.0F, UiFonts.REGULAR);
        float trailing = activeProgress ? 42.0F : 0.0F;
        float maxTextWidth = MAX_WIDTH - TEXT_X - RIGHT_PADDING - TRAILING_PADDING - trailing;
        String title = this.ellipsize(sourceTitle, titleFont, maxTextWidth);
        float titleWidth = titleFont.width(title);
        List<String> body = this.wrap(sourceMessage, bodyFont, maxTextWidth);
        float bodyWidth = 0.0F;
        for (String line : body) bodyWidth = Math.max(bodyWidth, bodyFont.width(line));
        float width = Math.max(MIN_WIDTH,
                Math.min(MAX_WIDTH, Math.max(titleWidth, bodyWidth) + TEXT_X + RIGHT_PADDING + TRAILING_PADDING + trailing));
        maxTextWidth = width - TEXT_X - RIGHT_PADDING - TRAILING_PADDING - trailing;
        title = this.ellipsize(sourceTitle, titleFont, maxTextWidth);
        body = this.wrap(sourceMessage, bodyFont, maxTextWidth);
        float titleHeight = title.isEmpty() ? 0.0F : titleFont.height();
        float bodyHeight = body.isEmpty() ? 0.0F : bodyFont.height() * body.size();
        float contentHeight = titleHeight + (titleHeight > 0.0F && bodyHeight > 0.0F ? TEXT_GAP : 0.0F) + bodyHeight;
        float height = Math.max(52.0F, TOP_PADDING + contentHeight + BOTTOM_PADDING);
        float totalHeight = height + (activeProgress ? PROGRESS_GAP + PROGRESS_HEIGHT : 0.0F);
        CardLayout layout = new CardLayout(title, body, width, height, totalHeight, activeProgress);
        this.layoutCache.put(entry, new LayoutCache(sourceTitle, sourceMessage, activeProgress, layout));
        return layout;
    }

    private void renderEntry(NotificationEntry entry, CardLayout layout, float y, float designWidth) {
        float alpha = this.alpha(entry);
        if (alpha <= 0.01F) return;
        float baseX = HudPosition.edgeX(this.positionX.getValue(), designWidth, layout.width, this.offsetX.getValue());
        float slideDistance = layout.width + RIGHT_MARGIN;
        float slideDirection = this.positionX.getValue() == 0 ? -1.0F : 1.0F;
        float slide = entry.isExiting()
                ? this.motion(entry) * slideDirection * slideDistance
                : (1.0F - this.motion(entry)) * slideDirection * slideDistance;
        float x = baseX + slide;
        int color = withAlpha(entry.getType().color, Math.round(255.0F * alpha));
        int white = withAlpha(0xFFFFFFFF, Math.round(255.0F * alpha));
        // Keep the Figma card tint at 50% opacity so UiRenderer's shared
        // backdrop texture remains visible through the notification.
        int background = withAlpha(0xFF1A1A24, Math.round(CARD_BACKGROUND_ALPHA * alpha));

        Myau.uiRenderer.shadow(x, y, layout.width, layout.height, 9.0F,
                0.0F, 3.0F, 10.0F, 0.0F, withAlpha(0xFF000000, Math.round(99.0F * alpha)));
        Myau.uiRenderer.backdrop(x, y, layout.width, layout.height, 9.0F, background);
        Myau.uiRenderer.gradientRoundedRect(x + layout.width - 5.0F, y + 4.0F, 5.0F,
                Math.max(1.0F, layout.height - 8.0F), 2.0F, color, darken(color));

        UiTexture icon = this.asset("notifications/Icons/Icon=" + entry.getType().iconName + ".svg");
        if (icon != null) {
            int iconColor = entry.getType() == NotificationType.ANALYSIS ? color : white;
            Myau.uiRenderer.imageContained(icon, x + 16.0F, y + (layout.height - ICON_SIZE) * 0.5F,
                    ICON_SIZE, ICON_SIZE, iconColor);
        }

        UiFont titleFont = Myau.uiRenderer.fonts().snPro(30.0F, UiFonts.BOLD);
        UiFont bodyFont = Myau.uiRenderer.fonts().snPro(24.0F, UiFonts.REGULAR);
        float textY = y + TOP_PADDING;
        if (!layout.title.isEmpty()) {
            titleFont.draw(layout.title, x + TEXT_X, textY, color, false);
            textY += titleFont.height() + (layout.body.isEmpty() ? 0.0F : TEXT_GAP);
        }
        for (String line : layout.body) {
            bodyFont.draw(line, x + TEXT_X, textY, white, false);
            textY += bodyFont.height();
        }

        if (layout.progress) {
            float progress = entry.getProgress();
            ProgressbarRenderer.renderNotification(Myau.uiRenderer, x, y + layout.height + PROGRESS_GAP,
                    layout.width, progress, Math.round(255.0F * alpha));
            this.renderThrobber(x + layout.width - 38.0F, y + 8.0F, alpha);
        }
    }

    private void renderThrobber(float centerX, float centerY, float alpha) {
        UiTexture track = this.asset("notifications/throbber/Track.svg");
        UiTexture filled = this.asset("notifications/throbber/Filled Track.svg");
        if (track == null || filled == null) return;
        Myau.uiRenderer.imageContained(track, centerX - 17.5F, centerY, 35.0F, 35.0F,
                withAlpha(0xFFFFFFFF, Math.round(255.0F * alpha)));
        GlStateManager.pushMatrix();
        GL11.glTranslatef(centerX, centerY + 17.5F, 0.0F);
        GL11.glRotatef((System.currentTimeMillis() % 2000L) * 0.18F, 0.0F, 0.0F, 1.0F);
        Myau.uiRenderer.imageContained(filled, -12.5F, -17.5F, 25.0F, 35.0F,
                withAlpha(0xFFFFFFFF, Math.round(255.0F * alpha)));
        GlStateManager.popMatrix();
    }

    private UiTexture asset(String path) {
        return Myau.uiRenderer.resource(path);
    }

    private float alpha(NotificationEntry entry) {
        if (entry.isExiting()) return 1.0F - smooth(Math.min(1.0F,
                entry.getExitAge() / (float) NotificationManager.EXIT_DURATION));
        return smooth(Math.min(1.0F, entry.getAge() / (float) NotificationManager.ENTER_DURATION));
    }

    private float motion(NotificationEntry entry) {
        if (entry.isExiting()) return Math.min(1.0F,
                smooth(entry.getExitAge() / (float) NotificationManager.EXIT_DURATION));
        return smooth(Math.min(1.0F, entry.getAge() / (float) NotificationManager.ENTER_DURATION));
    }

    private String ellipsize(String value, UiFont font, float width) {
        if (value == null) return "";
        if (font.width(value) <= width) return value;
        String suffix = "...";
        String result = value;
        while (result.length() > 0 && font.width(result + suffix) > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result.trim() + suffix;
    }

    private List<String> wrap(String value, UiFont font, float width) {
        List<String> lines = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return lines;
        String current = "";
        for (String word : value.replace('\n', ' ').trim().split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.width(candidate) > width) {
                lines.add(current);
                current = word;
            } else {
                current = candidate;
            }
        }
        if (!current.isEmpty()) lines.add(current);
        if (lines.size() <= 2) return lines;
        String second = lines.get(1);
        for (int i = 2; i < lines.size(); i++) second += " " + lines.get(i);
        lines.set(1, this.ellipsize(second, font, width));
        return lines.subList(0, 2);
    }

    private static float smooth(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int darken(int color) {
        int r = ((color >> 16) & 255) / 2;
        int g = ((color >> 8) & 255) / 2;
        int b = (color & 255) / 2;
        return (color & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static final class VerticalAnimation {
        private final float fromY;
        private final float targetY;
        private final long startedNanos;

        private VerticalAnimation(float fromY, float targetY, long startedNanos) {
            this.fromY = fromY;
            this.targetY = targetY;
            this.startedNanos = startedNanos;
        }

        private float value(long now) {
            float progress = Math.min(1.0F,
                    Math.max(0.0F, (now - this.startedNanos) / (float) STACK_REFLOW_DURATION_NANOS));
            return this.fromY + (this.targetY - this.fromY) * smooth(progress);
        }
    }

    private static final class CardLayout {
        private final String title;
        private final List<String> body;
        private final float width;
        private final float height;
        private final float totalHeight;
        private final boolean progress;

        private CardLayout(String title, List<String> body, float width, float height,
                           float totalHeight, boolean progress) {
            this.title = title;
            this.body = body;
            this.width = width;
            this.height = height;
            this.totalHeight = totalHeight;
            this.progress = progress;
        }
    }

    private static final class LayoutCache {
        private final String title;
        private final String message;
        private final boolean progress;
        private final CardLayout layout;

        private LayoutCache(String title, String message, boolean progress, CardLayout layout) {
            this.title = title;
            this.message = message;
            this.progress = progress;
            this.layout = layout;
        }

        private boolean matches(String title, String message, boolean progress) {
            return this.progress == progress && Objects.equals(this.title, title) && Objects.equals(this.message, message);
        }
    }
}

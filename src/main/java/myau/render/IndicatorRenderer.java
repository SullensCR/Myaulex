package myau.render;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.events.LoadWorldEvent;
import myau.events.Render2DEvent;
import myau.module.modules.Blink;
import myau.module.modules.KillAura;
import myau.module.modules.LagRange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Random;

/** Client-owned, non-module crosshair ring for Aura and its lag sources. */
public final class IndicatorRenderer {
    private static final float RADIUS = 10.0F;
    private static final float LINE_WIDTH = 1.0F;
    private static final float ANIMATION_SPEED = 12.0F;

    private final Random random = new Random();
    private Color startColor;
    private Color endColor;
    private float visibility;
    private float displayedSweep = 1.0F;
    private IndicatorState.Source displayedSource = IndicatorState.Source.HIDDEN;
    private long lastFrameNanos = System.nanoTime();

    public IndicatorRenderer() {
        rerollGradient();
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        rerollGradient();
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (Myau.clientSettings == null || Myau.moduleManager == null || Myau.blinkManager == null) return;

        KillAura aura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        Blink blink = (Blink) Myau.moduleManager.modules.get(Blink.class);
        LagRange lagRange = (LagRange) Myau.moduleManager.modules.get(LagRange.class);
        boolean blinkActive = blink != null && blink.isEnabled()
                && Myau.blinkManager.getBlinkingModule() == BlinkModules.BLINK;
        boolean blinkPulse = blinkActive && blink.mode.getValue() == 1;
        boolean lagRangeActive = lagRange != null && lagRange.isEnabled() && lagRange.isLagging();
        IndicatorState.Frame state = IndicatorState.resolve(
                Myau.clientSettings.isIndicatorEnabled(), aura != null && aura.isEnabled(),
                blinkActive, blinkPulse, Myau.blinkManager.getBlinkStartedAtMillis(),
                lagRangeActive, lagRange == null ? 0 : lagRange.getActiveLagDelayMillis(),
                lagRange == null ? 0L : lagRange.getActiveLagStartedAtMillis(), System.currentTimeMillis());

        float factor = smoothFactor(frameDelta());
        float targetVisibility = state.getSource() == IndicatorState.Source.HIDDEN ? 0.0F : 1.0F;
        visibility += (targetVisibility - visibility) * factor;
        if (state.getSource() != displayedSource
                && state.getSource() != IndicatorState.Source.AURA
                && state.getSource() != IndicatorState.Source.HIDDEN) {
            displayedSweep = state.getSweep();
        }
        displayedSweep += (state.getSweep() - displayedSweep) * factor;
        displayedSource = state.getSource();
        if (visibility <= 0.002F || mc.theWorld == null || mc.thePlayer == null) return;

        ScaledResolution resolution = new ScaledResolution(mc);
        float scale = 0.72F + 0.28F * visibility;
        drawArc(resolution.getScaledWidth() / 2.0F, resolution.getScaledHeight() / 2.0F,
                clamp(displayedSweep), scale, visibility);
    }

    private void rerollGradient() {
        startColor = IndicatorState.randomVividColor(random);
        endColor = IndicatorState.randomVividColor(random);
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float delta = Math.max(0.0F, Math.min(0.1F, (now - lastFrameNanos) / 1_000_000_000.0F));
        lastFrameNanos = now;
        return delta;
    }

    private static float smoothFactor(float delta) {
        return 1.0F - (float) Math.exp(-ANIMATION_SPEED * delta);
    }

    private void drawArc(float x, float y, float sweep, float scale, float alpha) {
        if (sweep <= 0.001F) return;
        int segments = Math.max(10, (int) Math.ceil(96.0F * sweep));
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(LINE_WIDTH);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= segments; i++) {
            float position = (float) i / (float) segments;
            Color color = blend(startColor, endColor, position);
            GL11.glColor4f(color.getRed() / 255.0F, color.getGreen() / 255.0F,
                    color.getBlue() / 255.0F, alpha);
            double angle = Math.toRadians(-90.0D + 360.0D * sweep * position);
            GL11.glVertex2d(Math.cos(angle) * RADIUS, Math.sin(angle) * RADIUS);
        }
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static Color blend(Color first, Color second, float progress) {
        float t = clamp(progress);
        return new Color((int) (first.getRed() + (second.getRed() - first.getRed()) * t),
                (int) (first.getGreen() + (second.getGreen() - first.getGreen()) * t),
                (int) (first.getBlue() + (second.getBlue() - first.getBlue()) * t));
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}

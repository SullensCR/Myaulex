package myau.module.modules;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TargetHudStateTest {
    @Test
    public void usesAuraBeforeManualAndManualAlwaysExpands() {
        assertEquals(TargetHudState.Source.AURA, TargetHudState.resolveSource(true, true));
        assertEquals(TargetHudState.Source.MANUAL, TargetHudState.resolveSource(false, true));
        assertEquals(TargetHudState.Source.NONE, TargetHudState.resolveSource(false, false));
        assertEquals(TargetHudState.Variant.EXPANDED,
                TargetHudState.resolveVariant(true, 99.0, 3.0F, 3.0F));
    }

    @Test
    public void usesAutoBlockForVisibilityAndSwingPlusHalfForExpansion() {
        assertEquals(TargetHudState.Variant.COLLAPSED,
                TargetHudState.resolveVariant(false, 6.0, 6.0F, 3.5F));
        assertEquals(TargetHudState.Variant.EXPANDED,
                TargetHudState.resolveVariant(false, 4.0, 6.0F, 3.5F));
        assertEquals(TargetHudState.Variant.HIDDEN,
                TargetHudState.resolveVariant(false, 6.01, 6.0F, 3.5F));
    }

    @Test
    public void smoothAnimationClampsFrameTimeAndConverges() {
        assertEquals(0.001F, TargetHudState.clampDeltaSeconds(0.0F), 0.0F);
        assertEquals(0.1F, TargetHudState.clampDeltaSeconds(3.0F), 0.0F);
        float expansion = 0.0F;
        for (int i = 0; i < 120; i++) {
            expansion = TargetHudState.animate(1.0F, expansion, 1.0F / 60.0F);
        }
        assertTrue(expansion > 0.99F);
        assertTrue(TargetHudState.height(expansion) > TargetHudState.COLLAPSED_HEIGHT);
        assertTrue(TargetHudState.height(expansion) <= TargetHudState.EXPANDED_HEIGHT);
    }

    @Test
    public void componentWidthGrowsWithTheVisibleTargetName() {
        float shortName = TargetHudState.componentWidth(20.0F, 35.0F);
        float longName = TargetHudState.componentWidth(20.0F, 180.0F);
        assertTrue(shortName >= TargetHudState.MIN_COMPONENT_WIDTH);
        assertTrue(longName > shortName);
    }
}

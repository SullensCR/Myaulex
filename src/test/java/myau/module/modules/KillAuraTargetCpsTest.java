package myau.module.modules;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillAuraTargetCpsTest {
    @Test
    public void exposesIndependentTargetCpsControls() {
        KillAura aura = new KillAura();

        assertEquals("target-cps", aura.targetCPS.getName());
        assertEquals(Integer.valueOf(10), aura.targetCPS.getValue());
        assertEquals(Integer.valueOf(1), aura.targetCPS.getMinimum());
        assertEquals(Integer.valueOf(20), aura.targetCPS.getMaximum());
        assertEquals("Target CPS", aura.targetCPS.getDisplayName());

        assertEquals("auto-block-target-cps", aura.autoBlockTargetCPS.getName());
        assertEquals(Integer.valueOf(10), aura.autoBlockTargetCPS.getValue());
        assertEquals(Integer.valueOf(1), aura.autoBlockTargetCPS.getMinimum());
        assertEquals(Integer.valueOf(10), aura.autoBlockTargetCPS.getMaximum());
        assertEquals("Target CPS", aura.autoBlockTargetCPS.getDisplayName());
    }

    @Test
    public void autoblockTargetCpsIsCappedAtTenWhenEditedAsText() {
        KillAura aura = new KillAura();
        aura.autoBlockTargetCPS.setValue(20);
        aura.verifyValue(aura.autoBlockTargetCPS.getName());

        assertEquals(Integer.valueOf(10), aura.autoBlockTargetCPS.getValue());
    }

    @Test
    public void externallyHeldBlockIsReleasedBeforeNonVanillaAuraAttack() {
        assertEquals(true, KillAura.shouldReleaseHeldBlock(true, false, 0));
        assertEquals(true, KillAura.shouldReleaseHeldBlock(true, false, 3));
        assertEquals(false, KillAura.shouldReleaseHeldBlock(true, true, 3));
        assertEquals(false, KillAura.shouldReleaseHeldBlock(true, false, 1));
        assertEquals(false, KillAura.shouldReleaseHeldBlock(false, false, 3));
    }

    @Test
    public void exposesExpoModesAndConditionalApsControl() {
        KillAura aura = new KillAura();

        assertEquals(
                Arrays.asList("NONE", "VANILLA", "SPOOF", "HYPIXEL", "BLINK", "INTERACT", "SWAP", "LEGIT", "FAKE",
                        "LAG_LEGIT", "LAG", "LAG_NEW"),
                Arrays.asList(aura.autoBlock.getModes())
        );
        assertEquals("expo-aps", aura.expoAutoBlockAPS.getName());
        assertEquals("Expo APS", aura.expoAutoBlockAPS.getDisplayName());
        assertEquals(Arrays.asList("3APS", "5APS", "7APS", "10APS", "14APS"), Arrays.asList(aura.expoAutoBlockAPS.getModes()));
        assertEquals(Integer.valueOf(0), aura.expoAutoBlockAPS.getValue());
        assertFalse(aura.expoAutoBlockAPS.isVisible());
        assertTrue(aura.autoBlockTargetCPS.isVisible());

        aura.autoBlock.setValue(ExpoAutoBlockState.LAG_NEW);
        assertTrue(aura.expoAutoBlockAPS.isVisible());
        assertFalse(aura.autoBlockTargetCPS.isVisible());
    }
}

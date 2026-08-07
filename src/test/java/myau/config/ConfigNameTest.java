package myau.config;

import org.junit.Test;

import static org.junit.Assert.*;

public class ConfigNameTest {
    @Test
    public void acceptsPortableProfileNames() {
        assertTrue(Config.isValidProfileName("legit"));
        assertTrue(Config.isValidProfileName("bedwars_1"));
        assertTrue(Config.isValidProfileName("practice-server"));
    }

    @Test
    public void rejectsReservedAndUnsafeNames() {
        assertFalse(Config.isValidProfileName(""));
        assertFalse(Config.isValidProfileName(" Latest "));
        assertFalse(Config.isValidProfileName("CLIENT"));
        assertFalse(Config.isValidProfileName("default"));
        assertFalse(Config.isValidProfileName("../escape"));
        assertFalse(Config.isValidProfileName("folder/name"));
        assertFalse(Config.isValidProfileName("folder\\name"));
    }
}

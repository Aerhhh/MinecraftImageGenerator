package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackIdTest {

    @Test
    void parseSplitsNamespaceAndName() {
        PackId id = PackId.parse("hypixel:skyblock");
        assertEquals("hypixel", id.namespace());
        assertEquals("skyblock", id.name());
        assertEquals("hypixel:skyblock", id.toString());
    }

    @Test
    void vanillaConstantIsMinecraftMinecraft() {
        assertEquals(PackId.parse("minecraft:minecraft"), PackId.VANILLA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "noseparator", "a:b:c", "UPPER:case", "spa ce:x", "hypixel:", ":skyblock", "../x:y"})
    void parseRejectsMalformedIds(String input) {
        assertThrows(IllegalArgumentException.class, () -> PackId.parse(input));
    }

    @Test
    void parseRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> PackId.parse(null));
    }

    /** The single pack-activation rule every generator and parser gates on. */
    @Test
    void isActiveTreatsNullAndVanillaAsNoPack() {
        assertFalse(PackId.isActive(null));
        assertFalse(PackId.isActive(PackId.VANILLA));
        assertFalse(PackId.isActive(PackId.parse("minecraft:minecraft")));
        assertTrue(PackId.isActive(PackId.parse("hypixel:skyblock")));
    }
}

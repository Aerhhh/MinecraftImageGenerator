package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}

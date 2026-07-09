package net.aerh.imagegenerator.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RarityTest {

    @Test
    void byNameMatchesTheNameField() {
        assertEquals("legendary", Rarity.byName("legendary").getName());
        assertEquals("very_special", Rarity.byName("very_special").getName(),
            "autocomplete offers name values, so underscored names must resolve");
        assertEquals("none", Rarity.byName("none").getName());
    }

    @Test
    void byNameIsCaseInsensitive() {
        assertEquals("legendary", Rarity.byName("LEGENDARY").getName());
        assertEquals("very_special", Rarity.byName("VERY_SPECIAL").getName());
    }

    @Test
    void byNameStillMatchesDisplayForLegacyCallers() {
        assertEquals("very_special", Rarity.byName("VERY SPECIAL").getName());
        assertEquals("very_special", Rarity.byName("very special").getName());
    }

    @Test
    void byNameReturnsNullForUnknownBlankOrNull() {
        assertNull(Rarity.byName("nope"));
        assertNull(Rarity.byName(""));
        assertNull(Rarity.byName(null));
    }

    @Test
    void byNameReturnsTheRegistryInstance() {
        // parseLore compares against Rarity.byName("NONE") by reference; lookups must be stable.
        assertSame(Rarity.byName("none"), Rarity.byName("NONE"));
    }
}

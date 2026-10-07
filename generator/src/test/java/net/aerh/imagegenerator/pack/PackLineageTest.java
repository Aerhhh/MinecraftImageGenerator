package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackLineageTest {

    private static final PackId SKYBLOCK = PackId.parse("hypixel:skyblock");
    private static final PackId ALPHA = PackId.parse("hypixel:alpha");
    private static final PackId OTHER = PackId.parse("other:pack");

    @Test
    void ofHasNoVariant() {
        PackLineage lineage = PackLineage.of(SKYBLOCK);

        assertEquals(SKYBLOCK, lineage.id());
        assertNull(lineage.variantOf());
        assertEquals(List.of(SKYBLOCK), lineage.lookupOrder());
    }

    @Test
    void lookupOrderIsOwnIdThenVariant() {
        PackLineage lineage = new PackLineage(ALPHA, SKYBLOCK);

        assertEquals(List.of(ALPHA, SKYBLOCK), lineage.lookupOrder());
    }

    @Test
    void nullIdIsRejected() {
        assertThrows(NullPointerException.class, () -> new PackLineage(null, SKYBLOCK));
        assertThrows(NullPointerException.class, () -> PackLineage.of(null));
    }

    @Test
    void packCannotBeAVariantOfItself() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLineage(ALPHA, PackId.parse("hypixel:alpha")));

        assertTrue(exception.getMessage().contains("hypixel:alpha"), exception.getMessage());
    }

    @Test
    void packCannotBeAVariantOfVanilla() {
        assertThrows(IllegalArgumentException.class, () -> new PackLineage(ALPHA, PackId.VANILLA));
    }

    @Test
    void findOverridePrefersTheOwnIdKey() {
        Map<String, String> overrides = Map.of("hypixel:alpha", "A", "hypixel:skyblock", "S");

        assertEquals("A", new PackLineage(ALPHA, SKYBLOCK).findOverride(overrides));
    }

    @Test
    void findOverrideFallsBackToTheVariantKey() {
        Map<String, String> overrides = Map.of("hypixel:skyblock", "S");

        assertEquals("S", new PackLineage(ALPHA, SKYBLOCK).findOverride(overrides));
    }

    @Test
    void findOverrideWithoutVariantOnlyMatchesTheOwnId() {
        Map<String, String> overrides = Map.of("hypixel:skyblock", "S");

        assertNull(PackLineage.of(ALPHA).findOverride(overrides));
        assertEquals("S", PackLineage.of(SKYBLOCK).findOverride(overrides));
    }

    @Test
    void findOverrideIsNullWhenNoKeyMatches() {
        assertNull(new PackLineage(OTHER, ALPHA).findOverride(Map.of("hypixel:skyblock", "S")));
    }

    @Test
    void findOverrideIsNullForMissingOrEmptyOverrides() {
        PackLineage lineage = new PackLineage(ALPHA, SKYBLOCK);

        assertNull(lineage.findOverride(null));
        assertNull(lineage.findOverride(Map.of()));
    }

    @Test
    void findOverrideSkipsANullOwnValueAndUsesTheVariant() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("hypixel:alpha", null);
        overrides.put("hypixel:skyblock", "S");

        assertEquals("S", new PackLineage(ALPHA, SKYBLOCK).findOverride(overrides));
    }

    @Test
    void isAnyOfMatchesTheOwnIdOrTheVariant() {
        Set<String> ids = Set.of("hypixel:skyblock");

        assertTrue(PackLineage.of(SKYBLOCK).isAnyOf(ids));
        assertTrue(new PackLineage(ALPHA, SKYBLOCK).isAnyOf(ids));
        assertFalse(PackLineage.of(ALPHA).isAnyOf(ids));
        assertFalse(new PackLineage(OTHER, ALPHA).isAnyOf(ids));
    }
}

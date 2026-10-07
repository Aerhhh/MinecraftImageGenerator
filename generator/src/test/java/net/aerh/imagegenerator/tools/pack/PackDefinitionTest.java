package net.aerh.imagegenerator.tools.pack;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackDefinitionTest {

    @Test
    void ofFillsEmptyMaps() {
        PackDefinition definition = PackDefinition.of("hypixel:skyblock", "/app/packs/hypixel-skyblock.zip");

        assertEquals("hypixel:skyblock", definition.id());
        assertEquals("/app/packs/hypixel-skyblock.zip", definition.path());
        assertTrue(definition.tooltipStyles().isEmpty());
        assertTrue(definition.textColorRemap().isEmpty());
        assertNull(definition.variantOf());
    }

    @Test
    void themeOnlyConstructorHasNoVariant() {
        PackDefinition definition = new PackDefinition("a:b", "p", Map.of(), Map.of());

        assertNull(definition.variantOf());
    }

    @Test
    void variantOfIsKeptAsConfigured() {
        // Normalisation (trim, lowercase, blank means none) happens when the service registers the pack.
        PackDefinition definition = new PackDefinition("hypixel:alpha", "p", Map.of(), Map.of(), " Hypixel:SkyBlock ");

        assertEquals(" Hypixel:SkyBlock ", definition.variantOf());
    }

    @Test
    void nullMapsBecomeEmptyMaps() {
        PackDefinition definition = new PackDefinition("a:b", "p", null, null);

        assertTrue(definition.tooltipStyles().isEmpty());
        assertTrue(definition.textColorRemap().isEmpty());
    }

    @Test
    void mapsAreDefensivelyCopied() {
        Map<String, String> styles = new java.util.HashMap<>(Map.of("legendary", "x:legendary"));
        PackDefinition definition = new PackDefinition("a:b", "p", styles, Map.of());
        styles.put("epic", "x:epic");

        assertEquals(Map.of("legendary", "x:legendary"), definition.tooltipStyles());
        assertThrows(UnsupportedOperationException.class, () -> definition.tooltipStyles().put("k", "v"));
    }

    @Test
    void nullValuesAreKeptAndTheMapStaysUnmodifiable() {
        Map<String, String> styles = new LinkedHashMap<>();
        styles.put("legendary", null);
        styles.put("epic", "x:epic");
        Map<String, String> remap = new HashMap<>();
        remap.put("#AA0000", null);

        PackDefinition definition = new PackDefinition("a:b", "p", styles, remap);

        assertTrue(definition.tooltipStyles().containsKey("legendary"));
        assertNull(definition.tooltipStyles().get("legendary"));
        assertEquals(List.of("legendary", "epic"), List.copyOf(definition.tooltipStyles().keySet()));
        assertTrue(definition.textColorRemap().containsKey("#AA0000"));
        assertThrows(UnsupportedOperationException.class, () -> definition.tooltipStyles().put("k", "v"));
        assertThrows(UnsupportedOperationException.class, () -> definition.textColorRemap().put("k", "v"));
    }

    @Test
    void registrationConfigOfKeepsOrderAndDefault() {
        PackDefinition first = PackDefinition.of("a:one", "one");
        PackDefinition second = PackDefinition.of("a:two", "two");

        PackRegistrationConfig config = PackRegistrationConfig.of("a:two", first, second);

        assertEquals(List.of(first, second), config.packs());
        assertEquals("a:two", config.defaultPack());
    }

    @Test
    void registrationConfigNullPacksBecomeEmptyList() {
        PackRegistrationConfig config = new PackRegistrationConfig(null, null);

        assertTrue(config.packs().isEmpty());
        assertNull(config.defaultPack());
    }
}

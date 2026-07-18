package net.aerh.imagegenerator.impl.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repeatable {@code template_repeat} rows: a marker object inside a JSON array expands, in place,
 * into one substituted copy per row (marker key stripped, row values layered over globals), or
 * vanishes when its section has no rows. Duplicate section names, nested repeats and misplaced
 * markers are parse-time rejections.
 */
class ContentTemplateRepeatTest {

    /** A template whose lore is one fixed header line then a repeat marker for section "rows". */
    private static ContentTemplate loreTemplate() {
        return ContentTemplate.parse("""
            {"schema_version": 1,
             "placeholders": {"label": {}, "value": {}, "tier": {"required": false, "default": "common"}},
             "content": {"components": {"minecraft:lore": [
               {"text": "Header"},
               {"template_repeat": "rows", "text": "{label}: {value}", "color": "{tier}"}
             ]}}}
            """);
    }

    private static JsonArray lore(JsonObject filled) {
        return filled.getAsJsonObject("components").getAsJsonArray("minecraft:lore");
    }

    @Test
    void expandsOneCopyPerRowInOrder() {
        JsonObject filled = loreTemplate().fill(Map.of("tier", "rare"), Map.of("rows", List.of(
            Map.of("label", "Damage", "value", "+10"),
            Map.of("label", "Speed", "value", "+5"))));

        JsonArray lore = lore(filled);
        assertEquals(3, lore.size(), "header plus two expanded rows");
        assertEquals("Header", lore.get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("Damage: +10", lore.get(1).getAsJsonObject().get("text").getAsString());
        assertEquals("Speed: +5", lore.get(2).getAsJsonObject().get("text").getAsString());
    }

    @Test
    void markerKeyIsStrippedFromEveryCopy() {
        JsonObject filled = loreTemplate().fill(Map.of("tier", "rare"),
            Map.of("rows", List.of(Map.of("label", "Damage", "value", "+10"))));
        JsonObject row = lore(filled).get(1).getAsJsonObject();
        assertFalse(row.has(ContentTemplate.REPEAT_KEY), "the template_repeat marker must not survive into copies");
    }

    @Test
    void rowValuesOverrideGlobals() {
        // "tier" has a global value; the row overrides it for its own copy.
        JsonObject filled = loreTemplate().fill(Map.of("tier", "common"), Map.of("rows", List.of(
            Map.of("label", "A", "value", "1"),
            Map.of("label", "B", "value", "2", "tier", "legendary"))));

        assertEquals("common", lore(filled).get(1).getAsJsonObject().get("color").getAsString());
        assertEquals("legendary", lore(filled).get(2).getAsJsonObject().get("color").getAsString());
    }

    @Test
    void emptyRowListRemovesTheMarkerNode() {
        JsonObject filled = loreTemplate().fill(Map.of("tier", "rare"), Map.of("rows", List.of()));
        JsonArray lore = lore(filled);
        assertEquals(1, lore.size(), "only the header survives when the section has no rows");
        assertEquals("Header", lore.get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    void absentSectionRemovesTheMarkerNode() {
        // fill without any rows map at all: the section resolves to no rows.
        JsonObject filled = loreTemplate().fill(Map.of("tier", "rare"));
        assertEquals(1, lore(filled).size(), "an absent section is removed like an empty one");
    }

    @Test
    void requiredRowPlaceholderMissingInARowIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> loreTemplate().fill(Map.of("tier", "rare"),
                Map.of("rows", List.of(Map.of("label", "Damage")))));
        assertTrue(thrown.getMessage().contains("value"),
            "a row missing a required placeholder is rejected naming it, got: " + thrown.getMessage());
    }

    @Test
    void rowSectionNamesReportsDiscoveredSections() {
        assertEquals(Set.of("rows"), loreTemplate().rowSectionNames());
    }

    @Test
    void duplicateSectionNamesAreRejectedAtParse() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("""
                {"schema_version": 1,
                 "content": {"components": {"minecraft:lore": [
                   {"template_repeat": "rows", "text": "a"},
                   {"template_repeat": "rows", "text": "b"}
                 ]}}}
                """));
        assertTrue(thrown.getMessage().contains("rows"),
            "duplicate section rejection must name the section, got: " + thrown.getMessage());
    }

    @Test
    void nestedRepeatIsRejectedAtParse() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("""
                {"schema_version": 1,
                 "content": {"components": {"minecraft:lore": [
                   {"template_repeat": "outer", "extra": [
                     {"template_repeat": "inner", "text": "x"}
                   ]}
                 ]}}}
                """));
        assertTrue(thrown.getMessage().toLowerCase().contains("nested"),
            "nested repeat must be rejected, got: " + thrown.getMessage());
    }

    @Test
    void markerNotDirectlyInsideAnArrayIsRejected() {
        // A template_repeat on an object that is a member value, not an array element, is misplaced.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("""
                {"schema_version": 1,
                 "content": {"components": {"minecraft:custom_name":
                   {"template_repeat": "rows", "text": "x"}}}}
                """));
        assertTrue(thrown.getMessage().contains("template_repeat"),
            "misplaced marker must be rejected, got: " + thrown.getMessage());
    }

    @Test
    void unknownRowSectionKeyIsRejected() {
        // A rows key that names no template_repeat section is a caller mistake (a mistyped section
        // name) whose rows would silently vanish, so it is rejected naming the offending section.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> loreTemplate().fill(Map.of("tier", "rare"),
                Map.of("enteries", List.of(Map.of("label", "A", "value", "1")))));
        assertTrue(thrown.getMessage().contains("enteries"),
            "unknown section rejection must name the bad key, got: " + thrown.getMessage());
    }

    @Test
    void nonStringSectionNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContentTemplate.parse("""
            {"schema_version": 1,
             "content": {"components": {"minecraft:lore": [
               {"template_repeat": 7, "text": "x"}
             ]}}}
            """));
    }
}

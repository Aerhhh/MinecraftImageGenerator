package net.aerh.imagegenerator.impl.template;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placeholder substitution: token replacement, brace escaping, defaults for non-required
 * placeholders, verbatim (non-recursive) insertion, and the two named rejections - a required
 * placeholder left unfilled at fill time, and an undeclared token caught at parse time.
 */
class ContentTemplateSubstitutionTest {

    private static ContentTemplate parse(String placeholders, String contentText) {
        return ContentTemplate.parse("""
            {"schema_version": 1,
             "placeholders": %s,
             "content": {"components": {"minecraft:custom_name": {"text": "%s"}}}}
            """.formatted(placeholders, contentText));
    }

    private static String nameText(JsonObject filled) {
        return filled.getAsJsonObject("components")
            .getAsJsonObject("minecraft:custom_name")
            .get("text").getAsString();
    }

    @Test
    void replacesMultipleTokensInOneString() {
        ContentTemplate template = parse("""
            {"who": {}, "what": {}}""", "{who} bought {what}");
        assertEquals("Aerh bought a gem",
            nameText(template.fill(Map.of("who", "Aerh", "what", "a gem"))));
    }

    @Test
    void doubledBracesEscapeToLiteralSingleBraces() {
        ContentTemplate template = parse("""
            {"x": {}}""", "{{literal}} then {x}");
        // {{ }} collapse to single braces; only the single-brace token substitutes.
        assertEquals("{literal} then value", nameText(template.fill(Map.of("x", "value"))));
    }

    @Test
    void nonRequiredPlaceholderUsesItsDefaultWhenUnfilled() {
        ContentTemplate template = parse("""
            {"suffix": {"required": false, "default": " (default)"}}""", "Name{suffix}");
        assertEquals("Name (default)", nameText(template.fill(Map.of())));
    }

    @Test
    void nonRequiredPlaceholderWithoutDefaultUsesEmptyString() {
        ContentTemplate template = parse("""
            {"suffix": {"required": false}}""", "Name{suffix}");
        assertEquals("Name", nameText(template.fill(Map.of())));
    }

    @Test
    void suppliedValueOverridesDefault() {
        ContentTemplate template = parse("""
            {"suffix": {"required": false, "default": " (default)"}}""", "Name{suffix}");
        assertEquals("Name!", nameText(template.fill(Map.of("suffix", "!"))));
    }

    @Test
    void insertedValuesAreVerbatimAndNotReSubstituted() {
        ContentTemplate template = parse("""
            {"x": {}}""", "{x}");
        // A value that itself looks like a token must appear verbatim, not trigger another pass.
        assertEquals("{y} stays", nameText(template.fill(Map.of("x", "{y} stays"))));
    }

    @Test
    void requiredPlaceholderMissingIsRejectedNamingIt() {
        ContentTemplate template = parse("""
            {"coins": {}}""", "You have {coins}");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> template.fill(Map.of()));
        assertTrue(thrown.getMessage().contains("coins"),
            "rejection must name the missing placeholder, got: " + thrown.getMessage());
    }

    @Test
    void requiredIsTheDefaultPolicyWhenSpecOmitsIt() {
        // An empty spec object means required=true, so an unfilled value is rejected.
        ContentTemplate template = parse("""
            {"coins": {}}""", "{coins}");
        assertThrows(IllegalArgumentException.class, () -> template.fill(Map.of()));
    }

    @Test
    void undeclaredTokenIsRejectedAtParseNamingIt() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> parse("{}", "Hello {stranger}"));
        assertTrue(thrown.getMessage().contains("stranger"),
            "parse rejection must name the undeclared token, got: " + thrown.getMessage());
    }

    @Test
    void tokensAreSubstitutedInAnyStringValueNotJustNamedTextNodes() {
        // The engine walks every string value, including a color field, not only "text".
        ContentTemplate template = ContentTemplate.parse("""
            {"schema_version": 1,
             "placeholders": {"hue": {}},
             "content": {"components": {"minecraft:custom_name": {"text": "hi", "color": "{hue}"}}}}
            """);
        JsonObject filled = template.fill(Map.of("hue", "#FF00FF"));
        assertEquals("#FF00FF", filled.getAsJsonObject("components")
            .getAsJsonObject("minecraft:custom_name").get("color").getAsString());
    }

    @Test
    void placeholderNamesReportsDeclaredNames() {
        ContentTemplate template = parse("""
            {"a": {}, "b": {"required": false}}""", "{a}{b}");
        assertEquals(Set.of("a", "b"), template.placeholderNames());
    }
}

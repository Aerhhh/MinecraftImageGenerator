package net.aerh.imagegenerator.impl.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Strict parse policy: the template rejects structural mistakes loudly (blank or malformed JSON,
 * trailing content, duplicate keys, an unsupported {@code schema_version}, a missing or wrong-typed
 * {@code content} or placeholder spec) while tolerating unknown top-level and placeholder-spec keys
 * with a warning. A silently mis-parsed template would render something quietly wrong.
 */
class ContentTemplateParseTest {

    private static final String CONTENT = "\"content\": {\"components\": {}}";

    @Test
    void blankJsonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContentTemplate.parse("   "));
    }

    @Test
    void nonObjectRootIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContentTemplate.parse("[1, 2, 3]"));
    }

    @Test
    void contentAfterTheDocumentIsRejected() {
        // The reader parses one document only; anything after it (valid JSON or not) is rejected.
        assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1, " + CONTENT + "} 5"));
        assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1, " + CONTENT + "} garbage"));
    }

    @Test
    void malformedJsonIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1, \"content\": {"));
        assertTrue(thrown.getMessage().toLowerCase().contains("malformed"),
            "message must flag malformed JSON, got: " + thrown.getMessage());
    }

    @Test
    void duplicateKeysAreRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1, \"schema_version\": 1, " + CONTENT + "}"));
        assertTrue(thrown.getMessage().toLowerCase().contains("duplicate"),
            "message must flag the duplicate key, got: " + thrown.getMessage());
    }

    @Test
    void duplicateKeyDeepInContentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContentTemplate.parse("""
            {"schema_version": 1,
             "content": {"components": {"minecraft:custom_name": {"text": "a", "text": "b"}}}}
            """));
    }

    @Test
    void missingSchemaVersionIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{" + CONTENT + "}"));
        assertTrue(thrown.getMessage().contains("schema_version"),
            "message must name schema_version, got: " + thrown.getMessage());
    }

    @Test
    void nonIntegerSchemaVersionIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": \"1\", " + CONTENT + "}"));
    }

    @Test
    void fractionalSchemaVersionIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1.5, " + CONTENT + "}"));
    }

    @Test
    void fractionalNotationWholeSchemaVersionIsRejected() {
        // 1.0 is 1 in value but written with a fractional part; `must be an integer` is about the
        // notation, so it is rejected just like 1.5, keeping the schema_version contract honest.
        assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1.0, " + CONTENT + "}"));
    }

    @Test
    void schemaVersionAboveOneIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 2, " + CONTENT + "}"));
        assertTrue(thrown.getMessage().contains("schema_version"),
            "message must name schema_version, got: " + thrown.getMessage());
    }

    @Test
    void missingContentIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1}"));
        assertTrue(thrown.getMessage().contains("content"),
            "message must name content, got: " + thrown.getMessage());
    }

    @Test
    void nonObjectContentIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1, \"content\": \"nope\"}"));
    }

    @Test
    void nonObjectPlaceholdersIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1, \"placeholders\": [], " + CONTENT + "}"));
    }

    @Test
    void nonObjectPlaceholderSpecIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1, \"placeholders\": {\"a\": \"x\"}, " + CONTENT + "}"));
    }

    @Test
    void nonBooleanRequiredFlagIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1, \"placeholders\": {\"a\": {\"required\": \"yes\"}}, " + CONTENT + "}"));
    }

    @Test
    void nonStringDefaultIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentTemplate.parse("{\"schema_version\": 1, \"placeholders\": {\"a\": {\"default\": 5}}, " + CONTENT + "}"));
    }

    @Test
    void unknownTopLevelKeyIsToleratedWithAWarning() {
        // Unknown top-level keys are warned about and ignored, not rejected - a forward-compatible field.
        ContentTemplate template = assertDoesNotThrow(() -> ContentTemplate.parse(
            "{\"schema_version\": 1, \"note\": \"authored 2026\", " + CONTENT + "}"));
        assertTrue(template.placeholderNames().isEmpty());
    }

    @Test
    void unknownPlaceholderSpecKeyIsToleratedWithAWarning() {
        ContentTemplate template = assertDoesNotThrow(() -> ContentTemplate.parse("""
            {"schema_version": 1,
             "placeholders": {"a": {"required": false, "hint": "unused"}},
             "content": {"components": {"minecraft:custom_name": {"text": "{a}"}}}}
            """));
        assertEquals("", template.fill(Map.of()).getAsJsonObject("components")
            .getAsJsonObject("minecraft:custom_name").get("text").getAsString());
    }

    @Test
    void requiredPlaceholderWithADefaultParsesButIgnoresTheDefault() {
        // A required placeholder that also declares a default is a contradictory spec: the pairing
        // is tolerated (warned) but the default is dead, so filling without a value still rejects.
        ContentTemplate template = assertDoesNotThrow(() -> ContentTemplate.parse("""
            {"schema_version": 1,
             "placeholders": {"a": {"required": true, "default": "fallback"}},
             "content": {"components": {"minecraft:custom_name": {"text": "{a}"}}}}
            """));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> template.fill(Map.of()));
        assertTrue(thrown.getMessage().contains("a"),
            "the default must not stand in for the required value, got: " + thrown.getMessage());
    }

    @Test
    void malformedBraceSyntaxIsRejectedAtParse() {
        assertThrows(IllegalArgumentException.class, () -> ContentTemplate.parse("""
            {"schema_version": 1,
             "content": {"components": {"minecraft:custom_name": {"text": "oops }"}}}}
            """));
    }
}

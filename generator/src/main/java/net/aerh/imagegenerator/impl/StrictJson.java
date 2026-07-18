package net.aerh.imagegenerator.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Set;

/**
 * The strict JSON helper stack shared by the document parsers in this package - the menu recipe
 * format ({@link MinecraftContainerGenerator#fromRecipe}) and the declarative scene format. A
 * recursive descent over the reader instead of {@code JsonParser.parseReader}: Gson's tree model
 * silently keeps the LAST duplicate object key, which would let a duplicated field render
 * something silently wrong. Strict parsing means duplicates fail loudly, before any per-field
 * validation runs.
 *
 * <p>The {@code what} argument every method takes labels the document in its error messages
 * (e.g. {@code "menu recipe"}, {@code "scene"}), so one shared stack still names the offending
 * document without the callers duplicating the tokenizer.
 */
@Slf4j
public final class StrictJson {

    private StrictJson() {
    }

    /**
     * Parses a single strict JSON object document, rejecting duplicate keys anywhere and any
     * content after the top-level value.
     *
     * @param json the document text
     * @param what the document label used in error messages
     *
     * @return the parsed root object
     * @throws IllegalArgumentException on blank input, malformed JSON, trailing content,
     *                                  duplicate keys, or a non-object root
     */
    public static JsonObject parseObject(String json, String what) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(what + " JSON must not be blank");
        }
        JsonElement element;
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.STRICT);
            element = readElement(reader, what);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException(
                    what + " must be a single JSON document; found trailing content");
            }
        } catch (IOException | JsonParseException e) {
            throw new IllegalArgumentException("Malformed " + what + " JSON: " + e.getMessage(), e);
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(what + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    /**
     * Recursive descent over the reader that rejects duplicate object keys as it reads. Numbers
     * keep their exact literal via {@link BigDecimal} so downstream whole-number checks still
     * reject fractional values.
     *
     * @param reader the strict reader positioned at a value
     * @param what   the document label used in error messages
     *
     * @return the parsed element
     */
    public static JsonElement readElement(JsonReader reader, String what) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) {
                        throw new IllegalArgumentException(
                            "Duplicate key `" + name + "` in " + what + " JSON");
                    }
                    object.add(name, readElement(reader, what));
                }
                reader.endObject();
                return object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(readElement(reader, what));
                }
                reader.endArray();
                return array;
            }
            case STRING -> {
                return new JsonPrimitive(reader.nextString());
            }
            case NUMBER -> {
                return new JsonPrimitive(new BigDecimal(reader.nextString()));
            }
            case BOOLEAN -> {
                return new JsonPrimitive(reader.nextBoolean());
            }
            case NULL -> {
                reader.nextNull();
                return JsonNull.INSTANCE;
            }
            default -> throw new IllegalArgumentException("Unexpected token " + token + " in " + what + " JSON");
        }
    }

    /**
     * Hard rejection of any key outside {@code allowed} - the recipe format's policy, where an
     * unknown key is a transcription error.
     *
     * @throws IllegalArgumentException naming the first offending key
     */
    public static void requireOnlyKeys(JsonObject object, Set<String> allowed, String what) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(
                    "Unknown " + what + " key `" + key + "` (allowed: "
                        + String.join(", ", allowed.stream().sorted().toList()) + ")");
            }
        }
    }

    /**
     * Tolerant counterpart of {@link #requireOnlyKeys}: unknown keys are logged (naming each one)
     * and ignored rather than rejected - the scene format's policy, so a document authored against
     * a newer schema still renders on an older build.
     */
    public static void warnUnknownKeys(JsonObject object, Set<String> allowed, String what) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                log.warn("Ignoring unknown {} key `{}`", what, key);
            }
        }
    }

    /**
     * Reads a whole-number field, rejecting missing values, non-numbers and fractional values.
     *
     * @throws IllegalArgumentException naming the key when the value is absent or not an integer
     */
    public static int requireInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("`" + key + "` must be an integer");
        }
        double value = element.getAsDouble();
        int intValue = (int) value;
        if (intValue != value) {
            throw new IllegalArgumentException("`" + key + "` must be an integer, got: " + value);
        }
        return intValue;
    }

    /**
     * Reads a required string field.
     *
     * @param what the enclosing element label used in the error message
     *
     * @throws IllegalArgumentException naming the enclosing element and key when absent or not a
     *                                  string
     */
    public static String requireString(JsonObject object, String key, String what) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(what + " `" + key + "` must be a string");
        }
        return element.getAsString();
    }

    /**
     * Reads an optional boolean field: absent means {@code false}, present must be a JSON boolean.
     *
     * @param what the enclosing element label used in the error message
     *
     * @throws IllegalArgumentException naming the enclosing element and key when present but not a
     *                                  boolean
     */
    public static boolean requireBoolean(JsonObject object, String key, String what) {
        JsonElement element = object.get(key);
        if (element == null) {
            return false;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(what + " `" + key + "` must be a boolean");
        }
        return element.getAsBoolean();
    }
}

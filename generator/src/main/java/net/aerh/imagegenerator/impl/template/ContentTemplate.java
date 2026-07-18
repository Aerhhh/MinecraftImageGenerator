package net.aerh.imagegenerator.impl.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.impl.StrictJson;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A capture-driven content template: a real captured item-NBT component payload (the JSON shape
 * {@link net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator.Builder#parseNbtJson}
 * accepts) whose text nodes carry {@code {placeholder}} tokens and whose repeatable rows are
 * marked, with one generic substitution engine filling it. Per-server tooling stays DATA (the
 * captures), never code: authoring a new menu is transcribing a capture and marking its variable
 * text, not writing a generator.
 *
 * <p><b>Template document (schema_version 1):</b>
 * <ul>
 * <li>{@code schema_version} (required int): the format version. Versions above 1 are rejected;
 *     this build speaks v1 only.</li>
 * <li>{@code placeholders} (optional object): {@code name -> {required, default}}. {@code required}
 *     is a boolean (default {@code true}); {@code default} is an optional string. A non-required
 *     placeholder left unfilled uses its {@code default} (or the empty string); a required one left
 *     unfilled where it is used is a named rejection at fill time.</li>
 * <li>{@code content} (required object): the item NBT tree, verbatim from the capture except for
 *     its {@code {placeholder}} tokens and {@code template_repeat} markers.</li>
 * </ul>
 *
 * <p><b>Substitution:</b> every string VALUE in the content tree (text nodes and any other string
 * value) is scanned for {@code {name}} tokens, each replaced by its filled value; {@code {{} and
 * {@code }}} escape to literal single braces. Values are inserted verbatim - text pulled in from a
 * value is never itself re-scanned for tokens. Every token found in content must be declared under
 * {@code placeholders} or the template is rejected at parse time (a typo guard, naming the token),
 * so glyph runs (private-use codepoints) and negative-space kern runs, which carry no tokens, stay
 * byte-verbatim.
 *
 * <p><b>Repeatable rows:</b> any object that is a direct element of a JSON array may carry a
 * {@code "template_repeat": "<sectionName>"} marker. At fill time that object is removed and, in
 * place, one copy per row in {@code rows.get(sectionName)} takes its spot (an empty or absent list
 * removes it entirely); each copy is substituted with the row's map layered OVER the global values,
 * and the marker key is stripped from the copies. Section names must be unique across the template,
 * and a {@code template_repeat} nested inside another is rejected at parse time - v1 sections are
 * flat.
 *
 * <p><b>Parse policy</b> mirrors the scene-engine convention: strict on known structure (duplicate
 * JSON keys, wrong types and trailing content are rejected with precise messages through a
 * hand-rolled reader), while unknown top-level keys and unknown placeholder-spec keys are warned
 * about and ignored.
 *
 * <p><b>Rendering bridge:</b> a filled tree is exactly what the tooltip generator ingests, so a
 * template feeds straight into the render pipeline:
 * <pre>{@code
 * ContentTemplate template = ContentTemplate.parse(templateJson);
 * JsonObject nbt = template.fill(Map.of("player", "Aerh", "coins", "1,024"));
 * GeneratedObject image = new MinecraftTooltipGenerator.Builder()
 *     .parseNbtJson(nbt)
 *     .build()
 *     .render(null);
 * }</pre>
 */
@Slf4j
public final class ContentTemplate {

    /** Marker key naming the repeatable section an array-element object belongs to. */
    static final String REPEAT_KEY = "template_repeat";
    /** Highest {@code schema_version} this build understands. */
    private static final int MAX_SCHEMA_VERSION = 1;
    private static final Set<String> TOP_LEVEL_KEYS = Set.of("schema_version", "placeholders", "content");
    private static final Set<String> PLACEHOLDER_SPEC_KEYS = Set.of("required", "default");

    private final JsonObject content;
    private final Map<String, PlaceholderSpec> placeholders;
    private final Set<String> sectionNames;

    private ContentTemplate(JsonObject content, Map<String, PlaceholderSpec> placeholders,
                            Set<String> sectionNames) {
        this.content = content;
        this.placeholders = placeholders;
        this.sectionNames = sectionNames;
    }

    /** One declared placeholder's fill policy: whether a value is required, and its fallback. */
    private record PlaceholderSpec(boolean required, @Nullable String defaultValue) {
    }

    /**
     * Parses a template document, validating its structure strictly (see the class javadoc for the
     * schema and parse policy). Every {@code {placeholder}} token in the content must be declared,
     * every {@code template_repeat} section name must be unique, and nested repeats are rejected -
     * so a valid template is guaranteed to fill without a structural surprise.
     *
     * @param json the template document
     *
     * @return the parsed template
     * @throws IllegalArgumentException on blank or malformed JSON, trailing content, duplicate or
     *                                  wrong-typed keys, a missing or unsupported
     *                                  {@code schema_version}, a missing {@code content}, a
     *                                  malformed placeholder spec, an undeclared content token,
     *                                  malformed brace syntax, or a duplicate or nested
     *                                  {@code template_repeat} section
     */
    public static ContentTemplate parse(String json) {
        JsonObject root = StrictJson.parseObject(json, "template");

        if (!root.has("schema_version")) {
            throw new IllegalArgumentException("Template requires `schema_version`");
        }
        int schemaVersion = StrictJson.requireInt(root, "schema_version");
        if (schemaVersion < 1 || schemaVersion > MAX_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported `schema_version` " + schemaVersion
                + " (this build supports 1)");
        }

        if (!root.has("content")) {
            throw new IllegalArgumentException("Template requires `content`");
        }
        JsonElement contentElement = root.get("content");
        if (!contentElement.isJsonObject()) {
            throw new IllegalArgumentException("`content` must be a JSON object (the item NBT tree)");
        }
        JsonObject content = contentElement.getAsJsonObject();

        Map<String, PlaceholderSpec> placeholders = parsePlaceholders(root);
        // Unknown top-level keys are a soft signal (a forward-compatible field, a typo), not a
        // hard failure: warn and carry on, exactly like the scene engine tolerates them.
        StrictJson.warnUnknownKeys(root, TOP_LEVEL_KEYS, "template");

        Set<String> sectionNames = new LinkedHashSet<>();
        validateElement(content, placeholders.keySet(), false, sectionNames);

        return new ContentTemplate(content, placeholders, Collections.unmodifiableSet(sectionNames));
    }

    /**
     * Fills the template with global values only, no repeatable rows. Equivalent to
     * {@link #fill(Map, Map)} with an empty rows map: any {@code template_repeat} section resolves
     * to no rows and its marker object is removed.
     *
     * @param values placeholder name to value; values are inserted verbatim
     *
     * @return a fresh filled copy of the content tree
     * @throws IllegalArgumentException when a required placeholder used in the content is not
     *                                  supplied a value
     */
    public JsonObject fill(Map<String, String> values) {
        return fill(values, Map.of());
    }

    /**
     * Fills the template with global values and repeatable rows. Each {@code template_repeat}
     * section's marker object is replaced, in place, by one copy per row in
     * {@code rows.get(sectionName)}; each copy is substituted with that row's map layered OVER the
     * global values (row entries win), and the marker key is stripped. A section with no rows (an
     * empty or absent list) is removed entirely. The template is never mutated - every call returns
     * a fresh tree.
     *
     * @param values global placeholder name to value; values are inserted verbatim
     * @param rows   section name to its ordered rows, each a placeholder name to value map. Every
     *               key must name a declared {@code template_repeat} section
     *
     * @return a fresh filled copy of the content tree
     * @throws IllegalArgumentException when {@code rows} names a section the template does not
     *                                  declare, or when a required placeholder used in a rendered
     *                                  node is not supplied a value, globally or by the row
     */
    public JsonObject fill(Map<String, String> values, Map<String, List<Map<String, String>>> rows) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(rows, "rows");
        rejectUnknownRowSections(rows);

        Map<String, String> globalValues = resolveGlobalValues(values);
        return (JsonObject) transformElement(content, globalValues, rows);
    }

    /**
     * Rejects any {@code rows} key that names no {@code template_repeat} section. A supplied value
     * the content never references is harmlessly unused, but rows keyed by an unknown section are
     * content the caller meant to render that would silently vanish - a mistyped section name, say -
     * so unlike the tolerant values map this is a named rejection rather than a quiet drop.
     */
    private void rejectUnknownRowSections(Map<String, List<Map<String, String>>> rows) {
        for (String section : rows.keySet()) {
            if (!sectionNames.contains(section)) {
                throw new IllegalArgumentException("Unknown row section `" + section + "`; template declares "
                    + (sectionNames.isEmpty() ? "no repeatable sections" : "sections " + sectionNames));
            }
        }
    }

    /**
     * The declared placeholder names, in declaration order. A supplied value for a name absent here
     * is simply unused (every content token is declared), so this set enumerates exactly what
     * {@link #fill} consults.
     */
    public Set<String> placeholderNames() {
        return Collections.unmodifiableSet(placeholders.keySet());
    }

    /** The {@code template_repeat} section names found in the content, in first-seen order. */
    public Set<String> rowSectionNames() {
        return sectionNames;
    }

    // ------------------------------------------------------------- fill internals

    /**
     * The global value map every substitution consults: each declared placeholder resolves to its
     * supplied value, or - when unsupplied and not required - its default (or the empty string). A
     * required placeholder left unsupplied is deliberately ABSENT here, so it is only rejected if a
     * rendered node actually uses it (a row-only required placeholder is legitimately absent
     * globally and satisfied per row).
     */
    private Map<String, String> resolveGlobalValues(Map<String, String> values) {
        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, PlaceholderSpec> entry : placeholders.entrySet()) {
            String name = entry.getKey();
            PlaceholderSpec spec = entry.getValue();
            String supplied = values.get(name);
            if (supplied != null) {
                resolved.put(name, supplied);
            } else if (!spec.required()) {
                resolved.put(name, spec.defaultValue() != null ? spec.defaultValue() : "");
            }
        }
        return resolved;
    }

    /**
     * Rebuilds the tree into a fresh copy, substituting string values against {@code scope} and
     * expanding {@code template_repeat} array elements against {@code rows}. Rebuilding rather than
     * mutating keeps the template reusable and preserves member order, so a template filled with a
     * capture's own values reproduces the capture exactly.
     */
    private JsonElement transformElement(JsonElement element, Map<String, String> scope,
                                         Map<String, List<Map<String, String>>> rows) {
        if (element.isJsonObject()) {
            JsonObject source = element.getAsJsonObject();
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                out.add(entry.getKey(), transformElement(entry.getValue(), scope, rows));
            }
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                if (isRepeatMarker(child)) {
                    expandRepeat(child.getAsJsonObject(), scope, rows, out);
                } else {
                    out.add(transformElement(child, scope, rows));
                }
            }
            return out;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(render(element.getAsString(), name -> resolveValue(name, scope)));
        }
        // Numbers, booleans and null carry no tokens; copy them so the returned tree owns no
        // references into the template.
        return element.deepCopy();
    }

    /**
     * Expands one {@code template_repeat} marker object into the output array: one substituted copy
     * (marker key stripped) per row, each row's values layered over the global scope. No rows means
     * nothing is appended - the marker object is removed.
     */
    private void expandRepeat(JsonObject marker, Map<String, String> scope,
                              Map<String, List<Map<String, String>>> rows, JsonArray out) {
        String section = marker.get(REPEAT_KEY).getAsString();
        List<Map<String, String>> sectionRows = rows.get(section);
        if (sectionRows == null || sectionRows.isEmpty()) {
            return;
        }

        JsonObject stripped = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : marker.entrySet()) {
            if (!entry.getKey().equals(REPEAT_KEY)) {
                stripped.add(entry.getKey(), entry.getValue());
            }
        }

        for (Map<String, String> row : sectionRows) {
            if (row == null) {
                throw new IllegalArgumentException("Row map for section `" + section + "` must not be null");
            }
            Map<String, String> merged = new HashMap<>(scope);
            merged.putAll(row);
            out.add(transformElement(stripped, merged, rows));
        }
    }

    /**
     * Resolves one token against a scope. A present key returns its value (verbatim); an absent key
     * is a required placeholder left unsupplied in this scope - parse already guaranteed the token
     * is declared, and non-required placeholders always carry a value in the global scope.
     */
    private String resolveValue(String name, Map<String, String> scope) {
        String value = scope.get(name);
        if (value != null) {
            return value;
        }
        throw new IllegalArgumentException("Required placeholder `" + name + "` was not supplied a value");
    }

    // ------------------------------------------------------------- token scanning

    /** Sink a scanned {@code {name}} token resolves through - records or substitutes it. */
    @FunctionalInterface
    private interface TokenSink {
        String resolve(String name);
    }

    /**
     * The one token scanner both parse-time validation and fill-time substitution run through, so
     * the two can never disagree on what a token is. Literal text is copied verbatim; {@code {{} and
     * {@code }}} collapse to single braces; {@code {name}} is handed to the sink. A lone unescaped
     * brace, an unterminated {@code {}, or an empty or malformed name is rejected.
     */
    private static String render(String input, TokenSink sink) {
        StringBuilder out = new StringBuilder(input.length());
        int index = 0;
        int length = input.length();
        while (index < length) {
            char current = input.charAt(index);
            if (current == '{') {
                if (index + 1 < length && input.charAt(index + 1) == '{') {
                    out.append('{');
                    index += 2;
                    continue;
                }
                int close = input.indexOf('}', index + 1);
                if (close < 0) {
                    throw new IllegalArgumentException(
                        "Unterminated placeholder in `" + input + "` (use `{{` for a literal `{`)");
                }
                String name = input.substring(index + 1, close);
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Empty placeholder `{}` in `" + input + "`");
                }
                if (name.indexOf('{') >= 0) {
                    throw new IllegalArgumentException(
                        "Malformed placeholder `{" + name + "}` in `" + input + "` (stray `{` in the name)");
                }
                out.append(sink.resolve(name));
                index = close + 1;
            } else if (current == '}') {
                if (index + 1 < length && input.charAt(index + 1) == '}') {
                    out.append('}');
                    index += 2;
                    continue;
                }
                throw new IllegalArgumentException(
                    "Unescaped `}` in `" + input + "` (use `}}` for a literal `}`)");
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------- parse-time validation

    /**
     * Walks the content tree once, validating brace syntax and that every token is declared,
     * collecting {@code template_repeat} sections, and rejecting duplicate or nested sections and
     * misplaced markers. {@code insideRepeat} is true once inside a repeat node, so a nested marker
     * fails loudly.
     */
    private static void validateElement(JsonElement element, Set<String> declared, boolean insideRepeat,
                                        Set<String> sections) {
        if (element.isJsonObject()) {
            validateObjectEntries(element.getAsJsonObject(), declared, insideRepeat, sections, false);
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonObject() && child.getAsJsonObject().has(REPEAT_KEY)) {
                    validateRepeatMarker(child.getAsJsonObject(), declared, insideRepeat, sections);
                } else {
                    validateElement(child, declared, insideRepeat, sections);
                }
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            render(element.getAsString(), name -> {
                if (!declared.contains(name)) {
                    throw new IllegalArgumentException("Undeclared placeholder `" + name
                        + "` in content; declare it under `placeholders`");
                }
                return "";
            });
        }
    }

    /**
     * Validates a {@code template_repeat} marker object: its section name must be a non-blank
     * string, unique, and not nested inside another section. The node's remaining entries are then
     * validated as a repeat node.
     */
    private static void validateRepeatMarker(JsonObject marker, Set<String> declared, boolean insideRepeat,
                                             Set<String> sections) {
        JsonElement sectionElement = marker.get(REPEAT_KEY);
        if (!sectionElement.isJsonPrimitive() || !sectionElement.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("`template_repeat` must name a section as a string");
        }
        String section = sectionElement.getAsString();
        if (section.isBlank()) {
            throw new IllegalArgumentException("`template_repeat` section name must not be blank");
        }
        if (insideRepeat) {
            throw new IllegalArgumentException("Nested `template_repeat` (section `" + section
                + "`) is not allowed; v1 sections are flat");
        }
        if (!sections.add(section)) {
            throw new IllegalArgumentException("Duplicate `template_repeat` section `" + section + "`");
        }
        validateObjectEntries(marker, declared, true, sections, true);
    }

    /**
     * Validates an object's entries. A {@code template_repeat} key is only legitimate on the marker
     * object itself (consumed by {@link #validateRepeatMarker}); anywhere else it is misplaced and
     * rejected.
     */
    private static void validateObjectEntries(JsonObject object, Set<String> declared, boolean insideRepeat,
                                              Set<String> sections, boolean isRepeatNode) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equals(REPEAT_KEY)) {
                if (isRepeatNode) {
                    continue;
                }
                throw new IllegalArgumentException(
                    "`template_repeat` is only valid on an object that is a direct element of an array");
            }
            validateElement(entry.getValue(), declared, insideRepeat, sections);
        }
    }

    private static boolean isRepeatMarker(JsonElement element) {
        return element.isJsonObject() && element.getAsJsonObject().has(REPEAT_KEY);
    }

    // ------------------------------------------------------------- placeholder parsing

    private static Map<String, PlaceholderSpec> parsePlaceholders(JsonObject root) {
        Map<String, PlaceholderSpec> placeholders = new LinkedHashMap<>();
        if (!root.has("placeholders")) {
            return placeholders;
        }
        JsonElement element = root.get("placeholders");
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("`placeholders` must be an object mapping names to specs");
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String name = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Placeholder `" + name + "` spec must be an object");
            }
            JsonObject spec = entry.getValue().getAsJsonObject();
            String specLabel = "Placeholder `" + name + "`";
            StrictJson.warnUnknownKeys(spec, PLACEHOLDER_SPEC_KEYS, "placeholder `" + name + "`");
            boolean required = StrictJson.requireBoolean(spec, "required", specLabel, true);
            String defaultValue = spec.has("default") ? StrictJson.requireString(spec, "default", specLabel) : null;
            if (required && defaultValue != null) {
                // A default only applies to a non-required placeholder, so this pairing is
                // contradictory and the default is dead - warn rather than silently drop it.
                log.warn("Placeholder `{}` is required but declares a default `{}`; the default is ignored"
                    + " (defaults apply only to non-required placeholders). Set `required: false` to use it.",
                    name, defaultValue);
            }
            placeholders.put(name, new PlaceholderSpec(required, defaultValue));
        }
        return placeholders;
    }
}

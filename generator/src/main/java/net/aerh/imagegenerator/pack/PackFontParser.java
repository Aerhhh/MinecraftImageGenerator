package net.aerh.imagegenerator.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.experimental.UtilityClass;
import net.aerh.imagegenerator.exception.PackLoadException;
import net.aerh.imagegenerator.pack.font.FontFilter;
import net.aerh.imagegenerator.pack.font.FontProviderDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses one pack font JSON ({@code assets/<ns>/font/<path>.json}) into its ordered provider
 * definition list. Fail-loud like the rest of the pack parsers: an unknown provider type, a
 * malformed chars grid, or {@code ascent > height} throws {@link PackLoadException}; callers
 * translate that into the resolve-time contract.
 *
 * <p>Provider types: {@code bitmap}, {@code space}, {@code reference} (renderable / expandable),
 * {@code ttf}, {@code unihex}, {@code legacy_unicode} (parsed but unsupported for rendering).
 * Every entry may carry a {@code filter} object with the {@code uniform} / {@code jp} keys.
 *
 * <p>Structural JSON helpers (object parsing, type normalization, string / integral-int members)
 * are shared with {@link PackJsonParser} so validation and diagnostics stay consistent across the
 * pack parsers.
 */
@UtilityClass
class PackFontParser {

    private static final String OWNER = "Font provider";
    private static final int DEFAULT_BITMAP_HEIGHT = 8;
    private static final float DEFAULT_TTF_SIZE = 11.0f;
    private static final float DEFAULT_TTF_OVERSAMPLE = 1.0f;

    public static List<FontProviderDefinition> parse(byte[] json) {
        JsonObject root = PackJsonParser.parseObject(json);
        JsonElement providersElement = root.get("providers");
        if (providersElement == null || !providersElement.isJsonArray()) {
            throw new PackLoadException("Font JSON is missing required 'providers' array");
        }
        List<FontProviderDefinition> providers = new ArrayList<>();
        for (JsonElement element : providersElement.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new PackLoadException("Font provider entry must be a JSON object");
            }
            providers.add(parseProvider(element.getAsJsonObject()));
        }
        return List.copyOf(providers);
    }

    private static FontProviderDefinition parseProvider(JsonObject provider) {
        String type = PackJsonParser.normalize(requireString(provider, "type"));
        FontFilter filter = parseFilter(provider);
        return switch (type) {
            case "bitmap" -> parseBitmap(provider, filter);
            case "space" -> parseSpace(provider, filter);
            case "reference" -> new FontProviderDefinition.Reference(requireString(provider, "id"), filter);
            case "ttf" -> parseTtf(provider, filter);
            // Parsed as bare markers: unsupported for rendering, but a font containing them
            // still loads (their data files are never read).
            case "unihex", "legacy_unicode" -> new FontProviderDefinition.Unsupported(type, filter);
            default -> throw new PackLoadException("Unknown font provider type '%s'", type);
        };
    }

    private static FontProviderDefinition.Bitmap parseBitmap(JsonObject provider, FontFilter filter) {
        String file = requireString(provider, "file");
        int height = provider.has("height") ? requireInt(provider, "height") : DEFAULT_BITMAP_HEIGHT;
        int ascent = requireInt(provider, "ascent");
        if (ascent > height) {
            throw new PackLoadException("Bitmap font ascent %s exceeds height %s",
                String.valueOf(ascent), String.valueOf(height));
        }
        JsonElement charsElement = provider.get("chars");
        if (charsElement == null || !charsElement.isJsonArray()) {
            throw new PackLoadException("Bitmap font provider is missing 'chars' array");
        }
        JsonArray charsArray = charsElement.getAsJsonArray();
        if (charsArray.isEmpty()) {
            throw new PackLoadException("Bitmap font 'chars' must not be empty");
        }
        List<String> rows = new ArrayList<>();
        int expectedCodePoints = -1;
        for (JsonElement rowElement : charsArray) {
            if (!rowElement.isJsonPrimitive() || !rowElement.getAsJsonPrimitive().isString()) {
                throw new PackLoadException("Bitmap font 'chars' rows must be strings");
            }
            String row = rowElement.getAsString();
            int codePoints = row.codePointCount(0, row.length());
            if (expectedCodePoints < 0) {
                if (codePoints == 0) {
                    throw new PackLoadException("Bitmap font 'chars' rows must not be empty strings");
                }
                expectedCodePoints = codePoints;
            } else if (codePoints != expectedCodePoints) {
                throw new PackLoadException(
                    "Bitmap font 'chars' rows must have equal length: row %s has %s codepoints, expected %s",
                    String.valueOf(rows.size()), String.valueOf(codePoints), String.valueOf(expectedCodePoints));
            }
            rows.add(row);
        }
        return new FontProviderDefinition.Bitmap(file, height, ascent, rows, filter);
    }

    private static FontProviderDefinition.Space parseSpace(JsonObject provider, FontFilter filter) {
        JsonElement advancesElement = provider.get("advances");
        if (advancesElement == null || !advancesElement.isJsonObject()) {
            throw new PackLoadException("Space font provider is missing 'advances' object");
        }
        Map<Integer, Float> advances = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : advancesElement.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            if (key.codePointCount(0, key.length()) != 1) {
                throw new PackLoadException("Space font advance key must be a single codepoint, got '%s'", key);
            }
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new PackLoadException("Space font advance for '%s' must be a number", key);
            }
            advances.put(key.codePointAt(0), value.getAsFloat());
        }
        return new FontProviderDefinition.Space(advances, filter);
    }

    private static FontProviderDefinition.Ttf parseTtf(JsonObject provider, FontFilter filter) {
        String file = requireString(provider, "file");
        float size = provider.has("size") ? requireFloat(provider, "size") : DEFAULT_TTF_SIZE;
        float oversample = provider.has("oversample") ? requireFloat(provider, "oversample") : DEFAULT_TTF_OVERSAMPLE;
        float shiftX = 0.0f;
        float shiftY = 0.0f;
        if (provider.has("shift")) {
            JsonElement shiftElement = provider.get("shift");
            if (!shiftElement.isJsonArray() || shiftElement.getAsJsonArray().size() != 2) {
                throw new PackLoadException("TTF font 'shift' must be an array of two numbers");
            }
            JsonArray shift = shiftElement.getAsJsonArray();
            shiftX = asFloat(shift.get(0), "shift[0]");
            shiftY = asFloat(shift.get(1), "shift[1]");
        }
        return new FontProviderDefinition.Ttf(file, size, oversample, shiftX, shiftY, parseSkip(provider), filter);
    }

    /** The TTF {@code skip} field: a string, or an array of strings, of codepoints to exclude. */
    private static Set<Integer> parseSkip(JsonObject provider) {
        Set<Integer> skip = new LinkedHashSet<>();
        JsonElement skipElement = provider.get("skip");
        if (skipElement == null || skipElement.isJsonNull()) {
            return skip;
        }
        if (skipElement.isJsonArray()) {
            for (JsonElement entry : skipElement.getAsJsonArray()) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    throw new PackLoadException("TTF font 'skip' array entries must be strings");
                }
                addCodePoints(skip, entry.getAsString());
            }
            return skip;
        }
        if (skipElement.isJsonPrimitive() && skipElement.getAsJsonPrimitive().isString()) {
            addCodePoints(skip, skipElement.getAsString());
            return skip;
        }
        throw new PackLoadException("TTF font 'skip' must be a string or an array of strings");
    }

    private static void addCodePoints(Set<Integer> target, String value) {
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            target.add(codePoint);
            i += Character.charCount(codePoint);
        }
    }

    private static FontFilter parseFilter(JsonObject provider) {
        JsonElement filterElement = provider.get("filter");
        if (filterElement == null || filterElement.isJsonNull()) {
            return FontFilter.none();
        }
        if (!filterElement.isJsonObject()) {
            throw new PackLoadException("Font provider 'filter' must be an object");
        }
        Boolean uniform = null;
        Boolean jp = null;
        for (Map.Entry<String, JsonElement> entry : filterElement.getAsJsonObject().entrySet()) {
            boolean value = asBoolean(entry.getValue(), entry.getKey());
            switch (entry.getKey()) {
                case "uniform" -> uniform = value;
                case "jp" -> jp = value;
                default -> throw new PackLoadException("Unknown font filter key '%s'", entry.getKey());
            }
        }
        return new FontFilter(uniform, jp);
    }

    private static String requireString(JsonObject node, String member) {
        return PackJsonParser.requireString(node, member, OWNER);
    }

    private static int requireInt(JsonObject node, String member) {
        return PackJsonParser.requireIntegralInt(node, member, OWNER);
    }

    private static float requireFloat(JsonObject node, String member) {
        JsonElement element = node.get(member);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new PackLoadException("Font provider is missing numeric member '%s'", member);
        }
        return element.getAsFloat();
    }

    private static float asFloat(JsonElement element, String description) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new PackLoadException("Font provider member '%s' must be a number", description);
        }
        return element.getAsFloat();
    }

    private static boolean asBoolean(JsonElement element, String description) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new PackLoadException("Font filter key '%s' must be a boolean", description);
        }
        return element.getAsBoolean();
    }
}

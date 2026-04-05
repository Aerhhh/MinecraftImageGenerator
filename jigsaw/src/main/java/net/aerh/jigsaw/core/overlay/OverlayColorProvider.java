package net.aerh.jigsaw.core.overlay;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Provides named overlay color data loaded from the bundled {@code overlay_colors.json} resource.
 *
 * <p>This class supports two use cases:
 * <ul>
 *   <li>Autocomplete: {@link #getAllColorOptionNames()} returns every named color key across all
 *       overlay categories (leather armor, potions, fireworks, etc.).</li>
 *   <li>Lookup: {@link #getColor(String, String)} resolves the first RGB integer for a named
 *       color within a specific category.</li>
 * </ul>
 *
 * <p>Instances are immutable once constructed. Use {@link #fromDefaults()} to obtain a
 * pre-loaded instance backed by the bundled resource file.
 */
public final class OverlayColorProvider {

    private static final String RESOURCE_PATH = "/minecraft/assets/json/overlay_colors.json";

    /**
     * Map from {@code category -> colorName -> first RGB value}.
     * Uses the first integer in the options array for each named color.
     */
    private final Map<String, Map<String, Integer>> colorsByCategory;

    private OverlayColorProvider(Map<String, Map<String, Integer>> colorsByCategory) {
        this.colorsByCategory = colorsByCategory;
    }

    /**
     * Returns a new {@link OverlayColorProvider} loaded from the bundled
     * {@code overlay_colors.json} resource.
     *
     * @return a fully loaded provider
     * @throws IllegalStateException if the resource cannot be found or parsed
     */
    public static OverlayColorProvider fromDefaults() {
        try (InputStream stream = OverlayColorProvider.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("overlay_colors.json not found at " + RESOURCE_PATH);
            }
            return parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load overlay_colors.json", e);
        }
    }

    /**
     * Returns all color option names across all overlay categories, suitable for autocomplete.
     *
     * <p>Names are the JSON keys within the {@code "options"} object of each category
     * (e.g. {@code "red"}, {@code "speed"}, {@code "gold"}).
     *
     * @return an unmodifiable set of all named color keys; never {@code null}
     */
    public Set<String> getAllColorOptionNames() {
        Set<String> names = new HashSet<>();
        for (Map<String, Integer> options : colorsByCategory.values()) {
            names.addAll(options.keySet());
        }
        return Collections.unmodifiableSet(names);
    }

    /**
     * Returns the first RGB integer for a named color within the specified overlay category.
     *
     * @param category  the overlay category name (e.g. {@code "leather_armor"}, {@code "potion"})
     * @param colorName the color name within that category (e.g. {@code "red"}, {@code "speed"})
     * @return an {@link Optional} containing the RGB value, or empty if not found
     */
    public Optional<Integer> getColor(String category, String colorName) {
        Map<String, Integer> options = colorsByCategory.get(category);
        if (options == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(options.get(colorName));
    }

    /**
     * Parses the JSON from the given reader into a category-to-name-to-RGB map.
     * Only entries with a non-empty {@code "options"} object are included.
     */
    private static OverlayColorProvider parse(InputStreamReader reader) {
        Map<String, Map<String, Integer>> result = new HashMap<>();

        for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("name") || !entry.has("options")) {
                continue;
            }
            String category = entry.get("name").getAsString();
            JsonElement optionsEl = entry.get("options");
            if (!optionsEl.isJsonObject()) {
                continue;
            }

            Map<String, Integer> colorMap = new HashMap<>();
            for (Map.Entry<String, JsonElement> colorEntry : optionsEl.getAsJsonObject().entrySet()) {
                String colorName = colorEntry.getKey();
                JsonElement value = colorEntry.getValue();
                // Each value may be an array or a primitive; take the first element
                int rgb;
                if (value.isJsonArray() && value.getAsJsonArray().size() > 0) {
                    rgb = value.getAsJsonArray().get(0).getAsInt();
                } else if (value.isJsonPrimitive()) {
                    rgb = value.getAsInt();
                } else {
                    continue;
                }
                colorMap.put(colorName, rgb);
            }

            if (!colorMap.isEmpty()) {
                result.put(category, Collections.unmodifiableMap(colorMap));
            }
        }

        return new OverlayColorProvider(Collections.unmodifiableMap(result));
    }
}

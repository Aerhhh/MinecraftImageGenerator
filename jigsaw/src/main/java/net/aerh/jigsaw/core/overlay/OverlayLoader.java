package net.aerh.jigsaw.core.overlay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.aerh.jigsaw.api.overlay.ColorMode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads and caches overlay resources from JSON configuration files and a spritesheet.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Loading the overlay spritesheet ({@code overlays.png})</li>
 *   <li>Loading overlay coordinates from JSON to extract individual overlay textures</li>
 *   <li>Loading color options from JSON</li>
 *   <li>Loading item-to-overlay bindings from JSON</li>
 * </ul>
 *
 * <p>Use {@link #fromDefaults()} to obtain a pre-loaded instance backed by the bundled resources.
 */
public final class OverlayLoader {

    private static final int DEFAULT_IMAGE_SIZE = 128;
    private static final String DEFAULT_RESOURCE_BASE = "/minecraft/assets";

    private final Map<String, ItemOverlayData> itemOverlays;
    private final Map<String, ColorOptionsEntry> colorOptionsMap;

    private OverlayLoader(Map<String, ItemOverlayData> itemOverlays,
                          Map<String, ColorOptionsEntry> colorOptionsMap) {
        this.itemOverlays = itemOverlays;
        this.colorOptionsMap = colorOptionsMap;
    }

    /**
     * Returns a new {@link OverlayLoader} loaded from the bundled default resources.
     *
     * @return a fully loaded overlay loader
     * @throws IllegalStateException if any required resource cannot be found or parsed
     */
    public static OverlayLoader fromDefaults() {
        return load(DEFAULT_RESOURCE_BASE);
    }

    /**
     * Loads overlay data from the given resource base path.
     *
     * @param resourceBasePath the base classpath prefix (e.g. {@code "/minecraft/assets"})
     * @return a fully loaded overlay loader
     * @throws IllegalStateException if any required resource cannot be found or parsed
     */
    public static OverlayLoader load(String resourceBasePath) {
        Objects.requireNonNull(resourceBasePath, "resourceBasePath must not be null");
        Gson gson = new Gson();

        try {
            // Load spritesheet
            BufferedImage spriteSheet = loadImage(resourceBasePath + "/spritesheets/overlays.png");

            // Load color options
            Map<String, ColorOptionsEntry> colorOptions = loadColorOptions(resourceBasePath, gson);

            // Load overlay coordinates and extract sub-images
            Map<String, OverlayDefinition> overlays = loadOverlayCoordinates(resourceBasePath, gson, spriteSheet);

            // Load item bindings and build the final item overlay map
            Map<String, ItemOverlayData> itemOverlays = loadItemBindings(resourceBasePath, gson, overlays, colorOptions);

            return new OverlayLoader(Collections.unmodifiableMap(itemOverlays),
                    Collections.unmodifiableMap(colorOptions));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load overlay resources from " + resourceBasePath, e);
        }
    }

    /**
     * Returns the overlay data for the given item ID, or empty if the item has no overlay.
     *
     * @param itemId the item ID (case-insensitive)
     * @return an {@link Optional} containing the overlay data, or empty if none exists
     */
    public Optional<ItemOverlayData> getOverlay(String itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        return Optional.ofNullable(itemOverlays.get(itemId.toLowerCase()));
    }

    /**
     * Returns {@code true} if the given item has an overlay binding.
     *
     * @param itemId the item ID (case-insensitive)
     * @return whether an overlay exists for the item
     */
    public boolean hasOverlay(String itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        return itemOverlays.containsKey(itemId.toLowerCase());
    }

    /**
     * Returns all available color option names across all overlay categories,
     * suitable for autocomplete suggestions.
     *
     * @return an unmodifiable set of all named color keys
     */
    public Set<String> getAllColorOptionNames() {
        return colorOptionsMap.values()
                .stream()
                .flatMap(entry -> entry.optionNames().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Resolves a named color or hex string to a packed RGB integer for the given color category.
     *
     * <p>Checks predefined option names first, then attempts hex parsing if the category allows it.
     * Falls back to the default color if configured.
     *
     * @param category    the color options category (e.g. {@code "leather_armor"})
     * @param colorNameOrHex the color name or hex string
     * @return an {@link Optional} containing the resolved RGB value, or empty if not resolvable
     */
    public Optional<Integer> resolveColor(String category, String colorNameOrHex) {
        ColorOptionsEntry entry = colorOptionsMap.get(category);
        if (entry == null) {
            return Optional.empty();
        }
        return entry.resolve(colorNameOrHex);
    }

    // ---- Private loading methods ----

    private static BufferedImage loadImage(String path) throws IOException {
        try (InputStream stream = OverlayLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + path);
            }
            return ImageIO.read(stream);
        }
    }

    private static Map<String, ColorOptionsEntry> loadColorOptions(String basePath, Gson gson) throws IOException {
        String path = basePath + "/json/overlay_colors.json";
        try (InputStream stream = OverlayLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("overlay_colors.json not found at " + path);
            }

            JsonArray array = gson.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), JsonArray.class);

            Map<String, ColorOptionsEntry> result = new HashMap<>();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                String name = obj.get("name").getAsString();

                // Parse options
                Map<String, int[]> options = new HashMap<>();
                if (obj.has("options") && obj.get("options").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("options").entrySet()) {
                        JsonArray colorArr = e.getValue().getAsJsonArray();
                        int[] colors = new int[colorArr.size()];
                        for (int i = 0; i < colorArr.size(); i++) {
                            colors[i] = colorArr.get(i).getAsInt();
                        }
                        options.put(e.getKey(), colors);
                    }
                }

                boolean allowHex = obj.has("allowHexColors") && obj.get("allowHexColors").getAsBoolean();
                boolean useDefault = obj.has("useDefaultIfMissing") && obj.get("useDefaultIfMissing").getAsBoolean();

                int[] defaultColors = null;
                if (obj.has("defaultColors")) {
                    JsonArray defArr = obj.getAsJsonArray("defaultColors");
                    defaultColors = new int[defArr.size()];
                    for (int i = 0; i < defArr.size(); i++) {
                        defaultColors[i] = defArr.get(i).getAsInt();
                    }
                }

                result.put(name, new ColorOptionsEntry(name, options, allowHex, useDefault, defaultColors));
            }
            return result;
        }
    }

    private static Map<String, OverlayDefinition> loadOverlayCoordinates(
            String basePath, Gson gson, BufferedImage spriteSheet) throws IOException {
        String path = basePath + "/json/overlay_coordinates.json";
        try (InputStream stream = OverlayLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("overlay_coordinates.json not found at " + path);
            }

            JsonArray array = gson.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), JsonArray.class);

            Map<String, OverlayDefinition> result = new HashMap<>();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                String name = obj.get("name").getAsString();

                // Skip enchant overlays
                if (name.contains("enchant")) {
                    continue;
                }

                int x = obj.get("x").getAsInt();
                int y = obj.get("y").getAsInt();
                int size = obj.has("size") && obj.get("size").getAsInt() > 0
                        ? obj.get("size").getAsInt()
                        : DEFAULT_IMAGE_SIZE;
                String type = obj.has("type") ? obj.get("type").getAsString() : "NORMAL";
                String colorOptions = obj.has("colorOptions") ? obj.get("colorOptions").getAsString() : null;
                String colorMode = obj.has("colorMode") ? obj.get("colorMode").getAsString() : null;

                BufferedImage subImage = spriteSheet.getSubimage(x, y, size, size);
                result.put(name, new OverlayDefinition(name, subImage, type, colorOptions, colorMode));
            }
            return result;
        }
    }

    private static Map<String, ItemOverlayData> loadItemBindings(
            String basePath, Gson gson,
            Map<String, OverlayDefinition> overlays,
            Map<String, ColorOptionsEntry> colorOptions) throws IOException {
        String path = basePath + "/json/item_overlay_binding.json";
        try (InputStream stream = OverlayLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("item_overlay_binding.json not found at " + path);
            }

            JsonArray bindings = gson.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), JsonArray.class);

            Map<String, ItemOverlayData> result = new HashMap<>();
            for (JsonElement element : bindings) {
                JsonObject binding = element.getAsJsonObject();
                String itemName = binding.get("name").getAsString().toLowerCase();
                String overlayName = binding.get("overlays").getAsString();

                OverlayDefinition def = overlays.get(overlayName);
                if (def == null) {
                    continue;
                }

                ColorMode colorMode = "OVERLAY".equalsIgnoreCase(def.colorMode)
                        ? ColorMode.OVERLAY
                        : ColorMode.BASE;

                // Resolve renderer type: the JSON uses uppercase (NORMAL), our registry uses lowercase
                String rendererType = def.type.toLowerCase();

                ColorOptionsEntry colorOpts = def.colorOptionsName != null
                        ? colorOptions.get(def.colorOptionsName)
                        : null;

                int[] defaultColors = colorOpts != null ? colorOpts.defaultColors : null;
                boolean allowHex = colorOpts != null && colorOpts.allowHexColors;

                result.put(itemName, new ItemOverlayData(
                        def.image, colorMode, rendererType, def.colorOptionsName, defaultColors, allowHex));
            }
            return result;
        }
    }

    // ---- Internal data classes ----

    /**
     * Parsed overlay coordinate definition from overlay_coordinates.json.
     */
    private record OverlayDefinition(
            String name,
            BufferedImage image,
            String type,
            String colorOptionsName,
            String colorMode
    ) {}

    /**
     * Parsed color options entry from overlay_colors.json.
     */
    static final class ColorOptionsEntry {
        final String name;
        final Map<String, int[]> options;
        final boolean allowHexColors;
        final boolean useDefaultIfMissing;
        final int[] defaultColors;

        ColorOptionsEntry(String name, Map<String, int[]> options,
                          boolean allowHexColors, boolean useDefaultIfMissing, int[] defaultColors) {
            this.name = name;
            this.options = Collections.unmodifiableMap(options);
            this.allowHexColors = allowHexColors;
            this.useDefaultIfMissing = useDefaultIfMissing;
            this.defaultColors = defaultColors;
        }

        Set<String> optionNames() {
            return options.keySet();
        }

        /**
         * Resolves a color name or hex string to a packed RGB integer.
         * Returns the first color in the array for named options.
         */
        Optional<Integer> resolve(String nameOrHex) {
            if (nameOrHex == null || nameOrHex.isBlank()) {
                return useDefaultIfMissing && defaultColors != null && defaultColors.length > 0
                        ? Optional.of(defaultColors[0])
                        : Optional.empty();
            }

            String key = nameOrHex.toLowerCase();

            // Check predefined options
            int[] colors = options.get(key);
            if (colors != null && colors.length > 0) {
                return Optional.of(colors[0]);
            }

            // Try hex parsing
            if (allowHexColors) {
                try {
                    String cleaned = key.startsWith("#") ? key.substring(1) : key;
                    cleaned = cleaned.replaceAll("[^a-f0-9]", "");
                    if (!cleaned.isEmpty()) {
                        return Optional.of(Integer.parseInt(cleaned, 16));
                    }
                } catch (NumberFormatException ignored) {
                    // Fall through to default
                }
            }

            // Fall back to default
            return useDefaultIfMissing && defaultColors != null && defaultColors.length > 0
                    ? Optional.of(defaultColors[0])
                    : Optional.empty();
        }
    }
}

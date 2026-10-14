package net.aerh.imagegenerator.spritesheet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.hypixel.nerdbot.marmalade.functional.Pair;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads item textures from one or more texture atlas pages.
 * <p>
 * The coordinates file {@code <base>/json/atlas_coordinates.json} is parsed on first use. Each entry names the atlas
 * page it lives on through its {@code atlas} field; entries without one belong to {@code minecraft_texture_atlas.png}.
 * Atlas pages under {@code <base>/spritesheets/} are decoded lazily, the first time a texture on that page is
 * requested, and are then kept in memory. Instances are safe to share between threads.
 */
@Slf4j
public class SpritesheetLoader {

    private static final String DEFAULT_RESOURCE_BASE_PATH = "/minecraft/assets";
    private static final String COORDINATES_PATH = "/json/atlas_coordinates.json";
    private static final String SPRITESHEETS_PATH = "/spritesheets/";
    /** Atlas file used by coordinate entries that predate paging and therefore carry no {@code atlas} field. */
    private static final String DEFAULT_ATLAS_FILE_NAME = "minecraft_texture_atlas.png";

    /**
     * One entry of the coordinates file: where a named texture sits on which atlas page.
     */
    private record AtlasEntry(String name, int x, int y, int size, String atlasFileName) {
    }

    private final String resourceBasePath;
    private final Gson gson = new Gson();
    /** Decoded atlas pages keyed by atlas file name. */
    private final Map<String, BufferedImage> pages = new ConcurrentHashMap<>();
    /** Sliced textures keyed by texture name. */
    private final Map<String, BufferedImage> textures = new ConcurrentHashMap<>();
    /** Parsed coordinates keyed by texture name, in file order. Null until {@link #load()} has run. */
    private volatile Map<String, AtlasEntry> entries;

    public SpritesheetLoader() {
        this(DEFAULT_RESOURCE_BASE_PATH);
    }

    /**
     * Constructor to specify a custom resource base path.
     *
     * @param resourceBasePath Base path for resources, for example {@code /minecraft/assets}
     */
    public SpritesheetLoader(String resourceBasePath) {
        this.resourceBasePath = resourceBasePath;
    }

    /**
     * Parse the coordinates file. Safe to call more than once and from several threads; only the first call does
     * any work. Atlas pages are not decoded here.
     *
     * @throws GeneratorException if the coordinates file is missing, unreadable or malformed
     */
    public synchronized void load() {
        if (entries != null) {
            return;
        }

        String path = resourceBasePath + COORDINATES_PATH;
        log.debug("Loading texture atlas coordinates from {}", path);
        JsonElement root;

        try (InputStream stream = openResource(path, "Texture atlas coordinates file")) {
            root = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonElement.class);
        } catch (IOException | JsonParseException e) {
            throw new GeneratorException("Failed to read texture atlas coordinates file: " + path, e);
        }

        Map<String, AtlasEntry> parsed = parseEntries(root, path);
        entries = Collections.unmodifiableMap(parsed);
        log.info("Loaded {} texture atlas coordinates from {}", parsed.size(), path);
    }

    /**
     * Get a texture by name, decoding its atlas page if this is the first texture requested from that page. Repeated
     * calls for the same name return the same instance.
     *
     * @param name Texture name as listed in the coordinates file
     *
     * @return The texture, or null if the name is null or not listed
     *
     * @throws GeneratorException if the coordinates file or the atlas page cannot be read, or the entry lies outside
     *                            its page
     */
    public BufferedImage getTexture(String name) {
        if (name == null) {
            return null;
        }

        AtlasEntry entry = entries().get(name);
        if (entry == null) {
            return null;
        }

        return textures.computeIfAbsent(name, key -> slice(entry));
    }

    /**
     * Find every texture whose name contains {@code query}. The match is case-sensitive. Only the atlas pages that
     * hold a match are decoded.
     *
     * @param query Substring to look for in texture names
     *
     * @return Matching textures paired with their names, in coordinates-file order; empty if nothing matches
     *
     * @throws GeneratorException if the coordinates file or an atlas page cannot be read
     */
    public List<Pair<String, BufferedImage>> searchForTexture(String query) {
        Objects.requireNonNull(query, "query");
        return entries().keySet().stream()
            .filter(name -> name.contains(query))
            .map(name -> Pair.of(name, getTexture(name)))
            .toList();
    }

    /**
     * Get every texture keyed by name. This decodes every atlas page, so prefer {@link #getTexture(String)} when only
     * some textures are needed.
     *
     * @return An unmodifiable map of texture name to texture, in coordinates-file order
     *
     * @throws GeneratorException if the coordinates file or an atlas page cannot be read
     */
    public Map<String, BufferedImage> getImageMap() {
        Map<String, BufferedImage> result = new LinkedHashMap<>();
        for (String name : entries().keySet()) {
            result.put(name, getTexture(name));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Protected method for resource loading (can be overridden for testing).
     *
     * @param path Resource path
     *
     * @return InputStream or null if not found
     */
    protected InputStream getResource(String path) {
        return getClass().getResourceAsStream(path);
    }

    private Map<String, AtlasEntry> entries() {
        Map<String, AtlasEntry> loaded = entries;
        if (loaded == null) {
            load();
            loaded = entries;
        }
        return loaded;
    }

    private Map<String, AtlasEntry> parseEntries(JsonElement root, String path) {
        if (root == null || !root.isJsonArray()) {
            throw new GeneratorException("Texture atlas coordinates file " + path + " must contain a JSON array");
        }

        Map<String, AtlasEntry> parsed = new LinkedHashMap<>();
        JsonArray array = root.getAsJsonArray();

        for (int index = 0; index < array.size(); index++) {
            AtlasEntry entry = parseEntry(array.get(index), index, path);
            AtlasEntry previous = parsed.put(entry.name(), entry);
            if (previous != null) {
                log.warn("Duplicate texture name '{}' in {}; keeping the last entry", entry.name(), path);
            }
        }

        return parsed;
    }

    private AtlasEntry parseEntry(JsonElement element, int index, String path) {
        if (!element.isJsonObject()) {
            throw new GeneratorException(entryDescription(index, path) + " is not a JSON object");
        }

        JsonObject object = element.getAsJsonObject();
        JsonElement atlas = object.get("atlas");

        try {
            return new AtlasEntry(
                requiredField(object, "name", index, path).getAsString(),
                requiredField(object, "x", index, path).getAsInt(),
                requiredField(object, "y", index, path).getAsInt(),
                requiredField(object, "size", index, path).getAsInt(),
                atlas == null || atlas.isJsonNull() ? DEFAULT_ATLAS_FILE_NAME : atlas.getAsString()
            );
        } catch (IllegalStateException | NumberFormatException | UnsupportedOperationException e) {
            throw new GeneratorException(entryDescription(index, path) + " has a field of the wrong type", e);
        }
    }

    private static JsonElement requiredField(JsonObject object, String field, int index, String path) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new GeneratorException(entryDescription(index, path) + " is missing field '" + field + "'");
        }
        return value;
    }

    private static String entryDescription(int index, String path) {
        return "Texture atlas coordinates file " + path + " entry " + index;
    }

    private BufferedImage slice(AtlasEntry entry) {
        BufferedImage page = pages.computeIfAbsent(entry.atlasFileName(), this::decodePage);

        try {
            return page.getSubimage(entry.x(), entry.y(), entry.size(), entry.size());
        } catch (RasterFormatException e) {
            throw new GeneratorException("Texture '" + entry.name() + "' at (" + entry.x() + ", " + entry.y()
                + ") size " + entry.size() + " lies outside texture atlas page " + pagePath(entry.atlasFileName())
                + " (" + page.getWidth() + "x" + page.getHeight() + ")", e);
        }
    }

    private BufferedImage decodePage(String atlasFileName) {
        String path = pagePath(atlasFileName);
        log.debug("Decoding texture atlas page {}", path);

        try (InputStream stream = openResource(path, "Texture atlas page")) {
            BufferedImage page = ImageIO.read(stream);
            if (page == null) {
                throw new GeneratorException("Texture atlas page is not a readable image: " + path);
            }
            log.info("Decoded texture atlas page {} ({}x{})", path, page.getWidth(), page.getHeight());
            return page;
        } catch (IOException e) {
            throw new GeneratorException("Failed to decode texture atlas page: " + path, e);
        }
    }

    private String pagePath(String atlasFileName) {
        return resourceBasePath + SPRITESHEETS_PATH + atlasFileName;
    }

    /**
     * Open a resource, failing loudly when it does not exist.
     *
     * @param path        Resource path
     * @param description What the resource is, used in the error message
     *
     * @return The open stream; the caller closes it
     *
     * @throws GeneratorException if the resource does not exist
     */
    private InputStream openResource(String path, String description) {
        InputStream stream = getResource(path);
        if (stream == null) {
            throw new GeneratorException(description + " not found: " + path);
        }
        return stream;
    }
}

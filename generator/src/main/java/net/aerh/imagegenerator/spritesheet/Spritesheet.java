package net.aerh.imagegenerator.spritesheet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.GeneratorException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class Spritesheet {

    private static final String DEFAULT_ATLAS_PATH = "/minecraft/assets/spritesheets/minecraft_texture_atlas.png";
    private static final String DEFAULT_COORDINATES_PATH = "/minecraft/assets/json/atlas_coordinates.json";
    private static final Gson GSON = new Gson();

    private static volatile Spritesheet instance;

    private final String atlasPath;
    private final String coordinatesPath;

    private volatile BufferedImage textureAtlas;
    private Map<String, AtlasEntry> atlasEntries;
    private final ConcurrentHashMap<String, BufferedImage> textureCache = new ConcurrentHashMap<>();
    private boolean initialized = false;

    public Spritesheet() {
        this(DEFAULT_ATLAS_PATH, DEFAULT_COORDINATES_PATH);
    }

    public Spritesheet(String atlasPath, String coordinatesPath) {
        this.atlasPath = atlasPath;
        this.coordinatesPath = coordinatesPath;
    }

    public static Spritesheet getDefault() {
        if (instance == null) {
            synchronized (Spritesheet.class) {
                if (instance == null) {
                    instance = new Spritesheet();
                }
            }
        }
        return instance;
    }

    /**
     * @deprecated Use {@link #getDefault()} and instance methods instead.
     */
    @Deprecated
    public static BufferedImage getTexture(String textureId) {
        return getDefault().getTextureById(textureId);
    }

    /**
     * @deprecated Use {@link #getDefault()} and instance methods instead.
     */
    @Deprecated
    public static List<Map.Entry<String, BufferedImage>> searchForTexture(String textureId) {
        return getDefault().searchTextures(textureId);
    }

    /**
     * @deprecated Use {@link #getDefault()} and instance methods instead.
     */
    @Deprecated
    public static Map<String, BufferedImage> getImageMap() {
        return getDefault().getAllTextures();
    }

    public BufferedImage getTextureById(String textureId) {
        initialize();
        return textureCache.computeIfAbsent(textureId, this::loadTextureFromAtlas);
    }

    public List<Map.Entry<String, BufferedImage>> searchTextures(String textureId) {
        initialize();
        return atlasEntries.keySet().stream()
            .filter(key -> key.contains(textureId))
            .map(key -> Map.entry(key, getTextureById(key)))
            .collect(Collectors.toList());
    }

    public Map<String, BufferedImage> getAllTextures() {
        initialize();
        for (String key : atlasEntries.keySet()) {
            getTextureById(key);
        }
        return Collections.unmodifiableMap(new HashMap<>(textureCache));
    }

    public Set<String> getTextureNames() {
        initialize();
        return Collections.unmodifiableSet(atlasEntries.keySet());
    }

    private void initialize() {
        if (initialized) {
            return;
        }

        synchronized (this) {
            if (initialized) {
                return;
            }

            loadAtlasAndCoordinates();
            initialized = true;
        }
    }

    private void loadAtlasAndCoordinates() {
        try (InputStream atlasStream = getResource(atlasPath)) {
            if (atlasStream == null) {
                throw new IOException("Texture atlas image not found: " + atlasPath);
            }
            log.info("Loading texture atlas image from {}", atlasPath);
            textureAtlas = ImageIO.read(atlasStream);
            log.info("Loaded texture atlas image (size: {}x{})", textureAtlas.getWidth(), textureAtlas.getHeight());
        } catch (IOException exception) {
            throw new GeneratorException("Failed to load texture atlas image", exception);
        }

        try (InputStream coordinatesStream = getResource(coordinatesPath)) {
            if (coordinatesStream == null) {
                throw new IOException("Texture atlas coordinates file not found: " + coordinatesPath);
            }
            log.info("Loading texture atlas coordinates from {}", coordinatesPath);

            JsonArray jsonCoordinates = GSON.fromJson(new InputStreamReader(coordinatesStream), JsonArray.class);
            Map<String, AtlasEntry> entries = new HashMap<>();

            for (JsonElement jsonElement : jsonCoordinates) {
                JsonObject itemData = jsonElement.getAsJsonObject();
                String name = itemData.get("name").getAsString();
                int x = itemData.get("x").getAsInt();
                int y = itemData.get("y").getAsInt();
                int size = itemData.get("size").getAsInt();
                entries.put(name, new AtlasEntry(x, y, size));
            }

            this.atlasEntries = Collections.unmodifiableMap(entries);
            log.info("Loaded {} texture atlas coordinate entries", entries.size());
        } catch (IOException exception) {
            throw new GeneratorException("Failed to load texture atlas coordinates", exception);
        }
    }

    private BufferedImage loadTextureFromAtlas(String textureId) {
        AtlasEntry entry = atlasEntries.get(textureId);
        if (entry == null) {
            return null;
        }

        log.debug("Lazy-loading texture: {} at ({}, {}) with size {}x{}", textureId, entry.x, entry.y, entry.size, entry.size);
        BufferedImage subImage = textureAtlas.getSubimage(entry.x, entry.y, entry.size, entry.size);

        // Copy to independent BufferedImage so the atlas raster isn't pinned
        BufferedImage copy = new BufferedImage(entry.size, entry.size, BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(subImage, 0, 0, null);
        return copy;
    }

    protected InputStream getResource(String path) {
        return getClass().getResourceAsStream(path);
    }

    private record AtlasEntry(int x, int y, int size) {
    }
}

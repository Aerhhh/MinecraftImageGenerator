package net.aerh.jigsaw.core.sprite;

import com.google.gson.Gson;
import net.aerh.jigsaw.api.sprite.SpriteProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A {@link SpriteProvider} that reads sprites from a packed texture atlas image paired with
 * a JSON file that describes each sprite's position and size.
 *
 * <p>Sprites are extracted lazily on first access and then cached in a {@link ConcurrentHashMap}.
 *
 * @see SpriteProvider
 */
public class AtlasSpriteProvider implements SpriteProvider {

    private static final Gson GSON = new Gson();

    private static final String DEFAULT_ATLAS_IMAGE = "minecraft/assets/spritesheets/minecraft_texture_atlas.png";
    private static final String DEFAULT_ATLAS_COORDINATES = "minecraft/assets/json/atlas_coordinates.json";

    private final BufferedImage atlas;
    private final Map<String, ImageCoordinates> coordinates;
    private final ConcurrentHashMap<String, BufferedImage> cache = new ConcurrentHashMap<>();

    /**
     * Creates an {@link AtlasSpriteProvider} from the default bundled atlas and coordinates.
     */
    public static AtlasSpriteProvider fromDefaults() {
        return new AtlasSpriteProvider(DEFAULT_ATLAS_IMAGE, DEFAULT_ATLAS_COORDINATES);
    }

    /**
     * Creates an {@link AtlasSpriteProvider} from the given classpath resources.
     *
     * @param atlasImagePath       classpath path to the packed PNG atlas image
     * @param coordinatesJsonPath  classpath path to the JSON coordinates file
     */
    public AtlasSpriteProvider(String atlasImagePath, String coordinatesJsonPath) {
        Objects.requireNonNull(atlasImagePath, "atlasImagePath must not be null");
        Objects.requireNonNull(coordinatesJsonPath, "coordinatesJsonPath must not be null");

        this.atlas = loadAtlas(atlasImagePath);
        this.coordinates = loadCoordinates(coordinatesJsonPath);
    }

    private static BufferedImage loadAtlas(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = cl.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Atlas image not found on classpath: " + path);
            }
            return ImageIO.read(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read atlas image: " + path, e);
        }
    }

    private static Map<String, ImageCoordinates> loadCoordinates(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = cl.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Atlas coordinates not found on classpath: " + path);
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                ImageCoordinates[] entries = GSON.fromJson(reader, ImageCoordinates[].class);
                return Arrays.stream(entries)
                        .collect(Collectors.toMap(ImageCoordinates::name, c -> c));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read atlas coordinates: " + path, e);
        }
    }

    /**
     * Returns the sprite for the given texture ID, extracting it from the atlas if not yet cached.
     *
     * @param textureId the texture identifier to look up; must not be {@code null}
     *
     * @return an {@link Optional} containing the sprite, or empty if not found in the atlas
     */
    @Override
    public Optional<BufferedImage> getSprite(String textureId) {
        Objects.requireNonNull(textureId, "textureId must not be null");
        ImageCoordinates coord = coordinates.get(textureId);
        if (coord == null) {
            return Optional.empty();
        }
        return Optional.of(cache.computeIfAbsent(textureId, id -> extractSprite(coord)));
    }

    /**
     * Returns an immutable snapshot of all known sprite texture IDs.
     *
     * @return the set of available texture IDs
     */
    @Override
    public Collection<String> availableSprites() {
        return List.copyOf(coordinates.keySet());
    }

    /**
     * Returns the first sprite whose texture ID contains the given query string.
     *
     * @param query the substring to search for; must not be {@code null}
     * @return an {@link Optional} containing the first matching sprite, or empty if none found
     */
    @Override
    public Optional<BufferedImage> search(String query) {
        Objects.requireNonNull(query, "query must not be null");
        return coordinates.keySet().stream()
                .filter(name -> name.contains(query))
                .findFirst()
                .flatMap(this::getSprite);
    }

    /**
     * Returns all sprites whose texture ID contains the given query string (case-insensitive),
     * sorted alphabetically by texture ID.
     *
     * @param query the substring to search for; must not be {@code null}
     * @return an alphabetically ordered list of matching name-to-image entries; never {@code null}
     */
    @Override
    public List<Map.Entry<String, BufferedImage>> searchAll(String query) {
        Objects.requireNonNull(query, "query must not be null");
        String lowerQuery = query.toLowerCase();
        return coordinates.keySet().stream()
                .filter(name -> name.toLowerCase().contains(lowerQuery))
                .sorted()
                .map(name -> (Map.Entry<String, BufferedImage>) new AbstractMap.SimpleImmutableEntry<>(
                        name, getSprite(name).orElse(null)))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toList());
    }

    /**
     * Returns a snapshot of every loaded sprite, keyed by texture ID.
     *
     * <p>The returned map is sorted alphabetically by texture ID and is unmodifiable.
     *
     * @return an unmodifiable, alphabetically sorted map of all texture IDs to their images
     */
    @Override
    public Map<String, BufferedImage> getAllSprites() {
        TreeMap<String, BufferedImage> result = new TreeMap<>();
        for (String id : coordinates.keySet()) {
            getSprite(id).ifPresent(img -> result.put(id, img));
        }
        return Collections.unmodifiableMap(result);
    }

    private BufferedImage extractSprite(ImageCoordinates coord) {
        BufferedImage sub = atlas.getSubimage(coord.x(), coord.y(), coord.size(), coord.size());
        BufferedImage copy = new BufferedImage(coord.size(), coord.size(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(sub, 0, 0, null);
        return copy;
    }
}

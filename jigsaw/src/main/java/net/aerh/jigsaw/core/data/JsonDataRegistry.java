package net.aerh.jigsaw.core.data;

import com.google.gson.Gson;
import net.aerh.jigsaw.api.data.DataRegistry;
import net.aerh.jigsaw.api.data.RegistryKey;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A {@link DataRegistry} that populates itself from a JSON array on the classpath.
 *
 * <p>Keys are stored lower-cased so that {@link #get(String)} is case-insensitive.
 * All map operations are backed by a {@link ConcurrentHashMap} making this registry thread-safe
 * for concurrent reads after construction.
 *
 * @param <T> the type of objects stored in this registry
 */
public class JsonDataRegistry<T> implements DataRegistry<T> {

    private static final Gson GSON = new Gson();

    private final RegistryKey<T> key;
    private final ConcurrentHashMap<String, T> store = new ConcurrentHashMap<>();

    /**
     * Creates and immediately populates the registry by loading {@code resourcePath} from the
     * thread context classloader.
     *
     * @param key           the {@link RegistryKey} that identifies this registry
     * @param arrayType     the array class for Gson deserialisation (e.g. {@code MyType[].class})
     * @param resourcePath  classpath-relative path to the JSON file (e.g. {@code "data/rarities.json"})
     * @param nameExtractor function that derives the lookup key from an item
     */
    public JsonDataRegistry(RegistryKey<T> key, Class<T[]> arrayType, String resourcePath,
                            Function<T, String> nameExtractor) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(arrayType, "arrayType must not be null");
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");
        Objects.requireNonNull(nameExtractor, "nameExtractor must not be null");

        this.key = key;
        load(arrayType, resourcePath, nameExtractor);
    }

    private void load(Class<T[]> arrayType, String resourcePath, Function<T, String> nameExtractor) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + resourcePath);
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                T[] items = GSON.fromJson(reader, arrayType);
                for (T item : items) {
                    String name = nameExtractor.apply(item);
                    store.put(name.toLowerCase(), item);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load registry from: " + resourcePath, e);
        }
    }

    @Override
    public Optional<T> get(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Optional.ofNullable(store.get(id.toLowerCase()));
    }

    @Override
    public Collection<T> values() {
        return store.values();
    }

    @Override
    public RegistryKey<T> key() {
        return key;
    }

    @Override
    public void register(String id, T value) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(value, "value must not be null");
        store.put(id.toLowerCase(), value);
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public boolean isEmpty() {
        return store.isEmpty();
    }
}

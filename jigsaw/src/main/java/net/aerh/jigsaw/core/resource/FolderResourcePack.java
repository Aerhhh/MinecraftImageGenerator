package net.aerh.jigsaw.core.resource;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link ResourcePack} backed by a directory on the filesystem.
 */
public class FolderResourcePack implements ResourcePack {

    private static final Gson GSON = new Gson();

    private final Path root;
    private final PackMetadata metadata;

    public FolderResourcePack(Path root) {
        Objects.requireNonNull(root, "root must not be null");
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }
        this.root = root;
        this.metadata = parseMetadata(root.resolve("pack.mcmeta"));
    }

    @Override
    public Optional<InputStream> getResource(String path) {
        Path resolved = root.resolve(path);
        if (!Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(resolved));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean hasResource(String path) {
        return Files.isRegularFile(root.resolve(path));
    }

    @Override
    public Set<String> listResources(String prefix) {
        Path prefixPath = root.resolve(prefix);
        if (!Files.isDirectory(prefixPath)) {
            return Set.of();
        }
        try (Stream<Path> walk = Files.walk(prefixPath)) {
            return walk
                    .filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return Set.of();
        }
    }

    @Override
    public PackMetadata metadata() {
        return metadata;
    }

    @Override
    public void close() {
        // No resources to close for folder packs
    }

    private static PackMetadata parseMetadata(Path mcmetaPath) {
        if (!Files.isRegularFile(mcmetaPath)) {
            throw new IllegalArgumentException("pack.mcmeta not found: " + mcmetaPath);
        }
        try {
            String json = Files.readString(mcmetaPath);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            JsonObject pack = root.getAsJsonObject("pack");
            if (pack == null) {
                throw new IllegalArgumentException("pack.mcmeta missing 'pack' object");
            }
            int format = pack.get("pack_format").getAsInt();
            String desc = pack.has("description") ? pack.get("description").getAsString() : "";
            return new PackMetadata(format, desc);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read pack.mcmeta: " + mcmetaPath, e);
        }
    }
}

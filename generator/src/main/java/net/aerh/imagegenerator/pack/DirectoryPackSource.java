package net.aerh.imagegenerator.pack;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackLoadException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link PackSource} over an extracted pack directory. Rejects reads that resolve outside the
 * root and files exceeding the per-entry byte cap.
 */
@Slf4j
final class DirectoryPackSource implements PackSource {

    private final Path root;
    private final PackLimits limits;

    DirectoryPackSource(Path root, PackLimits limits) {
        if (!Files.isDirectory(root)) {
            throw new PackLoadException("Pack directory does not exist: %s", root.toString());
        }
        this.root = root.toAbsolutePath().normalize();
        this.limits = limits;
    }

    @Override
    public byte[] read(String assetPath) {
        Path resolved = resolveInsideRoot(assetPath);
        try {
            if (!Files.isRegularFile(resolved)) {
                throw new PackLoadException("Pack file not found: %s", assetPath);
            }
            long size = Files.size(resolved);
            if (size > limits.maxEntryBytes()) {
                throw new PackLoadException("Pack file %s exceeds max entry size (%s > %s bytes)",
                    assetPath, String.valueOf(size), String.valueOf(limits.maxEntryBytes()));
            }
            return Files.readAllBytes(resolved);
        } catch (IOException e) {
            throw new PackLoadException("Failed to read pack file: " + assetPath, e);
        }
    }

    @Override
    public boolean exists(String assetPath) {
        try {
            return Files.isRegularFile(resolveInsideRoot(assetPath));
        } catch (PackLoadException e) {
            return false;
        }
    }

    @Override
    public List<String> list(String prefix) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .filter(path -> path.startsWith(prefix))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new PackLoadException("Failed to list pack directory", e);
        }
    }

    private Path resolveInsideRoot(String assetPath) {
        Path resolved = root.resolve(assetPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new PackLoadException("Pack path escapes pack root: %s", assetPath);
        }
        return resolved;
    }

    @Override
    public void close() {
        // Directory sources hold no OS resources.
    }
}

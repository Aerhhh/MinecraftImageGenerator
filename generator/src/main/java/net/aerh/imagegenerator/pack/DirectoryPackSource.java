package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link PackSource} over an extracted pack directory. Rejects reads that resolve outside the
 * root (including via symlinks) and files exceeding the per-entry byte cap.
 */
final class DirectoryPackSource implements PackSource {

    private final Path root;
    private final Path realRoot;
    private final PackLimits limits;

    DirectoryPackSource(Path root, PackLimits limits) {
        if (!Files.isDirectory(root)) {
            throw new PackLoadException("Pack directory does not exist: %s", root.toString());
        }
        this.root = root.toAbsolutePath().normalize();
        try {
            this.realRoot = root.toRealPath();
        } catch (IOException e) {
            throw new PackLoadException("Failed to resolve pack directory: " + root, e);
        }
        this.limits = limits;
    }

    @Override
    public byte[] read(String assetPath) {
        Path resolved = resolveInsideRoot(assetPath);
        try {
            if (!Files.isRegularFile(resolved)) {
                throw new PackLoadException("Pack file not found: %s", assetPath);
            }
            if (!resolved.toRealPath().startsWith(realRoot)) {
                throw new PackLoadException("Pack path escapes pack root: %s", assetPath);
            }
            try (InputStream in = Files.newInputStream(resolved)) {
                byte[] data = in.readNBytes((int) limits.maxEntryBytes() + 1);
                if (data.length > limits.maxEntryBytes()) {
                    throw new PackLoadException("Pack file %s exceeds max entry size (%s bytes)",
                        assetPath, String.valueOf(limits.maxEntryBytes()));
                }
                return data;
            }
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
            List<String> entries = walk.filter(Files::isRegularFile)
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .filter(path -> path.startsWith(prefix))
                .limit((long) limits.maxEntries() + 1)
                .sorted()
                .toList();
            if (entries.size() > limits.maxEntries()) {
                throw new PackLoadException("Pack directory exceeds max entry count (%s)",
                    String.valueOf(limits.maxEntries()));
            }
            return entries;
        } catch (IOException e) {
            throw new PackLoadException("Failed to list pack directory", e);
        }
    }

    private Path resolveInsideRoot(String assetPath) {
        Path resolved;
        try {
            resolved = root.resolve(assetPath).normalize();
        } catch (InvalidPathException e) {
            throw new PackLoadException("Invalid pack path: " + assetPath, e);
        }
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

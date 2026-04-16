package net.aerh.jigsaw.core.resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A {@link ResourcePack} backed by a zip file.
 */
public class ZipResourcePack implements ResourcePack {

    private final ZipFile zipFile;
    private final PackMetadata metadata;

    public ZipResourcePack(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        ZipFile opened;
        try {
            opened = new ZipFile(path.toFile());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to open zip file: " + path, e);
        }
        this.zipFile = opened;
        try {
            this.metadata = parseMetadata();
        } catch (RuntimeException e) {
            try {
                opened.close();
            } catch (IOException closeEx) {
                e.addSuppressed(closeEx);
            }
            throw e;
        }
    }

    @Override
    public Optional<InputStream> getResource(String path) {
        ZipEntry entry = zipFile.getEntry(path);
        if (entry == null || entry.isDirectory()) {
            return Optional.empty();
        }
        try {
            byte[] data = zipFile.getInputStream(entry).readAllBytes();
            return Optional.of(new ByteArrayInputStream(data));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean hasResource(String path) {
        ZipEntry entry = zipFile.getEntry(path);
        return entry != null && !entry.isDirectory();
    }

    @Override
    public Set<String> listResources(String prefix) {
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        Set<String> result = new HashSet<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(normalizedPrefix)) {
                result.add(entry.getName());
            }
        }
        return result;
    }

    @Override
    public PackMetadata metadata() {
        return metadata;
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    private PackMetadata parseMetadata() {
        ZipEntry mcmeta = zipFile.getEntry("pack.mcmeta");
        if (mcmeta == null) {
            throw new IllegalArgumentException("pack.mcmeta not found in zip");
        }
        try (InputStream is = zipFile.getInputStream(mcmeta)) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return PackMetadata.fromJson(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read pack.mcmeta from zip", e);
        }
    }
}

package net.aerh.imagegenerator.pack;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackLoadException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@link PackSource} over a pack ZIP. Entry names are validated once at construction (zip-slip,
 * entry-count cap); reads enforce the byte cap on actual decompressed bytes because ZIP size
 * headers can lie.
 */
@Slf4j
final class ZipPackSource implements PackSource {

    private final ZipFile zipFile;
    private final PackLimits limits;
    private final Set<String> entryNames;

    ZipPackSource(Path path, PackLimits limits) {
        this.limits = limits;
        try {
            this.zipFile = new ZipFile(path.toFile());
        } catch (IOException e) {
            throw new PackLoadException("Failed to open pack ZIP: " + path, e);
        }
        try {
            this.entryNames = indexEntries();
        } catch (PackLoadException e) {
            closeQuietly();
            throw e;
        }
    }

    private Set<String> indexEntries() {
        Set<String> names = new HashSet<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (name.startsWith("/") || name.contains("\\") || name.contains("..") || name.contains(":")) {
                throw new PackLoadException("Pack ZIP contains unsafe entry name: %s", name);
            }
            names.add(name);
            if (names.size() > limits.maxEntries()) {
                throw new PackLoadException("Pack ZIP exceeds max entry count (%s)",
                    String.valueOf(limits.maxEntries()));
            }
        }
        return names;
    }

    @Override
    public byte[] read(String assetPath) {
        ZipEntry entry = entryNames.contains(assetPath) ? zipFile.getEntry(assetPath) : null;
        if (entry == null) {
            throw new PackLoadException("Pack file not found: %s", assetPath);
        }
        try (InputStream in = zipFile.getInputStream(entry)) {
            byte[] data = in.readNBytes(limits.boundedReadLimit());
            if (data.length > limits.maxEntryBytes()) {
                throw new PackLoadException("Pack file %s exceeds max entry size (%s bytes)",
                    assetPath, String.valueOf(limits.maxEntryBytes()));
            }
            return data;
        } catch (IOException e) {
            throw new PackLoadException("Failed to read pack ZIP entry: " + assetPath, e);
        }
    }

    @Override
    public boolean exists(String assetPath) {
        return entryNames.contains(assetPath);
    }

    @Override
    public List<String> list(String prefix) {
        return entryNames.stream().filter(name -> name.startsWith(prefix)).sorted().toList();
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    private void closeQuietly() {
        try {
            zipFile.close();
        } catch (IOException e) {
            log.warn("Failed to close pack ZIP after indexing error", e);
        }
    }
}

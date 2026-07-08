package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;

/**
 * Byte-level access to a resource pack's files. The single choke point where defensive limits
 * (path traversal, size caps) are enforced for every pack input format.
 */
public interface PackSource extends Closeable {

    /**
     * Reads a pack-relative file (forward-slash separated, e.g. {@code assets/ns/items/x.json}).
     *
     * @throws PackLoadException if the path is missing, escapes the pack, or exceeds
     *                           {@link PackLimits#maxEntryBytes()}
     */
    byte[] read(String assetPath);

    boolean exists(String assetPath);

    /** Lists all file paths starting with the given prefix (forward-slash separated). */
    List<String> list(String prefix);

    static PackSource directory(Path root, PackLimits limits) {
        return new DirectoryPackSource(root, limits);
    }
}

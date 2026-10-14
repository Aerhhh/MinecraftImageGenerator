package net.aerh.imagegenerator.tooling;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Containment checks used before the tooling deletes a directory it was pointed at.
 */
public final class PathGuard {

    private PathGuard() {
    }

    /**
     * True when {@code child} is {@code parent} or lives somewhere beneath it. Both paths are made absolute
     * and normalised first, so {@code a/../a} and {@code a} compare equal.
     */
    public static boolean contains(Path parent, Path child) {
        Path normalisedParent = parent.toAbsolutePath().normalize();
        Path normalisedChild = child.toAbsolutePath().normalize();
        return normalisedChild.startsWith(normalisedParent);
    }

    /**
     * Explains why {@code target} must not be deleted recursively, or returns empty when deleting it is safe.
     * A relative {@code target} is resolved against {@code currentDir}. Deleting the current directory or any
     * of its ancestors is refused.
     */
    public static Optional<String> deletionRefusal(Path target, Path currentDir) {
        Path current = currentDir.toAbsolutePath().normalize();
        Path resolved = current.resolve(target).normalize();

        if (contains(resolved, current)) {
            return Optional.of(resolved + " contains the current directory " + current);
        }

        return Optional.empty();
    }
}

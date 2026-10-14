package net.aerh.imagegenerator.tooling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Debug output and file helpers shared by the tooling module. Directories live in {@link ToolingPaths}.
 */
public final class ToolingSupport {

    private static boolean debugEnabled = false;

    private ToolingSupport() {
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /**
     * Prints a debug message if debug mode is enabled.
     */
    public static void printDebug(String message) {
        if (debugEnabled) {
            System.out.println("[DEBUG] " + message);
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    public static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + p + " - " + e.getMessage());
                    }
                });
        }
    }
}

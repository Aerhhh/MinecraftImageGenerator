package net.aerh.imagegenerator.tooling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Where the tooling reads and writes. Built once per entry point from command line flags, then JVM system
 * properties, then the repository defaults, and passed to every component that touches the file system.
 *
 * @param workDir   scratch space for extracted assets, rendered icons and download caches
 * @param outputDir the generator assets directory that receives spritesheets and JSON
 */
public record ToolingPaths(Path workDir, Path outputDir) {

    public static final String WORK_DIR_FLAG = "--work-dir";
    public static final String OUTPUT_DIR_FLAG = "--output-dir";

    public static final String WORK_DIR_PROPERTY = "generator.tooling.workDir";
    public static final String OUTPUT_DIR_PROPERTY = "generator.tooling.outputDir";

    public static final Path DEFAULT_WORK_DIR = Path.of("tooling/build");
    public static final Path DEFAULT_OUTPUT_DIR = Path.of("generator/src/main/resources/minecraft/assets");

    private static final String JAR_PREFIX = "minecraft-";
    private static final String JAR_SUFFIX = "-client.jar";

    public ToolingPaths {
        Objects.requireNonNull(workDir, "workDir");
        Objects.requireNonNull(outputDir, "outputDir");
    }

    /**
     * The layout the repository's own CI uses: everything under {@code tooling/build}, output into the generator module.
     */
    public static ToolingPaths defaults() {
        return new ToolingPaths(DEFAULT_WORK_DIR, DEFAULT_OUTPUT_DIR);
    }

    /**
     * Resolves each directory from its flag, then its system property, then the default.
     *
     * @throws IllegalArgumentException when a flag is present with a blank value
     */
    public static ToolingPaths fromArgs(String[] args) {
        Path workDir = resolve(args, WORK_DIR_FLAG, WORK_DIR_PROPERTY).orElse(DEFAULT_WORK_DIR);
        Path outputDir = resolve(args, OUTPUT_DIR_FLAG, OUTPUT_DIR_PROPERTY).orElse(DEFAULT_OUTPUT_DIR);
        return new ToolingPaths(workDir, outputDir);
    }

    private static Optional<Path> resolve(String[] args, String flag, String property) {
        Optional<Path> fromFlag = CliArgs.pathValue(args, flag);
        if (fromFlag.isPresent()) {
            return fromFlag;
        }

        String fromProperty = System.getProperty(property);
        if (fromProperty == null || fromProperty.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(Path.of(fromProperty.trim()));
    }

    /**
     * True when the work directory is the repository's own {@code tooling/build}, which the tool may clean
     * up after itself without being asked. Any spelling of that directory counts.
     */
    public boolean isDefaultWorkDir() {
        return workDir.toAbsolutePath().normalize().equals(DEFAULT_WORK_DIR.toAbsolutePath().normalize());
    }

    public Path minecraftAssets() {
        return workDir.resolve("minecraft-assets");
    }

    public Path renderedItems() {
        return workDir.resolve("rendered-items");
    }

    public Path jarCache() {
        return workDir.resolve("minecraft-jars");
    }

    public Path mappingsCache() {
        return workDir.resolve("minecraft-mappings");
    }

    /** Scratch space the icon renderer library may use beside the JAR it is handed. */
    public Path rendererCache() {
        return workDir.resolve("asset-renderer");
    }

    public Path spritesheets() {
        return outputDir.resolve("spritesheets");
    }

    public Path json() {
        return outputDir.resolve("json");
    }

    public Path jarPath(String version) {
        return jarCache().resolve(JAR_PREFIX + version + JAR_SUFFIX);
    }

    public Path mappingsPath(String version) {
        return mappingsCache().resolve("minecraft-" + version + "-client.txt");
    }

    /**
     * The version of the one client JAR in the cache, or empty when none is cached.
     *
     * @throws IOException if several versions are cached, since picking one silently would be a guess
     */
    public Optional<String> cachedClientVersion() throws IOException {
        Path cache = jarCache();
        if (!Files.isDirectory(cache)) {
            return Optional.empty();
        }

        List<String> versions;
        try (Stream<Path> files = Files.list(cache)) {
            versions = files
                .map(file -> file.getFileName().toString())
                .filter(name -> name.startsWith(JAR_PREFIX) && name.endsWith(JAR_SUFFIX))
                .map(name -> name.substring(JAR_PREFIX.length(), name.length() - JAR_SUFFIX.length()))
                .sorted()
                .toList();
        }

        if (versions.isEmpty()) {
            return Optional.empty();
        }

        if (versions.size() > 1) {
            throw new IOException("Several client JARs are cached (" + String.join(", ", versions)
                + "); pass --version to choose one");
        }

        return Optional.of(versions.get(0));
    }

    /**
     * Explains why deleting the work directory would destroy something that is not ours, or returns empty when
     * cleanup is safe. Relative paths are resolved against {@code currentDir}.
     */
    public Optional<String> cleanupRefusal(Path currentDir) {
        Optional<String> currentDirRefusal = PathGuard.deletionRefusal(workDir, currentDir);
        if (currentDirRefusal.isPresent()) {
            return Optional.of("work dir " + currentDirRefusal.get());
        }

        Path current = currentDir.toAbsolutePath().normalize();
        Path work = current.resolve(workDir).normalize();
        Path output = current.resolve(outputDir).normalize();

        if (PathGuard.contains(work, output)) {
            return Optional.of("output dir " + output + " is inside work dir " + work);
        }

        return Optional.empty();
    }
}

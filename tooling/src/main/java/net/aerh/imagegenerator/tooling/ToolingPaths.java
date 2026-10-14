package net.aerh.imagegenerator.tooling;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the tooling reads and writes. Built once per entry point from command line flags, then JVM system
 * properties, then the repository defaults, and passed to every component that touches the file system.
 *
 * @param workDir     scratch space for extracted assets, rendered images and download caches
 * @param outputDir   the generator assets directory that receives spritesheets and JSON
 * @param rendererDir where the MinecraftRenderer checkout lives
 */
public record ToolingPaths(Path workDir, Path outputDir, Path rendererDir) {

    public static final String WORK_DIR_FLAG = "--work-dir";
    public static final String OUTPUT_DIR_FLAG = "--output-dir";
    public static final String RENDERER_DIR_FLAG = "--renderer-dir";

    public static final String WORK_DIR_PROPERTY = "generator.tooling.workDir";
    public static final String OUTPUT_DIR_PROPERTY = "generator.tooling.outputDir";
    public static final String RENDERER_DIR_PROPERTY = "generator.tooling.rendererDir";

    public static final Path DEFAULT_WORK_DIR = Path.of("tooling/build");
    public static final Path DEFAULT_OUTPUT_DIR = Path.of("generator/src/main/resources/minecraft/assets");

    private static final String RENDERER_DIR_NAME = "MinecraftRenderer";

    public ToolingPaths {
        Objects.requireNonNull(workDir, "workDir");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(rendererDir, "rendererDir");
    }

    /**
     * The layout the repository's own CI uses: everything under {@code tooling/build}, output into the generator module.
     */
    public static ToolingPaths defaults() {
        return new ToolingPaths(DEFAULT_WORK_DIR, DEFAULT_OUTPUT_DIR, DEFAULT_WORK_DIR.resolve(RENDERER_DIR_NAME));
    }

    /**
     * Resolves each directory from its flag, then its system property, then the default. The renderer directory
     * defaults to {@code MinecraftRenderer} inside whatever work directory was resolved.
     *
     * @throws IllegalArgumentException when a flag is present with a blank value
     */
    public static ToolingPaths fromArgs(String[] args) {
        Path workDir = resolve(args, WORK_DIR_FLAG, WORK_DIR_PROPERTY).orElse(DEFAULT_WORK_DIR);
        Path outputDir = resolve(args, OUTPUT_DIR_FLAG, OUTPUT_DIR_PROPERTY).orElse(DEFAULT_OUTPUT_DIR);
        Path rendererDir = resolve(args, RENDERER_DIR_FLAG, RENDERER_DIR_PROPERTY)
            .orElse(workDir.resolve(RENDERER_DIR_NAME));
        return new ToolingPaths(workDir, outputDir, rendererDir);
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
     * up after itself without being asked.
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

    public Path spritesheets() {
        return outputDir.resolve("spritesheets");
    }

    public Path json() {
        return outputDir.resolve("json");
    }

    public Path jarPath(String version) {
        return jarCache().resolve("minecraft-" + version + "-client.jar");
    }

    public Path mappingsPath(String version) {
        return mappingsCache().resolve("minecraft-" + version + "-client.txt");
    }

    /**
     * Explains why deleting the work directory would destroy something that is not ours, or returns empty when
     * cleanup is safe. Relative paths are resolved against {@code currentDir}. The renderer checkout at its
     * default location inside the work directory is part of the work directory and may go with it; a renderer
     * directory the caller placed elsewhere inside the work directory is theirs and blocks cleanup.
     */
    public Optional<String> cleanupRefusal(Path currentDir) {
        Optional<String> currentDirRefusal = PathGuard.deletionRefusal(workDir, currentDir);
        if (currentDirRefusal.isPresent()) {
            return Optional.of("work dir " + currentDirRefusal.get());
        }

        Path current = currentDir.toAbsolutePath().normalize();
        Path work = current.resolve(workDir).normalize();
        Path output = current.resolve(outputDir).normalize();
        Path renderer = current.resolve(rendererDir).normalize();

        if (PathGuard.contains(work, output)) {
            return Optional.of("output dir " + output + " is inside work dir " + work);
        }

        if (PathGuard.contains(work, renderer) && !renderer.equals(work.resolve(RENDERER_DIR_NAME))) {
            return Optional.of("renderer dir " + renderer + " is inside work dir " + work);
        }

        return Optional.empty();
    }
}

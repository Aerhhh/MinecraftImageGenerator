package net.aerh.imagegenerator.tooling.minecraft;

import net.aerh.imagegenerator.tooling.CliArgs;
import net.aerh.imagegenerator.tooling.ToolingSupport;
import net.aerh.imagegenerator.tooling.ToolingPaths;
import net.aerh.imagegenerator.tooling.spritesheet.AtlasLayout;
import net.aerh.imagegenerator.tooling.spritesheet.OverlayColorConfigGenerator;
import net.aerh.imagegenerator.tooling.spritesheet.OverlayGenerator;
import net.aerh.imagegenerator.tooling.spritesheet.SpritesheetGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Unified CLI tool for Minecraft asset operations.
 * <p>
 * Supports downloading assets, rendering items/blocks, and generating spritesheets
 * either individually or as a complete pipeline.
 * <p>
 * Usage:
 * <pre>{@code
 * # Run complete pipeline
 * java MinecraftAssetTool --all
 *
 * # Run specific steps
 * java MinecraftAssetTool --download --render --spritesheet
 *
 * # Download only
 * java MinecraftAssetTool --download --version=1.21.4
 *
 * # Skip certain steps in pipeline
 * java MinecraftAssetTool --all --skip-download
 * }</pre>
 * <p>
 * Maven profiles:
 * <pre>{@code
 * mvn compile -P asset-pipeline -pl tooling
 * mvn compile -P download-assets -pl tooling
 * mvn compile -P render-items -pl tooling
 * mvn compile -P generate-spritesheet -pl tooling
 * }</pre>
 */
public class MinecraftAssetTool {

    public static final String SIZE_FLAG = "--size";
    public static final String VERSION_FLAG = "--version";

    private static final int DEFAULT_RENDER_SIZE = 256;

    private static final Set<String> FLAGS = Set.of(
        "--all", "--pipeline", "--download", "--render", "--spritesheet", "--overlays", "--skip-download",
        "--skip-render", "--debug", "--clean", "--no-clean", "--help"
    );
    private static final Set<String> VALUED_FLAGS = Set.of(
        VERSION_FLAG, SIZE_FLAG, AtlasLayout.TILE_SIZE_FLAG, AtlasLayout.ATLAS_WIDTH_FLAG,
        AtlasLayout.ATLAS_HEIGHT_FLAG, AtlasLayout.PADDING_FLAG, ToolingPaths.WORK_DIR_FLAG,
        ToolingPaths.OUTPUT_DIR_FLAG, OverlayGenerator.DEFINITIONS_FLAG
    );

    private boolean runDownload = false;
    private boolean runRender = false;
    private boolean runSpritesheet = false;
    private boolean runOverlays = false;
    private boolean skipDownload = false;
    private boolean skipRender = false;
    private boolean debug = false;

    private String minecraftVersion = null;
    private int renderSize = DEFAULT_RENDER_SIZE;
    private boolean keepJar = true;
    /** Null until {@code --clean} or {@code --no-clean} decides; the default then depends on the work directory. */
    private Boolean cleanWorkDir = null;

    private ToolingPaths paths = ToolingPaths.defaults();
    private AtlasLayout layout = AtlasLayout.DEFAULT;
    private Path overlayDefinitions = null;

    public static void main(String[] args) {
        if (CliArgs.hasFlag(args, "--help")) {
            printHelp();
            return;
        }

        MinecraftAssetTool tool;
        try {
            tool = parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            tool.run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (tool.debug) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    /**
     * Reads every flag and validates the resulting configuration, so a bad layout or render size fails here
     * rather than after a download and render have already run.
     *
     * @throws IllegalArgumentException for a value that cannot be used
     */
    static MinecraftAssetTool parse(String[] args) {
        CliArgs.checkKnown(args, FLAGS, VALUED_FLAGS);

        MinecraftAssetTool tool = new MinecraftAssetTool();
        tool.paths = ToolingPaths.fromArgs(args);
        tool.layout = AtlasLayout.fromArgs(args, AtlasLayout.DEFAULT);
        tool.renderSize = CliArgs.intValue(args, SIZE_FLAG, DEFAULT_RENDER_SIZE);
        tool.minecraftVersion = CliArgs.value(args, VERSION_FLAG)
            .map(String::trim)
            .filter(version -> !version.isEmpty())
            .orElse(null);
        tool.overlayDefinitions = CliArgs.pathValue(args, OverlayGenerator.DEFINITIONS_FLAG).orElse(null);

        if (tool.renderSize <= 0) {
            throw new IllegalArgumentException(SIZE_FLAG + " must be positive, got " + tool.renderSize);
        }

        if (tool.overlayDefinitions != null) {
            try {
                OverlayGenerator.checkDefinitions(tool.overlayDefinitions);
            } catch (IOException e) {
                throw new IllegalArgumentException(OverlayGenerator.DEFINITIONS_FLAG + ": " + e.getMessage(), e);
            }
        }

        for (String arg : args) {
            switch (arg) {
                case "--all", "--pipeline" -> {
                    tool.runDownload = true;
                    tool.runRender = true;
                    tool.runSpritesheet = true;
                    tool.runOverlays = true;
                }
                case "--download" -> tool.runDownload = true;
                case "--render" -> tool.runRender = true;
                case "--spritesheet" -> tool.runSpritesheet = true;
                case "--overlays" -> tool.runOverlays = true;
                case "--skip-download" -> tool.skipDownload = true;
                case "--skip-render" -> tool.skipRender = true;
                case "--debug" -> {
                    tool.debug = true;
                    ToolingSupport.setDebugEnabled(true);
                }
                case "--clean" -> tool.cleanWorkDir = true;
                case "--no-clean" -> tool.cleanWorkDir = false;
                default -> {
                    // Valued flags were read above and checkKnown has already rejected anything else.
                }
            }
        }

        if (!tool.runDownload && !tool.runRender && !tool.runSpritesheet && !tool.runOverlays) {
            tool.runDownload = true;
            tool.runRender = true;
            tool.runSpritesheet = true;
            tool.runOverlays = true;
        }

        return tool;
    }

    private static void printHelp() {
        System.out.println("Minecraft Asset Tool");
        System.out.println("====================");
        System.out.println("Unified tool for downloading, rendering, and generating Minecraft asset spritesheets.");
        System.out.println();
        System.out.println("Usage: java MinecraftAssetTool [steps] [options]");
        System.out.println();
        System.out.println("Steps (can combine multiple):");
        System.out.println("  --all, --pipeline    Run complete pipeline (download + render + spritesheet + overlays)");
        System.out.println("  --download           Download Minecraft assets from Mojang");
        System.out.println("  --render             Render every block and item icon from the client JAR");
        System.out.println("  --spritesheet        Generate spritesheet from rendered images");
        System.out.println("  --overlays           Generate overlay spritesheet and color configs from assets");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --version=<ver>      Minecraft version (default: latest release)");
        System.out.println("  --size=<pixels>      Render size in pixels (default: 256)");
        System.out.println("  --tile-size=<px>     Atlas tile size in pixels (default: 256)");
        System.out.println("  --atlas-width=<px>   Maximum atlas page width (default: 4096)");
        System.out.println("  --atlas-height=<px>  Maximum atlas page height (default: unlimited, one page)");
        System.out.println("  --padding=<px>       Transparent gutter on every side of each tile (default: 0)");
        System.out.println("  --work-dir=<path>    Extracted assets, rendered images and caches (default: tooling/build)");
        System.out.println("  --output-dir=<path>  Generated spritesheets and JSON directory");
        System.out.println("                       (default: generator/src/main/resources/minecraft/assets)");
        System.out.println("  --overlay-definitions=<file>");
        System.out.println("                       Overlay definitions JSON to use instead of the bundled one");
        System.out.println("  --skip-download      Skip download step (use cached assets)");
        System.out.println("  --skip-render        Skip render step (use existing renders)");
        System.out.println("  --clean              Delete the work directory after completion");
        System.out.println("                       (the default for tooling/build; a custom --work-dir is kept unless asked)");
        System.out.println("  --no-clean           Keep the work directory after completion");
        System.out.println("  --debug              Enable debug output");
        System.out.println("  --help               Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java MinecraftAssetTool --all                    # Full pipeline");
        System.out.println("  java MinecraftAssetTool --all --skip-download    # Pipeline without download");
        System.out.println("  java MinecraftAssetTool --download --version=1.21.4");
        System.out.println("  java MinecraftAssetTool --render --size=512");
        System.out.println("  java MinecraftAssetTool --spritesheet --atlas-height=4096 --padding=1");
        System.out.println();
        System.out.println("Maven profiles:");
        System.out.println("  mvn compile -P asset-pipeline -pl tooling");
        System.out.println("  mvn compile -P download-assets -pl tooling [-Dmc.version=1.21.4]");
        System.out.println("  mvn compile -P render-items -pl tooling");
        System.out.println("  mvn compile -P generate-spritesheet -pl tooling");
        System.out.println();
        System.out.println("Prerequisites:");
        System.out.println("  - A JDK and network access to Mojang's servers; icons are rendered in this JVM");
    }

    public void run() throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("Minecraft Asset Tool");
        System.out.println("=".repeat(60));

        printConfig();

        if (runDownload && !skipDownload) {
            runDownloadStep();
        } else if (runDownload) {
            System.out.println("\n[Download] Skipped (--skip-download)");
        }

        if (runRender && !skipRender) {
            runRenderStep();
        } else if (runRender) {
            System.out.println("\n[Render] Skipped (--skip-render)");
        }

        if (runSpritesheet) {
            runSpritesheetStep();
        }

        if (runOverlays) {
            runOverlaysStep();
        }

        if (cleansWorkDir()) {
            cleanWorkDirectory(Path.of("").toAbsolutePath());
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Complete!");
        System.out.println("=".repeat(60));
    }

    ToolingPaths paths() {
        return paths;
    }

    AtlasLayout layout() {
        return layout;
    }

    int renderSize() {
        return renderSize;
    }

    String minecraftVersion() {
        return minecraftVersion;
    }

    Path overlayDefinitions() {
        return overlayDefinitions;
    }

    /**
     * Whether the work directory is deleted at the end of the run. The repository's own {@code tooling/build}
     * is cleaned unless {@code --no-clean} says otherwise; a work directory the caller chose is left alone
     * unless they pass {@code --clean}.
     */
    boolean cleansWorkDir() {
        return cleanWorkDir != null ? cleanWorkDir : paths.isDefaultWorkDir();
    }

    String stepsSummary() {
        StringBuilder sb = new StringBuilder();
        if (runDownload) sb.append(skipDownload ? "download(skipped) " : "download ");
        if (runRender) sb.append(skipRender ? "render(skipped) " : "render ");
        if (runSpritesheet) sb.append("spritesheet ");
        if (runOverlays) sb.append("overlays ");
        return sb.toString().trim();
    }

    private void printConfig() {
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Minecraft version: " + (minecraftVersion != null ? minecraftVersion : "latest"));
        System.out.println("  Render size: " + renderSize + "px");
        System.out.println("  Atlas layout: " + layout.tileSize() + "px tiles, pages up to " + layout.maxWidth() + "x"
            + (layout.maxHeight() == AtlasLayout.UNLIMITED ? "unlimited" : layout.maxHeight()) + ", padding " + layout.padding() + "px");
        System.out.println("  Work dir: " + paths.workDir());
        System.out.println("  Output dir: " + paths.outputDir());
        System.out.println("  Overlay definitions: " + (overlayDefinitions != null ? overlayDefinitions : "bundled"));
        System.out.println("  Clean work dir after run: " + (cleansWorkDir() ? "yes" : "no"));
        System.out.println("  Steps: " + stepsSummary());
        if (debug) {
            System.out.println("  Debug: enabled");
        }
    }

    private void runDownloadStep() throws IOException, InterruptedException {
        System.out.println("\n[Download] Downloading Minecraft assets...");
        System.out.println("-".repeat(40));

        AssetDownloader downloader = new AssetDownloader(paths, minecraftVersion, keepJar, true);
        downloader.setDebug(debug);
        int count = downloader.download();
        this.minecraftVersion = downloader.getResolvedVersion();

        System.out.println("[Download] Extracted " + count + " files");
    }

    /**
     * Renders every block and item icon in this JVM from the cached client JAR. When no version was given and
     * no JAR is cached yet, the download step runs first so the render has something to read; a render that
     * fails ends the run, because packing a missing or stale set of icons afterwards would look like success.
     */
    private void runRenderStep() throws Exception {
        System.out.println("\n[Render] Rendering block and item icons...");
        System.out.println("-".repeat(40));

        if (minecraftVersion == null) {
            minecraftVersion = paths.cachedClientVersion().orElse(null);
        }

        if (minecraftVersion == null || !Files.isRegularFile(paths.jarPath(minecraftVersion))) {
            System.out.println("No cached client JAR for the requested version; downloading first.");
            runDownloadStep();
        }

        if (Files.exists(paths.renderedItems())) {
            System.out.println("Cleaning previous renders: " + paths.renderedItems());
            ToolingSupport.deleteDirectory(paths.renderedItems());
        }

        new ItemIconRenderer(paths).renderAll(paths.renderedItems(), minecraftVersion, renderSize);
    }

    private void runSpritesheetStep() {
        System.out.println("\n[Spritesheet] Generating spritesheets...");
        System.out.println("-".repeat(40));

        new SpritesheetGenerator(paths, layout).generate();
    }

    private void runOverlaysStep() throws IOException {
        System.out.println("\n[Overlays] Generating overlay spritesheets and color configs...");
        System.out.println("-".repeat(40));

        OverlayGenerator generator = overlayDefinitions != null
            ? new OverlayGenerator(paths, overlayDefinitions)
            : new OverlayGenerator(paths);
        generator.generate();
        new OverlayColorConfigGenerator(paths, minecraftVersion).generate();
    }

    /**
     * Deletes the work directory unless doing so would take the current directory, the output or a custom
     * renderer checkout with it. A refused cleanup the caller asked for explicitly is an error; a refused
     * default cleanup is only reported.
     */
    void cleanWorkDirectory(Path currentDir) {
        System.out.println("\n[Cleanup] Removing work directory " + paths.workDir() + "...");

        Optional<String> refusal = paths.cleanupRefusal(currentDir);
        if (refusal.isPresent()) {
            if (Boolean.TRUE.equals(cleanWorkDir)) {
                throw new IllegalStateException("Refusing to clean: " + refusal.get());
            }
            System.err.println("[Cleanup] Skipped: " + refusal.get());
            return;
        }

        try {
            ToolingSupport.deleteDirectory(currentDir.resolve(paths.workDir()));
            System.out.println("[Cleanup] Work directory removed.");
        } catch (IOException e) {
            System.err.println("[Cleanup] Failed to remove work directory: " + e.getMessage());
        }
    }

}

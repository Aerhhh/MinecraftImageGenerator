package net.aerh.imagegenerator.tooling.minecraft;

import net.aerh.imagegenerator.tooling.ToolingConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for rendering Minecraft items and blocks using the MinecraftRenderer .NET library.
 * <p>
 * Handles cloning, building, and running the .NET renderer to generate images.
 * Used by {@link MinecraftAssetTool} for the render step.
 * <p>
 * Prerequisites: .NET SDK 9.0+ and Git.
 *
 * @see MinecraftAssetTool
 * @see <a href="https://github.com/ptlthg/MinecraftRenderer">MinecraftRenderer on GitHub</a>
 */
public class ItemRenderer {

    private static final String REPO_URL = "https://github.com/ptlthg/MinecraftRenderer.git";

    private final Path repoPath;
    private boolean debug;
    private String revision;
    private boolean updateExisting = true;

    public void setRevision(String revision) {
        if (revision != null && !revision.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("Renderer commit must be a full 40-character SHA");
        }
        this.revision = revision;
    }

    public void setUpdateExisting(boolean updateExisting) {
        this.updateExisting = updateExisting;
    }

    public ItemRenderer() {
        this(ToolingConstants.RENDERER_CLONE);
    }

    public ItemRenderer(Path repoPath) {
        this.repoPath = repoPath;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
        ToolingConstants.setDebugEnabled(debug);
    }

    /**
     * Sets up the MinecraftRenderer by cloning and building if necessary.
     *
     * @throws IOException          if setup fails
     * @throws InterruptedException if the process is interrupted
     */
    public void setup() throws IOException, InterruptedException {
        setup(false, false);
    }

    /**
     * Sets up the MinecraftRenderer by cloning and building.
     *
     * @param forceReclone if true, delete and re-clone the repository
     * @param forceRebuild if true, force a clean rebuild
     *
     * @throws IOException          if setup fails
     * @throws InterruptedException if the process is interrupted
     */
    public void setup(boolean forceReclone, boolean forceRebuild) throws IOException, InterruptedException {
        checkPrerequisites();

        if (forceReclone && Files.exists(repoPath)) {
            System.out.println("Force re-clone requested, deleting existing repository...");
            ToolingConstants.deleteDirectory(repoPath);
        }

        if (!isCloned()) {
            cloneRepository();
        } else {
            System.out.println("Repository already cloned at: " + repoPath);
            if (revision == null && updateExisting) pullLatest();
        }

        boolean revisionChanged = revision != null && checkoutRevision();
        if (forceRebuild || revisionChanged || !isBuilt()) {
            buildProject();
        } else {
            System.out.println("Project already built.");
        }
    }

    /**
     * Checks if required tools are available.
     */
    protected void checkPrerequisites() throws IOException, InterruptedException {
        if (!isCommandAvailable("git", "--version")) {
            throw new IllegalStateException("Git is not installed or not in PATH. Please install Git.");
        }

        if (!isCommandAvailable("dotnet", "--version")) {
            throw new IllegalStateException(
                ".NET SDK is not installed or not in PATH. " +
                    "Please install .NET SDK 10.0+ from https://dotnet.microsoft.com/download"
            );
        }

        ToolingConstants.printDebug("Prerequisites check passed.");
    }

    private boolean isCommandAvailable(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (finished) {
                String output = new String(process.getInputStream().readAllBytes());
                ToolingConstants.printDebug(command[0] + " version: " + output.trim().split("\n")[0]);
            }
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the repository is already cloned.
     */
    public boolean isCloned() {
        return Files.exists(repoPath.resolve(".git"));
    }

    /**
     * Checks if the project has been built.
     */
    public boolean isBuilt() {
        return Files.isRegularFile(repoPath.resolve("MinecraftRenderer/bin/Release/net10.0/MinecraftRenderer.dll"));
    }

    /**
     * Clones the MinecraftRenderer repository.
     */
    private void cloneRepository() throws IOException, InterruptedException {
        System.out.println("Cloning MinecraftRenderer repository...");
        Files.createDirectories(repoPath.getParent());

        ProcessBuilder pb = new ProcessBuilder(
            "git", "clone", "--depth", "1", REPO_URL, repoPath.toString()
        );
        runProcess(pb, "Failed to clone repository");

        System.out.println("Repository cloned successfully.");
    }

    /**
     * Pulls the latest changes from the repository.
     */
    private void pullLatest() throws IOException, InterruptedException {
        System.out.println("Pulling latest changes...");

        ProcessBuilder pb = new ProcessBuilder("git", "pull", "--ff-only");
        pb.directory(repoPath.toFile());

        runProcess(pb, "Failed to pull latest changes");
    }

    /**
     * Builds the .NET project.
     */
    private boolean checkoutRevision() throws IOException, InterruptedException {
        String previous = capture("rev-parse", "HEAD");
        ProcessBuilder exists = git("cat-file", "-e", revision + "^{commit}");
        exists.redirectErrorStream(true);
        Process probe = exists.start();
        probe.getInputStream().readAllBytes();
        if (probe.waitFor() != 0) {
            runProcess(git("fetch", "--depth=1", "origin", revision), "Failed to fetch renderer commit");
        }
        // Do not force checkout: caller changes must not be silently overwritten.
        runProcess(git("checkout", "--detach", revision), "Failed to checkout renderer commit");
        return !previous.equalsIgnoreCase(capture("rev-parse", "HEAD"));
    }

    private ProcessBuilder git(String... args) {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(java.util.List.of(args));
        return new ProcessBuilder(command).directory(repoPath.toFile());
    }

    private String capture(String... args) throws IOException, InterruptedException {
        Process process = git(args).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new IOException("Cannot read renderer revision: " + output);
        return output;
    }

    protected void buildProject() throws IOException, InterruptedException {
        System.out.println("Building MinecraftRenderer...");

        ProcessBuilder restorePb = new ProcessBuilder("dotnet", "restore", "MinecraftRenderer/MinecraftRenderer.csproj");
        restorePb.directory(repoPath.toFile());
        runProcess(restorePb, "Failed to restore .NET dependencies");

        ProcessBuilder buildPb = new ProcessBuilder(
            "dotnet", "build", "MinecraftRenderer/MinecraftRenderer.csproj", "--framework", "net10.0",
            "--configuration", "Release",
            "--no-restore"
        );
        buildPb.directory(repoPath.toFile());
        runProcess(buildPb, "Failed to build project");

        System.out.println("Build completed successfully.");
    }

    /**
     * Renders all items to individual PNG files.
     *
     * @param outputPath the directory to write rendered images to
     *
     * @throws IOException          if rendering fails
     * @throws InterruptedException if the process is interrupted
     */
    public void renderAllItems(Path outputPath) throws IOException, InterruptedException {
        renderAllItems(outputPath, null, 256);
    }

    /**
     * Renders all items to individual PNG files.
     *
     * @param outputPath       the directory to write rendered images to
     * @param minecraftVersion the Minecraft version for assets (null for default)
     * @param size             the render size in pixels
     *
     * @throws IOException          if rendering fails
     * @throws InterruptedException if the process is interrupted
     */
    public void renderAllItems(Path outputPath, String minecraftVersion, int size) throws IOException, InterruptedException {
        if (!isBuilt()) {
            throw new IllegalStateException("Project not built. Call setup() first.");
        }

        Path assetsPath = ensureMinecraftAssets(minecraftVersion);
        Files.createDirectories(outputPath);

        Path batchDir = createBatchRenderScript(assetsPath, outputPath, size);
        Path batchProject = batchDir.resolve("BatchRenderer.csproj").toAbsolutePath();

        try {
            System.out.println("Building batch renderer...");
            System.out.println("Project path: " + batchProject);

            ProcessBuilder buildPb = new ProcessBuilder(
                "dotnet", "build",
                batchProject.toString(),
                "--configuration", "Release"
            );
            buildPb.directory(repoPath.toFile());
            runProcess(buildPb, "Failed to build batch renderer");

            System.out.println("Rendering items to: " + outputPath.toAbsolutePath());
            System.out.println("Using assets from: " + assetsPath.toAbsolutePath());

            ProcessBuilder runPb = new ProcessBuilder(
                "dotnet", "run",
                "--project", batchProject.toString(),
                "--configuration", "Release",
                "--no-build"
            );
            runPb.directory(repoPath.toFile());
            runProcessWithOutput(runPb);
        } finally {
            ToolingConstants.deleteDirectory(batchDir);
        }
    }

    /**
     * Creates a C# project that batch renders all items to individual PNGs.
     * Reads template files from resources and substitutes placeholders.
     */
    private Path createBatchRenderScript(Path assetsPath, Path outputPath, int size) throws IOException {
        Path batchDir = repoPath.resolve("BatchRenderer_" + System.currentTimeMillis());
        Files.createDirectories(batchDir);

        try (InputStream csprojStream = getClass().getResourceAsStream("/renderer/BatchRenderer.csproj")) {
            if (csprojStream == null) {
                throw new IOException("Could not find BatchRenderer.csproj in resources");
            }
            Files.copy(csprojStream, batchDir.resolve("BatchRenderer.csproj"));
        }

        String programTemplate;
        try (InputStream programStream = getClass().getResourceAsStream("/renderer/Program.cs")) {
            if (programStream == null) {
                throw new IOException("Could not find Program.cs in resources");
            }
            programTemplate = new String(programStream.readAllBytes());
        }

        String program = programTemplate
            .replace("{{DATA_PATH}}", assetsPath.toAbsolutePath().toString().replace("\\", "\\\\"))
            .replace("{{OUTPUT_PATH}}", outputPath.toAbsolutePath().toString().replace("\\", "\\\\"))
            .replace("{{SIZE}}", String.valueOf(size));

        Files.writeString(batchDir.resolve("Program.cs"), program);

        return batchDir;
    }

    /**
     * Ensures Minecraft assets are downloaded and returns the path to them.
     */
    private Path ensureMinecraftAssets(String version) throws IOException, InterruptedException {
        Path assetsPath = ToolingConstants.MINECRAFT_ASSETS;

        if (Files.exists(assetsPath.resolve("models")) && Files.exists(assetsPath.resolve("textures"))) {
            System.out.println("Minecraft assets already downloaded.");
            return assetsPath;
        }

        System.out.println("Downloading Minecraft assets (this may take a moment)...");
        AssetDownloader downloader = new AssetDownloader(
            version,
            assetsPath,
            true,   // keep JAR (for caching)
            true    // clean first
        );
        downloader.setDebug(debug);
        downloader.download();

        return assetsPath;
    }

    private void runProcess(ProcessBuilder pb, String errorMessage) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ToolingConstants.printDebug(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(errorMessage + " (exit code: " + exitCode + ")");
        }
    }

    private void runProcessWithOutput(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Process failed with exit code: " + exitCode);
        }
    }

}

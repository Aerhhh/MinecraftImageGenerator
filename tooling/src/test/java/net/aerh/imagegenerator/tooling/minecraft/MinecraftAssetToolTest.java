package net.aerh.imagegenerator.tooling.minecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftAssetToolTest {

    @TempDir
    Path root;

    @Test
    void renderSizeDoesNotChangeTheAtlasTileSize() {
        MinecraftAssetTool tool = MinecraftAssetTool.parse(new String[]{"--size=64"});
        assertEquals(64, tool.renderSize());
        assertEquals(256, tool.layout().tileSize());
    }

    @Test
    void tileSizeIsSetExplicitly() {
        MinecraftAssetTool tool = MinecraftAssetTool.parse(new String[]{"--tile-size=64"});
        assertEquals(64, tool.layout().tileSize());
        assertEquals(256, tool.renderSize());
    }

    @Test
    void layoutFlagsAreValidatedBeforeAnythingRuns() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MinecraftAssetTool.parse(new String[]{"--padding=-1"}));
        assertEquals("Padding must be non-negative, got -1", error.getMessage());
    }

    @Test
    void renderSizeMustBePositive() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MinecraftAssetTool.parse(new String[]{"--size=0"}));
        assertEquals("--size must be positive, got 0", error.getMessage());
    }

    @Test
    void blankVersionMeansLatest() {
        assertNull(MinecraftAssetTool.parse(new String[]{"--version="}).minecraftVersion());
        assertEquals("1.21.4", MinecraftAssetTool.parse(new String[]{"--version=1.21.4"}).minecraftVersion());
    }

    @Test
    void versionIsTrimmed() {
        assertEquals("26.3", MinecraftAssetTool.parse(new String[]{"--version= 26.3 "}).minecraftVersion());
    }

    @Test
    void unknownOptionsAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MinecraftAssetTool.parse(new String[]{"--spritesheet", "--atlas-hieght=2048"}));
        assertEquals("Unknown option: --atlas-hieght=2048", error.getMessage());
    }

    @Test
    void aValuedOptionWithoutAValueIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MinecraftAssetTool.parse(new String[]{"--size"}));
        assertEquals("--size needs a value, written as --size=<value>", error.getMessage());
    }

    @Test
    void aValidOverlayDefinitionsFileIsAccepted() throws IOException {
        Path definitions = root.resolve("defs.json");
        Files.writeString(definitions, "{\"specialOverlays\": [], \"armorOverlayPatterns\": [\"wolf_\"]}");
        MinecraftAssetTool tool = MinecraftAssetTool.parse(new String[]{"--overlay-definitions=" + definitions});
        assertEquals(definitions, tool.overlayDefinitions());
        assertNull(MinecraftAssetTool.parse(new String[]{}).overlayDefinitions());
    }

    @Test
    void aMissingOverlayDefinitionsFileFailsAtParseTime() {
        Path definitions = root.resolve("missing.json");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MinecraftAssetTool.parse(new String[]{"--overlay-definitions=" + definitions}));
        assertTrue(error.getMessage().contains("missing.json"), error.getMessage());
    }

    @Test
    void aMalformedOverlayDefinitionsFileFailsAtParseTime() throws IOException {
        Path definitions = root.resolve("broken.json");
        Files.writeString(definitions, "{ not json");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MinecraftAssetTool.parse(new String[]{"--overlay-definitions=" + definitions}));
        assertTrue(error.getMessage().contains("broken.json"), error.getMessage());
    }

    @Test
    void anAbsolutePathToTheRepositoryWorkDirectoryStillCleansByDefault() {
        Path absolute = Path.of("tooling/build").toAbsolutePath();
        assertTrue(MinecraftAssetTool.parse(new String[]{"--work-dir=" + absolute}).cleansWorkDir());
    }

    @Test
    void noStepFlagsMeansTheWholePipeline() {
        assertEquals("download render spritesheet overlays", MinecraftAssetTool.parse(new String[]{}).stepsSummary());
        assertEquals("spritesheet", MinecraftAssetTool.parse(new String[]{"--spritesheet"}).stepsSummary());
    }

    @Test
    void cleanupIsOnByDefaultForTheRepositoryWorkDirectory() {
        assertTrue(MinecraftAssetTool.parse(new String[]{}).cleansWorkDir());
    }

    @Test
    void cleanupIsOffByDefaultForACustomWorkDirectory() {
        assertFalse(MinecraftAssetTool.parse(new String[]{"--work-dir=build/w"}).cleansWorkDir());
    }

    @Test
    void cleanFlagTurnsCleanupOnForACustomWorkDirectory() {
        assertTrue(MinecraftAssetTool.parse(new String[]{"--work-dir=build/w", "--clean"}).cleansWorkDir());
    }

    @Test
    void noCleanFlagTurnsCleanupOff() {
        assertFalse(MinecraftAssetTool.parse(new String[]{"--no-clean"}).cleansWorkDir());
    }

    @Test
    void cleanupDeletesTheWorkDirectory() throws IOException {
        Path project = root.resolve("project");
        Path workDir = project.resolve("tooling/build");
        Files.createDirectories(workDir.resolve("rendered-items"));
        Files.writeString(workDir.resolve("rendered-items/stone.png"), "x");

        MinecraftAssetTool.parse(new String[]{}).cleanWorkDirectory(project);

        assertFalse(Files.exists(workDir));
    }

    @Test
    void anExplicitlyRequestedCleanupThatWouldDeleteTheCurrentDirectoryFails() throws IOException {
        Path project = root.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve("keep.txt"), "x");
        MinecraftAssetTool tool = MinecraftAssetTool.parse(new String[]{"--work-dir=.", "--clean"});

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> tool.cleanWorkDirectory(project));

        assertTrue(error.getMessage().contains("contains the current directory"), error.getMessage());
        assertTrue(Files.exists(project.resolve("keep.txt")));
    }

    @Test
    void anImplicitCleanupThatIsRefusedOnlyWarns() throws IOException {
        Path project = root.resolve("project");
        Path output = project.resolve("tooling/build/out");
        Files.createDirectories(output);
        Files.writeString(output.resolve("atlas.png"), "x");
        MinecraftAssetTool tool = MinecraftAssetTool.parse(new String[]{"--output-dir=tooling/build/out"});
        assertTrue(tool.cleansWorkDir(), "default work dir still cleans by default");

        tool.cleanWorkDirectory(project);

        assertTrue(Files.exists(output.resolve("atlas.png")), "output inside the work dir must survive");
    }
}

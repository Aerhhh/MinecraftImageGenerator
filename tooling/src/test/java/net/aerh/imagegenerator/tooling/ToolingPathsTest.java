package net.aerh.imagegenerator.tooling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolingPathsTest {

    @TempDir
    Path root;

    @Test
    void defaultsUseTheRepositoryLayout() {
        ToolingPaths paths = ToolingPaths.defaults();
        assertEquals(Path.of("tooling/build"), paths.workDir());
        assertEquals(Path.of("generator/src/main/resources/minecraft/assets"), paths.outputDir());
        assertEquals(Path.of("tooling/build/MinecraftRenderer"), paths.rendererDir());
    }

    @Test
    void derivedPathsHangOffTheWorkAndOutputDirectories() {
        ToolingPaths paths = new ToolingPaths(Path.of("w"), Path.of("o"), Path.of("r"));
        assertEquals(Path.of("w/minecraft-assets"), paths.minecraftAssets());
        assertEquals(Path.of("w/rendered-items"), paths.renderedItems());
        assertEquals(Path.of("w/minecraft-jars"), paths.jarCache());
        assertEquals(Path.of("w/minecraft-mappings"), paths.mappingsCache());
        assertEquals(Path.of("o/spritesheets"), paths.spritesheets());
        assertEquals(Path.of("o/json"), paths.json());
    }

    @Test
    void cachedJarAndMappingsFilesAreNamedByVersion() {
        ToolingPaths paths = new ToolingPaths(Path.of("w"), Path.of("o"), Path.of("r"));
        assertEquals(Path.of("w/minecraft-jars/minecraft-1.21.4-client.jar"), paths.jarPath("1.21.4"));
        assertEquals(Path.of("w/minecraft-mappings/minecraft-1.21.4-client.txt"), paths.mappingsPath("1.21.4"));
    }

    @Test
    void flagsOverrideEveryDirectory() {
        String[] args = {"--work-dir=build/w", "--output-dir=build/o", "--renderer-dir=build/r"};
        ToolingPaths paths = ToolingPaths.fromArgs(args);
        assertEquals(Path.of("build/w"), paths.workDir());
        assertEquals(Path.of("build/o"), paths.outputDir());
        assertEquals(Path.of("build/r"), paths.rendererDir());
    }

    @Test
    void rendererDirectoryFollowsACustomWorkDirectoryWhenNotGiven() {
        ToolingPaths paths = ToolingPaths.fromArgs(new String[]{"--work-dir=build/w"});
        assertEquals(Path.of("build/w/MinecraftRenderer"), paths.rendererDir());
    }

    @Test
    void systemPropertiesApplyWhenFlagsAreAbsent() {
        System.setProperty(ToolingPaths.WORK_DIR_PROPERTY, "prop/w");
        System.setProperty(ToolingPaths.OUTPUT_DIR_PROPERTY, "prop/o");
        System.setProperty(ToolingPaths.RENDERER_DIR_PROPERTY, "prop/r");
        try {
            ToolingPaths paths = ToolingPaths.fromArgs(new String[]{});
            assertEquals(Path.of("prop/w"), paths.workDir());
            assertEquals(Path.of("prop/o"), paths.outputDir());
            assertEquals(Path.of("prop/r"), paths.rendererDir());
        } finally {
            System.clearProperty(ToolingPaths.WORK_DIR_PROPERTY);
            System.clearProperty(ToolingPaths.OUTPUT_DIR_PROPERTY);
            System.clearProperty(ToolingPaths.RENDERER_DIR_PROPERTY);
        }
    }

    @Test
    void flagsBeatSystemProperties() {
        System.setProperty(ToolingPaths.WORK_DIR_PROPERTY, "prop/w");
        try {
            assertEquals(Path.of("flag/w"), ToolingPaths.fromArgs(new String[]{"--work-dir=flag/w"}).workDir());
        } finally {
            System.clearProperty(ToolingPaths.WORK_DIR_PROPERTY);
        }
    }

    @Test
    void anEmptyDirectoryFlagIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> ToolingPaths.fromArgs(new String[]{"--output-dir="}));
        assertEquals("--output-dir must not be empty", error.getMessage());
    }

    @Test
    void aBlankSystemPropertyCountsAsUnset() {
        System.setProperty(ToolingPaths.WORK_DIR_PROPERTY, "  ");
        try {
            assertEquals(Path.of("tooling/build"), ToolingPaths.fromArgs(new String[]{}).workDir());
        } finally {
            System.clearProperty(ToolingPaths.WORK_DIR_PROPERTY);
        }
    }

    @Test
    void workDirectoryCountsAsDefaultOnlyWhenItIsTheRepositoryBuildDirectory() {
        assertTrue(ToolingPaths.defaults().isDefaultWorkDir());
        assertTrue(ToolingPaths.fromArgs(new String[]{"--work-dir=tooling/build"}).isDefaultWorkDir());
        assertFalse(ToolingPaths.fromArgs(new String[]{"--work-dir=build/w"}).isDefaultWorkDir());
    }

    @Test
    void dottedAndAbsoluteSpellingsOfTheBuildDirectoryAreStillDefault() {
        assertTrue(ToolingPaths.fromArgs(new String[]{"--work-dir=./tooling/build"}).isDefaultWorkDir());
        Path absolute = Path.of("tooling/build").toAbsolutePath();
        assertTrue(ToolingPaths.fromArgs(new String[]{"--work-dir=" + absolute}).isDefaultWorkDir());
    }

    @Test
    void cleanupIsAllowedForTheDefaultLayout() {
        assertEquals(Optional.empty(), ToolingPaths.defaults().cleanupRefusal(root.resolve("project")));
    }

    @Test
    void cleanupIsRefusedWhenTheWorkDirectoryIsTheCurrentDirectory() {
        ToolingPaths paths = ToolingPaths.fromArgs(new String[]{"--work-dir=."});
        Optional<String> refusal = paths.cleanupRefusal(root.resolve("project"));
        assertTrue(refusal.isPresent());
        assertTrue(refusal.get().contains("contains the current directory"), refusal.get());
    }

    @Test
    void cleanupIsRefusedWhenTheWorkDirectoryIsAnAncestorOfTheCurrentDirectory() {
        ToolingPaths paths = ToolingPaths.fromArgs(new String[]{"--work-dir=" + root});
        assertTrue(paths.cleanupRefusal(root.resolve("project")).isPresent());
    }

    @Test
    void cleanupIsRefusedWhenTheOutputDirectoryIsInsideTheWorkDirectory() {
        ToolingPaths paths = ToolingPaths.fromArgs(new String[]{"--work-dir=build/26.3", "--output-dir=build/26.3/generated"});
        Optional<String> refusal = paths.cleanupRefusal(root.resolve("project"));
        assertTrue(refusal.isPresent());
        assertTrue(refusal.get().contains("output dir"), refusal.get());
    }

    @Test
    void cleanupIsRefusedWhenACustomRendererDirectoryIsInsideTheWorkDirectory() {
        ToolingPaths paths = ToolingPaths.fromArgs(new String[]{"--work-dir=build/w", "--renderer-dir=build/w/renderer"});
        Optional<String> refusal = paths.cleanupRefusal(root.resolve("project"));
        assertTrue(refusal.isPresent());
        assertTrue(refusal.get().contains("renderer dir"), refusal.get());
    }

    @Test
    void cleanupIsAllowedWhenTheRendererDirectoryIsAtItsDefaultLocationInsideTheWorkDirectory() {
        ToolingPaths paths = ToolingPaths.fromArgs(new String[]{"--work-dir=build/w", "--renderer-dir=build/w/MinecraftRenderer"});
        assertEquals(Optional.empty(), paths.cleanupRefusal(root.resolve("project")));
    }

    @Test
    void cleanupIsAllowedWhenTheRendererDirectoryIsOutsideTheWorkDirectory() {
        ToolingPaths paths = ToolingPaths.fromArgs(new String[]{"--work-dir=build/w", "--renderer-dir=renderer"});
        assertEquals(Optional.empty(), paths.cleanupRefusal(root.resolve("project")));
    }
}

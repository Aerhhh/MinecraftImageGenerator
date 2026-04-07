package net.aerh.jigsaw.skyblock.engine;

import net.aerh.jigsaw.api.Engine;
import net.aerh.jigsaw.core.resource.PackMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class EngineManagerTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a zip resource pack with a pack.mcmeta and a synthetic test_item texture + model.
     */
    private Path createTestPack(String name, int format, String description) throws IOException {
        Path zipPath = tempDir.resolve(name + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            // pack.mcmeta
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            String mcmeta = """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "%s"
                  }
                }
                """.formatted(format, description);
            zos.write(mcmeta.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Item model JSON
            zos.putNextEntry(new ZipEntry("assets/minecraft/models/item/test_item.json"));
            String modelJson = """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "minecraft:item/test_item"
                  }
                }
                """;
            zos.write(modelJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 16x16 solid green PNG texture
            zos.putNextEntry(new ZipEntry("assets/minecraft/textures/item/test_item.png"));
            zos.write(createSolidGreenPng16x16());
            zos.closeEntry();
        }
        return zipPath;
    }

    /**
     * Writes a byte sequence that is not a valid zip to a {@code .zip} file.
     */
    private Path createCorruptZip(String name) throws IOException {
        Path zipPath = tempDir.resolve(name + ".zip");
        Files.writeString(zipPath, "this is not a zip file");
        return zipPath;
    }

    /**
     * Produces the raw bytes of a 16x16 solid green PNG image.
     */
    private byte[] createSolidGreenPng16x16() throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, Color.GREEN.getRGB());
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    @Test
    void loadPacks_loadsZipsFromDirectory() throws IOException {
        createTestPack("alpha", 34, "Alpha pack");
        createTestPack("beta", 34, "Beta pack");

        try (EngineManager manager = new EngineManager(tempDir, null, false)) {
            assertThat(manager.availablePackNames()).containsExactlyInAnyOrder("alpha", "beta");
        }
    }

    @Test
    void getEngine_returnsNamedEngine() throws IOException {
        createTestPack("mypack", 34, "My Pack");

        try (EngineManager manager = new EngineManager(tempDir, null, false)) {
            Engine engine = manager.getEngine("mypack");
            assertThat(engine).isNotNull();
            assertThat(engine.sprites().getSprite("test_item")).isPresent();
        }
    }

    @Test
    void getEngine_isCaseInsensitive() throws IOException {
        createTestPack("mypack", 34, "My Pack");

        try (EngineManager manager = new EngineManager(tempDir, null, false)) {
            Engine lower = manager.getEngine("mypack");
            Engine upper = manager.getEngine("MYPACK");
            Engine mixed = manager.getEngine("MyPack");

            assertThat(lower).isSameAs(upper).isSameAs(mixed);
        }
    }

    @Test
    void getEngine_unknownNameFallsBackToDefault() throws IOException {
        createTestPack("mypack", 34, "My Pack");

        try (EngineManager manager = new EngineManager(tempDir, "mypack", false)) {
            Engine unknown = manager.getEngine("nonexistent_pack");
            Engine defaultEngine = manager.getDefaultEngine();
            assertThat(unknown).isSameAs(defaultEngine);
        }
    }

    @Test
    void getEngine_nullReturnDefault() throws IOException {
        createTestPack("mypack", 34, "My Pack");

        try (EngineManager manager = new EngineManager(tempDir, "mypack", false)) {
            Engine fromNull = manager.getEngine(null);
            Engine defaultEngine = manager.getDefaultEngine();
            assertThat(fromNull).isSameAs(defaultEngine);
        }
    }

    // -------------------------------------------------------------------------
    // Default pack selection
    // -------------------------------------------------------------------------

    @Test
    void defaultPack_usedWhenConfigured() throws IOException {
        createTestPack("thedefault", 34, "Default Pack");

        try (EngineManager manager = new EngineManager(tempDir, "thedefault", false)) {
            Engine defaultEngine = manager.getDefaultEngine();
            // The default engine should resolve our custom item
            assertThat(defaultEngine.sprites().getSprite("test_item")).isPresent();
        }
    }

    @Test
    void defaultPack_fallsBackToVanillaWhenNotFound() throws IOException {
        // No packs in directory; default pack name references something that doesn't exist
        try (EngineManager manager = new EngineManager(tempDir, "nonexistent", false)) {
            Engine defaultEngine = manager.getDefaultEngine();
            // Vanilla engine should still work - it can render diamond_sword
            assertThat(defaultEngine).isNotNull();
            assertThat(defaultEngine.sprites().getSprite("diamond_sword")).isPresent();
        }
    }

    @Test
    void emptyDirectory_onlyVanillaAvailable() {
        try (EngineManager manager = new EngineManager(tempDir, null, true)) {
            assertThat(manager.availablePackNames()).isEmpty();
            // Vanilla engine should still serve items
            Engine engine = manager.getDefaultEngine();
            assertThat(engine.sprites().getSprite("diamond_sword")).isPresent();
        }
    }

    // -------------------------------------------------------------------------
    // Resilience
    // -------------------------------------------------------------------------

    @Test
    void corruptZip_skippedWithoutCrashing() throws IOException {
        createCorruptZip("badpack");
        createTestPack("goodpack", 34, "Good Pack");

        try (EngineManager manager = new EngineManager(tempDir, null, false)) {
            // Only the good pack is loaded; corrupt pack is silently skipped
            assertThat(manager.availablePackNames()).containsExactly("goodpack");
        }
    }

    @Test
    void directoryDoesNotExist_createdAutomatically() {
        Path nonExistent = tempDir.resolve("packs/sub/dir");

        try (EngineManager manager = new EngineManager(nonExistent, null, false)) {
            assertThat(Files.isDirectory(nonExistent)).isTrue();
            assertThat(manager.availablePackNames()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Reload
    // -------------------------------------------------------------------------

    @Test
    void reload_picksUpNewPacks() throws IOException {
        try (EngineManager manager = new EngineManager(tempDir, null, false)) {
            assertThat(manager.availablePackNames()).isEmpty();

            // Add a pack after construction
            createTestPack("newpack", 34, "New Pack");
            manager.reload();

            assertThat(manager.availablePackNames()).containsExactly("newpack");
        }
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    @Test
    void getPackMetadata_returnsMetadata() throws IOException {
        createTestPack("metapack", 42, "Meta Description");

        try (EngineManager manager = new EngineManager(tempDir, null, false)) {
            Optional<PackMetadata> meta = manager.getPackMetadata("metapack");
            assertThat(meta).isPresent();
            assertThat(meta.get().packFormat()).isEqualTo(42);
            assertThat(meta.get().description()).isEqualTo("Meta Description");
        }
    }

    @Test
    void getPackMetadata_emptyForUnknownPack() {
        try (EngineManager manager = new EngineManager(tempDir, null, false)) {
            assertThat(manager.getPackMetadata("does_not_exist")).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Vanilla fallback
    // -------------------------------------------------------------------------

    @Test
    void vanillaFallback_enabledMeansVanillaItemsAvailable() throws IOException {
        createTestPack("mypack", 34, "My Pack");

        try (EngineManager manager = new EngineManager(tempDir, null, true)) {
            Engine engine = manager.getEngine("mypack");
            // Custom item from the pack
            assertThat(engine.sprites().getSprite("test_item")).isPresent();
            // Vanilla item available via fallback
            assertThat(engine.sprites().getSprite("diamond_sword")).isPresent();
        }
    }

    @Test
    void vanillaFallback_disabledMeansOnlyPackItems() throws IOException {
        createTestPack("mypack", 34, "My Pack");

        try (EngineManager manager = new EngineManager(tempDir, null, false)) {
            Engine engine = manager.getEngine("mypack");
            // Custom item available
            assertThat(engine.sprites().getSprite("test_item")).isPresent();
            // Vanilla item NOT available (pack only has test_item)
            assertThat(engine.sprites().getSprite("diamond_sword")).isEmpty();
        }
    }
}

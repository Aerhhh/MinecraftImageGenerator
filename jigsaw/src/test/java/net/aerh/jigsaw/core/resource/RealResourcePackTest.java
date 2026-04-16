package net.aerh.jigsaw.core.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that exercise resource pack loading against a real Minecraft resource pack
 * downloaded from the internet. Tests are skipped by default - set the environment variable
 * {@code JIGSAW_REAL_PACK_TESTS=true} to enable them.
 */
@EnabledIfEnvironmentVariable(named = "JIGSAW_REAL_PACK_TESTS", matches = "true")
class RealResourcePackTest {

    private static final String PACK_URL =
            "https://cdn.modrinth.com/data/p6lVqTvA/versions/sjnZTyP1/Hypixel%2B%200.23.4%20for%201.21.8.zip";

    @TempDir
    Path tempDir;

    private ZipResourcePack downloadPack() throws Exception {
        Path packPath = tempDir.resolve("test-pack.zip");
        try (InputStream in = URI.create(PACK_URL).toURL().openStream()) {
            Files.copy(in, packPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return new ZipResourcePack(packPath);
    }

    // -------------------------------------------------------------------------
    // Pack loading and metadata
    // -------------------------------------------------------------------------

    @Test
    void metadata_parsesArrayDescription() throws Exception {
        try (ZipResourcePack pack = downloadPack()) {
            PackMetadata meta = pack.metadata();

            assertThat(meta.packFormat()).isEqualTo(55);
            assertThat(meta.description()).contains("Hypixel+");
            assertThat(meta.description()).contains("Created by ic22487");
        }
    }

    // -------------------------------------------------------------------------
    // Item definition resolution
    // -------------------------------------------------------------------------

    @Test
    void resolve_deeplyNestedConditions_doesNotStackOverflow() throws Exception {
        try (ZipResourcePack pack = downloadPack()) {
            ModelResolver resolver = new ModelResolver();

            // allium has 4 levels of condition nesting
            assertThat(pack.hasResource("assets/minecraft/items/allium.json")).isTrue();
            Optional<ItemModelData> result = resolver.resolve(pack, "allium");
            // Model file itself isn't in this pack, but definition parsing shouldn't throw
            assertThat(result).isEmpty();

            // diamond_sword has select -> 20+ nested conditions
            assertThat(pack.hasResource("assets/minecraft/items/diamond_sword.json")).isTrue();
            result = resolver.resolve(pack, "diamond_sword");
            assertThat(result).isEmpty();

            // bow has select -> 40+ nested conditions
            assertThat(pack.hasResource("assets/minecraft/items/bow.json")).isTrue();
            result = resolver.resolve(pack, "bow");
            assertThat(result).isEmpty();
        }
    }

    @Test
    void availableSprites_includesItemDefinitions() throws Exception {
        try (ZipResourcePack pack = downloadPack()) {
            ResourcePackSpriteProvider provider = new ResourcePackSpriteProvider(pack);

            Collection<String> available = provider.availableSprites();

            assertThat(available.size()).isGreaterThan(300);
            assertThat(available).contains("diamond_sword", "bow", "allium");
        }
    }

    @Test
    void listResources_findsItemDefinitions() throws Exception {
        try (ZipResourcePack pack = downloadPack()) {
            Set<String> items = pack.listResources("assets/minecraft/items");

            assertThat(items).isNotEmpty();
            assertThat(items).anyMatch(p -> p.endsWith("diamond_sword.json"));
        }
    }
}

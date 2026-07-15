package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Container background resolution policy on {@link PackRepository} / LoadedPack: present
 * resolves, absent is empty (callers fall back to procedural chrome), broken fails loudly, and
 * a pack shipping ONLY a container texture still registers.
 */
class LoadedPackContainerTest {

    @TempDir
    Path packDir;

    @Test
    void containerTextureOnlyPackRegistersAndResolves() {
        FixturePacks.writeContainerArtPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = repository.register("test:artonly",
            PackSource.directory(packDir, PackLimits.fromSystemProperties()));

        Optional<BufferedImage> background = repository.resolveContainerBackground(packId);
        assertTrue(background.isPresent(), "generic_54 override resolves");
        assertEquals(256, background.get().getWidth());
        assertEquals(256, background.get().getHeight());
        assertEquals(0xFFFF0000, background.get().getRGB(0, 0), "texture decoded as authored");
    }

    @Test
    void packWithoutContainerTextureResolvesEmpty() {
        FixturePacks.writeFontPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = repository.register("test:nofontcontainer",
            PackSource.directory(packDir, PackLimits.fromSystemProperties()));

        assertTrue(repository.resolveContainerBackground(packId).isEmpty(),
            "no generic_54 override means empty, not an error");
    }

    @Test
    void brokenContainerTextureFailsLoudly() throws IOException {
        Files.writeString(packDir.resolve("pack.mcmeta"), """
            {"pack":{"pack_format":88,"description":"broken container texture fixture"}}""");
        Path texture = packDir.resolve("assets/minecraft/textures/gui/container/generic_54.png");
        Files.createDirectories(texture.getParent());
        Files.write(texture, new byte[] {1, 2, 3, 4});

        PackRepository repository = new PackRepository();
        PackId packId = repository.register("test:brokencontainer",
            PackSource.directory(packDir, PackLimits.fromSystemProperties()));

        assertThrows(PackResolveException.class, () -> repository.resolveContainerBackground(packId),
            "a present-but-broken texture is a loud error, not a silent fallback");
    }

    @Test
    void unregisteredPackFailsLoudly() {
        PackRepository repository = new PackRepository();
        assertThrows(PackResolveException.class,
            () -> repository.resolveContainerBackground(PackId.parse("test:never")));
    }

    @Test
    void resolvedBackgroundIsADefensiveCopy() {
        FixturePacks.writeContainerArtPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = repository.register("test:artcopy",
            PackSource.directory(packDir, PackLimits.fromSystemProperties()));

        BufferedImage first = repository.resolveContainerBackground(packId).orElseThrow();
        first.setRGB(0, 0, 0xFF123456);
        BufferedImage second = repository.resolveContainerBackground(packId).orElseThrow();
        assertEquals(0xFFFF0000, second.getRGB(0, 0), "callers cannot mutate the cached texture");
    }
}

package net.aerh.imagegenerator.tooling.minecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorExtractorTest {

    private static final String DYE_COLOR = "net.minecraft.world.item.DyeColor";

    @TempDir
    Path directory;

    @Test
    void withoutMappingsClassNamesAreUsedAsTheyAre() throws IOException {
        Path jar = writeJar("net/minecraft/world/item/DyeColor.class");

        Map<String, String> resolved = new ColorExtractor(jar).resolveClassNames();

        assertEquals(DYE_COLOR, resolved.get(DYE_COLOR));
        assertEquals("net.minecraft.world.effect.MobEffects", resolved.get("net.minecraft.world.effect.MobEffects"));
    }

    @Test
    void withMappingsClassNamesAreTranslatedToTheirObfuscatedForm() throws IOException {
        Path jar = writeJar("dcl.class");
        Path mappings = directory.resolve("mappings.txt");
        Files.writeString(mappings, """
            net.minecraft.world.item.DyeColor -> dcl:
                int id -> a
            net.minecraft.world.effect.MobEffects -> bzq:
            """);

        Map<String, String> resolved = new ColorExtractor(jar, mappings).resolveClassNames();

        assertEquals("dcl", resolved.get(DYE_COLOR));
        assertEquals("bzq", resolved.get("net.minecraft.world.effect.MobEffects"));
    }

    @Test
    void withoutMappingsAJarThatDoesNotHaveTheNamedClassesIsRejected() throws IOException {
        Path jar = writeJar("dcl.class");

        IOException error = assertThrows(IOException.class, () -> new ColorExtractor(jar).resolveClassNames());

        assertTrue(error.getMessage().contains("obfuscated"), error.getMessage());
        assertTrue(error.getMessage().contains(jar.getFileName().toString()), error.getMessage());
    }

    private Path writeJar(String... entryNames) throws IOException {
        Path jar = directory.resolve("client.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String entryName : entryNames) {
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
                zip.closeEntry();
            }
        }
        return jar;
    }
}

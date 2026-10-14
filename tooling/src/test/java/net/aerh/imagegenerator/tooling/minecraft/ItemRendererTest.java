package net.aerh.imagegenerator.tooling.minecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemRendererTest {

    @TempDir
    Path repo;

    @Test
    void aLicenseKeyInTheEnvironmentSatisfiesTheRendererBuild() {
        Map<String, String> env = Map.of(ItemRenderer.LICENSE_KEY_VARIABLE, "abc");

        assertEquals(Optional.empty(), ItemRenderer.licenseRefusal(repo, env));
    }

    @Test
    void aLicenseFileInTheRendererCheckoutSatisfiesTheRendererBuild() throws IOException {
        Files.writeString(repo.resolve("sixlabors.lic"), "signed");

        assertEquals(Optional.empty(), ItemRenderer.licenseRefusal(repo, Map.of()));
    }

    @Test
    void aBlankLicenseKeyDoesNotCount() {
        Map<String, String> env = Map.of(ItemRenderer.LICENSE_KEY_VARIABLE, "  ");

        assertTrue(ItemRenderer.licenseRefusal(repo, env).isPresent());
    }

    @Test
    void withoutAKeyOrFileTheRefusalSaysWhereToGetOne() {
        Optional<String> refusal = ItemRenderer.licenseRefusal(repo, Map.of());

        assertTrue(refusal.isPresent());
        assertTrue(refusal.get().contains(ItemRenderer.LICENSE_KEY_VARIABLE), refusal.get());
        assertTrue(refusal.get().contains("sixlabors.lic"), refusal.get());
        assertTrue(refusal.get().contains("licensing.sixlabors.com"), refusal.get());
    }
}

package net.aerh.imagegenerator.impl;

import feign.FeignException;
import feign.Request;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.http.MojangApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Rejection-path canary. Proves the Phase 1 test stack works end-to-end:
 * <ul>
 *   <li>Surefire discovers a JUnit 5 test under {@code generator/src/test/java}</li>
 *   <li>Mockito's {@code -javaagent} agent (via parent pom surefire pluginManagement) self-attaches without warnings</li>
 *   <li>{@link MockitoExtension} populates {@code @Mock} fields</li>
 *   <li>{@link MinecraftPlayerHeadGenerator.Builder#withMojangApiClient(MojangApiClient)} injects a Mockito-mocked client</li>
 *   <li>A simulated Mojang 404 raised by Feign surfaces as the expected {@link GeneratorException}</li>
 * </ul>
 * Pairs with TEST-02 - the rejection-path half of the paired-test pattern that activates from Phase 2 onward.
 */
@ExtendWith(MockitoExtension.class)
class MinecraftPlayerHeadGeneratorTest {

    @Mock
    MojangApiClient mojangClient;

    @Test
    void render_whenMojangReturns404_throwsGeneratorExceptionWithExpectedMessage() {
        when(mojangClient.lookupUuid(any())).thenThrow(buildFeignNotFound());

        MinecraftPlayerHeadGenerator generator = new MinecraftPlayerHeadGenerator.Builder()
            .withMojangApiClient(mojangClient)
            .withSkin("notarealplayer")
            .build();

        GeneratorException thrown = assertThrows(
            GeneratorException.class,
            () -> generator.render(null),
            "404 from Mojang should surface as GeneratorException"
        );

        assertNotNull(thrown.getMessage(), "GeneratorException should carry a formatted message");
        assertTrue(
            thrown.getMessage().contains("Could not find player with name"),
            () -> "expected message to contain 'Could not find player with name', got: " + thrown.getMessage()
        );
    }

    private FeignException.NotFound buildFeignNotFound() {
        Request stubRequest = Request.create(
            Request.HttpMethod.GET,
            "https://api.mojang.com/users/profiles/minecraft/notarealplayer",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null
        );
        return new FeignException.NotFound("not found", stubRequest, null, Map.of());
    }

    private static byte[] png(int width, int height) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(
            new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    private static String base64(String json) {
        return java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private GeneratorException renderFailure(String skin) {
        MinecraftPlayerHeadGenerator generator = new MinecraftPlayerHeadGenerator.Builder()
            .withSkin(skin)
            .withMojangApiClient(mojangClient)
            .build();
        return org.junit.jupiter.api.Assertions.assertThrows(GeneratorException.class, () -> generator.render(null));
    }

    @Test
    void decodeSkin_acceptsRealSkinSizes() throws java.io.IOException {
        org.junit.jupiter.api.Assertions.assertNotNull(MinecraftPlayerHeadGenerator.decodeSkin(png(64, 64)));
        org.junit.jupiter.api.Assertions.assertNotNull(MinecraftPlayerHeadGenerator.decodeSkin(png(64, 32)));
    }

    @Test
    void decodeSkin_rejectsOversizedAndUndersizedImagesBeforeDecoding() {
        org.junit.jupiter.api.Assertions.assertThrows(GeneratorException.class,
            () -> MinecraftPlayerHeadGenerator.decodeSkin(png(MinecraftPlayerHeadGenerator.MAX_SKIN_DIMENSION + 1, 64)));
        org.junit.jupiter.api.Assertions.assertThrows(GeneratorException.class,
            () -> MinecraftPlayerHeadGenerator.decodeSkin(png(8, 8)));
    }

    @Test
    void decodeSkin_rejectsNonPngContent() {
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
            () -> MinecraftPlayerHeadGenerator.decodeSkin("GIF89a not a png".getBytes()));
    }

    @Test
    void render_rejectsTextureIdsThatWouldChangeTheRequestPath() {
        // Longer than 16 characters, so not treated as a player name; never reaches the network
        org.junit.jupiter.api.Assertions.assertTrue(
            renderFailure("../../some/other/path?x=1").getMessage().contains("Invalid skin texture ID"));
        org.junit.jupiter.api.Assertions.assertTrue(
            renderFailure("https://example.com/texture/abc").getMessage().contains("Invalid skin texture ID"));
    }

    @Test
    void render_withNoSkinFailsCleanly() {
        org.junit.jupiter.api.Assertions.assertTrue(renderFailure(null).getMessage().contains("No skin"));
    }

    @Test
    void render_withMalformedTextureValueFailsCleanly() {
        // "textures" is not an object, then SKIN has no url: both used to escape as raw runtime exceptions
        renderFailure(base64("{\"textures\":\"not an object, just padding text\"}"));
        org.junit.jupiter.api.Assertions.assertTrue(
            renderFailure(base64("{\"textures\":{\"SKIN\":{}}}")).getMessage().contains("not a valid texture value"));
    }

    @Test
    void render_takesOnlyTheLastPathSegmentOfATextureUrl() {
        // The url member is attacker-controlled; whatever it says, only a bare ID is requested
        String value = base64("{\"textures\":{\"SKIN\":{\"url\":\"https://example.com/a/../../b?c=d\"}}}");
        org.junit.jupiter.api.Assertions.assertTrue(renderFailure(value).getMessage().contains("Invalid skin texture ID"));
    }
}

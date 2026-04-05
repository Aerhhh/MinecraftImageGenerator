package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.exception.RenderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PlayerHeadGenerator}.
 *
 * <p>These tests use a locally constructed skin image (a 64x64 BufferedImage written to a
 * data-URL equivalent flow) to avoid network calls. The Base64 URL extraction test uses a
 * hand-crafted JSON payload that points to a local HTTP mock, but since we do not spin up
 * an HTTP server here, URL-based tests are exercised via the URL-load path with a
 * deliberately invalid URL (to verify error handling).
 */
class PlayerHeadGeneratorTest {

    private PlayerHeadGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PlayerHeadGenerator();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a 64x64 test skin where:
     * - The face region (x=8, y=8, 8x8) is filled with red (0xFFFF0000).
     * - The hat region (x=40, y=8, 8x8) is fully transparent (alpha=0).
     */
    private static BufferedImage createTestSkin() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        // Fill face layer with red
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                skin.setRGB(x, y, 0xFFFF0000);
            }
        }
        // Hat region stays transparent (0x00000000 default)
        return skin;
    }

    /**
     * Creates a 64x64 test skin where:
     * - The face region is red.
     * - The hat region is semi-opaque blue (0xFF0000FF) so it composites on top.
     */
    private static BufferedImage createTestSkinWithHat() {
        BufferedImage skin = createTestSkin();
        for (int y = 8; y < 16; y++) {
            for (int x = 40; x < 48; x++) {
                skin.setRGB(x, y, 0xFF0000FF);
            }
        }
        return skin;
    }

    /**
     * Encodes a fake Minecraft profile texture JSON pointing to a given URL as Base64.
     */
    private static String fakeBase64Texture(String skinUrl) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes());
    }

    // -------------------------------------------------------------------------
    // PlayerHeadRequest construction
    // -------------------------------------------------------------------------

    @Test
    void playerHeadRequest_base64TextureFactoryMethod() {
        PlayerHeadRequest request = PlayerHeadRequest.fromBase64("someBase64Value").build();
        assertThat(request.base64Texture()).contains("someBase64Value");
        assertThat(request.scale()).isEqualTo(1);
    }

    @Test
    void playerHeadRequest_urlFactoryMethod() {
        PlayerHeadRequest request = PlayerHeadRequest.fromUrl("http://example.com/skin.png").build();
        assertThat(request.textureUrl()).contains("http://example.com/skin.png");
        assertThat(request.base64Texture()).isEmpty();
    }

    @Test
    void playerHeadRequest_requiresAtLeastOneSource() {
        assertThatThrownBy(() -> new PlayerHeadRequest(
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void playerHeadRequest_scaleOneMustBeAtLeastOne() {
        assertThatThrownBy(() -> PlayerHeadRequest.fromUrl("http://example.com/skin.png")
                .scale(0)
                .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale must be >= 1");
    }

    @Test
    void playerHeadRequest_builderPreservesAllFields() {
        PlayerHeadRequest request = PlayerHeadRequest.fromBase64("b64data")
                .textureUrl("http://example.com/skin.png")
                .playerName("Notch")
                .scale(8)
                .build();

        assertThat(request.base64Texture()).contains("b64data");
        assertThat(request.textureUrl()).contains("http://example.com/skin.png");
        assertThat(request.playerName()).contains("Notch");
        assertThat(request.scale()).isEqualTo(8);
    }

    // -------------------------------------------------------------------------
    // Generator behavior
    // -------------------------------------------------------------------------

    @Test
    void render_invalidUrlThrowsRenderException() {
        PlayerHeadRequest request = PlayerHeadRequest.fromUrl("not_a_valid_url://bad").build();

        assertThatThrownBy(() -> generator.render(request, GenerationContext.defaults()))
                .isInstanceOf(RenderException.class);
    }

    @Test
    void render_badBase64ThrowsRenderException() {
        PlayerHeadRequest request = PlayerHeadRequest.fromBase64("!!!not-valid-base64!!!").build();

        assertThatThrownBy(() -> generator.render(request, GenerationContext.defaults()))
                .isInstanceOf(RenderException.class);
    }

    @Test
    void render_base64WithMissingUrlFieldThrowsRenderException() {
        // Valid Base64 but JSON does not contain a "url" key
        String json = "{\"textures\":{}}";
        String base64 = Base64.getEncoder().encodeToString(json.getBytes());
        PlayerHeadRequest request = PlayerHeadRequest.fromBase64(base64).build();

        assertThatThrownBy(() -> generator.render(request, GenerationContext.defaults()))
                .isInstanceOf(RenderException.class)
                .hasMessageContaining("Could not find texture URL");
    }

    // -------------------------------------------------------------------------
    // inputType / outputType
    // -------------------------------------------------------------------------

    @Test
    void inputType_returnsPlayerHeadRequestClass() {
        assertThat(generator.inputType()).isEqualTo(PlayerHeadRequest.class);
    }

    @Test
    void outputType_returnsGeneratorResultClass() {
        assertThat(generator.outputType()).isEqualTo(GeneratorResult.class);
    }

    // -------------------------------------------------------------------------
    // Null guards
    // -------------------------------------------------------------------------

    @Test
    void render_nullInputThrowsNullPointerException() {
        assertThatThrownBy(() -> generator.render(null, GenerationContext.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void render_nullContextThrowsNullPointerException() {
        PlayerHeadRequest request = PlayerHeadRequest.fromUrl("http://example.com/skin.png").build();

        assertThatThrownBy(() -> generator.render(request, null))
                .isInstanceOf(NullPointerException.class);
    }
}

package net.aerh.jigsaw.core.generator.player;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.exception.RenderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerModelGeneratorTest {

    private PlayerModelGenerator generator;

    @BeforeEach
    void setUp() {
        generator = PlayerModelGenerator.withDefaults();
    }

    @Test
    void inputType_returnsPlayerModelRequestClass() {
        assertThat(generator.inputType()).isEqualTo(PlayerModelRequest.class);
    }

    @Test
    void outputType_returnsGeneratorResultClass() {
        assertThat(generator.outputType()).isEqualTo(GeneratorResult.class);
    }

    @Test
    void render_nullInputThrowsNullPointerException() {
        assertThatThrownBy(() -> generator.render(null, GenerationContext.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void render_nullContextThrowsNullPointerException() {
        PlayerModelRequest request = PlayerModelRequest.fromUrl("http://example.com/skin.png").build();
        assertThatThrownBy(() -> generator.render(request, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void render_invalidUrlThrowsRenderException() {
        PlayerModelRequest request = PlayerModelRequest.fromUrl("not_a_valid_url://bad").build();
        assertThatThrownBy(() -> generator.render(request, GenerationContext.defaults()))
                .isInstanceOf(RenderException.class);
    }

    @Test
    void render_badBase64ThrowsRenderException() {
        PlayerModelRequest request = PlayerModelRequest.fromBase64("!!!not-valid-base64!!!").build();
        assertThatThrownBy(() -> generator.render(request, GenerationContext.defaults()))
                .isInstanceOf(RenderException.class);
    }

    @Test
    void render_base64WithMissingUrlFieldThrowsRenderException() {
        String json = "{\"textures\":{}}";
        String base64 = Base64.getEncoder().encodeToString(json.getBytes());
        PlayerModelRequest request = PlayerModelRequest.fromBase64(base64).build();
        assertThatThrownBy(() -> generator.render(request, GenerationContext.defaults()))
                .isInstanceOf(RenderException.class)
                .hasMessageContaining("Could not find texture URL");
    }
}

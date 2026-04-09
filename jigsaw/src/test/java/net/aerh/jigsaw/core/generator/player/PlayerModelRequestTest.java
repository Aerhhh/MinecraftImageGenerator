package net.aerh.jigsaw.core.generator.player;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerModelRequestTest {

    @Test
    void fromBase64_createsRequestWithBase64Texture() {
        PlayerModelRequest request = PlayerModelRequest.fromBase64("someBase64Value").build();
        assertThat(request.base64Texture()).contains("someBase64Value");
        assertThat(request.textureUrl()).isEmpty();
        assertThat(request.scale()).isEqualTo(1);
        assertThat(request.slim()).isFalse();
    }

    @Test
    void fromUrl_createsRequestWithTextureUrl() {
        PlayerModelRequest request = PlayerModelRequest.fromUrl("http://example.com/skin.png").build();
        assertThat(request.textureUrl()).contains("http://example.com/skin.png");
        assertThat(request.base64Texture()).isEmpty();
    }

    @Test
    void requiresAtLeastOneSource() {
        assertThatThrownBy(() -> new PlayerModelRequest(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), false, 1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void scaleMinimumIsOne() {
        assertThatThrownBy(() -> PlayerModelRequest.fromUrl("http://example.com/skin.png")
                .scale(0).build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale must be >= 1");
    }

    @Test
    void scaleMaximumIs64() {
        assertThatThrownBy(() -> PlayerModelRequest.fromUrl("http://example.com/skin.png")
                .scale(65).build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale must be <= 64");
    }

    @Test
    void builderPreservesAllFields() {
        ArmorSet armor = ArmorSet.builder().helmet("iron").build();
        PlayerModelRequest request = PlayerModelRequest.fromBase64("b64data")
                .textureUrl("http://example.com/skin.png")
                .playerName("Notch")
                .slim(true)
                .armor(armor)
                .scale(4)
                .build();

        assertThat(request.base64Texture()).contains("b64data");
        assertThat(request.textureUrl()).contains("http://example.com/skin.png");
        assertThat(request.playerName()).contains("Notch");
        assertThat(request.slim()).isTrue();
        assertThat(request.armor()).isPresent();
        assertThat(request.scale()).isEqualTo(4);
    }

    @Test
    void withInheritedScale_preservesExistingScale() {
        PlayerModelRequest request = PlayerModelRequest.fromUrl("http://example.com/skin.png")
                .scale(4).build();
        assertThat(request.withInheritedScale(8)).isSameAs(request);
    }

    @Test
    void withInheritedScale_appliesWhenScaleIsOne() {
        PlayerModelRequest request = PlayerModelRequest.fromUrl("http://example.com/skin.png").build();
        PlayerModelRequest inherited = (PlayerModelRequest) request.withInheritedScale(8);
        assertThat(inherited.scale()).isEqualTo(8);
    }

    @Test
    void defaultSlimIsFalse() {
        PlayerModelRequest request = PlayerModelRequest.fromUrl("http://example.com/skin.png").build();
        assertThat(request.slim()).isFalse();
    }

    @Test
    void defaultArmorIsEmpty() {
        PlayerModelRequest request = PlayerModelRequest.fromUrl("http://example.com/skin.png").build();
        assertThat(request.armor()).isEmpty();
    }
}

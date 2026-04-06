package net.aerh.jigsaw.core.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerHeadRequestScaleTest {

    @Test
    void withInheritedScale_appliesWhenDefault() {
        PlayerHeadRequest request = PlayerHeadRequest.fromBase64("dGVzdA==").build();

        PlayerHeadRequest inherited = (PlayerHeadRequest) request.withInheritedScale(4);
        assertThat(inherited.scale()).isEqualTo(4);
        assertThat(inherited.base64Texture()).hasValue("dGVzdA==");
    }

    @Test
    void withInheritedScale_preservesExplicitScale() {
        PlayerHeadRequest request = PlayerHeadRequest.fromBase64("dGVzdA==").scale(8).build();

        PlayerHeadRequest inherited = (PlayerHeadRequest) request.withInheritedScale(4);
        assertThat(inherited.scale()).isEqualTo(8);
    }
}

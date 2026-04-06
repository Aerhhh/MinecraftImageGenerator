package net.aerh.jigsaw.core.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeRequestTest {

    @Test
    void builder_defaultScaleFactor_isOne() {
        CompositeRequest request = CompositeRequest.builder()
                .add(new StubRequest())
                .build();

        assertThat(request.scaleFactor()).isEqualTo(1);
    }

    @Test
    void builder_scaleFactor_isPreserved() {
        CompositeRequest request = CompositeRequest.builder()
                .scaleFactor(4)
                .add(new StubRequest())
                .build();

        assertThat(request.scaleFactor()).isEqualTo(4);
    }

    @Test
    void builder_scaleFactor_belowOne_throws() {
        assertThatThrownBy(() -> CompositeRequest.builder()
                .scaleFactor(0)
                .add(new StubRequest())
                .build()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builder_scaleFactor_above64_throws() {
        assertThatThrownBy(() -> CompositeRequest.builder()
                .scaleFactor(65)
                .add(new StubRequest())
                .build()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withInheritedScale_appliesWhenDefault() {
        CompositeRequest request = CompositeRequest.builder()
                .add(new StubRequest())
                .build();

        CompositeRequest inherited = (CompositeRequest) request.withInheritedScale(4);
        assertThat(inherited.scaleFactor()).isEqualTo(4);
    }

    @Test
    void withInheritedScale_preservesExplicitScale() {
        CompositeRequest request = CompositeRequest.builder()
                .scaleFactor(8)
                .add(new StubRequest())
                .build();

        CompositeRequest inherited = (CompositeRequest) request.withInheritedScale(4);
        assertThat(inherited.scaleFactor()).isEqualTo(8);
    }

    record StubRequest() implements net.aerh.jigsaw.api.generator.RenderRequest {}
}

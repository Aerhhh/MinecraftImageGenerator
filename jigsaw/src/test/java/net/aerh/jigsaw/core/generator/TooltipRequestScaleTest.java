package net.aerh.jigsaw.core.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TooltipRequestScaleTest {

    @Test
    void withInheritedScale_appliesWhenDefault() {
        TooltipRequest request = TooltipRequest.builder().line("Hello").build();

        TooltipRequest inherited = (TooltipRequest) request.withInheritedScale(4);
        assertThat(inherited.scaleFactor()).isEqualTo(4);
        assertThat(inherited.lines()).containsExactly("Hello");
    }

    @Test
    void withInheritedScale_preservesExplicitScale() {
        TooltipRequest request = TooltipRequest.builder().line("Hello").scaleFactor(8).build();

        TooltipRequest inherited = (TooltipRequest) request.withInheritedScale(4);
        assertThat(inherited.scaleFactor()).isEqualTo(8);
    }

    @Test
    void withInheritedScale_preservesAllOtherFields() {
        TooltipRequest request = TooltipRequest.builder()
                .line("Line 1")
                .line("Line 2")
                .alpha(200)
                .padding(10)
                .firstLinePadding(false)
                .maxLineLength(50)
                .centeredText(true)
                .renderBorder(false)
                .build();

        TooltipRequest inherited = (TooltipRequest) request.withInheritedScale(4);
        assertThat(inherited.scaleFactor()).isEqualTo(4);
        assertThat(inherited.lines()).containsExactly("Line 1", "Line 2");
        assertThat(inherited.alpha()).isEqualTo(200);
        assertThat(inherited.padding()).isEqualTo(10);
        assertThat(inherited.firstLinePadding()).isFalse();
        assertThat(inherited.maxLineLength()).isEqualTo(50);
        assertThat(inherited.centeredText()).isTrue();
        assertThat(inherited.renderBorder()).isFalse();
    }
}

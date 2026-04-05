package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.exception.RenderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TooltipGeneratorTest {

    private TooltipGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TooltipGenerator();
    }

    // --- Basic rendering ---

    @Test
    void render_basicTooltipWithColorCodedLines() throws RenderException {
        TooltipRequest request = TooltipRequest.builder()
                .line("&6Diamond Sword")
                .line("&7A very sharp sword")
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
        assertThat(result.firstFrame()).isNotNull();
        assertThat(result.firstFrame().getWidth()).isGreaterThan(0);
        assertThat(result.firstFrame().getHeight()).isGreaterThan(0);
        assertThat(result.isAnimated()).isFalse();
    }

    @Test
    void render_emptyLinesProducesValidImage() throws RenderException {
        TooltipRequest request = TooltipRequest.builder()
                .line("&fEmpty Item")
                .line("")
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
        assertThat(result.firstFrame()).isNotNull();
    }

    @Test
    void render_noLinesProducesValidImage() throws RenderException {
        TooltipRequest request = TooltipRequest.builder().build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result).isNotNull();
        assertThat(result.firstFrame()).isNotNull();
    }

    // --- maxLineLength respected ---

    @Test
    void render_respectsMaxLineLength() throws RenderException {
        TooltipRequest narrow = TooltipRequest.builder()
                .line("&fItem")
                .line("&7Short lore")
                .maxLineLength(10)
                .build();

        TooltipRequest wide = TooltipRequest.builder()
                .line("&fItem")
                .line("&7Short lore")
                .maxLineLength(80)
                .build();

        GeneratorResult narrowResult = generator.render(narrow, GenerationContext.defaults());
        GeneratorResult wideResult = generator.render(wide, GenerationContext.defaults());

        assertThat(narrowResult.firstFrame()).isNotNull();
        assertThat(wideResult.firstFrame()).isNotNull();
    }

    // --- renderBorder respected ---

    @Test
    void render_withoutBorderProducesValidImage() throws RenderException {
        TooltipRequest request = TooltipRequest.builder()
                .line("&fNo Border")
                .renderBorder(false)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result.firstFrame()).isNotNull();
    }

    // --- alpha respected ---

    @Test
    void render_withReducedAlphaProducesValidImage() throws RenderException {
        TooltipRequest request = TooltipRequest.builder()
                .line("&fTranslucent")
                .alpha(128)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result.firstFrame()).isNotNull();
    }

    // --- lines() via builder helpers ---

    @Test
    void builder_addLinesViaList() {
        TooltipRequest request = TooltipRequest.builder()
                .lines(List.of("&6Name", "&7Lore line 1", "&7Lore line 2"))
                .build();

        assertThat(request.lines()).hasSize(3);
        assertThat(request.lines().get(0)).isEqualTo("&6Name");
    }

    @Test
    void builder_mixLineAndLines() {
        TooltipRequest request = TooltipRequest.builder()
                .line("&6Name")
                .lines(List.of("&7Lore 1", "&7Lore 2"))
                .line("&6&lLEGENDARY")
                .build();

        assertThat(request.lines()).hasSize(4);
        assertThat(request.lines().getLast()).isEqualTo("&6&lLEGENDARY");
    }

    // --- inputType / outputType ---

    @Test
    void inputType_returnsTooltipRequestClass() {
        assertThat(generator.inputType()).isEqualTo(TooltipRequest.class);
    }

    @Test
    void outputType_returnsGeneratorResultClass() {
        assertThat(generator.outputType()).isEqualTo(GeneratorResult.class);
    }

    // --- null guards ---

    @Test
    void render_nullInputThrowsNullPointerException() {
        assertThatThrownBy(() -> generator.render(null, GenerationContext.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void render_nullContextThrowsNullPointerException() {
        TooltipRequest request = TooltipRequest.builder().line("&fX").build();
        assertThatThrownBy(() -> generator.render(request, null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- TooltipRequest defaults ---

    @Test
    void tooltipRequest_defaultConstants() {
        assertThat(TooltipRequest.DEFAULT_ALPHA).isEqualTo(255);
        assertThat(TooltipRequest.DEFAULT_PADDING).isEqualTo(7);
        assertThat(TooltipRequest.DEFAULT_MAX_LINE_LENGTH).isEqualTo(38);
    }

    @Test
    void tooltipRequest_builderDefaults() {
        TooltipRequest request = TooltipRequest.builder().line("&fX").build();

        assertThat(request.alpha()).isEqualTo(TooltipRequest.DEFAULT_ALPHA);
        assertThat(request.padding()).isEqualTo(TooltipRequest.DEFAULT_PADDING);
        assertThat(request.maxLineLength()).isEqualTo(TooltipRequest.DEFAULT_MAX_LINE_LENGTH);
        assertThat(request.firstLinePadding()).isTrue();
        assertThat(request.centeredText()).isFalse();
        assertThat(request.renderBorder()).isTrue();
        assertThat(request.scaleFactor()).isEqualTo(1);
        assertThat(request.bypassMaxLineLength()).isFalse();
    }

    @Test
    void tooltipRequest_maxLineLengthClampedWithoutBypass() {
        TooltipRequest request = TooltipRequest.builder()
                .maxLineLength(500)
                .build();

        assertThat(request.maxLineLength()).isEqualTo(TooltipRequest.MAX_LINE_LENGTH);
    }

    @Test
    void tooltipRequest_maxLineLengthNotClampedWithBypass() {
        TooltipRequest request = TooltipRequest.builder()
                .bypassMaxLineLength(true)
                .maxLineLength(500)
                .build();

        assertThat(request.maxLineLength()).isEqualTo(500);
    }

    @Test
    void tooltipRequest_scaleFactorClampedToMinimumOne() {
        TooltipRequest request = TooltipRequest.builder()
                .scaleFactor(0)
                .build();

        assertThat(request.scaleFactor()).isEqualTo(1);
    }

    @Test
    void tooltipRequest_alphaClampedToRange() {
        TooltipRequest request = TooltipRequest.builder()
                .alpha(-50)
                .build();

        assertThat(request.alpha()).isEqualTo(0);

        TooltipRequest request2 = TooltipRequest.builder()
                .alpha(300)
                .build();

        assertThat(request2.alpha()).isEqualTo(255);
    }

    @Test
    void tooltipRequest_linesAreImmutable() {
        TooltipRequest request = TooltipRequest.builder()
                .line("&fLine")
                .build();

        assertThatThrownBy(() -> request.lines().add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

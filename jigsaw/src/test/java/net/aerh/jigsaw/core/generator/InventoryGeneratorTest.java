package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.font.FontRegistry;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.effect.EffectPipeline;
import net.aerh.jigsaw.core.effect.GlintEffect;
import net.aerh.jigsaw.core.font.DefaultFontRegistry;
import net.aerh.jigsaw.core.sprite.AtlasSpriteProvider;
import net.aerh.jigsaw.exception.RenderException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryGeneratorTest {

    private static SpriteProvider spriteProvider;
    private static FontRegistry fontRegistry;
    private EffectPipeline emptyPipeline;
    private InventoryGenerator generator;

    @BeforeAll
    static void initSpriteProvider() {
        spriteProvider = AtlasSpriteProvider.fromDefaults();
        fontRegistry = DefaultFontRegistry.withBuiltins();
    }

    @BeforeEach
    void setUp() {
        emptyPipeline = EffectPipeline.builder().build();
        generator = new InventoryGenerator(spriteProvider, emptyPipeline, fontRegistry);
    }

    // --- Empty inventory ---

    @Test
    void render_emptyInventoryProducesStaticImage() throws RenderException {
        InventoryRequest request = InventoryRequest.builder()
                .rows(3)
                .slotsPerRow(9)
                .title("Test")
                .items(java.util.List.of())
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
        assertThat(result.firstFrame()).isNotNull();
        assertThat(result.isAnimated()).isFalse();
    }

    // --- Dimensions are correct ---

    @Test
    void render_dimensionsArePositive() throws RenderException {
        InventoryRequest request = InventoryRequest.builder()
                .rows(6)
                .slotsPerRow(9)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result.firstFrame().getWidth()).isGreaterThan(0);
        assertThat(result.firstFrame().getHeight()).isGreaterThan(0);
    }

    @Test
    void render_moreRowsMeansGreaterHeight() throws RenderException {
        InventoryRequest small = InventoryRequest.builder().rows(1).slotsPerRow(9).build();
        InventoryRequest large = InventoryRequest.builder().rows(6).slotsPerRow(9).build();

        int smallH = generator.render(small, GenerationContext.defaults()).firstFrame().getHeight();
        int largeH = generator.render(large, GenerationContext.defaults()).firstFrame().getHeight();

        assertThat(largeH).isGreaterThan(smallH);
    }

    @Test
    void render_moreSlotsPerRowMeansGreaterWidth() throws RenderException {
        InventoryRequest narrow = InventoryRequest.builder().rows(3).slotsPerRow(3).build();
        InventoryRequest wide = InventoryRequest.builder().rows(3).slotsPerRow(9).build();

        int narrowW = generator.render(narrow, GenerationContext.defaults()).firstFrame().getWidth();
        int wideW = generator.render(wide, GenerationContext.defaults()).firstFrame().getWidth();

        assertThat(wideW).isGreaterThan(narrowW);
    }

    // --- Items placed in slots ---

    @Test
    void render_inventoryWithItemProducesImage() throws RenderException {
        InventoryItem item = InventoryItem.builder(0, "diamond_sword").build();
        InventoryRequest request = InventoryRequest.builder()
                .rows(3)
                .slotsPerRow(9)
                .item(item)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result).isNotNull();
        assertThat(result.firstFrame().getWidth()).isGreaterThan(0);
    }

    @Test
    void render_itemWithStackCountGreaterThanOneIsAllowed() throws RenderException {
        InventoryItem item = InventoryItem.builder(0, "diamond_sword").stackCount(64).build();
        InventoryRequest request = InventoryRequest.builder()
                .rows(1)
                .slotsPerRow(9)
                .item(item)
                .build();

        // Should not throw
        GeneratorResult result = generator.render(request, GenerationContext.defaults());
        assertThat(result).isNotNull();
    }

    // --- Stack count drawing ---

    @Test
    void render_stackCountOneDoesNotModifyRenderResult() throws RenderException {
        // Stack count of 1 is valid and should render without error
        InventoryItem item = InventoryItem.builder(0, "diamond_sword").stackCount(1).build();
        InventoryRequest request = InventoryRequest.builder()
                .rows(1)
                .slotsPerRow(9)
                .item(item)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());
        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
    }

    // --- Enchanted items ---

    @Test
    void render_enchantedItemWithGlintPipelineReturnsAnimated() throws RenderException {
        EffectPipeline glintPipeline = EffectPipeline.builder().add(new GlintEffect()).build();
        InventoryGenerator glintGen = new InventoryGenerator(spriteProvider, glintPipeline, fontRegistry);

        InventoryItem item = InventoryItem.builder(0, "diamond_sword").enchanted(true).build();
        InventoryRequest request = InventoryRequest.builder()
                .rows(1)
                .slotsPerRow(9)
                .item(item)
                .build();

        GeneratorResult result = glintGen.render(request, GenerationContext.defaults());

        assertThat(result.isAnimated()).isTrue();
        assertThat(result).isInstanceOf(GeneratorResult.AnimatedImage.class);
    }

    @Test
    void render_noEnchantedItemsWithGlintPipelineStayStatic() throws RenderException {
        EffectPipeline glintPipeline = EffectPipeline.builder().add(new GlintEffect()).build();
        InventoryGenerator glintGen = new InventoryGenerator(spriteProvider, glintPipeline, fontRegistry);

        InventoryItem item = InventoryItem.builder(0, "diamond_sword").enchanted(false).build();
        InventoryRequest request = InventoryRequest.builder()
                .rows(1)
                .slotsPerRow(9)
                .item(item)
                .build();

        GeneratorResult result = glintGen.render(request, GenerationContext.defaults());

        assertThat(result.isAnimated()).isFalse();
    }

    // --- Null guards ---

    @Test
    void render_nullInputThrowsNullPointerException() {
        assertThatThrownBy(() -> generator.render(null, GenerationContext.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void render_nullContextThrowsNullPointerException() {
        InventoryRequest request = InventoryRequest.builder().build();
        assertThatThrownBy(() -> generator.render(request, null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- inputType / outputType ---

    @Test
    void inputType_returnsInventoryRequestClass() {
        assertThat(generator.inputType()).isEqualTo(InventoryRequest.class);
    }

    @Test
    void outputType_returnsGeneratorResultClass() {
        assertThat(generator.outputType()).isEqualTo(GeneratorResult.class);
    }

    // --- Constructor null guards ---

    @Test
    void constructor_nullSpriteProviderThrows() {
        assertThatThrownBy(() -> new InventoryGenerator(null, emptyPipeline, fontRegistry))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_nullEffectPipelineThrows() {
        assertThatThrownBy(() -> new InventoryGenerator(spriteProvider, null, fontRegistry))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_nullFontRegistryThrows() {
        assertThatThrownBy(() -> new InventoryGenerator(spriteProvider, emptyPipeline, null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Parallel rendering determinism ---

    @Test
    void render_parallelItemComputationIsDeterministic() throws RenderException {
        InventoryItem item1 = InventoryItem.builder(0, "diamond_sword").build();
        InventoryItem item2 = InventoryItem.builder(1, "diamond_sword").build();
        InventoryItem item3 = InventoryItem.builder(2, "diamond_sword").build();
        InventoryRequest request = InventoryRequest.builder()
                .rows(1)
                .slotsPerRow(9)
                .item(item1)
                .item(item2)
                .item(item3)
                .build();

        GeneratorResult result1 = generator.render(request, GenerationContext.defaults());
        GeneratorResult result2 = generator.render(request, GenerationContext.defaults());

        BufferedImage img1 = result1.firstFrame();
        BufferedImage img2 = result2.firstFrame();

        assertThat(img1.getWidth()).isEqualTo(img2.getWidth());
        assertThat(img1.getHeight()).isEqualTo(img2.getHeight());

        int[] pixels1 = img1.getRGB(0, 0, img1.getWidth(), img1.getHeight(), null, 0, img1.getWidth());
        int[] pixels2 = img2.getRGB(0, 0, img2.getWidth(), img2.getHeight(), null, 0, img2.getWidth());
        assertThat(pixels1).isEqualTo(pixels2);
    }
}

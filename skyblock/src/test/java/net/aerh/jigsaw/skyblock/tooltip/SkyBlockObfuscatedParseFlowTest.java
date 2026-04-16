package net.aerh.jigsaw.skyblock.tooltip;

import net.aerh.jigsaw.api.Engine;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.core.generator.CompositeRequest;
import net.aerh.jigsaw.core.generator.ItemRequest;
import net.aerh.jigsaw.core.generator.TooltipRequest;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end regression tests for the {@code /gen parse} command flow with obfuscated lore.
 *
 * <p>Reproduces the reported bug where NBT with {@code obfuscated: 1b} produced a tooltip
 * that showed the original character unchanged in every animation frame instead of rendering
 * as Minecraft-style scrambled text.
 */
class SkyBlockObfuscatedParseFlowTest {

    /** Exact SNBT fragment from the reported bug - a Mythic Boots footer with obfuscated "a"s. */
    private static final String MYTHIC_FOOTER_SNBT =
        "{components:{minecraft:custom_name:{extra:[{color:\"aqua\",text:\"X \"},"
            + "{color:\"light_purple\",text:\"Renowned Sorrow Boots\"}],text:\"\",italic:0b},"
            + "minecraft:lore:[{extra:[{color:\"gray\",text:\"Health: \"},"
            + "{color:\"red\",text:\"+175\"}],text:\"\",italic:0b},"
            + "{extra:[{color:\"light_purple\",text:\"a\",bold:1b,obfuscated:1b},\"\","
            + "{extra:[\" \"],underlined:0b,text:\"\",bold:0b,strikethrough:0b,italic:0b,obfuscated:0b},"
            + "{color:\"light_purple\",text:\"MYTHIC BOOTS \",bold:1b},"
            + "{color:\"light_purple\",text:\"a\",bold:1b,obfuscated:1b}],text:\"\",italic:0b}]},"
            + "count:1,id:\"minecraft:leather_boots\"}";

    @Test
    void parseNbt_obfuscatedLore_emitsKFormatCode() throws Exception {
        Engine engine = Engine.builder().build();

        ParsedItem parsed = engine.parseNbt(MYTHIC_FOOTER_SNBT);

        assertThat(parsed.lore()).hasSize(2);
        assertThat(parsed.lore().get(1)).contains("&k");
    }

    @Test
    void parseCommandFlow_obfuscatedLore_scale2_producesDistinctFrames() throws Exception {
        Engine engine = Engine.builder().build();
        ParsedItem parsed = engine.parseNbt(MYTHIC_FOOTER_SNBT);

        SkyBlockTooltipBuilder.Builder tooltipBuilder = SkyBlockTooltipBuilder.builder();
        parsed.displayName().ifPresent(tooltipBuilder::name);
        tooltipBuilder.lore(String.join("\\n", parsed.lore()));

        CompositeRequest composite = CompositeRequest.builder()
            .scaleFactor(2)
            .add(ItemRequest.builder().itemId("leather_boots").build())
            .add(tooltipBuilder.build())
            .build();

        GeneratorResult result = engine.render(composite, GenerationContext.defaults());

        assertThat(result.isAnimated())
            .as("composite with obfuscated tooltip should be animated")
            .isTrue();

        List<BufferedImage> frames = ((GeneratorResult.AnimatedImage) result).frames();
        long uniqueFrames = frames.stream().map(SkyBlockObfuscatedParseFlowTest::frameSignature).distinct().count();

        assertThat(uniqueFrames)
            .as("frames must differ: the obfuscated 'a' should be replaced by a random character per frame")
            .isGreaterThan(1);
    }

    private static String frameSignature(BufferedImage image) {
        int[] rgb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        long hash = 1469598103934665603L;
        for (int pixel : rgb) {
            hash = (hash ^ pixel) * 1099511628211L;
        }
        return Long.toHexString(hash);
    }
}

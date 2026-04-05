package net.aerh.jigsaw.skyblock.tooltip;

import net.aerh.jigsaw.core.generator.TooltipRequest;
import net.aerh.jigsaw.skyblock.data.Rarity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the behaviour of {@link SkyBlockTooltipBuilder}.
 */
class SkyBlockTooltipBuilderTest {

    // -----------------------------------------------------------------------
    // Basic construction
    // -----------------------------------------------------------------------

    @Test
    void buildWithNameAndRarity() {
        Rarity legendary = Rarity.byName("legendary").orElseThrow();

        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Hyperion")
            .rarity(legendary)
            .type("SWORD")
            .build();

        assertThat(request.lines()).isNotEmpty();
        // First line must contain the item name
        assertThat(request.lines().get(0)).contains("Hyperion");
        // First line must be prefixed with the rarity color code
        assertThat(request.lines().get(0)).startsWith("&6");
    }

    @Test
    void buildWithoutRarityHasNoColorPrefix() {
        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Plain Item")
            .build();

        assertThat(request.lines()).isNotEmpty();
        // No rarity so no color prefix - line starts directly with the name
        assertThat(request.lines().get(0)).isEqualTo("Plain Item");
    }

    // -----------------------------------------------------------------------
    // Stat placeholder resolution
    // -----------------------------------------------------------------------

    @Test
    void resolvesStatPlaceholderWithValue() {
        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Sword")
            .lore("%%damage:500%%")
            .build();

        List<String> lines = request.lines();
        // One of the lines must contain resolved stat text with a + sign or stat display
        String loreText = String.join(" ", lines);
        assertThat(loreText).contains("Damage");
        assertThat(loreText).contains("+500");
    }

    @Test
    void resolvesStatPlaceholderWithoutValue() {
        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Sword")
            .lore("%%strength%%")
            .build();

        String loreText = String.join(" ", request.lines());
        assertThat(loreText).contains("Strength");
    }

    @Test
    void resolveFlavorPlaceholder() {
        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Sword")
            .lore("%%soulbound%%")
            .build();

        String loreText = String.join(" ", request.lines());
        assertThat(loreText).containsIgnoringCase("Soulbound");
    }

    @Test
    void unknownPlaceholderIsLeftUnchanged() {
        String unknownPlaceholder = "%%not_a_real_key:99%%";
        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Item")
            .lore(unknownPlaceholder)
            .build();

        String loreText = String.join(" ", request.lines());
        assertThat(loreText).contains(unknownPlaceholder);
    }

    // -----------------------------------------------------------------------
    // Rarity footer
    // -----------------------------------------------------------------------

    @Test
    void addsRarityFooterLine() {
        Rarity legendary = Rarity.byName("legendary").orElseThrow();

        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Hyperion")
            .rarity(legendary)
            .type("SWORD")
            .build();

        List<String> lines = request.lines();
        String lastLine = lines.get(lines.size() - 1);
        // Footer must contain the bold rarity name in uppercase
        assertThat(lastLine).contains("LEGENDARY");
        assertThat(lastLine).contains("SWORD");
        assertThat(lastLine).contains("&l");
    }

    @Test
    void noneRarityProducesNoFooter() {
        Rarity none = Rarity.byName("none").orElseThrow();

        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Item")
            .rarity(none)
            .build();

        List<String> lines = request.lines();
        // No footer separator or rarity line expected
        assertThat(lines).hasSize(1);
    }

    @Test
    void nullRarityProducesNoFooter() {
        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Item")
            .rarity(null)
            .build();

        List<String> lines = request.lines();
        assertThat(lines).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Empty lore
    // -----------------------------------------------------------------------

    @Test
    void emptyLoreProducesOnlyNameAndFooter() {
        Rarity rare = Rarity.byName("rare").orElseThrow();

        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Some Item")
            .rarity(rare)
            .lore("")
            .build();

        // name line + separator + footer = 3 lines
        assertThat(request.lines()).hasSize(3);
    }

    @Test
    void nullLoreProducesOnlyNameAndFooter() {
        Rarity rare = Rarity.byName("rare").orElseThrow();

        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Some Item")
            .rarity(rare)
            .lore(null)
            .build();

        assertThat(request.lines()).hasSize(3);
    }

    // -----------------------------------------------------------------------
    // buildSlashCommand
    // -----------------------------------------------------------------------

    @Test
    void buildSlashCommandProducesValidString() {
        Rarity legendary = Rarity.byName("legendary").orElseThrow();

        String command = SkyBlockTooltipBuilder.builder()
            .name("Hyperion")
            .rarity(legendary)
            .type("SWORD")
            .buildSlashCommand();

        assertThat(command).startsWith("/");
        assertThat(command).contains("item_name");
        assertThat(command).contains("Hyperion");
    }

    @Test
    void buildSlashCommandContainsRarityName() {
        Rarity epic = Rarity.byName("epic").orElseThrow();

        String command = SkyBlockTooltipBuilder.builder()
            .name("Livid Dagger")
            .rarity(epic)
            .buildSlashCommand();

        assertThat(command).contains("epic");
    }

    // -----------------------------------------------------------------------
    // TooltipRequest settings pass-through
    // -----------------------------------------------------------------------

    @Test
    void alphaIsPassedThrough() {
        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Item")
            .alpha(128)
            .build();

        assertThat(request.alpha()).isEqualTo(128);
    }

    @Test
    void paddingIsPassedThrough() {
        TooltipRequest request = SkyBlockTooltipBuilder.builder()
            .name("Item")
            .padding(10)
            .build();

        assertThat(request.padding()).isEqualTo(10);
    }
}

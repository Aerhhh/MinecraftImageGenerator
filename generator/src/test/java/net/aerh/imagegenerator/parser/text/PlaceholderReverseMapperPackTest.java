package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.data.Stat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reverse mapping (/gen parse) for pack font glyphs: bare glyphs map to icon placeholders and
 * pack-rendered stat text maps back to stat placeholders.
 */
class PlaceholderReverseMapperPackTest {

    private final PlaceholderReverseMapper mapper = new PlaceholderReverseMapper();

    @Test
    void bareNewGlyphsMapToIconPlaceholders() {
        assertEquals("Kill %%mob_undead:3%% Zombies",
            mapper.mapPlaceholders("Kill \uE084\uE084\uE084 Zombies"));
    }

    @Test
    void overrideCharactersMapToTheOwningIconEntry() {
        assertEquals("%%zone%% The Rift", mapper.mapPlaceholders("\uE067 The Rift"));
    }

    @Test
    void sharedBareCharactersMapToTheFirstEntryInFileOrder() {
        // starred and fragged share U+269A; bare chars have no adjacent text to disambiguate, so
        // the first icons.json entry wins (both placeholder names render identically anyway).
        assertEquals("%%starred%% Item", mapper.mapPlaceholders("\u269A Item"));
    }

    @Test
    void sharedStatGlyphsAreDisambiguatedBySurroundingText() {
        // Five fortune stats share U+E053; the format-rule pattern includes the stat text, so the
        // right placeholder comes back for each.
        Stat mining = Stat.byName("mining_fortune");
        String colorCode = String.valueOf(mining.getColor().getCode());

        assertEquals("%%mining_fortune%%", mapper.mapPlaceholders("\u00A7" + colorCode + "\uE053 Mining Fortune"));
        assertEquals("%%ore_fortune%%", mapper.mapPlaceholders("\u00A7" + colorCode + "\uE053 Ore Fortune"));
    }

    @Test
    void duplicateMelonEntriesResolveToTheFirstInFileOrder() {
        // melon_fortune and melon_slice_fortune are duplicate stats.json entries with identical
        // display text - nothing can tell them apart; this pins the deterministic winner.
        Stat melon = Stat.byName("melon_fortune");
        String colorCode = String.valueOf(melon.getColor().getCode());

        assertEquals("%%melon_fortune%%", mapper.mapPlaceholders("\u00A7" + colorCode + "\uE051 Melon Slice Fortune"));
    }

    @Test
    void packFormattedStatTextReverseMaps() {
        Stat strength = Stat.byName("strength");
        String colorCode = String.valueOf(strength.getColor().getCode());

        assertEquals("%%strength%%", mapper.mapPlaceholders("\u00A7" + colorCode + "\uE00D Strength"));
    }

    @Test
    void baseFormattedStatTextStillReverseMaps() {
        Stat strength = Stat.byName("strength");
        String colorCode = String.valueOf(strength.getColor().getCode());

        assertEquals("%%strength%%", mapper.mapPlaceholders("\u00A7" + colorCode + "\u2741 Strength"));
    }

    @Test
    void packRenderedFlavorTextMapsToTheFlavorNotTheIcon() {
        // U+E084 is both the undead flavor's override char and mob_undead's base char; flavor
        // format rules run before bare-char icon rules, so the surrounding text wins.
        assertEquals("%%undead%%", mapper.mapPlaceholders("\u00A72\uE084 Undead"));
        assertEquals("%%undead%%", mapper.mapPlaceholders("\u00A72\u0F15 Undead"));
    }

    @Test
    void packRenderedEmbeddedFlavorTextReverseMaps() {
        assertEquals("%%undead_item%%",
            mapper.mapPlaceholders("\u00A72This armor piece is undead \uE084!"));
        assertEquals("%%undead_item%%",
            mapper.mapPlaceholders("\u00A72This armor piece is undead \u0F15!"));
    }

    @Test
    void packRenderedGemstoneSlotsReverseMap() {
        assertEquals("%%gem_ruby:unlocked%%",
            mapper.mapPlaceholders("\u00A78[\u00A77\uE010\u00A78]\u00A7r"));
        assertEquals("%%gem_ruby:fine%%",
            mapper.mapPlaceholders("\u00A79[\u00A7c\uE010\u00A79]\u00A7r"));
        assertEquals("%%gem_ruby:fine%%",
            mapper.mapPlaceholders("\u00A79[\u00A7c\u2764\u00A79]\u00A7r"));
    }

    @Test
    void packGlyphInsideFormattedStatTextMapsToTheStatNotTheIcon() {
        // U+E077 is both the tracking stat's override char and mob_elusive's base char; stat
        // format rules run before bare-char icon rules, so the surrounding "Tracking" text wins.
        Stat tracking = Stat.byName("tracking");
        String colorCode = String.valueOf(tracking.getColor().getCode());

        assertEquals("%%tracking%%", mapper.mapPlaceholders("\u00A7" + colorCode + "\uE077 Tracking"));
    }
}

package net.aerh.imagegenerator.parser.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholderReverseMapperTest {

    private final PlaceholderReverseMapper mapper = new PlaceholderReverseMapper();

    @Test
    void duplicateStatOnOneLineMapsToTwoPlaceholders() {
        assertEquals("%%farming_fortune:+5%% and %%farming_fortune:+10%%",
            mapper.mapPlaceholders("&6+5 ☘ Farming Fortune and &6+10 ☘ Farming Fortune"));
    }

    @Test
    void duplicateStatWithSurroundingColorCodesMapsToTwoPlaceholders() {
        assertEquals("&7Gain %%farming_fortune:+5%%&7, plus %%farming_fortune:+10%%&7.",
            mapper.mapPlaceholders("&7Gain &6+5 ☘ Farming Fortune&7, plus &6+10 ☘ Farming Fortune&7."));
    }

    @Test
    void duplicateBoldIconStatOnOneLineMapsToTwoPlaceholders() {
        assertEquals("%%sea_creature_chance:+5%% and %%sea_creature_chance:+10%%",
            mapper.mapPlaceholders("&3+5 &3&lα&3 Sea Creature Chance and &3+10 &3&lα&3 Sea Creature Chance"));
    }

    @Test
    void singleStatWithDetailsMapsToOnePlaceholder() {
        assertEquals("%%farming_fortune:+25%%", mapper.mapPlaceholders("&6+25 ☘ Farming Fortune"));
    }

    @Test
    void singleStatWithoutDetailsMapsToBarePlaceholder() {
        assertEquals("%%farming_fortune%%", mapper.mapPlaceholders("&6☘ Farming Fortune"));
    }

    @Test
    void postDualTrailingCaptureStillConsumesRestOfSegment() {
        assertEquals("%%mana_cost:50%%", mapper.mapPlaceholders("&8Mana Cost: &350"));
    }

    @Test
    void abilityTrailingCaptureConsumesFormattedTail() {
        assertEquals("%%ability:Wither Impact:RIGHT CLICK%%",
            mapper.mapPlaceholders("&6Ability: Wither Impact &e&lRIGHT CLICK"));
    }
}

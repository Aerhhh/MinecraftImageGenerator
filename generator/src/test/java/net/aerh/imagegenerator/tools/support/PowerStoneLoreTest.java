package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.data.PowerStrength;
import net.aerh.imagegenerator.data.Stat;
import net.aerh.imagegenerator.exception.GeneratorException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing a comma-separated {@code stat:value} string into a name-to-value map: multiple entries,
 * whitespace trimming, duplicate summing, blank handling, and the malformed-input errors. Also
 * covers the power scaling formula and the full lore template it feeds into.
 */
class PowerStoneLoreTest {

    /** A stat with no {@code powerScalingMultiplier} in stats.json. */
    private static final String UNSCALED = "damage";
    /** A stat with a {@code powerScalingMultiplier} of 1.4 in stats.json. */
    private static final String SCALED = "health";
    /** A power strength in power_strengths.json; display "Marvelous", a stone. */
    private static final String STRENGTH = "marvelous";

    @Test
    void parsesMultipleEntries() {
        assertEquals(Map.of("health", -50, "damage", 10),
            PowerStoneLore.parseStatsToMap("health:-50,damage:10"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals(Map.of("health", -50), PowerStoneLore.parseStatsToMap("  health : -50  "));
    }

    @Test
    void returnsEmptyForNullOrBlank() {
        assertTrue(PowerStoneLore.parseStatsToMap(null).isEmpty());
        assertTrue(PowerStoneLore.parseStatsToMap("   ").isEmpty());
    }

    @Test
    void sumsDuplicateStats() {
        assertEquals(Map.of("strength", 15), PowerStoneLore.parseStatsToMap("strength:5,strength:10"));
    }

    @Test
    void ignoresBlankEntries() {
        assertEquals(Map.of("health", 10, "damage", 5), PowerStoneLore.parseStatsToMap("health:10,,damage:5,"));
    }

    @Test
    void rejectsInvalidFormat() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> PowerStoneLore.parseStatsToMap("health"));

        assertTrue(exception.getMessage().contains("invalid format"));
    }

    @Test
    void rejectsNonNumericValue() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> PowerStoneLore.parseStatsToMap("health:abc"));

        assertTrue(exception.getMessage().contains("Invalid number"));
    }

    @Test
    void formulaMatchesTheBotAtKnownPoints() {
        Stat unscaled = Stat.byName(UNSCALED);

        double atZeroMagicalPower = PowerStoneLore.calculateStat(unscaled, 100, 0);
        double atThousand = PowerStoneLore.calculateStat(unscaled, 100, 1000);

        assertEquals(0.0, atZeroMagicalPower, 1e-9);
        double expected = 719.28 * Math.pow(Math.log(1 + 0.0019 * 1000), 1.2);
        assertEquals(expected, atThousand, 1e-9);
    }

    @Test
    void formulaScalesLinearlyWithBasePowerAndMultiplier() {
        Stat unscaled = Stat.byName(UNSCALED);
        Stat scaled = Stat.byName(SCALED);

        double single = PowerStoneLore.calculateStat(unscaled, 100, 500);
        double doubled = PowerStoneLore.calculateStat(unscaled, 200, 500);
        double withMultiplier = PowerStoneLore.calculateStat(scaled, 100, 500);

        assertEquals(single * 2, doubled, 1e-9);
        assertEquals(single * scaled.getPowerScalingMultiplier(), withMultiplier, 1e-6);
    }

    @Test
    void formulaKeepsTheSignOfNegativeMagicalPower() {
        Stat unscaled = Stat.byName(UNSCALED);

        assertTrue(PowerStoneLore.calculateStat(unscaled, 100, -100) < 0);
    }

    @Test
    void loreTemplateSelected() {
        String lore = PowerStoneLore.build(STRENGTH, 1500, null, null, true);

        assertEquals("&8" + PowerStrength.byName(STRENGTH).getFormattedDisplay()
            + "\\n\\n&7You have: &61,500 Magical Power\\n\\n&aPower is selected!", lore);
    }

    @Test
    void loreTemplateUnselectedAndUnknownStrengthUsesRawText() {
        String lore = PowerStoneLore.build("Mystery", 10, null, null, false);

        assertEquals("&8Mystery\\n\\n&7You have: &610 Magical Power\\n\\n&eClick to select power!", lore);
    }

    @Test
    void loreTemplateIncludesScalingAndBonusBlocks() {
        String lore = PowerStoneLore.build("Mystery", 0, UNSCALED + ":100", UNSCALED + ":25", true);

        assertEquals("&8Mystery\\n\\n"
                + "&7Stats:\\n%%" + UNSCALED + ":0%%\\n\\n"
                + "&7Unique Power Bonus:\\n%%" + UNSCALED + ":25%%\\n\\n"
                + "&7You have: &60 Magical Power\\n\\n&aPower is selected!",
            lore);
    }

    @Test
    void unknownScalingStatUsesBackticks() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> PowerStoneLore.build("Mystery", 0, "nope:1", null, true));

        assertEquals("`nope` is not a valid stat", exception.getMessage());
    }

    @Test
    void unknownBonusStatUsesSingleQuotes() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> PowerStoneLore.build("Mystery", 0, null, "nope:1", true));

        assertEquals("'nope' is not a valid stat", exception.getMessage());
    }
}

package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.data.PowerStrength;
import net.aerh.imagegenerator.data.Stat;
import net.aerh.imagegenerator.exception.GeneratorException;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Builds the lore of a Power Stone tooltip from its strength, magical power and stats. */
public final class PowerStoneLore {

    private PowerStoneLore() {
    }

    /**
     * Parses a comma-separated {@code stat:value} string (for example {@code health:-50,damage:10})
     * into a map of stat name to summed value. Blank entries are ignored and duplicates are summed.
     *
     * @throws GeneratorException if an entry is not {@code stat:value} or the value is not an integer
     */
    public static Map<String, Integer> parseStatsToMap(@Nullable String stats) {
        Map<String, Integer> map = new HashMap<>();

        if (stats == null || stats.trim().isEmpty()) {
            return map;
        }

        String[] entries = stats.split(",");

        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }

            String[] stat = entry.split(":");

            if (stat.length != 2 || stat[0].trim().isEmpty() || stat[1].trim().isEmpty()) {
                throw new GeneratorException("Stat `" + entry + "` is using an invalid format. Use `stat:value` and separate multiple entries with commas (e.g., `health:-50,damage:10`)");
            }

            String statName = stat[0].trim();

            int statValue;

            try {
                statValue = Integer.parseInt(stat[1].trim());
            } catch (NumberFormatException e) {
                throw new GeneratorException("Invalid number for stat `" + statName + "`: " + stat[1].trim() + ". Use `stat:value` (e.g., `health:-50`)");
            }

            map.merge(statName, statValue, Integer::sum);
        }

        return map;
    }

    /** The scaled value of a stat for a base power and magical power, using Hypixel's curve. */
    public static double calculateStat(Stat stat, int basePower, int magicalPower) {
        double statMultiplier = stat.getPowerScalingMultiplier() != null ? stat.getPowerScalingMultiplier() : 1;
        double logValue = Math.log(1 + (0.0019 * magicalPower));
        double magnitude = Math.pow(Math.abs(logValue), 1.2);
        double signedFactor = Math.signum(logValue) * magnitude;
        return ((double) basePower / 100) * statMultiplier * 719.28 * signedFactor;
    }

    /**
     * The full lore text. Line breaks are the literal two characters {@code \n}, which the tooltip
     * generator turns into real line breaks.
     *
     * @param powerStrength the strength name; an unknown name is printed as given
     * @param magicalPower  the magical power used for scaling and shown in the footer
     * @param scalingStats  {@code stat:value} pairs scaled by magical power, may be null
     * @param uniqueBonus   {@code stat:value} pairs shown as given, may be null
     * @param selected      whether the stone shows as selected
     *
     * @throws GeneratorException if a stat name is unknown or a pair is malformed
     */
    public static String build(String powerStrength, int magicalPower, @Nullable String scalingStats, @Nullable String uniqueBonus, boolean selected) {
        StringBuilder scalingStatsFormatted = new StringBuilder();
        Map<String, Integer> scalingStatsMap = parseStatsToMap(scalingStats);

        for (Map.Entry<String, Integer> entry : scalingStatsMap.entrySet()) {
            String statName = entry.getKey();
            Integer basePower = entry.getValue();
            Stat stat = Stat.byName(statName);

            if (stat == null) {
                throw new GeneratorException("`" + statName + "` is not a valid stat");
            }

            scalingStatsFormatted.append(String.format("%%%%%s:%s%%%%\\n", statName, NumberFormats.COMMA_SEPARATED.format(calculateStat(stat, basePower, magicalPower))));
        }

        if (!scalingStatsFormatted.isEmpty()) {
            scalingStatsFormatted = new StringBuilder("&7Stats:\\n")
                .append(scalingStatsFormatted)
                .append("\\n");
        }

        StringBuilder bonusStatsFormatted = new StringBuilder();
        Map<String, Integer> bonusStats = parseStatsToMap(uniqueBonus);

        for (Map.Entry<String, Integer> entry : bonusStats.entrySet()) {
            String statName = entry.getKey();
            Integer statAmount = entry.getValue();
            Stat stat = Stat.byName(statName);

            if (stat == null) {
                throw new GeneratorException("'" + statName + "' is not a valid stat");
            }

            bonusStatsFormatted.append(String.format("%%%%%s:%s%%%%\\n", statName, NumberFormats.COMMA_SEPARATED.format(statAmount)));
        }

        if (!bonusStatsFormatted.isEmpty()) {
            bonusStatsFormatted = new StringBuilder("&7Unique Power Bonus:\\n")
                .append(bonusStatsFormatted)
                .append("\\n");
        }

        String itemLoreTemplate =
            "&8%s\\n"
                + "\\n"
                + "%s"
                + "%s"
                + "&7You have: &6%s Magical Power\\n"
                + "\\n"
                + (selected ? "&aPower is selected!" : "&eClick to select power!");

        PowerStrength strength = PowerStrength.byName(powerStrength);

        return String.format(itemLoreTemplate,
            strength == null ? powerStrength : strength.getFormattedDisplay(),
            scalingStatsFormatted,
            bonusStatsFormatted,
            NumberFormats.COMMA_SEPARATED.format(magicalPower)
        );
    }
}

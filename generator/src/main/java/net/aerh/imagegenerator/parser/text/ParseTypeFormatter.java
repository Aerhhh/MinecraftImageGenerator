package net.aerh.imagegenerator.parser.text;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.data.FormattableEntry;
import net.aerh.imagegenerator.data.ParseType;
import net.aerh.imagegenerator.text.ChatFormat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared formatting logic for expanding a {@link ParseType} format template
 * against a {@link FormattableEntry} (stat or flavor).
 * <p>
 * Both {@link StatParser} and {@link FlavorParser} delegate here so that
 * parse-type-specific logic (ABILITY, ITEM_STAT) is defined once.
 */
@Slf4j
public final class ParseTypeFormatter {

    static final Map<String, String> BASE_PLACEHOLDERS = new HashMap<>();

    static {
        try {
            Arrays.stream(ChatFormat.values()).forEach(format ->
                BASE_PLACEHOLDERS.put(format.name().toLowerCase(), String.valueOf(format.getCode()))
            );
            BASE_PLACEHOLDERS.put("ampersand", String.valueOf(ChatFormat.AMPERSAND_SYMBOL));
            log.info("Initialized ParseTypeFormatter with {} base placeholders", BASE_PLACEHOLDERS.size());
        } catch (Exception e) {
            log.error("Failed to initialize ParseTypeFormatter placeholders", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private ParseTypeFormatter() {
    }

    /**
     * Expands a {@link ParseType} format template using the given entry's
     * properties and optional extra details.
     *
     * @param entry        the stat or flavor entry providing colors, icons, etc.
     * @param parseType    the parse type whose format template to expand
     * @param extraDetails optional colon-separated data (e.g. "100" or "Vacuum:HOLD RIGHT CLICK")
     *
     * @return the fully expanded format string with ampersand color codes
     */
    public static String format(FormattableEntry entry, ParseType parseType, String extraDetails) {
        boolean hasExtra = extraDetails != null && !extraDetails.isEmpty();
        String format = hasExtra ? parseType.getFormatWithDetails() : parseType.getFormatWithoutDetails();

        if (format == null) {
            log.warn("Format string is null for parse type '{}' (entry '{}')", parseType.getName(), entry.getName());
            return "[INVALID FORMAT]";
        }

        Map<String, String> placeholders = new HashMap<>(BASE_PLACEHOLDERS);
        placeholders.put("color", String.valueOf(entry.getColor().getCode()));
        placeholders.put("subColor", String.valueOf(entry.getSecondaryColor().getCode()));
        placeholders.put("icon", entry.getIcon() != null ? entry.getIcon() : "");
        placeholders.put("stat", entry.getStat() != null ? entry.getStat() : "");
        placeholders.put("display", entry.getDisplay() != null ? entry.getDisplay() : "");
        placeholders.put("extraDetails", hasExtra ? extraDetails : "");

        if (parseType.getName().equalsIgnoreCase("ITEM_STAT")) {
            if (!hasExtra) {
                log.warn("Missing extra details for ITEM_STAT: {}", entry.getName());
                return "[ITEM_STAT_MISSING_DETAILS]";
            }

            int separator = extraDetails.indexOf(":");
            if (separator == -1) {
                log.warn("Missing separator ':' in extra details for ITEM_STAT: {}", extraDetails);
                return "[ITEM_STAT_MISSING_SEPARATOR]";
            }

            placeholders.put("itemStat", extraDetails.substring(0, separator));
            placeholders.put("amount", extraDetails.substring(separator + 1));
        } else if (parseType.getName().equalsIgnoreCase("ABILITY")) {
            if (!hasExtra) {
                log.warn("Missing extra details for ABILITY: {}", entry.getName());
                return "[ABILITY_MISSING_DETAILS]";
            }

            int separator = extraDetails.indexOf(":");
            if (separator == -1) {
                log.warn("Missing separator ':' in extra details for ABILITY: {}", extraDetails);
                return "[ABILITY_MISSING_SEPARATOR]";
            }

            placeholders.put("abilityName", extraDetails.substring(0, separator));
            placeholders.put("abilityType", extraDetails.substring(separator + 1));
        }

        String result = format;
        for (Map.Entry<String, String> mapEntry : placeholders.entrySet()) {
            result = result.replaceAll(
                "\\{" + Pattern.quote(mapEntry.getKey()) + "}",
                Matcher.quoteReplacement(mapEntry.getValue())
            );
        }

        return result;
    }
}

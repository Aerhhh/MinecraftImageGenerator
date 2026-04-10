package net.aerh.jigsaw.skyblock.tooltip;

import net.aerh.jigsaw.api.text.ChatColor;
import net.aerh.jigsaw.core.generator.TooltipRequest;
import net.aerh.jigsaw.skyblock.data.Flavor;
import net.aerh.jigsaw.skyblock.data.ParseType;
import net.aerh.jigsaw.skyblock.data.Rarity;
import net.aerh.jigsaw.skyblock.data.Stat;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a Jigsaw {@link TooltipRequest} from SkyBlock-specific inputs.
 *
 * <p>Handles SkyBlock concerns that the generic {@link net.aerh.jigsaw.core.generator.TooltipGenerator}
 * does not: rarity color prefixes, stat/flavor placeholder resolution, and rarity footer lines.
 *
 * <p>Stat and flavor placeholders in the lore string use the format
 * {@code %%key:value%%}, where {@code key} is the internal stat or flavor name and
 * {@code value} is an optional numeric or text detail. For example:
 * <ul>
 *   <li>{@code %%damage:500%%} resolves to a formatted damage stat line</li>
 *   <li>{@code %%soulbound%%} resolves to a formatted Soulbound flavor line</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * TooltipRequest request = SkyBlockTooltipBuilder.builder()
 *     .name("Hyperion")
 *     .rarity(Rarity.byName("legendary").orElse(null))
 *     .lore("%%damage:500%%\n%%strength:200%%\n&7Ability: Wither Impact")
 *     .type("SWORD")
 *     .alpha(255)
 *     .padding(7)
 *     .build();
 * }</pre>
 */
public final class SkyBlockTooltipBuilder {

    /** Pattern that matches {@code %%key%%} or {@code %%key:value%%} placeholders in lore strings. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%%([^%:]+)(?::([^%]*))?%%");

    /** Pattern that matches remaining {@code {tokenName}} tokens in a format string. */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{([a-zA-Z]+)}");

    private SkyBlockTooltipBuilder() {}

    /**
     * Returns a new {@link Builder} with default settings.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves a stat placeholder into a formatted tooltip line.
     *
     * <p>Uses the stat's parse type format string. When {@code extraDetails} is provided and the
     * parse type has a {@code formatWithDetails} template, that template is used; otherwise
     * {@code formatWithoutDetails} is used.
     *
     * @param stat         the stat definition to format
     * @param extraDetails the numeric or text detail value, or {@code null}
     * @return the formatted line string with ampersand color codes
     */
    static String formatStatLine(Stat stat, @Nullable String extraDetails) {
        ParseType parseType = ParseType.byName(stat.parseType()).orElse(null);
        String format = selectFormat(parseType, extraDetails);
        return applyStatTokens(format, stat, extraDetails);
    }

    /**
     * Resolves a flavor placeholder into a formatted tooltip line.
     *
     * @param flavor       the flavor definition to format
     * @param extraDetails the text detail value, or {@code null}
     * @return the formatted line string with ampersand color codes
     */
    static String formatFlavorLine(Flavor flavor, @Nullable String extraDetails) {
        ParseType parseType = ParseType.byName(flavor.parseType()).orElse(null);
        String format = selectFormat(parseType, extraDetails);
        return applyFlavorTokens(format, flavor, extraDetails);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String selectFormat(@Nullable ParseType parseType, @Nullable String extraDetails) {
        if (parseType == null) {
            return extraDetails != null && !extraDetails.isBlank()
                ? "{display} {extraDetails}"
                : "{display}";
        }
        return extraDetails != null && !extraDetails.isBlank()
            ? parseType.formatWithDetails()
            : parseType.formatWithoutDetails();
    }

    private static String applyStatTokens(String format, Stat stat, @Nullable String extraDetails) {
        ChatColor color = stat.chatColor();
        ChatColor subColor = stat.secondaryChatColor();
        String colorCode = color != null ? String.valueOf(color.code()) : "f";
        String subColorCode = subColor != null ? String.valueOf(subColor.code()) : colorCode;

        String result = format
            .replace("{color}", colorCode)
            .replace("{subColor}", subColorCode)
            .replace("{icon}", stat.icon() != null ? stat.icon() : "")
            .replace("{stat}", stat.stat() != null ? stat.stat() : "")
            .replace("{display}", stat.display() != null ? stat.display() : "")
            .replace("{bold}", "l")
            .replace("{obfuscated}", "k")
            .replace("{reset}", "r")
            .replace("{gray}", "7");

        if (extraDetails != null && !extraDetails.isBlank()) {
            String numericDetail = formatNumericDetail(extraDetails);
            result = result.replace("{extraDetails}", numericDetail);
            result = resolveRemainingTokens(result, extraDetails);
        }

        return result;
    }

    private static String applyFlavorTokens(String format, Flavor flavor, @Nullable String extraDetails) {
        ChatColor color = flavor.chatColor();
        ChatColor subColor = flavor.secondaryChatColor();
        String colorCode = color != null ? String.valueOf(color.code()) : "f";
        String subColorCode = subColor != null ? String.valueOf(subColor.code()) : colorCode;

        String result = format
            .replace("{color}", colorCode)
            .replace("{subColor}", subColorCode)
            .replace("{icon}", flavor.icon() != null ? flavor.icon() : "")
            .replace("{stat}", flavor.stat() != null ? flavor.stat() : "")
            .replace("{display}", flavor.display() != null ? flavor.display() : "")
            .replace("{bold}", "l")
            .replace("{obfuscated}", "k")
            .replace("{reset}", "r")
            .replace("{gray}", "7");

        if (extraDetails != null && !extraDetails.isBlank()) {
            result = result.replace("{extraDetails}", extraDetails);
            result = resolveRemainingTokens(result, extraDetails);
        }

        return result;
    }

    /**
     * Resolves any remaining {@code {token}} placeholders in the result by positionally
     * mapping them to colon-delimited segments of {@code extraDetails}.
     *
     * <p>For example, if the format still contains {@code {abilityName}} and {@code {abilityType}}
     * and extraDetails is {@code "Wither Impact:RIGHT CLICK"}, the first unresolved token gets
     * "Wither Impact" and the second gets "RIGHT CLICK".
     */
    private static String resolveRemainingTokens(String result, String extraDetails) {
        Matcher tokenMatcher = TOKEN_PATTERN.matcher(result);
        List<String> unresolvedTokens = new ArrayList<>();
        while (tokenMatcher.find()) {
            unresolvedTokens.add(tokenMatcher.group(0));
        }

        if (unresolvedTokens.isEmpty()) {
            return result;
        }

        String[] parts = extraDetails.split(":");
        for (int i = 0; i < unresolvedTokens.size() && i < parts.length; i++) {
            result = result.replace(unresolvedTokens.get(i), parts[i].trim());
        }

        return result;
    }

    /**
     * Formats a numeric extra-details string with a leading {@code +} sign when positive,
     * or passes non-numeric values through unchanged.
     */
    private static String formatNumericDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        try {
            double value = Double.parseDouble(detail);
            if (value > 0) {
                return "+" + formatNumber(value);
            }
            return formatNumber(value);
        } catch (NumberFormatException e) {
            return detail;
        }
    }

    /** Formats a double as an integer when it has no fractional part, otherwise as a decimal. */
    private static String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * Resolves all {@code %%key%%} / {@code %%key:value%%} placeholders in the given lore string.
     *
     * <p>Each placeholder is looked up first in the stats registry, then in the flavors registry.
     * Unresolved placeholders are left unchanged.
     *
     * @param rawLore the raw lore string containing placeholders
     * @return the lore string with all resolvable placeholders replaced
     */
    static String resolvePlaceholders(String rawLore) {
        if (rawLore == null || rawLore.isBlank()) {
            return rawLore;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(rawLore);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2); // may be null

            String resolved = tryResolveStat(key, value);
            if (resolved == null) {
                resolved = tryResolveFlavor(key, value);
            }

            if (resolved != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    @Nullable
    private static String tryResolveStat(String key, @Nullable String value) {
        return Stat.byName(key).map(stat -> formatStatLine(stat, value)).orElse(null);
    }

    @Nullable
    private static String tryResolveFlavor(String key, @Nullable String value) {
        return Flavor.byName(key).map(flavor -> formatFlavorLine(flavor, value)).orElse(null);
    }

    /**
     * Builder for {@link SkyBlockTooltipBuilder}.
     *
     * <p>Call {@link #build()} to produce a {@link TooltipRequest} ready to pass to
     * {@link net.aerh.jigsaw.core.generator.TooltipGenerator}.
     */
    public static final class Builder {

        private String itemName;
        private Rarity rarity;
        private String itemLore = "";
        private String type = "";
        private int alpha = TooltipRequest.DEFAULT_ALPHA;
        private int padding = TooltipRequest.DEFAULT_PADDING;
        private boolean firstLinePadding = true;
        private int maxLineLength = TooltipRequest.DEFAULT_MAX_LINE_LENGTH;
        private boolean centered = false;
        private boolean renderBorder = true;
        private transient int scaleFactor = 1;

        private Builder() {}

        /**
         * Sets the item name displayed as the first line of the tooltip.
         *
         * @param name the item name; must not be {@code null}
         * @return this builder
         */
        public Builder name(String name) {
            this.itemName = Objects.requireNonNull(name, "name must not be null");
            return this;
        }

        /**
         * Sets the rarity used to color the name prefix and generate the footer line.
         *
         * @param rarity the rarity, or {@code null} for no rarity decoration
         * @return this builder
         */
        public Builder rarity(@Nullable Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        /**
         * Sets the raw lore string. Lines are separated by {@code \n} (or the literal
         * {@code \\n} sequence). Placeholders in {@code %%key:value%%} format are resolved
         * against the SkyBlock stat and flavor registries when {@link #build()} is called.
         *
         * @param lore the lore string; may be {@code null} or empty
         * @return this builder
         */
        public Builder lore(@Nullable String lore) {
            this.itemLore = lore != null ? lore : "";
            return this;
        }

        /**
         * Sets the item type label appended to the rarity footer (e.g. {@code "SWORD"}).
         *
         * @param type the type label; may be {@code null} or empty for no type suffix
         * @return this builder
         */
        public Builder type(@Nullable String type) {
            this.type = type != null ? type : "";
            return this;
        }

        /**
         * Sets the background alpha value. Clamped to [0, 255].
         *
         * @param alpha the alpha value
         * @return this builder
         */
        public Builder alpha(int alpha) {
            this.alpha = Math.max(0, Math.min(255, alpha));
            return this;
        }

        /**
         * Sets the tooltip padding in pixels.
         *
         * @param padding the padding; negative values are treated as 0
         * @return this builder
         */
        public Builder padding(int padding) {
            this.padding = Math.max(0, padding);
            return this;
        }

        /**
         * Sets whether extra vertical padding is added below the first line.
         *
         * @param firstLinePadding {@code true} to enable first-line padding
         * @return this builder
         */
        public Builder firstLinePadding(boolean firstLinePadding) {
            this.firstLinePadding = firstLinePadding;
            return this;
        }

        /**
         * Sets the maximum visible character count per line.
         *
         * @param maxLineLength the max line length; values less than 1 are treated as 1
         * @return this builder
         */
        public Builder maxLineLength(int maxLineLength) {
            this.maxLineLength = Math.max(TooltipRequest.MIN_LINE_LENGTH, maxLineLength);
            return this;
        }

        /**
         * Sets whether each line should be horizontally centered.
         *
         * @param centered {@code true} to center text
         * @return this builder
         */
        public Builder centered(boolean centered) {
            this.centered = centered;
            return this;
        }

        /**
         * Sets whether to render the Minecraft-style tooltip border.
         *
         * @param renderBorder {@code true} to render the border
         * @return this builder
         */
        public Builder renderBorder(boolean renderBorder) {
            this.renderBorder = renderBorder;
            return this;
        }

        /**
         * Sets the integer scale multiplier applied to all pixel coordinates.
         * Values less than 1 are treated as 1.
         *
         * @param scaleFactor the scale factor
         * @return this builder
         */
        public Builder scaleFactor(int scaleFactor) {
            this.scaleFactor = Math.max(1, scaleFactor);
            return this;
        }

        /**
         * Builds the list of pre-formatted lines that will be passed to the {@link TooltipRequest}.
         *
         * <p>The list is constructed as follows:
         * <ol>
         *   <li>The item name, prefixed with the rarity color code if a non-NONE rarity is set.</li>
         *   <li>Each lore line, after resolving {@code %%key:value%%} placeholders.</li>
         *   <li>An empty separator line (always present between lore and footer).</li>
         *   <li>The rarity footer line (e.g. {@code "&6&lLEGENDARY SWORD"}),
         *       if a non-NONE rarity is set.</li>
         * </ol>
         *
         * @return an ordered list of formatted line strings
         */
        public List<String> buildLines() {
            List<String> lines = new ArrayList<>();

            // Name line
            if (itemName != null && !itemName.isBlank()) {
                String name = itemName;
                if (isNonNoneRarity(rarity)) {
                    name = rarity.colorCode() + name;
                }
                lines.add(name);
            }

            // Lore lines
            String resolvedLore = resolvePlaceholders(itemLore);
            if (resolvedLore != null && !resolvedLore.isEmpty()) {
                String[] rawLines = normalizeNewlines(resolvedLore).split("\n", -1);
                for (String line : rawLines) {
                    lines.add(line);
                }
            }

            // Rarity footer
            if (isNonNoneRarity(rarity)) {
                lines.add(""); // separator line
                String formattedType = type == null || type.isBlank() ? "" : " " + type.toUpperCase();
                lines.add(rarity.formattedDisplay() + formattedType);
            }

            return lines;
        }

        /**
         * Builds a {@link TooltipRequest} from the current builder state.
         *
         * <p>Placeholder resolution and rarity formatting are applied automatically.
         *
         * @return a fully populated {@link TooltipRequest}
         */
        public TooltipRequest build() {
            return TooltipRequest.builder()
                .lines(buildLines())
                .alpha(alpha)
                .padding(padding)
                .firstLinePadding(firstLinePadding)
                .maxLineLength(maxLineLength)
                .centeredText(centered)
                .renderBorder(renderBorder)
                .scaleFactor(scaleFactor)
                .build();
        }

        /**
         * Generates a Discord slash command string from the current builder state.
         *
         * <p>The base command name can be overridden via the {@code generator.base.command}
         * system property; it defaults to {@code "gen"}.
         *
         * @return a slash command string such as {@code "/gen item item_name: Hyperion rarity: legendary ..."}
         */
        public String buildSlashCommand() {
            String baseCommand = System.getProperty("generator.base.command", "gen");
            StringBuilder commandBuilder = new StringBuilder("/" + baseCommand + " item ");
            Field[] fields = this.getClass().getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);

                    if (Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }

                    Object value = field.get(this);
                    if (value != null && !(value instanceof String s && s.isEmpty())) {
                        String serialized = formatCommandValue(value);
                        if (serialized == null || serialized.isEmpty()) {
                            continue;
                        }

                        String paramName = camelCaseToSnakeCase(field.getName());
                        commandBuilder.append(paramName).append(": ").append(serialized).append(" ");
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Failed to build slash command", e);
                }
            }

            return commandBuilder.toString().trim();
        }

        // -----------------------------------------------------------------------
        // Private helpers
        // -----------------------------------------------------------------------

        private static boolean isNonNoneRarity(@Nullable Rarity r) {
            if (r == null) {
                return false;
            }
            // "none" rarity has an empty display and is treated as no rarity
            return r.display() != null && !r.display().isBlank();
        }

        private static String normalizeNewlines(String text) {
            if (text == null) {
                return "";
            }
            return text.replace("\\n", "\n");
        }

        private static String formatCommandValue(Object value) {
            if (value instanceof Boolean bool) {
                String s = bool.toString();
                return Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
            if (value instanceof Rarity r) {
                return r.name();
            }
            if (value instanceof String s) {
                return normalizeNewlines(s).replace("\n", "\\n");
            }
            return value.toString();
        }

        private static String camelCaseToSnakeCase(String camel) {
            if (camel == null) {
                return null;
            }
            return camel.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
        }
    }
}

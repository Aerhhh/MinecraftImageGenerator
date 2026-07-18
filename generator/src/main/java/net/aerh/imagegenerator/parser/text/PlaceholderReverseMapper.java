package net.aerh.imagegenerator.parser.text;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.data.Gemstone;
import net.aerh.imagegenerator.data.Flavor;
import net.aerh.imagegenerator.data.PackGlyphIndex;
import net.aerh.imagegenerator.data.ParseType;
import net.aerh.imagegenerator.data.Stat;
import net.aerh.imagegenerator.parser.Parser;
import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.text.LegacyCode;
import net.aerh.imagegenerator.text.wrapper.TextWrapper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Attempts to reverse map rendered lore/name text back into generator placeholders using configured data
 */
@Slf4j
public class PlaceholderReverseMapper {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{([^}]+)}");
    private static final String DEFAULT_CAPTURE = "[^\\n]+";
    private static final String MID_PATTERN_CAPTURE = "(?:[^\\n§&])+?";

    private record ReplacementRule(Pattern pattern, ReplacementProvider provider) {
    }

    @FunctionalInterface
    private interface ReplacementProvider {
        String provide(Matcher matcher);
    }

    private final List<ReplacementRule> rules;

    public PlaceholderReverseMapper() {
        PackGlyphIndex glyphIndex = PackGlyphIndex.fromRegistries();

        // Format rules (stat, gemstone, flavor) must run before bare-character icon rules:
        // pack-override characters double as icons.json base characters (e.g. U+E084 is both the
        // undead flavor's override and mob_undead's base), and only the surrounding format text
        // can disambiguate them.
        this.rules = new ArrayList<>();
        this.rules.addAll(buildStatRules());
        this.rules.addAll(buildGemstoneRules());
        this.rules.addAll(buildFlavorRules());
        this.rules.addAll(buildIconRules(glyphIndex));
        this.rules.addAll(buildStatIconRules(glyphIndex));

        log.info("Initialized PlaceholderReverseMapper with {} rules", rules.size());
    }

    public String mapPlaceholders(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String normalized = TextWrapper.normalizeNewlines(input.replace(LegacyCode.SECTION_SYMBOL, LegacyCode.AMPERSAND_SYMBOL));
        String[] lines = normalized.split("\n", -1);
        List<String> mapped = new ArrayList<>(lines.length);

        for (String line : lines) {
            mapped.add(applyRules(line));
        }

        return String.join("\n", mapped);
    }

    private String applyRules(String line) {
        String result = line;

        for (ReplacementRule rule : rules) {
            Matcher matcher = rule.pattern().matcher(result);
            boolean replaced = false;
            StringBuilder stringBuilder = new StringBuilder();

            while (matcher.find()) {
                String replacement = rule.provider().provide(matcher);
                matcher.appendReplacement(stringBuilder, Matcher.quoteReplacement(replacement));
                replaced = true;
            }

            if (replaced) {
                matcher.appendTail(stringBuilder);
                result = stringBuilder.toString();
            }
        }

        return result;
    }

    private List<ReplacementRule> buildStatRules() {
        List<ReplacementRule> statRules = new ArrayList<>();

        for (Stat stat : Stat.getStats()) {
            ParseType parseType = ParseType.byName(stat.getParseType());

            if (parseType == null) {
                log.warn("Missing parse type for stat '{}'", stat.getName());
                continue;
            }

            // One rule per format template with the base icon, plus a variant per distinct
            // pack-override character so pack-rendered lore reverse maps too.
            List<String> iconVariants = new ArrayList<>();
            iconVariants.add(null); // base icon
            if (stat.getPackOverrides() != null) {
                stat.getPackOverrides().values().stream()
                    .distinct()
                    .filter(character -> !character.equals(stat.getIcon()))
                    .forEach(iconVariants::add);
            }

            for (String iconVariant : iconVariants) {
                if (parseType.getFormatWithDetails() != null) {
                    statRules.add(buildStatRule(stat, parseType.getFormatWithDetails(), iconVariant));
                }

                if (parseType.getFormatWithoutDetails() != null) {
                    statRules.add(buildStatRule(stat, parseType.getFormatWithoutDetails(), iconVariant));
                }
            }
        }

        return statRules;
    }

    private List<ReplacementRule> buildFlavorRules() {
        List<ReplacementRule> flavorRules = new ArrayList<>();

        for (Flavor flavor : Flavor.getFlavors()) {
            ParseType parseType = ParseType.byName(flavor.getParseType());

            if (parseType == null) {
                log.warn("Missing parse type for flavor text '{}'", flavor.getName());
                continue;
            }

            // One rule per format template with the base icon, plus a variant per distinct
            // pack-override character so pack-rendered lore reverse maps too.
            List<String> iconVariants = new ArrayList<>();
            iconVariants.add(null); // base icon
            if (flavor.getPackOverrides() != null) {
                flavor.getPackOverrides().values().stream()
                    .distinct()
                    .filter(character -> !character.equals(flavor.getIcon()))
                    .forEach(iconVariants::add);
            }

            for (String iconVariant : iconVariants) {
                if (parseType.getFormatWithDetails() != null) {
                    flavorRules.add(buildFlavorRule(flavor, parseType.getFormatWithDetails(), iconVariant));
                }

                if (parseType.getFormatWithoutDetails() != null) {
                    flavorRules.add(buildFlavorRule(flavor, parseType.getFormatWithoutDetails(), iconVariant));
                }
            }
        }

        return flavorRules;
    }

    private ReplacementRule buildFlavorRule(Flavor flavor, String format, @Nullable String iconOverride) {
        PatternBuildResult result = buildPattern(format, token -> resolveFlavorToken(flavor, token, iconOverride));

        return new ReplacementRule(result.pattern(), matcher -> {
            List<String> parts = new ArrayList<>();
            if (!result.captureOrder().isEmpty()) {
                for (int i = 0; i < result.captureOrder().size(); i++) {
                    int groupIndex = i + 1;
                    if (groupIndex <= matcher.groupCount()) {
                        parts.add(matcher.group(groupIndex));
                    }
                }
            }

            String placeholder = "%%" + flavor.getName();
            if (!parts.isEmpty()) {
                placeholder += ":" + String.join(":", parts).trim();
            }

            return placeholder + "%%";
        });
    }

    private ReplacementRule buildStatRule(Stat stat, String format, @Nullable String iconOverride) {
        PatternBuildResult result = buildPattern(format, token -> resolveStatToken(stat, token, iconOverride));

        return new ReplacementRule(result.pattern(), matcher -> {
            List<String> parts = new ArrayList<>();
            if (!result.captureOrder().isEmpty()) {
                for (int i = 0; i < result.captureOrder().size(); i++) {
                    int groupIndex = i + 1;
                    if (groupIndex <= matcher.groupCount()) {
                        parts.add(matcher.group(groupIndex));
                    }
                }
            }

            String placeholder = "%%" + stat.getName();
            if (!parts.isEmpty()) {
                placeholder += ":" + String.join(":", parts).trim();
            }

            return placeholder + "%%";
        });
    }

    private List<ReplacementRule> buildIconRules(PackGlyphIndex glyphIndex) {
        // One rule per bare character (base icons AND icon pack-override characters), each owned by
        // exactly one canonical entry, so contested characters map deterministically.
        return glyphIndex.getBareCharacterOwners().entrySet().stream()
            .map(entry -> bareCharacterRule(entry.getKey(), entry.getValue().getName()))
            .collect(Collectors.toList());
    }

    private List<ReplacementRule> buildStatIconRules(PackGlyphIndex glyphIndex) {
        // Stat characters not claimed by any icon regenerate through the icon-only reference form
        // (%%icon:health%%); these rules run after icon rules and after stat format rules, so they
        // only catch stat glyphs standing outside formatted stat text.
        return glyphIndex.getStatBareCharacterOwners().entrySet().stream()
            .map(entry -> bareCharacterRule(entry.getKey(),
                Parser.ICON_REFERENCE_NAME + ":" + entry.getValue().getName()))
            .collect(Collectors.toList());
    }

    private ReplacementRule bareCharacterRule(String iconText, String placeholderName) {
        Pattern pattern = Pattern.compile("(" + Pattern.quote(iconText) + ")+");

        return new ReplacementRule(pattern, matcher -> {
            int count = matcher.group(0).length() / iconText.length();
            String placeholder = "%%" + placeholderName;

            if (count > 1) {
                placeholder += ":" + count;
            }

            return placeholder + "%%";
        });
    }

    private List<ReplacementRule> buildGemstoneRules() {
        List<ReplacementRule> gemstoneRules = new ArrayList<>();

        for (Gemstone gemstone : Gemstone.getGemstones()) {
            String icon = safeString(gemstone.getIcon());
            String formattedIcon = safeString(gemstone.getFormattedIcon());

            // Formatted variants first (they carry a color-code prefix), then bare characters;
            // pack-override characters join both groups so pack-rendered slots reverse map too.
            Set<String> iconAlternatives = new LinkedHashSet<>();
            iconAlternatives.add(formattedIcon);
            if (gemstone.getPackOverrides() != null && !icon.isEmpty()) {
                gemstone.getPackOverrides().values()
                    .forEach(override -> iconAlternatives.add(formattedIcon.replace(icon, override)));
            }
            iconAlternatives.add(icon);
            if (gemstone.getPackOverrides() != null) {
                iconAlternatives.addAll(gemstone.getPackOverrides().values());
            }

            for (Map.Entry<String, String> entry : safeMap(gemstone.getFormattedTiers()).entrySet()) {
                String tierName = entry.getKey();
                String format = entry.getValue();
                if (format == null) {
                    continue;
                }

                String alternation = iconAlternatives.stream()
                    .map(alternative -> Pattern.quote(alternative.replace(LegacyCode.SECTION_SYMBOL, LegacyCode.AMPERSAND_SYMBOL)))
                    .collect(Collectors.joining("|"));
                // The tier format is literal text (brackets included), so every segment around
                // the icon placeholder must be regex-quoted.
                String regex = Arrays.stream(format.replace(LegacyCode.SECTION_SYMBOL, LegacyCode.AMPERSAND_SYMBOL).split("%s", -1))
                    .map(segment -> segment.isEmpty() ? "" : Pattern.quote(segment))
                    .collect(Collectors.joining("(?:" + alternation + ")"));

                Pattern pattern = Pattern.compile(regex);
                gemstoneRules.add(new ReplacementRule(pattern, matcher -> "%%" + gemstone.getName() + ":" + tierName + "%%"));
            }
        }

        return gemstoneRules;
    }

    private record PatternBuildResult(Pattern pattern, List<String> captureOrder) {
    }

    private PatternBuildResult buildPattern(String format, TokenResolver resolver) {
        StringBuilder regex = new StringBuilder();
        List<String> captureOrder = new ArrayList<>();

        List<MatchResult> tokens = TOKEN_PATTERN.matcher(format).results().toList();
        int last = 0;

        for (int i = 0; i < tokens.size(); i++) {
            MatchResult token = tokens.get(i);
            String literal = format.substring(last, token.start());
            regex.append(escapeLiteral(literal));

            String key = token.group(1);
            String value = resolver.resolve(key);
            if (value != null && !value.isEmpty()) {
                regex.append(escapeLiteral(value));
            } else {
                captureOrder.add(key);
                // A trailing capture may greedily consume the rest of the segment (POST/POST_DUAL/ITEM_STAT/ABILITY),
                // but a mid-pattern capture must stop at the next color code so the same stat can match twice on one line
                boolean trailing = i == tokens.size() - 1 && format.substring(token.end()).isEmpty();
                regex.append("(").append(trailing ? DEFAULT_CAPTURE : MID_PATTERN_CAPTURE).append(")");
            }

            last = token.end();
        }

        regex.append(escapeLiteral(format.substring(last)));
        regex.append("(?:[§&]r)?");

        String regexString = escapeAmpersands(regex.toString());
        return new PatternBuildResult(Pattern.compile(regexString, Pattern.CASE_INSENSITIVE), captureOrder);
    }

    private static String escapeLiteral(String text) {
        return Pattern.quote(text);
    }

    private String resolveFlavorToken(Flavor flavor, String token, @Nullable String iconOverride) {
        String value = resolveFlavorToken(flavor, token);

        // Icon, stat and display values may embed the icon character anywhere (e.g. "This armor
        // piece is undead X!"), so pack variants swap every occurrence.
        if (iconOverride != null && value != null
            && flavor.getIcon() != null && !flavor.getIcon().isEmpty()
            && ("icon".equalsIgnoreCase(token) || "stat".equalsIgnoreCase(token) || "display".equalsIgnoreCase(token))) {
            return value.replace(flavor.getIcon(), iconOverride);
        }

        return value;
    }

    private String resolveFlavorToken(Flavor flavor, String token) {
        if ("ampersand".equalsIgnoreCase(token)) {
            return String.valueOf(LegacyCode.AMPERSAND_SYMBOL);
        }

        LegacyCode chatFormat = LegacyCode.of(token.toUpperCase());
        if (chatFormat != null) {
            return String.valueOf(chatFormat.getCode());
        }

        try {
            String methodName = "get" + Character.toUpperCase(token.charAt(0)) + token.substring(1);
            Method method = Flavor.class.getMethod(methodName);
            Object value = method.invoke(flavor);
            if (value instanceof ChatColor.Legacy) {
                return String.valueOf(((ChatColor.Legacy) value).codeChar());
            }
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String escapeAmpersands(String input) {
        return input.replace("\\Q&\\E", "[§&]");
    }

    @FunctionalInterface
    private interface TokenResolver {
        String resolve(String token);
    }

    private String resolveStatToken(Stat stat, String token, @Nullable String iconOverride) {
        if (iconOverride != null) {
            if ("icon".equalsIgnoreCase(token)) {
                return iconOverride;
            }
            if ("display".equalsIgnoreCase(token)) {
                return stat.getStat() != null ? iconOverride + " " + stat.getStat() : iconOverride;
            }
        }

        return resolveStatToken(stat, token);
    }

    private String resolveStatToken(Stat stat, String token) {
        if ("ampersand".equalsIgnoreCase(token)) {
            return String.valueOf(LegacyCode.AMPERSAND_SYMBOL);
        }

        LegacyCode chatFormat = LegacyCode.of(token.toUpperCase());
        if (chatFormat != null) {
            return String.valueOf(chatFormat.getCode());
        }

        try {
            String methodName = "get" + Character.toUpperCase(token.charAt(0)) + token.substring(1);
            Method method = Stat.class.getMethod(methodName);
            Object value = method.invoke(stat);
            if (value instanceof ChatColor.Legacy cf) {
                return String.valueOf(cf.codeChar());
            }
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, String> safeMap(Map<String, String> map) {
        return map == null ? new LinkedHashMap<>() : map;
    }
}

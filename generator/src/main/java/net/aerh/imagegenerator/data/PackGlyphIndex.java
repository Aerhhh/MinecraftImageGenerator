package net.aerh.imagegenerator.data;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.parser.Parser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates the pack-glyph data ({@code packOverrides}) across icons.json and stats.json, and
 * resolves which icon entry owns each bare character for reverse mapping.
 * <p>
 * The bare-character domain has two tiers. Icons.json entries (base characters plus their override
 * characters) own bare characters first: an icon placeholder regenerates the bare character
 * faithfully. Stat characters not claimed by any icon form a second tier that regenerates through
 * the icon-only {@code %%icon:<stat>%%} form; formatted stat text still reverse maps through stat
 * <em>format</em> rules, which run before any bare-character rule. Within a tier, a character
 * claimed by multiple entries resolves to the first entry in file order.
 * <p>
 * Validation fails fast with an {@link IllegalStateException} listing every violation:
 * <ul>
 *     <li>entry names must match {@code [a-zA-Z_]+} (the placeholder pattern),</li>
 *     <li>icon names must not shadow stat/flavor/gemstone placeholder names,</li>
 *     <li>no entry may claim the reserved {@code %%icon:<name>%%} keyword as its name,</li>
 *     <li>{@code packOverrides} keys must parse as {@link PackId},</li>
 *     <li>{@code packOverrides} values must be exactly one code point.</li>
 * </ul>
 */
public final class PackGlyphIndex {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z_]+");

    // The data registries load once in static initializers and are immutable at runtime, so the
    // validated registry index is built once and cached (benign race: build is idempotent).
    private static volatile PackGlyphIndex cachedRegistryIndex;

    private final Map<String, Icon> bareCharacterOwners;
    private final Map<String, Stat> statBareCharacterOwners;

    private PackGlyphIndex(Map<String, Icon> bareCharacterOwners, Map<String, Stat> statBareCharacterOwners) {
        this.bareCharacterOwners = bareCharacterOwners;
        this.statBareCharacterOwners = statBareCharacterOwners;
    }

    /**
     * Returns the validated index over the loaded {@link Icon} and {@link Stat} registries,
     * building it on first use. Call this once at application startup to fail fast on invalid
     * data (including external override files) instead of on the first command that needs it.
     *
     * @return the validated index
     *
     * @throws IllegalStateException if the loaded data violates any pack-glyph constraint
     */
    public static PackGlyphIndex fromRegistries() {
        PackGlyphIndex index = cachedRegistryIndex;
        if (index == null) {
            index = build(Icon.getIcons(), Stat.getStats(), reservedRegistryNames());
            cachedRegistryIndex = index;
        }
        return index;
    }

    private static Map<String, String> reservedRegistryNames() {
        Map<String, String> reserved = new LinkedHashMap<>();
        Flavor.getFlavors().forEach(flavor -> {
            if (flavor.getName() != null) {
                reserved.put(flavor.getName(), "flavor.json");
            }
        });
        Gemstone.getGemstones().forEach(gemstone -> {
            if (gemstone.getName() != null) {
                reserved.put(gemstone.getName(), "gemstones.json");
            }
        });
        return reserved;
    }

    /**
     * Builds and validates an index from explicit entry collections. Exposed for tests.
     */
    static PackGlyphIndex build(Collection<Icon> icons, Collection<Stat> stats) {
        return build(icons, stats, Map.of());
    }

    /**
     * Builds and validates an index from explicit entry collections.
     *
     * @param icons         the icon entries participating in bare-character ownership
     * @param stats         the stat entries (validated only; stats never own bare characters)
     * @param reservedNames placeholder names owned by other registries (flavors, gemstones)
     *                      mapped to their source file; icon names must not collide with them
     *                      because IconParser runs first and would shadow them
     *
     * @return the validated index
     *
     * @throws IllegalStateException if the data violates any pack-glyph constraint
     */
    static PackGlyphIndex build(Collection<Icon> icons, Collection<Stat> stats, Map<String, String> reservedNames) {
        List<String> violations = new ArrayList<>();

        Set<String> statNames = new LinkedHashSet<>();
        for (Stat stat : stats) {
            if (stat.getName() != null) {
                statNames.add(stat.getName());
            }
        }

        for (Icon icon : icons) {
            validateName(icon.getName(), "icons.json", violations);
            validateReservedKeyword(icon.getName(), "icons.json", violations);
            validateOverrides(icon.getName(), icon.getPackOverrides(), "icons.json", violations);

            if (icon.getIcon() == null || icon.getIcon().isEmpty()) {
                violations.add("icons.json entry '" + icon.getName() + "' has no icon character");
            }

            String reservedBy = reservedNames.get(icon.getName());
            if (reservedBy != null) {
                violations.add("icons.json entry '" + icon.getName() + "' collides with a " + reservedBy
                    + " entry of the same name; IconParser runs first and would shadow it");
            }
            if (statNames.contains(icon.getName())) {
                violations.add("icons.json entry '" + icon.getName() + "' collides with a stats.json"
                    + " entry of the same name; IconParser runs first and would shadow it");
            }
        }

        for (Stat stat : stats) {
            validateName(stat.getName(), "stats.json", violations);
            validateReservedKeyword(stat.getName(), "stats.json", violations);
            validateOverrides(stat.getName(), stat.getPackOverrides(), "stats.json", violations);
        }

        reservedNames.forEach((name, file) -> validateReservedKeyword(name, file, violations));

        Map<String, List<Icon>> claims = new LinkedHashMap<>();
        for (Icon icon : icons) {
            Set<String> characters = new LinkedHashSet<>();

            if (icon.getIcon() != null && !icon.getIcon().isEmpty()) {
                characters.add(icon.getIcon());
            }
            if (icon.getPackOverrides() != null) {
                characters.addAll(icon.getPackOverrides().values());
            }

            for (String character : characters) {
                claims.computeIfAbsent(character, key -> new ArrayList<>()).add(icon);
            }
        }

        // A character claimed by multiple entries resolves to the first one in file order - the
        // difference is only which placeholder name /gen parse prints; both render identically.
        Map<String, Icon> owners = new LinkedHashMap<>();
        claims.forEach((character, claimants) -> owners.put(character, claimants.getFirst()));

        // Stat characters not claimed by any icon form the second bare-character tier; they
        // regenerate through %%icon:<stat>%%, so icons keep precedence for contested characters.
        Map<String, Stat> statOwners = new LinkedHashMap<>();
        for (Stat stat : stats) {
            Set<String> characters = new LinkedHashSet<>();

            if (stat.getIcon() != null && !stat.getIcon().isEmpty()) {
                characters.add(stat.getIcon());
            }
            if (stat.getPackOverrides() != null) {
                characters.addAll(stat.getPackOverrides().values());
            }

            for (String character : characters) {
                if (!owners.containsKey(character)) {
                    statOwners.putIfAbsent(character, stat);
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException("Invalid pack glyph data:\n - " + String.join("\n - ", violations));
        }

        return new PackGlyphIndex(owners, statOwners);
    }

    /**
     * @return every bare character mapped to the icon entry that canonically owns it
     */
    public Map<String, Icon> getBareCharacterOwners() {
        return Collections.unmodifiableMap(bareCharacterOwners);
    }

    /**
     * @return every bare character owned by a stat (none claimed by an icon), mapped to the stat
     *     whose {@code %%icon:<name>%%} form regenerates it
     */
    public Map<String, Stat> getStatBareCharacterOwners() {
        return Collections.unmodifiableMap(statBareCharacterOwners);
    }

    private static void validateReservedKeyword(@Nullable String name, String file, List<String> violations) {
        if (Parser.ICON_REFERENCE_NAME.equalsIgnoreCase(name)) {
            violations.add(file + " entry '" + name + "' collides with the reserved %%"
                + Parser.ICON_REFERENCE_NAME + ":<name>%% placeholder keyword");
        }
    }

    private static void validateName(@Nullable String name, String file, List<String> violations) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            violations.add(file + " entry name '" + name + "' must match " + NAME_PATTERN.pattern()
                + " (letters and underscores only; digits are not matched by the placeholder pattern)");
        }
    }

    private static void validateOverrides(String entryName, @Nullable Map<String, String> overrides,
                                          String file, List<String> violations) {
        if (overrides == null) {
            return;
        }

        overrides.forEach((packKey, character) -> {
            try {
                PackId.parse(packKey);
            } catch (IllegalArgumentException e) {
                violations.add(file + " entry '" + entryName + "' has invalid packOverrides key '"
                    + packKey + "': " + e.getMessage());
            }

            if (character == null || character.isEmpty()
                || character.codePointCount(0, character.length()) != 1) {
                violations.add(file + " entry '" + entryName + "' has packOverrides value "
                    + describe(character) + " for '" + packKey + "'; exactly one code point required");
            }
        });
    }

    private static String describe(@Nullable String character) {
        if (character == null || character.isEmpty()) {
            return "''";
        }
        if (character.codePointCount(0, character.length()) == 1) {
            return "U+%04X".formatted(character.codePointAt(0));
        }
        return "'" + character + "'";
    }
}

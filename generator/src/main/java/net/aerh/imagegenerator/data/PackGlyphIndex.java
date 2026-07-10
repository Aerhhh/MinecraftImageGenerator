package net.aerh.imagegenerator.data;

import net.aerh.imagegenerator.pack.PackId;
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
 * Validates the pack-glyph data ({@code packOverrides}) across icons.json, stats.json,
 * flavor.json and gemstones.json, and resolves which icon entry owns each bare character for
 * reverse mapping.
 * <p>
 * The bare-character domain covers icons.json entries only (base characters plus their override
 * characters): an icon placeholder regenerates the bare character faithfully, whereas stat,
 * flavor and gemstone placeholders expand to formatted text. Their override characters are
 * therefore reverse mapped through pack-variant <em>format</em> rules, never through
 * bare-character rules. A character claimed by multiple icon entries resolves to the first entry
 * in file order.
 * <p>
 * Validation fails fast with an {@link IllegalStateException} listing every violation:
 * <ul>
 *     <li>entry names must match {@code [a-zA-Z_]+} (the placeholder pattern),</li>
 *     <li>icon names must not shadow stat/flavor/gemstone placeholder names,</li>
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

    private PackGlyphIndex(Map<String, Icon> bareCharacterOwners) {
        this.bareCharacterOwners = bareCharacterOwners;
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
            index = build(Icon.getIcons(), Stat.getStats(), Flavor.getFlavors(), Gemstone.getGemstones());
            cachedRegistryIndex = index;
        }
        return index;
    }

    /**
     * Builds and validates an index from explicit entry collections. Exposed for tests.
     */
    static PackGlyphIndex build(Collection<Icon> icons, Collection<Stat> stats) {
        return build(icons, stats, List.of(), List.of());
    }

    /**
     * Builds and validates an index from explicit entry collections.
     *
     * @param icons     the icon entries participating in bare-character ownership
     * @param stats     the stat entries (validated only; stats never own bare characters)
     * @param flavors   the flavor entries (validated only; flavor glyphs reverse map through
     *                  flavor format rules); their names are reserved against icon shadowing
     * @param gemstones the gemstone entries (validated only; gemstone glyphs reverse map through
     *                  gemstone tier rules); their names are reserved against icon shadowing
     *
     * @return the validated index
     *
     * @throws IllegalStateException if the data violates any pack-glyph constraint
     */
    static PackGlyphIndex build(Collection<Icon> icons, Collection<Stat> stats,
                                Collection<Flavor> flavors, Collection<Gemstone> gemstones) {
        List<String> violations = new ArrayList<>();

        Map<String, String> reservedNames = new LinkedHashMap<>();
        for (Flavor flavor : flavors) {
            if (flavor.getName() != null) {
                reservedNames.put(flavor.getName(), "flavor.json");
            }
        }
        for (Gemstone gemstone : gemstones) {
            if (gemstone.getName() != null) {
                reservedNames.put(gemstone.getName(), "gemstones.json");
            }
        }

        Set<String> statNames = new LinkedHashSet<>();
        for (Stat stat : stats) {
            if (stat.getName() != null) {
                statNames.add(stat.getName());
            }
        }

        for (Icon icon : icons) {
            validateName(icon.getName(), "icons.json", violations);
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
            validateOverrides(stat.getName(), stat.getPackOverrides(), "stats.json", violations);
        }

        for (Flavor flavor : flavors) {
            validateName(flavor.getName(), "flavor.json", violations);
            validateOverrides(flavor.getName(), flavor.getPackOverrides(), "flavor.json", violations);
        }

        for (Gemstone gemstone : gemstones) {
            validateName(gemstone.getName(), "gemstones.json", violations);
            validateOverrides(gemstone.getName(), gemstone.getPackOverrides(), "gemstones.json", violations);
        }

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

        if (!violations.isEmpty()) {
            throw new IllegalStateException("Invalid pack glyph data:\n - " + String.join("\n - ", violations));
        }

        return new PackGlyphIndex(owners);
    }

    /**
     * @return every bare character mapped to the icon entry that canonically owns it
     */
    public Map<String, Icon> getBareCharacterOwners() {
        return Collections.unmodifiableMap(bareCharacterOwners);
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

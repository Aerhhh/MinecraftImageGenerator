package net.aerh.imagegenerator.tools.pack;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One resource pack to register at startup.
 *
 * @param id             Pack identifier in {@code namespace:name} form, for example {@code hypixel:skyblock}
 * @param path           Path to the pack zip file or extracted pack directory
 * @param tooltipStyles  Tooltip style refs keyed by rarity name, for example {@code legendary} to
 *                       {@code hypixel_skyblock:legendary}. Rarities without an entry render with the
 *                       pack's default tooltip override, if any
 * @param textColorRemap Text color replacements keyed by vanilla hex color, for example {@code #AA0000}
 *                       to {@code #D13228}, mirroring the pack's text shader palette swap
 * @param variantOf      Id of the pack this one is a variant of, for example {@code hypixel:skyblock} for an
 *                       alpha build of the SkyBlock pack, so that pack's placeholder overrides and emissive
 *                       alpha convention apply to this one too. Null or blank for none. Kept as configured;
 *                       the pack service trims and lowercases it like the id when registering
 */
public record PackDefinition(String id, String path, Map<String, String> tooltipStyles, Map<String, String> textColorRemap,
                             @Nullable String variantOf) {

    public PackDefinition {
        tooltipStyles = copyKeepingNulls(tooltipStyles);
        textColorRemap = copyKeepingNulls(textColorRemap);
    }

    /** A definition that is not a variant of another pack. */
    public PackDefinition(String id, String path, Map<String, String> tooltipStyles, Map<String, String> textColorRemap) {
        this(id, path, tooltipStyles, textColorRemap, null);
    }

    /**
     * An unmodifiable, insertion-ordered copy. Unlike {@code Map.copyOf} it tolerates null keys and
     * values, which a config file can produce; the pack service rejects those entries with a clear
     * log line instead of the whole config failing to load.
     */
    private static Map<String, String> copyKeepingNulls(Map<String, String> map) {
        return Collections.unmodifiableMap(map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map));
    }

    /** A definition with no theming and no variant. */
    public static PackDefinition of(String id, String path) {
        return new PackDefinition(id, path, Map.of(), Map.of());
    }
}

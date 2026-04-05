package net.aerh.jigsaw.core.data;

import net.aerh.jigsaw.api.data.RegistryKey;
import net.aerh.jigsaw.core.data.types.*;

/**
 * Central collection of {@link RegistryKey} constants for every built-in data type.
 *
 * <p>Pass these to {@link JsonDataRegistry} to identify a registry's content type and name.
 */
public final class DataRegistryKeys {

    /**
     * Registry key for item rarity tiers (e.g. COMMON, RARE, LEGENDARY).
     */
    public static final RegistryKey<Rarity> RARITIES = RegistryKey.of("rarities", Rarity.class);

    /** Registry key for item stats (e.g. Strength, Defense, Health). */
    public static final RegistryKey<Stat> STATS = RegistryKey.of("stats", Stat.class);

    /** Registry key for flavor line types (e.g. requires, view_recipes). */
    public static final RegistryKey<Flavor> FLAVORS = RegistryKey.of("flavors", Flavor.class);

    /** Registry key for gemstone types (e.g. ruby, amethyst). */
    public static final RegistryKey<Gemstone> GEMSTONES = RegistryKey.of("gemstones", Gemstone.class);

    /** Registry key for named icon characters used in tooltip rendering. */
    public static final RegistryKey<Icon> ICONS = RegistryKey.of("icons", Icon.class);

    /** Registry key for stat parse/display format types. */
    public static final RegistryKey<ParseType> PARSE_TYPES = RegistryKey.of("parse_types", ParseType.class);

    /** Registry key for Accessory Bag power stone strength tiers. */
    public static final RegistryKey<PowerStrength> POWER_STRENGTHS = RegistryKey.of("power_strengths", PowerStrength.class);

    /** Registry key for armor material types and their rendering capabilities. */
    public static final RegistryKey<ArmorType> ARMOR_TYPES = RegistryKey.of("armor_types", ArmorType.class);

    private DataRegistryKeys() {
        // utility class - no instances
    }
}

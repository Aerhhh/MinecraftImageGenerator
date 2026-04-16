package net.aerh.jigsaw.skyblock.data;

import net.aerh.jigsaw.api.data.RegistryKey;

/**
 * Canonical {@link RegistryKey} constants for all SkyBlock data registries.
 *
 * <p>These keys are used internally by {@link SkyBlockRegistries} and may also be used by
 * callers who need to reference a registry by key rather than by direct static accessor.
 */
public final class SkyBlockRegistryKeys {

    /** Registry key for {@link Rarity} entries. */
    public static final RegistryKey<Rarity> RARITIES = RegistryKey.of("rarities", Rarity.class);

    /** Registry key for {@link Stat} entries. */
    public static final RegistryKey<Stat> STATS = RegistryKey.of("stats", Stat.class);

    /** Registry key for {@link Flavor} entries. */
    public static final RegistryKey<Flavor> FLAVORS = RegistryKey.of("flavors", Flavor.class);

    /** Registry key for {@link Gemstone} entries. */
    public static final RegistryKey<Gemstone> GEMSTONES = RegistryKey.of("gemstones", Gemstone.class);

    /** Registry key for {@link Icon} entries. */
    public static final RegistryKey<Icon> ICONS = RegistryKey.of("icons", Icon.class);

    /** Registry key for {@link ParseType} entries. */
    public static final RegistryKey<ParseType> PARSE_TYPES = RegistryKey.of("parse_types", ParseType.class);

    /** Registry key for {@link PowerStrength} entries. */
    public static final RegistryKey<PowerStrength> POWER_STRENGTHS = RegistryKey.of("power_strengths", PowerStrength.class);

    /** Registry key for {@link ArmorType} entries. */
    public static final RegistryKey<ArmorType> ARMOR_TYPES = RegistryKey.of("armor_types", ArmorType.class);

    private SkyBlockRegistryKeys() {}
}

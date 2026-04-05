package net.aerh.jigsaw.skyblock.data;

import net.aerh.jigsaw.api.data.DataRegistry;
import net.aerh.jigsaw.core.data.JsonDataRegistry;

/**
 * Lazy-initialized holder for all SkyBlock data registries.
 *
 * <p>Each registry is constructed on first access and is thereafter shared for the lifetime of
 * the JVM. Construction is thread-safe: the {@code synchronized} block on the holder class
 * ensures that only one thread populates the registry even under concurrent access.
 *
 * <p>All registries load their data from JSON files on the classpath under {@code data/}.
 */
public final class SkyBlockRegistries {

    private SkyBlockRegistries() {}

    // -----------------------------------------------------------------------
    // Rarity
    // -----------------------------------------------------------------------

    private static volatile DataRegistry<Rarity> RARITIES;

    /**
     * Returns the registry of all {@link Rarity} entries.
     *
     * @return the {@link Rarity} registry; never {@code null}
     */
    public static DataRegistry<Rarity> rarities() {
        if (RARITIES == null) {
            synchronized (SkyBlockRegistries.class) {
                if (RARITIES == null) {
                    RARITIES = new JsonDataRegistry<>(
                        SkyBlockRegistryKeys.RARITIES,
                        Rarity[].class,
                        "data/rarities.json",
                        Rarity::name
                    );
                }
            }
        }
        return RARITIES;
    }

    // -----------------------------------------------------------------------
    // Stat
    // -----------------------------------------------------------------------

    private static volatile DataRegistry<Stat> STATS;

    /**
     * Returns the registry of all {@link Stat} entries.
     *
     * @return the {@link Stat} registry; never {@code null}
     */
    public static DataRegistry<Stat> stats() {
        if (STATS == null) {
            synchronized (SkyBlockRegistries.class) {
                if (STATS == null) {
                    STATS = new JsonDataRegistry<>(
                        SkyBlockRegistryKeys.STATS,
                        Stat[].class,
                        "data/stats.json",
                        Stat::name
                    );
                }
            }
        }
        return STATS;
    }

    // -----------------------------------------------------------------------
    // Flavor
    // -----------------------------------------------------------------------

    private static volatile DataRegistry<Flavor> FLAVORS;

    /**
     * Returns the registry of all {@link Flavor} entries.
     *
     * @return the {@link Flavor} registry; never {@code null}
     */
    public static DataRegistry<Flavor> flavors() {
        if (FLAVORS == null) {
            synchronized (SkyBlockRegistries.class) {
                if (FLAVORS == null) {
                    FLAVORS = new JsonDataRegistry<>(
                        SkyBlockRegistryKeys.FLAVORS,
                        Flavor[].class,
                        "data/flavor.json",
                        Flavor::name
                    );
                }
            }
        }
        return FLAVORS;
    }

    // -----------------------------------------------------------------------
    // Gemstone
    // -----------------------------------------------------------------------

    private static volatile DataRegistry<Gemstone> GEMSTONES;

    /**
     * Returns the registry of all {@link Gemstone} entries.
     *
     * @return the {@link Gemstone} registry; never {@code null}
     */
    public static DataRegistry<Gemstone> gemstones() {
        if (GEMSTONES == null) {
            synchronized (SkyBlockRegistries.class) {
                if (GEMSTONES == null) {
                    GEMSTONES = new JsonDataRegistry<>(
                        SkyBlockRegistryKeys.GEMSTONES,
                        Gemstone[].class,
                        "data/gemstones.json",
                        Gemstone::name
                    );
                }
            }
        }
        return GEMSTONES;
    }

    // -----------------------------------------------------------------------
    // Icon
    // -----------------------------------------------------------------------

    private static volatile DataRegistry<Icon> ICONS;

    /**
     * Returns the registry of all {@link Icon} entries.
     *
     * @return the {@link Icon} registry; never {@code null}
     */
    public static DataRegistry<Icon> icons() {
        if (ICONS == null) {
            synchronized (SkyBlockRegistries.class) {
                if (ICONS == null) {
                    ICONS = new JsonDataRegistry<>(
                        SkyBlockRegistryKeys.ICONS,
                        Icon[].class,
                        "data/icons.json",
                        Icon::name
                    );
                }
            }
        }
        return ICONS;
    }

    // -----------------------------------------------------------------------
    // ParseType
    // -----------------------------------------------------------------------

    private static volatile DataRegistry<ParseType> PARSE_TYPES;

    /**
     * Returns the registry of all {@link ParseType} entries.
     *
     * @return the {@link ParseType} registry; never {@code null}
     */
    public static DataRegistry<ParseType> parseTypes() {
        if (PARSE_TYPES == null) {
            synchronized (SkyBlockRegistries.class) {
                if (PARSE_TYPES == null) {
                    PARSE_TYPES = new JsonDataRegistry<>(
                        SkyBlockRegistryKeys.PARSE_TYPES,
                        ParseType[].class,
                        "data/parse_types.json",
                        ParseType::name
                    );
                }
            }
        }
        return PARSE_TYPES;
    }

    // -----------------------------------------------------------------------
    // PowerStrength
    // -----------------------------------------------------------------------

    private static volatile DataRegistry<PowerStrength> POWER_STRENGTHS;

    /**
     * Returns the registry of all {@link PowerStrength} entries.
     *
     * @return the {@link PowerStrength} registry; never {@code null}
     */
    public static DataRegistry<PowerStrength> powerStrengths() {
        if (POWER_STRENGTHS == null) {
            synchronized (SkyBlockRegistries.class) {
                if (POWER_STRENGTHS == null) {
                    POWER_STRENGTHS = new JsonDataRegistry<>(
                        SkyBlockRegistryKeys.POWER_STRENGTHS,
                        PowerStrength[].class,
                        "data/power_strengths.json",
                        PowerStrength::name
                    );
                }
            }
        }
        return POWER_STRENGTHS;
    }

    // -----------------------------------------------------------------------
    // ArmorType
    // -----------------------------------------------------------------------

    private static volatile DataRegistry<ArmorType> ARMOR_TYPES;

    /**
     * Returns the registry of all {@link ArmorType} entries.
     *
     * @return the {@link ArmorType} registry; never {@code null}
     */
    public static DataRegistry<ArmorType> armorTypes() {
        if (ARMOR_TYPES == null) {
            synchronized (SkyBlockRegistries.class) {
                if (ARMOR_TYPES == null) {
                    ARMOR_TYPES = new JsonDataRegistry<>(
                        SkyBlockRegistryKeys.ARMOR_TYPES,
                        ArmorType[].class,
                        "data/armor_types.json",
                        ArmorType::materialName
                    );
                }
            }
        }
        return ARMOR_TYPES;
    }
}

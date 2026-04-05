package net.aerh.jigsaw.core.data;

import net.aerh.jigsaw.api.data.RegistryKey;
import net.aerh.jigsaw.core.data.types.*;

/**
 * Central collection of {@link RegistryKey} constants for every built-in data type.
 *
 * <p>Pass these to {@link JsonDataRegistry} to identify a registry's content type and name.
 */
public final class DataRegistryKeys {

    public static final RegistryKey<Rarity> RARITIES = RegistryKey.of("rarities", Rarity.class);
    public static final RegistryKey<Stat> STATS = RegistryKey.of("stats", Stat.class);
    public static final RegistryKey<Flavor> FLAVORS = RegistryKey.of("flavors", Flavor.class);
    public static final RegistryKey<Gemstone> GEMSTONES = RegistryKey.of("gemstones", Gemstone.class);
    public static final RegistryKey<Icon> ICONS = RegistryKey.of("icons", Icon.class);
    public static final RegistryKey<ParseType> PARSE_TYPES = RegistryKey.of("parse_types", ParseType.class);
    public static final RegistryKey<PowerStrength> POWER_STRENGTHS = RegistryKey.of("power_strengths", PowerStrength.class);
    public static final RegistryKey<ArmorType> ARMOR_TYPES = RegistryKey.of("armor_types", ArmorType.class);

    private DataRegistryKeys() {
        // utility class - no instances
    }
}

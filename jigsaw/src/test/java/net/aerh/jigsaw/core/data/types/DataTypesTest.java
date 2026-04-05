package net.aerh.jigsaw.core.data.types;

import net.aerh.jigsaw.core.data.DataRegistryKeys;
import net.aerh.jigsaw.core.data.JsonDataRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that each data type loads correctly from the real JSON resources in data/.
 */
class DataTypesTest {

    // --- Rarity ---

    @Test
    void rarities_loadsFromJson() {
        JsonDataRegistry<Rarity> reg = new JsonDataRegistry<>(
                DataRegistryKeys.RARITIES, Rarity[].class, "data/rarities.json", Rarity::name);
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void rarities_knownEntryResolves() {
        JsonDataRegistry<Rarity> reg = new JsonDataRegistry<>(
                DataRegistryKeys.RARITIES, Rarity[].class, "data/rarities.json", Rarity::name);
        assertThat(reg.get("legendary")).isPresent()
                .hasValueSatisfying(r -> {
                    assertThat(r.display()).isEqualTo("LEGENDARY");
                    assertThat(r.color()).isEqualTo("GOLD");
                });
    }

    @Test
    void rarities_lookupIsCaseInsensitive() {
        JsonDataRegistry<Rarity> reg = new JsonDataRegistry<>(
                DataRegistryKeys.RARITIES, Rarity[].class, "data/rarities.json", Rarity::name);
        assertThat(reg.get("COMMON")).isPresent();
    }

    // --- Stat ---

    @Test
    void stats_loadsFromJson() {
        JsonDataRegistry<Stat> reg = new JsonDataRegistry<>(
                DataRegistryKeys.STATS, Stat[].class, "data/stats.json", Stat::name);
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void stats_knownEntryResolves() {
        JsonDataRegistry<Stat> reg = new JsonDataRegistry<>(
                DataRegistryKeys.STATS, Stat[].class, "data/stats.json", Stat::name);
        assertThat(reg.get("health")).isPresent()
                .hasValueSatisfying(s -> {
                    assertThat(s.stat()).isEqualTo("Health");
                    assertThat(s.color()).isEqualTo("RED");
                });
    }

    // --- Flavor ---

    @Test
    void flavors_loadsFromJson() {
        JsonDataRegistry<Flavor> reg = new JsonDataRegistry<>(
                DataRegistryKeys.FLAVORS, Flavor[].class, "data/flavor.json", Flavor::name);
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void flavors_knownEntryResolves() {
        JsonDataRegistry<Flavor> reg = new JsonDataRegistry<>(
                DataRegistryKeys.FLAVORS, Flavor[].class, "data/flavor.json", Flavor::name);
        assertThat(reg.get("requires")).isPresent()
                .hasValueSatisfying(f -> {
                    assertThat(f.color()).isEqualTo("DARK_RED");
                    assertThat(f.parseType()).isEqualTo("DIFFERENT_ICON_COLOR");
                });
    }

    // --- Gemstone ---

    @Test
    void gemstones_loadsFromJson() {
        JsonDataRegistry<Gemstone> reg = new JsonDataRegistry<>(
                DataRegistryKeys.GEMSTONES, Gemstone[].class, "data/gemstones.json", Gemstone::name);
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void gemstones_knownEntryResolves() {
        JsonDataRegistry<Gemstone> reg = new JsonDataRegistry<>(
                DataRegistryKeys.GEMSTONES, Gemstone[].class, "data/gemstones.json", Gemstone::name);
        assertThat(reg.get("gem_ruby")).isPresent()
                .hasValueSatisfying(g -> {
                    assertThat(g.color()).isEqualTo("#FF5555");
                    assertThat(g.formattedTiers()).containsKey("flawless");
                });
    }

    // --- Icon ---

    @Test
    void icons_loadsFromJson() {
        JsonDataRegistry<Icon> reg = new JsonDataRegistry<>(
                DataRegistryKeys.ICONS, Icon[].class, "data/icons.json", Icon::name);
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void icons_knownEntryResolves() {
        JsonDataRegistry<Icon> reg = new JsonDataRegistry<>(
                DataRegistryKeys.ICONS, Icon[].class, "data/icons.json", Icon::name);
        assertThat(reg.get("dot")).isPresent()
                .hasValueSatisfying(i -> assertThat(i.icon()).isEqualTo("•"));
    }

    // --- ParseType ---

    @Test
    void parseTypes_loadsFromJson() {
        JsonDataRegistry<ParseType> reg = new JsonDataRegistry<>(
                DataRegistryKeys.PARSE_TYPES, ParseType[].class, "data/parse_types.json", ParseType::name);
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void parseTypes_knownEntryResolves() {
        JsonDataRegistry<ParseType> reg = new JsonDataRegistry<>(
                DataRegistryKeys.PARSE_TYPES, ParseType[].class, "data/parse_types.json", ParseType::name);
        assertThat(reg.get("NORMAL")).isPresent()
                .hasValueSatisfying(p -> {
                    assertThat(p.formatWithDetails()).isNotBlank();
                    assertThat(p.formatWithoutDetails()).isNotBlank();
                });
    }

    // --- PowerStrength ---

    @Test
    void powerStrengths_loadsFromJson() {
        JsonDataRegistry<PowerStrength> reg = new JsonDataRegistry<>(
                DataRegistryKeys.POWER_STRENGTHS, PowerStrength[].class, "data/power_strengths.json", PowerStrength::name);
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void powerStrengths_knownEntryResolves() {
        JsonDataRegistry<PowerStrength> reg = new JsonDataRegistry<>(
                DataRegistryKeys.POWER_STRENGTHS, PowerStrength[].class, "data/power_strengths.json", PowerStrength::name);
        assertThat(reg.get("marvelous")).isPresent()
                .hasValueSatisfying(p -> {
                    assertThat(p.display()).isEqualTo("Marvelous");
                    assertThat(p.stone()).isTrue();
                });
    }

    // --- ArmorType ---

    @Test
    void armorTypes_loadsFromJson() {
        JsonDataRegistry<ArmorType> reg = new JsonDataRegistry<>(
                DataRegistryKeys.ARMOR_TYPES, ArmorType[].class, "data/armor_types.json", ArmorType::materialName);
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void armorTypes_knownEntryResolves() {
        JsonDataRegistry<ArmorType> reg = new JsonDataRegistry<>(
                DataRegistryKeys.ARMOR_TYPES, ArmorType[].class, "data/armor_types.json", ArmorType::materialName);
        assertThat(reg.get("leather")).isPresent()
                .hasValueSatisfying(a -> assertThat(a.supportsCustomColoring()).isTrue());
        assertThat(reg.get("diamond")).isPresent()
                .hasValueSatisfying(a -> assertThat(a.supportsCustomColoring()).isFalse());
    }
}

package net.aerh.jigsaw.skyblock.data;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that all 8 SkyBlock data registries load from JSON and expose correct lookup behaviour.
 */
class SkyBlockDataTest {

    // -----------------------------------------------------------------------
    // Rarity
    // -----------------------------------------------------------------------

    @Test
    void raritiesRegistryLoadsEntries() {
        assertThat(Rarity.getAllRarities()).isNotEmpty();
    }

    @Test
    void rarityByNameReturnsValue() {
        Optional<Rarity> rarity = Rarity.byName("legendary");
        assertThat(rarity).isPresent();
        assertThat(rarity.get().display()).isEqualTo("LEGENDARY");
    }

    @Test
    void rarityByNameIsCaseInsensitive() {
        assertThat(Rarity.byName("LEGENDARY")).isPresent();
        assertThat(Rarity.byName("Legendary")).isPresent();
    }

    @Test
    void rarityByNameUnknownReturnsEmpty() {
        assertThat(Rarity.byName("does_not_exist")).isEmpty();
    }

    @Test
    void rarityGetNamesIsNonEmpty() {
        assertThat(Rarity.getRarityNames()).contains("legendary", "common", "rare");
    }

    // -----------------------------------------------------------------------
    // Stat
    // -----------------------------------------------------------------------

    @Test
    void statsRegistryLoadsEntries() {
        assertThat(Stat.getStats()).isNotEmpty();
    }

    @Test
    void statByNameReturnsValue() {
        Optional<Stat> stat = Stat.byName("strength");
        assertThat(stat).isPresent();
        assertThat(stat.get().stat()).isEqualTo("Strength");
    }

    @Test
    void statByNameIsCaseInsensitive() {
        assertThat(Stat.byName("STRENGTH")).isPresent();
        assertThat(Stat.byName("Strength")).isPresent();
    }

    @Test
    void statByNameUnknownReturnsEmpty() {
        assertThat(Stat.byName("not_a_real_stat")).isEmpty();
    }

    @Test
    void statWithNullableFieldsLoadsCorrectly() {
        // health has a powerScalingMultiplier; damage does not
        Optional<Stat> health = Stat.byName("health");
        assertThat(health).isPresent();
        assertThat(health.get().powerScalingMultiplier()).isNotNull();

        Optional<Stat> damage = Stat.byName("damage");
        assertThat(damage).isPresent();
        assertThat(damage.get().powerScalingMultiplier()).isNull();
    }

    // -----------------------------------------------------------------------
    // Flavor
    // -----------------------------------------------------------------------

    @Test
    void flavorsRegistryLoadsEntries() {
        assertThat(Flavor.getFlavors()).isNotEmpty();
    }

    @Test
    void flavorByNameReturnsValue() {
        Optional<Flavor> flavor = Flavor.byName("soulbound");
        assertThat(flavor).isPresent();
        assertThat(flavor.get().stat()).isEqualTo("Soulbound");
    }

    @Test
    void flavorByNameIsCaseInsensitive() {
        assertThat(Flavor.byName("SOULBOUND")).isPresent();
    }

    @Test
    void flavorByNameUnknownReturnsEmpty() {
        assertThat(Flavor.byName("not_a_real_flavor")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Gemstone
    // -----------------------------------------------------------------------

    @Test
    void gemstonesRegistryLoadsEntries() {
        assertThat(Gemstone.getGemstones()).isNotEmpty();
    }

    @Test
    void gemstoneByNameReturnsValue() {
        Optional<Gemstone> gem = Gemstone.byName("gem_ruby");
        assertThat(gem).isPresent();
        assertThat(gem.get().formattedTiers()).isNotEmpty();
    }

    @Test
    void gemstoneByNameIsCaseInsensitive() {
        assertThat(Gemstone.byName("GEM_RUBY")).isPresent();
    }

    @Test
    void gemstoneByNameUnknownReturnsEmpty() {
        assertThat(Gemstone.byName("gem_nonexistent")).isEmpty();
    }

    @Test
    void gemstoneWithNullColorLoadsCorrectly() {
        // gem_combat has no color field in JSON
        Optional<Gemstone> gem = Gemstone.byName("gem_combat");
        assertThat(gem).isPresent();
        assertThat(gem.get().color()).isNull();
    }

    // -----------------------------------------------------------------------
    // Icon
    // -----------------------------------------------------------------------

    @Test
    void iconsRegistryLoadsEntries() {
        assertThat(Icon.getIcons()).isNotEmpty();
    }

    @Test
    void iconByNameReturnsValue() {
        Optional<Icon> icon = Icon.byName("star");
        assertThat(icon).isPresent();
        assertThat(icon.get().icon()).isEqualTo("✪");
    }

    @Test
    void iconByNameIsCaseInsensitive() {
        assertThat(Icon.byName("STAR")).isPresent();
    }

    @Test
    void iconByNameUnknownReturnsEmpty() {
        assertThat(Icon.byName("not_an_icon")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // ParseType
    // -----------------------------------------------------------------------

    @Test
    void parseTypesRegistryLoadsEntries() {
        assertThat(SkyBlockRegistries.parseTypes().values()).isNotEmpty();
    }

    @Test
    void parseTypeByNameReturnsValue() {
        Optional<ParseType> pt = ParseType.byName("NORMAL");
        assertThat(pt).isPresent();
        assertThat(pt.get().formatWithDetails()).isNotBlank();
        assertThat(pt.get().formatWithoutDetails()).isNotBlank();
    }

    @Test
    void parseTypeByNameIsCaseInsensitive() {
        assertThat(ParseType.byName("normal")).isPresent();
    }

    @Test
    void parseTypeByNameUnknownReturnsEmpty() {
        assertThat(ParseType.byName("NOT_A_REAL_PARSE_TYPE")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // PowerStrength
    // -----------------------------------------------------------------------

    @Test
    void powerStrengthsRegistryLoadsEntries() {
        assertThat(PowerStrength.getAllPowerStrengths()).isNotEmpty();
    }

    @Test
    void powerStrengthByNameReturnsValue() {
        Optional<PowerStrength> ps = PowerStrength.byName("Starter");
        assertThat(ps).isPresent();
        assertThat(ps.get().stone()).isFalse();
    }

    @Test
    void powerStrengthByNameIsCaseInsensitive() {
        assertThat(PowerStrength.byName("starter")).isPresent();
        assertThat(PowerStrength.byName("STARTER")).isPresent();
    }

    @Test
    void powerStrengthByNameUnknownReturnsEmpty() {
        assertThat(PowerStrength.byName("Nonexistent")).isEmpty();
    }

    @Test
    void powerStrengthGetNamesIsNonEmpty() {
        assertThat(PowerStrength.getPowerStrengthNames()).contains("starter", "master");
    }

    // -----------------------------------------------------------------------
    // ArmorType
    // -----------------------------------------------------------------------

    @Test
    void armorTypesRegistryLoadsEntries() {
        assertThat(ArmorType.getAll()).isNotEmpty();
    }

    @Test
    void armorTypeColorableLeatherDetected() {
        assertThat(ArmorType.isColorableArmor("leather_helmet")).isTrue();
    }

    @Test
    void armorTypeNonColorableDiamondDetected() {
        assertThat(ArmorType.isColorableArmor("diamond_chestplate")).isFalse();
    }

    @Test
    void armorTypeNullOverlayReturnsFalse() {
        assertThat(ArmorType.isColorableArmor(null)).isFalse();
    }

    @Test
    void armorTypeFromOverlayNameReturnsLeather() {
        assertThat(ArmorType.fromOverlayName("leather_chestplate")).isPresent();
        assertThat(ArmorType.fromOverlayName("leather_chestplate").get().materialName()).isEqualTo("leather");
    }
}

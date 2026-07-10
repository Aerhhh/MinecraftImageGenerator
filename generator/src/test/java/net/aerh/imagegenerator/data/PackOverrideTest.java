package net.aerh.imagegenerator.data;

import net.aerh.imagegenerator.pack.PackId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pack-conditional icon resolution over the bundled data files.
 */
class PackOverrideTest {

    private static final PackId HYPIXEL = PackId.parse("hypixel:skyblock");
    private static final PackId OTHER = PackId.parse("other:pack");

    private static final String ZONE_BASE = "\u23E3";      // the classic zone symbol
    private static final String ZONE_PACK = "\uE067";      // the pack's zone/area glyph
    private static final String STRENGTH_BASE = "\u2741";  // the classic strength flower
    private static final String STRENGTH_PACK = "\uE00D";  // the pack's strength glyph
    private static final String STARRED = "\u269A";
    private static final String MOB_UNDEAD = "\uE084";
    private static final String MOB_ELUSIVE = "\uE077";
    private static final String STAFF_BADGE = "\u12DE";
    private static final String RUBY_BASE = "\u2764";
    private static final String RUBY_PACK = "\uE010";
    private static final String UNDEAD_BASE = "\u0F15";

    @Test
    void iconOverrideAppliesOnlyForMatchingPack() {
        Icon zone = Icon.byName("zone");
        assertNotNull(zone);
        assertEquals(ZONE_BASE, zone.getIcon());
        assertEquals(ZONE_PACK, zone.getIcon(HYPIXEL));
        assertEquals(ZONE_BASE, zone.getIcon(OTHER));
        assertEquals(ZONE_BASE, zone.getIcon(null));
    }

    @Test
    void newPuaEntriesResolveRegardlessOfPack() {
        // New entries carry the pack glyph as their base char; the baked fonts render it globally.
        Icon undead = Icon.byName("mob_undead");
        assertNotNull(undead);
        assertEquals(MOB_UNDEAD, undead.getIcon());
        assertEquals(MOB_UNDEAD, undead.getIcon(HYPIXEL));
        assertEquals(MOB_UNDEAD, undead.getIcon(OTHER));
    }

    @Test
    void statIconOverrideAppliesOnlyForMatchingPack() {
        Stat strength = Stat.byName("strength");
        assertNotNull(strength);
        assertEquals(STRENGTH_BASE, strength.getIcon());
        assertEquals(STRENGTH_PACK, strength.getIcon(HYPIXEL));
        assertEquals(STRENGTH_BASE, strength.getIcon(OTHER));
        assertEquals(STRENGTH_BASE, strength.getIcon(null));
    }

    @Test
    void statDisplayIsDerivedOnlyWhenOverrideActive() {
        Stat strength = Stat.byName("strength");
        assertEquals(STRENGTH_BASE + " Strength", strength.getDisplay());
        assertEquals(STRENGTH_PACK + " Strength", strength.getDisplay(HYPIXEL));
        assertEquals(STRENGTH_BASE + " Strength", strength.getDisplay(OTHER));
    }

    @Test
    void statsWithoutOverridesKeepBaseIconUnderAnyPack() {
        // The pack has no wisdom glyph, so wisdom stats stay on their base character.
        Stat combatWisdom = Stat.byName("combat_wisdom");
        assertNotNull(combatWisdom);
        assertEquals(combatWisdom.getIcon(), combatWisdom.getIcon(HYPIXEL));
        assertEquals(combatWisdom.getDisplay(), combatWisdom.getDisplay(HYPIXEL));
    }

    @Test
    void curatedOverridesArePresent() {
        assertEquals("\uE051", Stat.byName("farming_fortune").getIcon(HYPIXEL));
        assertEquals("\uE053", Stat.byName("mining_fortune").getIcon(HYPIXEL));
        assertEquals("\uE077", Stat.byName("tracking").getIcon(HYPIXEL));
        assertEquals("\uE017", Stat.byName("overflow_mana").getIcon(HYPIXEL));
        assertEquals("\uE004", Stat.byName("mana_regen").getIcon(HYPIXEL));
        assertEquals("\uE010", Stat.byName("health").getIcon(HYPIXEL));
        assertEquals("\uE000", Stat.byName("absorption").getIcon(HYPIXEL));
        assertEquals("\uE05B", Stat.byName("syphon_luck").getIcon(HYPIXEL));
    }

    @Test
    void gemstoneIconOverrideAppliesOnlyForMatchingPack() {
        Gemstone ruby = Gemstone.byName("gem_ruby");
        assertNotNull(ruby);
        assertEquals(RUBY_BASE, ruby.getIcon());
        assertEquals(RUBY_PACK, ruby.getIcon(HYPIXEL));
        assertEquals(RUBY_BASE, ruby.getIcon(OTHER));
        assertEquals(RUBY_BASE, ruby.getIcon(null));
    }

    @Test
    void gemstoneFormattedIconKeepsItsColorCodes() {
        Gemstone ruby = Gemstone.byName("gem_ruby");
        assertEquals("&c" + RUBY_BASE, ruby.getFormattedIcon());
        assertEquals("&c" + RUBY_PACK, ruby.getFormattedIcon(HYPIXEL));
        assertEquals("&c" + RUBY_BASE, ruby.getFormattedIcon(OTHER));
    }

    @Test
    void gemstoneWithoutGlyphKeepsBaseIconUnderAnyPack() {
        // The pack has no caduceus glyph, so the defensive slot stays on its base character.
        Gemstone defensive = Gemstone.byName("gem_defensive");
        assertNotNull(defensive);
        assertEquals(defensive.getIcon(), defensive.getIcon(HYPIXEL));
        assertEquals(defensive.getFormattedIcon(), defensive.getFormattedIcon(HYPIXEL));
    }

    @Test
    void flavorIconOverrideAppliesOnlyForMatchingPack() {
        Flavor undead = Flavor.byName("undead");
        assertNotNull(undead);
        assertEquals(UNDEAD_BASE, undead.getIcon());
        assertEquals(MOB_UNDEAD, undead.getIcon(HYPIXEL));
        assertEquals(UNDEAD_BASE, undead.getIcon(OTHER));
        assertEquals(MOB_UNDEAD + " Undead", undead.getDisplay(HYPIXEL));
    }

    @Test
    void flavorEmbeddedIconCharactersAreSwappedEverywhere() {
        Flavor undeadItem = Flavor.byName("undead_item");
        assertNotNull(undeadItem);
        assertEquals("This armor piece is undead " + UNDEAD_BASE + "!", undeadItem.getStat());
        assertEquals("This armor piece is undead " + MOB_UNDEAD + "!", undeadItem.getStat(HYPIXEL));
        assertEquals(MOB_UNDEAD + " This armor piece is undead " + MOB_UNDEAD + "!", undeadItem.getDisplay(HYPIXEL));
        assertEquals(UNDEAD_BASE + " This armor piece is undead " + UNDEAD_BASE + "!", undeadItem.getDisplay(OTHER));
    }

    @Test
    void bundledDataPassesGlyphValidationAndResolvesOwners() {
        PackGlyphIndex index = PackGlyphIndex.fromRegistries();

        assertEquals("starred", index.getBareCharacterOwners().get(STARRED).getName(),
            "U+269A is shared by starred and fragged; the first entry in file order wins");
        assertEquals("mob_elusive", index.getBareCharacterOwners().get(MOB_ELUSIVE).getName());
        assertEquals("zone", index.getBareCharacterOwners().get(ZONE_BASE).getName());
        assertEquals("zone", index.getBareCharacterOwners().get(ZONE_PACK).getName(),
            "an icon's override character joins the bare-character domain");
        assertEquals("hypixel_staff", index.getBareCharacterOwners().get(STAFF_BADGE).getName());
    }
}

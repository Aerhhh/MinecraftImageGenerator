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
        Stat syphonLuck = Stat.byName("syphon_luck");
        assertNotNull(syphonLuck);
        assertEquals(syphonLuck.getIcon(), syphonLuck.getIcon(HYPIXEL));
        assertEquals(syphonLuck.getDisplay(), syphonLuck.getDisplay(HYPIXEL));
    }

    @Test
    void curatedOverridesArePresent() {
        assertEquals("\uE051", Stat.byName("farming_fortune").getIcon(HYPIXEL));
        assertEquals("\uE053", Stat.byName("mining_fortune").getIcon(HYPIXEL));
        assertEquals("\uE077", Stat.byName("tracking").getIcon(HYPIXEL));
        assertEquals("\uE017", Stat.byName("overflow_mana").getIcon(HYPIXEL));
        assertEquals("\uE004", Stat.byName("mana_regen").getIcon(HYPIXEL));
        assertEquals("\uE010", Stat.byName("health").getIcon(HYPIXEL));
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

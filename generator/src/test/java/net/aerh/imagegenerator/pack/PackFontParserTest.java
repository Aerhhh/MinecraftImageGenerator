package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import net.aerh.imagegenerator.pack.font.FontFilter;
import net.aerh.imagegenerator.pack.font.FontProviderDefinition;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackFontParserTest {

    private static List<FontProviderDefinition> parse(String json) {
        return PackFontParser.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesAllProviderTypesInOrder() {
        List<FontProviderDefinition> providers = parse("""
            {"providers":[
              {"type":"bitmap","file":"minecraft:font/ascii.png","height":8,"ascent":7,"chars":["AB","CD"]},
              {"type":"space","advances":{" ":4.0,"a":-3.5}},
              {"type":"reference","id":"minecraft:alt"},
              {"type":"ttf","file":"minecraft:font/rune.ttf","size":9.5,"oversample":2.0,"shift":[0.5,-1.0],"skip":"xy"},
              {"type":"unihex","hex_file":"minecraft:font/unifont.zip"},
              {"type":"legacy_unicode","sizes":"minecraft:font/glyph_sizes.bin","template":"minecraft:font/unicode_page_%s.png"}
            ]}""");
        assertEquals(6, providers.size());
        FontProviderDefinition.Bitmap bitmap = assertInstanceOf(FontProviderDefinition.Bitmap.class, providers.get(0));
        assertEquals("minecraft:font/ascii.png", bitmap.file());
        assertEquals(8, bitmap.height());
        assertEquals(7, bitmap.ascent());
        assertEquals(List.of("AB", "CD"), bitmap.charRows());
        FontProviderDefinition.Space space = assertInstanceOf(FontProviderDefinition.Space.class, providers.get(1));
        assertEquals(Map.of((int) ' ', 4.0f, (int) 'a', -3.5f), space.advances());
        FontProviderDefinition.Reference reference =
            assertInstanceOf(FontProviderDefinition.Reference.class, providers.get(2));
        assertEquals("minecraft:alt", reference.id());
        FontProviderDefinition.Ttf ttf = assertInstanceOf(FontProviderDefinition.Ttf.class, providers.get(3));
        assertEquals("minecraft:font/rune.ttf", ttf.file());
        assertEquals(9.5f, ttf.size());
        assertEquals(2.0f, ttf.oversample());
        assertEquals(0.5f, ttf.shiftX());
        assertEquals(-1.0f, ttf.shiftY());
        assertEquals(Set.of((int) 'x', (int) 'y'), ttf.skip());
        assertEquals("unihex",
            assertInstanceOf(FontProviderDefinition.Unsupported.class, providers.get(4)).type());
        assertEquals("legacy_unicode",
            assertInstanceOf(FontProviderDefinition.Unsupported.class, providers.get(5)).type());
    }

    @Test
    void normalizesMinecraftPrefixedType() {
        List<FontProviderDefinition> providers = parse("""
            {"providers":[{"type":"minecraft:space","advances":{"z":1.5}}]}""");
        assertInstanceOf(FontProviderDefinition.Space.class, providers.get(0));
    }

    @Test
    void heightDefaultsToEight() {
        FontProviderDefinition.Bitmap bitmap = assertInstanceOf(FontProviderDefinition.Bitmap.class, parse("""
            {"providers":[{"type":"bitmap","file":"a:b.png","ascent":7,"chars":["A"]}]}""").get(0));
        assertEquals(8, bitmap.height());
    }

    @Test
    void negativeAscentIsLegal() {
        FontProviderDefinition.Bitmap bitmap = assertInstanceOf(FontProviderDefinition.Bitmap.class, parse("""
            {"providers":[{"type":"bitmap","file":"a:b.png","height":4,"ascent":-2,"chars":["A"]}]}""").get(0));
        assertEquals(-2, bitmap.ascent());
    }

    @Test
    void ascentGreaterThanHeightFails() {
        PackLoadException exception = assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"bitmap","file":"a:b.png","height":8,"ascent":9,"chars":["A"]}]}"""));
        assertTrue(exception.getMessage().contains("ascent"));
    }

    @Test
    void missingAscentFails() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"bitmap","file":"a:b.png","chars":["A"]}]}"""));
    }

    @Test
    void unequalCharsRowLengthsFail() {
        PackLoadException exception = assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"bitmap","file":"a:b.png","ascent":7,"chars":["AB","C"]}]}"""));
        assertTrue(exception.getMessage().contains("equal length"));
    }

    @Test
    void astralCodepointCountsAsOneGridEntry() {
        // U+10348 is one codepoint but two UTF-16 chars; the grid validates in codepoints.
        List<FontProviderDefinition> providers = parse("""
            {"providers":[{"type":"bitmap","file":"a:b.png","ascent":7,"chars":["A\\uD800\\uDF48","BC"]}]}""");
        assertEquals(2, providers.get(0) instanceof FontProviderDefinition.Bitmap bitmap
            ? bitmap.charRows().size() : -1);
    }

    @Test
    void emptyCharsListFails() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"bitmap","file":"a:b.png","ascent":7,"chars":[]}]}"""));
    }

    @Test
    void emptyCharsRowFails() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"bitmap","file":"a:b.png","ascent":7,"chars":[""]}]}"""));
    }

    @Test
    void unknownProviderTypeFailsLoudly() {
        PackLoadException exception = assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"hologram"}]}"""));
        assertTrue(exception.getMessage().contains("hologram"));
    }

    @Test
    void missingProvidersArrayFails() {
        assertThrows(PackLoadException.class, () -> parse("{}"));
    }

    @Test
    void spaceAdvancesAcceptNegativeFractionalAndZero() {
        FontProviderDefinition.Space space = assertInstanceOf(FontProviderDefinition.Space.class, parse("""
            {"providers":[{"type":"space","advances":{" ":4.0,"a":-3.5,"b":0.0,"c":2.25}}]}""").get(0));
        assertEquals(-3.5f, space.advances().get((int) 'a'));
        assertEquals(0.0f, space.advances().get((int) 'b'));
        assertEquals(2.25f, space.advances().get((int) 'c'));
    }

    @Test
    void spaceAdvanceKeyWithMultipleCodepointsFails() {
        PackLoadException exception = assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"space","advances":{"ab":4.0}}]}"""));
        assertTrue(exception.getMessage().contains("single codepoint"));
    }

    @Test
    void spaceAdvanceNonNumericValueFails() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"space","advances":{"a":"wide"}}]}"""));
    }

    @Test
    void ttfDefaultsApplied() {
        FontProviderDefinition.Ttf ttf = assertInstanceOf(FontProviderDefinition.Ttf.class, parse("""
            {"providers":[{"type":"ttf","file":"a:b.ttf"}]}""").get(0));
        assertEquals(11.0f, ttf.size());
        assertEquals(1.0f, ttf.oversample());
        assertEquals(0.0f, ttf.shiftX());
        assertEquals(0.0f, ttf.shiftY());
        assertTrue(ttf.skip().isEmpty());
    }

    @Test
    void ttfSkipAcceptsArrayOfStrings() {
        FontProviderDefinition.Ttf ttf = assertInstanceOf(FontProviderDefinition.Ttf.class, parse("""
            {"providers":[{"type":"ttf","file":"a:b.ttf","skip":["ab","c"]}]}""").get(0));
        assertEquals(Set.of((int) 'a', (int) 'b', (int) 'c'), ttf.skip());
    }

    @Test
    void ttfShiftWrongArityFails() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"ttf","file":"a:b.ttf","shift":[1.0]}]}"""));
    }

    @Test
    void filterParsesUniformAndJp() {
        List<FontProviderDefinition> providers = parse("""
            {"providers":[
              {"type":"space","advances":{"a":1.0},"filter":{"uniform":true,"jp":false}},
              {"type":"space","advances":{"b":1.0}}
            ]}""");
        assertEquals(new FontFilter(true, false), providers.get(0).filter());
        assertEquals(FontFilter.none(), providers.get(1).filter());
    }

    @Test
    void filterOnAnyProviderTypeParses() {
        List<FontProviderDefinition> providers = parse("""
            {"providers":[{"type":"reference","id":"minecraft:alt","filter":{"jp":true}}]}""");
        assertEquals(new FontFilter(null, true), providers.get(0).filter());
    }

    @Test
    void unknownFilterKeyFails() {
        PackLoadException exception = assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"space","advances":{"a":1.0},"filter":{"bold":true}}]}"""));
        assertTrue(exception.getMessage().contains("bold"));
    }

    @Test
    void nonBooleanFilterValueFails() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"space","advances":{"a":1.0},"filter":{"uniform":"yes"}}]}"""));
    }

    @Test
    void referenceWithoutIdFails() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"providers":[{"type":"reference"}]}"""));
    }

    @Test
    void malformedJsonFails() {
        assertThrows(PackLoadException.class, () -> parse("{nope"));
    }
}

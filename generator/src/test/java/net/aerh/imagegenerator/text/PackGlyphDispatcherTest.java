package net.aerh.imagegenerator.text;

import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.pack.font.FontFilter;
import net.aerh.imagegenerator.pack.font.FontProviderDefinition;
import net.aerh.imagegenerator.pack.font.PackFont;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.text.segment.ColorSegment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for the shared per-codepoint dispatcher: effective font id precedence
 * (segment pack font id over the built-in font's resource location), fall-through behavior
 * (no pack, unknown font, unmapped codepoint, malformed id), per-font-id memoization, and
 * deterministic equal-advance obfuscation substitution.
 */
class PackGlyphDispatcherTest {

    /**
     * 6x2 sheet, chars ["ABC"], 2x2 cells, height 2, ascent 2, scale 1. 'A' and 'B' each ink one
     * column (advance 2); 'C' inks both columns (advance 3). 'A' and 'B' use different colors so
     * substitution is observable in drawn pixels.
     */
    private static PackFont bitmapFont(String id) {
        BufferedImage sheet = new BufferedImage(6, 2, BufferedImage.TYPE_INT_ARGB);
        sheet.setRGB(0, 0, 0xFFFF0000); // 'A' red
        sheet.setRGB(2, 0, 0xFF00FF00); // 'B' green
        sheet.setRGB(4, 0, 0xFF0000FF); // 'C' blue, two columns
        sheet.setRGB(5, 0, 0xFF0000FF);
        return PackFont.create(id,
            List.of(new FontProviderDefinition.Bitmap(id + "/sheet.png", 2, 2, List.of("ABC"), FontFilter.none())),
            ref -> sheet);
    }

    private static PackFont spaceFont(String id) {
        return PackFont.create(id,
            List.of(new FontProviderDefinition.Space(Map.of((int) ' ', 4.5f), FontFilter.none())),
            ref -> {
                throw new AssertionError("space fonts load no textures");
            });
    }

    private static ColorSegment segment(String text) {
        return ColorSegment.builder().withText(text).build();
    }

    @Test
    void disabledDispatcherNeverDispatches() {
        assertTrue(PackGlyphDispatcher.disabled().dispatch(segment("A"), 'A').isEmpty());
        assertSame(PackGlyphDispatcher.disabled(), PackGlyphDispatcher.of(null),
            "null source is the disabled dispatcher");
    }

    @Test
    void forPackReturnsNullWithoutAnActivePack() {
        assertNull(PackGlyphDispatcher.FontSource.forPack(null, new PackRepository()),
            "no pack selection means no dispatch");
        assertNull(PackGlyphDispatcher.FontSource.forPack(PackId.VANILLA, new PackRepository()),
            "the built-in vanilla pack never dispatches pack glyphs");
    }

    @Test
    void forPackResolvesFontsAgainstTheSelectedPack(@TempDir Path packDir) {
        FixturePacks.writeFontPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = repository.register("test:fontsource",
            PackSource.directory(packDir, PackLimits.fromSystemProperties()));

        PackGlyphDispatcher.FontSource source = PackGlyphDispatcher.FontSource.forPack(packId, repository);
        assertNotNull(source, "an active pack yields a source");
        assertTrue(source.resolveFont("testpack:pixel").isPresent(), "pack fonts resolve");
        assertTrue(source.resolveFont("testpack:absent").isEmpty(), "unknown fonts stay empty");
    }

    @Test
    void forPackBindsTheInjectedRepositoryNotTheGlobalOne() {
        PackRepository empty = new PackRepository();
        PackGlyphDispatcher.FontSource source =
            PackGlyphDispatcher.FontSource.forPack(PackId.parse("test:unregistered"), empty);
        assertNotNull(source);
        assertThrows(PackResolveException.class, () -> source.resolveFont("minecraft:default"),
            "the source must consult the injected repository, where this pack is not registered");
    }

    @Test
    void segmentPackFontIdWinsOverBuiltInFont() {
        Set<String> requested = new HashSet<>();
        PackFont special = bitmapFont("test:special");
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> {
            requested.add(fontId);
            return fontId.equals("test:special") ? Optional.of(special) : Optional.empty();
        });
        ColorSegment segment = ColorSegment.builder().withText("A").withPackFontId("test:special").build();
        assertTrue(dispatcher.dispatch(segment, 'A').isPresent());
        assertEquals(Set.of("test:special"), requested, "only the pack font id is resolved");
    }

    @Test
    void unsetFontIdResolvesMinecraftDefault() {
        Set<String> requested = new HashSet<>();
        PackFont defaultFont = bitmapFont("minecraft:default");
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> {
            requested.add(fontId);
            return fontId.equals("minecraft:default") ? Optional.of(defaultFont) : Optional.empty();
        });
        assertTrue(dispatcher.dispatch(segment("A"), 'A').isPresent());
        assertEquals(Set.of("minecraft:default"), requested);
    }

    @Test
    void galacticSegmentResolvesMinecraftAlt() {
        Set<String> requested = new HashSet<>();
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> {
            requested.add(fontId);
            return Optional.empty();
        });
        ColorSegment segment = ColorSegment.builder().withText("A").withFont(MinecraftFont.GALACTIC).build();
        assertTrue(dispatcher.dispatch(segment, 'A').isEmpty());
        assertEquals(Set.of("minecraft:alt"), requested,
            "legacy &g segments resolve against the alt font's resource location");
    }

    @Test
    void unknownFontFallsThroughToBuiltInPath() {
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> Optional.empty());
        assertTrue(dispatcher.dispatch(segment("A"), 'A').isEmpty());
    }

    @Test
    void unmappedCodepointFallsThroughToBuiltInPath() {
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> Optional.of(bitmapFont(fontId)));
        assertTrue(dispatcher.dispatch(segment("Z"), 'Z').isEmpty(),
            "the font maps only A-C; Z falls back to the OTF path");
    }

    @Test
    void malformedFontIdIsTreatedAsAbsentAndMemoized() {
        AtomicInteger calls = new AtomicInteger();
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("bad id: " + fontId);
        });
        ColorSegment segment = ColorSegment.builder().withText("AA").withPackFontId("a:b:c").build();
        assertTrue(dispatcher.dispatch(segment, 'A').isEmpty());
        assertTrue(dispatcher.dispatch(segment, 'A').isEmpty());
        assertEquals(1, calls.get(), "malformed ids resolve once and memoize as absent");
    }

    @Test
    void brokenPackFontFailsLoudly() {
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> {
            throw new PackResolveException("font `%s` is broken", fontId);
        });
        assertThrows(PackResolveException.class, () -> dispatcher.dispatch(segment("A"), 'A'));
    }

    @Test
    void fontResolutionIsMemoizedPerFontId() {
        AtomicInteger calls = new AtomicInteger();
        PackFont font = bitmapFont("minecraft:default");
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> {
            calls.incrementAndGet();
            return Optional.of(font);
        });
        ColorSegment segment = segment("ABC");
        for (int codePoint : new int[]{'A', 'B', 'C', 'A'}) {
            assertTrue(dispatcher.dispatch(segment, codePoint).isPresent());
        }
        assertEquals(1, calls.get());
    }

    @Test
    void boldAddsExactlyOneToTheAdvance() {
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> Optional.of(bitmapFont(fontId)));
        float plain = dispatcher.dispatch(segment("A"), 'A').orElseThrow().advanceGuiPx();
        ColorSegment bold = ColorSegment.builder().withText("A").isBold().build();
        assertEquals(plain + 1.0f, dispatcher.dispatch(bold, 'A').orElseThrow().advanceGuiPx());
    }

    @Test
    void spaceGlyphsDispatchAsPureAdvances() {
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> Optional.of(spaceFont(fontId)));
        PackGlyphDispatcher.Dispatched dispatched = dispatcher.dispatch(segment(" "), ' ').orElseThrow();
        assertTrue(dispatched.isSpace());
        assertEquals(4.5f, dispatched.advanceGuiPx());
        assertSame(dispatched, dispatched.obfuscated(42L), "space glyphs never substitute");
    }

    @Test
    void obfuscationSubstitutesDeterministicallyAmongEqualAdvanceGlyphs() {
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> Optional.of(bitmapFont(fontId)));
        PackGlyphDispatcher.Dispatched original = dispatcher.dispatch(segment("A"), 'A').orElseThrow();

        Set<String> distinctRenders = new HashSet<>();
        for (long seed = 0; seed < 16; seed++) {
            PackGlyphDispatcher.Dispatched first = original.obfuscated(seed);
            PackGlyphDispatcher.Dispatched second = original.obfuscated(seed);
            assertEquals(original.advanceGuiPx(), first.advanceGuiPx(), "substitutes keep the advance");
            int[] firstPixels = renderedPixels(first);
            assertArrayEquals(firstPixels, renderedPixels(second), "same seed, same substitute");
            distinctRenders.add(Arrays.toString(firstPixels));
        }
        // 'A' (red) and 'B' (green) share advance 2; 'C' (advance 3) must never appear. The
        // substitute index comes from the splitmix64-mixed seed (Dispatched.mix), and seeds
        // 0..15 cover both candidates under the current mixer constants; retuning the mixer may
        // require a different seed range to keep hitting both.
        assertEquals(2, distinctRenders.size(), "both equal-advance glyphs appear, nothing else");
    }

    @Test
    void drawWithoutShadowColorSkipsTheShadowPass() {
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> Optional.of(bitmapFont(fontId)));
        PackGlyphDispatcher.Dispatched dispatched = dispatcher.dispatch(segment("A"), 'A').orElseThrow();
        BufferedImage canvas = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            dispatched.draw(graphics, 0.0, 0.0, 1, Color.WHITE, null);
        } finally {
            graphics.dispose();
        }
        // Cell top edge = lineTop + 7 - ascent = 5; the red pixel is cell (0,0).
        assertEquals(0xFFFF0000, canvas.getRGB(0, 5), "main glyph pixel drawn");
        assertEquals(0, canvas.getRGB(1, 6), "no shadow at +1,+1");
    }

    @Test
    void drawTintsTheShadowPassWithTheCallerSuppliedShadowColor() {
        PackGlyphDispatcher dispatcher = PackGlyphDispatcher.of(fontId -> Optional.of(bitmapFont(fontId)));
        PackGlyphDispatcher.Dispatched dispatched = dispatcher.dispatch(segment("A"), 'A').orElseThrow();
        BufferedImage canvas = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            // The caller owns the shadow color (its pipeline's, e.g. quartered white).
            dispatched.draw(graphics, 0.0, 0.0, 1, Color.WHITE, new Color(0x3F3F3F));
        } finally {
            graphics.dispose();
        }
        assertEquals(0xFFFF0000, canvas.getRGB(0, 5), "main glyph pixel drawn over the shadow");
        assertEquals(0xFF3F0000, canvas.getRGB(1, 6), "shadow is the shadow-color texture-tint product");
    }

    @Test
    void mappedCodePointsEnumerateRenderableProvidersSorted() {
        PackFont font = PackFont.create("test:mixed",
            List.of(
                new FontProviderDefinition.Bitmap("test:mixed/sheet.png", 2, 2, List.of("ABC"), FontFilter.none()),
                new FontProviderDefinition.Space(Map.of((int) ' ', 1.0f), FontFilter.none())),
            ref -> {
                BufferedImage sheet = new BufferedImage(6, 2, BufferedImage.TYPE_INT_ARGB);
                sheet.setRGB(0, 0, 0xFFFFFFFF);
                return sheet;
            });
        assertEquals(List.of((int) ' ', (int) 'A', (int) 'B', (int) 'C'), font.mappedCodePoints());
        assertFalse(font.mappedCodePoints().contains((int) 'Z'));
    }

    private static int[] renderedPixels(PackGlyphDispatcher.Dispatched dispatched) {
        BufferedImage canvas = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            dispatched.draw(graphics, 0.0, 0.0, 1, Color.WHITE, null);
        } finally {
            graphics.dispose();
        }
        return canvas.getRGB(0, 0, 16, 16, null, 0, 16);
    }
}

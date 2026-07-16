package net.aerh.imagegenerator.pack.font;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackResolveException;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A resolved pack font: an ordered provider list ready for per-codepoint glyph lookup. Build one
 * from a {@link FontResolver#resolveProviders resolved} definition list via {@link #create}.
 *
 * <p>Provider priority is vanilla-exact: the FIRST provider in the resolved list that supplies a
 * codepoint wins, including across reference boundaries (a reference expands inline at its
 * position).
 *
 * <p>TTF providers render as real vector glyphs (Tier 1): when a {@code ttf} provider's font file
 * resolves from the pack and loads, it becomes an {@link OtfFontProvider} that CLAIMS the
 * codepoints its font can display (minus its {@code skip} set), first-wins like any provider. A
 * missing or unloadable TTF degrades to the never-claim behavior below so a later provider still
 * serves the codepoint.
 *
 * <p>Deviation from vanilla, by design: unihex and legacy_unicode providers (and a TTF that failed
 * to load) are unsupported for rendering in this library, so they never claim a codepoint here -
 * {@link #glyph} returns empty for them and LATER providers may serve the codepoint instead
 * (vanilla would serve their glyph and stop). {@link #hasUnsupportedProviderFor} reports whether
 * such a provider might have served a codepoint so callers can log the fidelity gap once.
 *
 * <p>Second deviation, also by design: a bitmap provider whose sheet texture is ABSENT (per
 * {@link TextureLoader#exists}) is handled in two steps. First the loader's bundled vanilla
 * fallback set is consulted ({@link TextureLoader#vanillaFallbackSheet}): a pack that references a
 * vanilla client sheet it does not ship (e.g. {@code minecraft:font/ascii.png}, as MCC's
 * {@code minecraft:default} and {@code default_offset} fonts do) renders real bitmap glyphs from
 * the bundled copy, combined with the pack's OWN metrics (so a re-declared ascent still shifts the
 * text). Only when no bundled sheet matches is the provider skipped with a warning instead of
 * failing the whole font - real packs also reference non-vanilla sheets this library does not
 * bundle, and dropping just those providers keeps every glyph the pack actually ships renderable. A
 * font whose providers all skip still resolves - as an empty font that claims no codepoint. A pack
 * that DOES ship its own sheet always wins: its provider is present, so the fallback is never
 * consulted.
 */
@Slf4j
public final class PackFont {

    /** Loads a bitmap provider's sheet texture by its {@code file} reference. */
    @FunctionalInterface
    public interface TextureLoader {

        /**
         * @param textureFileRef the bitmap provider {@code file} value, extension included (e.g.
         *                       {@code minecraft:font/ascii.png})
         * @return the decoded sheet
         * @throws PackResolveException when the texture is missing or fails to decode
         */
        BufferedImage load(String textureFileRef);

        /**
         * Whether the sheet texture exists at all. {@link #create} skips bitmap providers whose
         * sheet is absent (see the class javadoc) and only calls {@link #load} for present ones,
         * so decode failures of PRESENT sheets stay loud. The default returns true: loaders that
         * cannot probe existence keep the vanilla fail-loud behavior for absent sheets too.
         *
         * @param textureFileRef the bitmap provider {@code file} value, extension included
         * @throws PackResolveException when the reference itself is malformed
         */
        default boolean exists(String textureFileRef) {
            return true;
        }

        /**
         * The bundled vanilla fallback sheet for an ABSENT pack texture, consulted by
         * {@link #create} before the skip-with-warn when {@link #exists} is false. This lets a pack
         * that references a vanilla client sheet it does not ship (e.g.
         * {@code minecraft:font/ascii.png}) still render real bitmap glyphs from the bundled copy.
         * The returned sheet is combined with the PACK's own provider metrics (height, ascent,
         * grid), so a re-declared ascent still applies. The default returns empty: loaders with no
         * bundled set keep the plain skip-with-warn for every absent sheet.
         *
         * @param textureFileRef the bitmap provider {@code file} value, extension included
         * @return the decoded fallback sheet, or empty when no bundled sheet matches
         * @throws PackResolveException when the reference itself is malformed
         */
        default Optional<BufferedImage> vanillaFallbackSheet(String textureFileRef) {
            return Optional.empty();
        }

        /**
         * The raw bytes of a {@code ttf} provider's font file, resolved beneath
         * {@code assets/<ns>/font/} (the vanilla TTF location, NOT {@code textures/}). Consulted by
         * {@link #create} to build an {@link OtfFontProvider}; empty (the default) means the file is
         * absent or unreadable, so the TTF provider degrades to a never-claim unsupported entry and
         * a later provider serves its codepoints. An oversized or unreadable file returns empty
         * rather than throwing, so a broken TTF never fails the whole font.
         *
         * @param fontFileRef the ttf provider {@code file} value, extension included (e.g.
         *                    {@code minecraft:pinch/hud.ttf})
         * @return the font bytes, or empty when the file is absent or unreadable
         * @throws PackResolveException when the reference itself is malformed
         */
        default Optional<byte[]> ttfFontData(String fontFileRef) {
            return Optional.empty();
        }
    }

    private sealed interface Provider permits BitmapEntry, SpaceEntry, OtfEntry, UnsupportedEntry {
    }

    private record BitmapEntry(BitmapFontProvider provider) implements Provider {
    }

    private record OtfEntry(OtfFontProvider provider) implements Provider {
    }

    private record SpaceEntry(Map<Integer, SpaceGlyph> glyphs) implements Provider {
    }

    /**
     * @param skippedCodePoints codepoints the provider explicitly excludes (the TTF {@code skip}
     *                          field); empty for unihex / legacy_unicode, which may cover anything
     */
    private record UnsupportedEntry(String type, Set<Integer> skippedCodePoints) implements Provider {
    }

    private final String id;
    private final List<Provider> providers;
    private volatile List<Integer> mappedCodePoints;

    private PackFont(String id, List<Provider> providers) {
        this.id = id;
        this.providers = providers;
    }

    /**
     * Builds the runtime font from a RESOLVED provider list (references already expanded by
     * {@link FontResolver}; filters already applied). Bitmap sheets are loaded eagerly through
     * {@code textures} and each glyph copies its cell out, so the sheets themselves are not
     * retained; providers whose sheet is absent are skipped with a warning (see the class
     * javadoc). Equivalent to the {@link BitmapProviderCache} overload with a fresh,
     * single-use cache.
     *
     * @throws PackResolveException when the list still contains a reference provider, a present
     *                              sheet fails to decode, or a sheet is too small for its grid
     */
    public static PackFont create(String fontId, List<FontProviderDefinition> definitions, TextureLoader textures) {
        return create(fontId, definitions, textures, new BitmapProviderCache());
    }

    /**
     * Builds the runtime font like {@link #create(String, List, TextureLoader)}, but shares built
     * bitmap providers through {@code sharedProviders}: fonts of one pack that reference the same
     * sheet (same file, height, ascent and codepoint grid) reuse one decoded provider instead of
     * re-decoding the sheet and duplicating every glyph cell, mirroring vanilla's shared glyph
     * providers.
     *
     * @throws PackResolveException when the list still contains a reference provider, a present
     *                              sheet fails to decode, or a sheet is too small for its grid
     */
    public static PackFont create(String fontId, List<FontProviderDefinition> definitions, TextureLoader textures,
                                  BitmapProviderCache sharedProviders) {
        List<Provider> providers = new ArrayList<>();
        for (FontProviderDefinition definition : definitions) {
            switch (definition) {
                case FontProviderDefinition.Bitmap bitmap -> {
                    if (!textures.exists(bitmap.file())) {
                        Optional<BufferedImage> fallback = textures.vanillaFallbackSheet(bitmap.file());
                        if (fallback.isPresent()) {
                            // The pack references a vanilla client sheet it does not ship; render it
                            // from the bundled vanilla copy with the PACK's own metrics (so a
                            // re-declared ascent still shifts). See the class javadoc.
                            BufferedImage sheet = fallback.get();
                            providers.add(new BitmapEntry(
                                sharedProviders.bitmapProvider(bitmap, () -> sheet, fontId)));
                            continue;
                        }
                        // Deviation from vanilla (see class javadoc): real packs reference
                        // vanilla client sheets that are not bundled here; skip the provider
                        // instead of failing the whole font.
                        log.warn("Font `{}`: skipping bitmap provider whose sheet `{}` is absent",
                            fontId, bitmap.file());
                        continue;
                    }
                    providers.add(new BitmapEntry(sharedProviders.bitmapProvider(bitmap, textures, fontId)));
                }
                case FontProviderDefinition.Space space -> {
                    Map<Integer, SpaceGlyph> glyphs = new HashMap<>();
                    space.advances().forEach((codePoint, advance) -> glyphs.put(codePoint, new SpaceGlyph(advance)));
                    providers.add(new SpaceEntry(glyphs));
                }
                case FontProviderDefinition.Ttf ttf -> {
                    // A ttf whose font file resolves and loads renders real vector glyphs and
                    // claims its codepoints; a missing or broken one degrades to the never-claim
                    // unsupported entry so a later provider still serves them (see the class
                    // javadoc).
                    Optional<OtfFontProvider> otf = OtfFontProvider.tryCreate(ttf, textures, fontId);
                    if (otf.isPresent()) {
                        providers.add(new OtfEntry(otf.get()));
                    } else {
                        providers.add(new UnsupportedEntry("ttf", ttf.skip()));
                    }
                }
                case FontProviderDefinition.Unsupported unsupported ->
                    providers.add(new UnsupportedEntry(unsupported.type(), Set.of()));
                case FontProviderDefinition.Reference reference -> throw new PackResolveException(
                    "Font `%s` contains an unexpanded reference to `%s`; resolve with FontResolver first",
                    fontId, reference.id());
            }
        }
        return new PackFont(fontId, List.copyOf(providers));
    }

    /** The normalized font id this font was resolved as (e.g. {@code minecraft:default}). */
    public String id() {
        return id;
    }

    /**
     * The glyph for a codepoint: the first provider in resolved order that supplies it wins.
     * Unsupported-render providers (ttf, unihex, legacy_unicode) never claim a codepoint.
     *
     * @return empty when no renderable provider supplies the codepoint - callers fall back to
     *     built-in fonts
     */
    public Optional<PackGlyph> glyph(int codePoint) {
        for (Provider provider : providers) {
            PackGlyph glyph = claimedGlyph(provider, codePoint);
            if (glyph != null) {
                return Optional.of(glyph);
            }
        }
        return Optional.empty();
    }

    /**
     * Convenience for width math: the winning glyph's advance in GUI pixels, or 0 when the font
     * has no glyph for the codepoint.
     */
    public float advanceOf(int codePoint, boolean bold) {
        return glyph(codePoint).map(glyph -> glyph.advance(bold)).orElse(0.0f);
    }

    /**
     * The unbold advance when the WINNING provider for the codepoint is a space provider (pure
     * advance, nothing drawn); empty when the codepoint is unmapped or served by a bitmap
     * provider. Space advances may be negative or fractional. Implemented on top of
     * {@link #glyph} so both share one provider-priority walk.
     */
    public OptionalDouble spaceAdvance(int codePoint) {
        PackGlyph winner = glyph(codePoint).orElse(null);
        return winner instanceof SpaceGlyph space
            ? OptionalDouble.of(space.advance())
            : OptionalDouble.empty();
    }

    /**
     * Every codepoint mapped by this font's renderable providers (bitmap and space), sorted
     * ascending. This is the candidate pool for obfuscation substitution; provider priority still
     * applies when looking a listed codepoint up through {@link #glyph}. Computed once on first
     * use and cached (the set is immutable for the font's lifetime).
     */
    public List<Integer> mappedCodePoints() {
        List<Integer> cached = mappedCodePoints;
        if (cached == null) {
            SortedSet<Integer> union = new TreeSet<>();
            for (Provider provider : providers) {
                switch (provider) {
                    case BitmapEntry bitmap -> union.addAll(bitmap.provider().codePoints());
                    case SpaceEntry space -> union.addAll(space.glyphs().keySet());
                    case OtfEntry ignored -> {
                        // TTF providers claim codepoints but are not enumerated here: a real font
                        // covers thousands of codepoints and this pool drives obfuscation
                        // substitution only, so TTF glyphs obfuscate to themselves (a Tier-1
                        // limitation) rather than pay a full-font scan.
                    }
                    case UnsupportedEntry ignored -> {
                        // Unsupported providers never claim a codepoint (see class javadoc).
                    }
                }
            }
            cached = List.copyOf(union);
            mappedCodePoints = cached;
        }
        return cached;
    }

    /**
     * Whether an unsupported-render provider (ttf, unihex, legacy_unicode) might have served the
     * codepoint in the vanilla client. Providers are scanned in resolved order, exactly like
     * vanilla's first-wins lookup: a renderable provider claiming the codepoint BEFORE any
     * unsupported provider covers it means vanilla would have served that renderable glyph too,
     * so there is no fidelity gap and this returns false. TTF providers are checked against
     * their {@code skip} set; unihex and legacy_unicode coverage is unknowable without their
     * data files, so they count for every codepoint. Callers use this to log the fidelity gap
     * once per font.
     */
    public boolean hasUnsupportedProviderFor(int codePoint) {
        for (Provider provider : providers) {
            if (provider instanceof UnsupportedEntry unsupported) {
                if (!unsupported.skippedCodePoints().contains(codePoint)) {
                    return true;
                }
            } else if (claimedGlyph(provider, codePoint) != null) {
                // An earlier renderable provider claims the codepoint: vanilla's first-wins
                // order would never reach the unsupported provider, so no gap exists.
                return false;
            }
        }
        return false;
    }

    /**
     * Estimated bytes retained by this font's glyph cells, for weighing font caches. Bitmap
     * providers report their exact live cell bytes (cut eagerly at build). TTF providers rasterize
     * lazily, so they report their bounded worst-case reservation
     * ({@link OtfFontProvider#maxRetainedCellBytes()}) rather than their zero-at-insertion live
     * bytes - the font cache weighs a font once, at insertion, so a TTF font must be accounted at
     * the memory it can hold or it evades the budget entirely. A provider referenced from multiple
     * positions of this font counts once per position, making this an upper bound when providers
     * are shared.
     */
    public long retainedCellBytes() {
        long total = 0;
        for (Provider provider : providers) {
            if (provider instanceof BitmapEntry bitmap) {
                total += bitmap.provider().retainedCellBytes();
            } else if (provider instanceof OtfEntry otf) {
                total += otf.provider().maxRetainedCellBytes();
            }
        }
        return total;
    }

    private static PackGlyph claimedGlyph(Provider provider, int codePoint) {
        return switch (provider) {
            case BitmapEntry bitmap -> bitmap.provider().glyph(codePoint);
            case SpaceEntry space -> space.glyphs().get(codePoint);
            case OtfEntry otf -> otf.provider().glyph(codePoint);
            case UnsupportedEntry ignored -> null;
        };
    }
}

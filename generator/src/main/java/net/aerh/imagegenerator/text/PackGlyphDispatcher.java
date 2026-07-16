package net.aerh.imagegenerator.text;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.font.PackFont;
import net.aerh.imagegenerator.pack.font.PackGlyph;
import net.aerh.imagegenerator.text.segment.ColorSegment;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single per-codepoint decision point between pack font glyphs and the built-in OTF/Unifont
 * text path. Both the draw pass and the measure pass of a text renderer MUST route every
 * codepoint through {@link #dispatch} so their font-resolution and advance logic can never
 * drift apart.
 *
 * <p>Effective font resolution per segment: the segment's
 * {@link ColorSegment#getPackFontId() pack font id} when set, otherwise the resource location of
 * its built-in {@link MinecraftFont} (so a pack overriding {@code minecraft:default} restyles
 * ordinary text, and one overriding {@code minecraft:alt} restyles legacy {@code &g} text). A
 * codepoint takes the pack glyph path only when the source resolves that font AND the font
 * supplies the codepoint; otherwise the caller falls back to its built-in rendering. A
 * {@link #disabled() disabled} dispatcher (no pack active) dispatches nothing, leaving no-pack
 * rendering untouched.
 *
 * <p>Resolution outcomes are memoized per font id for the dispatcher's lifetime, so create one
 * dispatcher per render pipeline instance, not per call. Instances are not thread-safe.
 */
@Slf4j
public final class PackGlyphDispatcher {

    /** Resolves a font id against the active resource pack. */
    @FunctionalInterface
    public interface FontSource {

        /**
         * @param fontId the effective font id (e.g. {@code minecraft:default}, {@code wynn:chat})
         * @return the resolved pack font, or empty when the pack defines no such font
         * @throws IllegalArgumentException when the font id itself is malformed (treated by the
         *                                  dispatcher as absent, since segment font ids are text
         *                                  input rather than pack content)
         * @throws net.aerh.imagegenerator.exception.PackResolveException when the font exists but
         *                                                                is broken (propagated:
         *                                                                broken pack content
         *                                                                fails loudly)
         */
        Optional<PackFont> resolveFont(String fontId);

        /**
         * The font source for a pack selection - the single conversion shared by every generator
         * that renders text through the tooltip line machinery, so their pack-activation and
         * repository-defaulting rules can never drift apart.
         *
         * @param packId         the selected pack, or null / {@link PackId#VANILLA} for none
         * @param packRepository the repository to resolve against, or null for
         *                       {@link PackRepository#global()}
         * @return a source resolving font ids against the selected pack, or null when no pack is
         *     active (keeping rendering entirely on the built-in font path)
         */
        @Nullable
        static FontSource forPack(@Nullable PackId packId, @Nullable PackRepository packRepository) {
            if (!PackId.isActive(packId)) {
                return null;
            }
            PackRepository repository = packRepository != null ? packRepository : PackRepository.global();
            return fontId -> repository.resolveFont(packId, fontId);
        }
    }

    private static final PackGlyphDispatcher DISABLED = new PackGlyphDispatcher(null);

    private final FontSource fontSource;
    private final Map<String, Optional<PackFont>> resolvedFonts = new HashMap<>();
    private final Map<PackFont, Map<Integer, List<Integer>>> obfuscationCandidates = new HashMap<>();
    private final Set<String> loggedFidelityGaps = new HashSet<>();

    private PackGlyphDispatcher(FontSource fontSource) {
        this.fontSource = fontSource;
    }

    /** A dispatcher that never dispatches: the no-pack rendering path. */
    public static PackGlyphDispatcher disabled() {
        return DISABLED;
    }

    /**
     * A dispatcher backed by the given font source; {@code null} yields the
     * {@link #disabled() disabled} dispatcher.
     */
    public static PackGlyphDispatcher of(FontSource fontSource) {
        return fontSource == null ? DISABLED : new PackGlyphDispatcher(fontSource);
    }

    /**
     * Resolves the winning pack glyph for one codepoint of a segment.
     *
     * @return the dispatched glyph, or empty when the codepoint stays on the built-in text path
     *     (dispatcher disabled, font not defined by the pack, or codepoint not supplied)
     */
    public Optional<Dispatched> dispatch(ColorSegment segment, int codePoint) {
        if (fontSource == null) {
            return Optional.empty();
        }
        String fontId = effectiveFontId(segment);
        PackFont font = resolvedFonts.computeIfAbsent(fontId, this::resolve).orElse(null);
        if (font == null) {
            return Optional.empty();
        }
        Optional<PackGlyph> glyph = font.glyph(codePoint);
        if (glyph.isEmpty()) {
            if (font.hasUnsupportedProviderFor(codePoint) && loggedFidelityGaps.add(font.id())) {
                log.warn("Font {} serves some codepoints through an unsupported provider type "
                    + "(ttf/unihex/legacy_unicode); those render via built-in fonts instead", font.id());
            }
            return Optional.empty();
        }
        boolean space = font.spaceAdvance(codePoint).isPresent();
        return Optional.of(new Dispatched(font, glyph.get(), segment.isBold(), segment.isItalic(), space));
    }

    /** The font id a segment's codepoints resolve against; see the class javadoc. */
    private static String effectiveFontId(ColorSegment segment) {
        String packFontId = segment.getPackFontId();
        return packFontId != null && !packFontId.isEmpty() ? packFontId : segment.getFont().getResourceLocation();
    }

    private Optional<PackFont> resolve(String fontId) {
        try {
            return fontSource.resolveFont(fontId);
        } catch (IllegalArgumentException e) {
            // The font id came from text input (a segment), not from the pack; a malformed id
            // falls back to built-in fonts instead of failing the render.
            log.warn("Ignoring malformed segment font id `{}`: {}", fontId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * One pack glyph dispatched for a specific segment: the winning glyph plus the segment's
     * captured bold/italic styles, exposing the advance used by measurement and the vanilla draw
     * order used by rendering, so both derive from the same object.
     */
    public final class Dispatched {

        private final PackFont font;
        private final PackGlyph glyph;
        private final boolean bold;
        private final boolean italic;
        private final boolean space;

        private Dispatched(PackFont font, PackGlyph glyph, boolean bold, boolean italic, boolean space) {
            this.font = font;
            this.glyph = glyph;
            this.bold = bold;
            this.italic = italic;
            this.space = space;
        }

        /**
         * Horizontal advance in GUI pixels, bold included ({@code +1.0}). May be negative or
         * fractional for space glyphs; callers must accumulate without per-step rounding.
         */
        public float advanceGuiPx() {
            return glyph.advance(bold);
        }

        /**
         * Whether this is a space-provider glyph: pure advance, no glyph art drawn - no glyph,
         * no shadow, no bold copy. Strikethrough/underline decorations still span the advance
         * (vanilla emits effect quads for every glyph, space glyphs included), so callers draw
         * those themselves regardless.
         */
        public boolean isSpace() {
            return space;
        }

        /**
         * Whether {@link #draw} paints any pixels at all: false for space glyphs and for bitmap
         * glyphs whose cell is fully transparent. Canvas-sizing callers use this to skip art
         * extents for glyphs that only move the cursor.
         */
        public boolean drawsArt() {
            return !space && !glyph.isEmpty();
        }

        /**
         * Whether the captured segment style renders this glyph italic. Italic art shears each
         * drawn row horizontally ({@code 1 - 0.25 * guiPxBelowLineTop} GUI px), so measurement
         * callers must widen their horizontal extents by the shear at the cell's first and last
         * rows.
         */
        public boolean isItalic() {
            return italic;
        }

        /**
         * Whether the captured segment style renders this glyph bold. A bold glyph draws a second
         * copy one GUI px to the right, so measurement callers folding the drawn ink box must
         * extend its right edge by {@code +1} GUI px.
         */
        public boolean isBold() {
            return bold;
        }

        /**
         * Left edge of the glyph's drawn ink relative to the pen origin, GUI px; see
         * {@link PackGlyph#inkLeftGuiPx()}. Meaningful only when {@link #inkRightGuiPx()} exceeds
         * it (a TTF cell shifted or wider than its advance); bitmap and space glyphs report a
         * degenerate {@code [0, 0]} box the advance already bounds.
         */
        public double inkLeftGuiPx() {
            return glyph.inkLeftGuiPx();
        }

        /**
         * Right edge of the glyph's drawn ink relative to the pen origin, GUI px (the unbold cell,
         * bold copy excluded); see {@link PackGlyph#inkRightGuiPx()}.
         */
        public double inkRightGuiPx() {
            return glyph.inkRightGuiPx();
        }

        /**
         * Provider {@code ascent} in GUI pixels (negative legal; 0 for space glyphs). The glyph
         * cell's top edge sits {@code ascent} GUI pixels above the baseline, i.e. at
         * {@code lineTop + 7 - ascent}.
         */
        public int ascentGuiPx() {
            return glyph.ascent();
        }

        /** Provider {@code height} in GUI pixels (0 for space glyphs): the drawn cell height. */
        public int heightGuiPx() {
            return glyph.height();
        }

        /**
         * Draws the glyph in vanilla order: the full shadow pass first (tinted
         * {@code shadowColor} at {@code +1,+1} GUI px, bold copy at a further {@code +1} GUI
         * px), then the main pass (bold copy again at {@code +1}). Space glyphs and blank cells
         * draw nothing. Italic shear is applied by the glyph itself.
         *
         * @param graphics     target canvas graphics
         * @param xGuiPx       left edge in GUI pixels (fractional legal)
         * @param lineTopGuiPx top of the text line in GUI pixels (the baseline sits 7 GUI px
         *                     below it)
         * @param pixelSize    canvas pixels per GUI pixel, at least 1
         * @param color        text color; glyph textures are tinted multiplicatively
         * @param shadowColor  tint for the shadow pass, or null to skip it. Pass the SAME shadow
         *                     color the surrounding pipeline draws built-in text shadows with
         *                     (vanilla's default is the quartered text color) so one segment
         *                     never mixes shadow hues
         */
        public void draw(Graphics2D graphics, double xGuiPx, double lineTopGuiPx, int pixelSize,
                         Color color, Color shadowColor) {
            if (glyph.isEmpty()) {
                return;
            }
            if (shadowColor != null) {
                glyph.draw(graphics, xGuiPx + 1, lineTopGuiPx + 1, pixelSize, shadowColor, italic);
                if (bold) {
                    glyph.draw(graphics, xGuiPx + 2, lineTopGuiPx + 1, pixelSize, shadowColor, italic);
                }
            }
            glyph.draw(graphics, xGuiPx, lineTopGuiPx, pixelSize, color, italic);
            if (bold) {
                glyph.draw(graphics, xGuiPx + 1, lineTopGuiPx, pixelSize, color, italic);
            }
        }

        /**
         * The deterministic obfuscation substitute for this glyph: another glyph of the SAME
         * font with equal {@code ceil} unbold advance, chosen by {@code seed} (same seed, same
         * substitute; the seed is scrambled internally, so sequential caller seeds still spread
         * across the candidates). Space glyphs return themselves (nothing is drawn for them
         * anyway), as does a glyph with no equal-advance candidates.
         */
        public Dispatched obfuscated(long seed) {
            if (space) {
                return this;
            }
            List<Integer> candidates = obfuscationCandidates(font)
                .getOrDefault((int) Math.ceil(glyph.advance(false)), List.of());
            if (candidates.isEmpty()) {
                return this;
            }
            int codePoint = candidates.get((int) Math.floorMod(mix(seed), candidates.size()));
            PackGlyph substitute = font.glyph(codePoint).orElse(null);
            return substitute == null ? this : new Dispatched(font, substitute, bold, italic, false);
        }

        /**
         * splitmix64 finalizer: bijective, well distributed even for adjacent inputs (which
         * {@code java.util.Random} is not when only the low bits of small seeds differ).
         */
        private long mix(long seed) {
            long mixed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
            mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
            return mixed ^ (mixed >>> 31);
        }
    }

    /**
     * Bitmap-served codepoints of a font grouped by {@code ceil} unbold advance, in ascending
     * codepoint order (deterministic), built once per font per dispatcher. Space-served
     * codepoints are excluded: substituting a drawn glyph with pure whitespace is not
     * vanilla-plausible obfuscation.
     */
    private Map<Integer, List<Integer>> obfuscationCandidates(PackFont font) {
        return obfuscationCandidates.computeIfAbsent(font, f -> {
            Map<Integer, List<Integer>> byAdvance = new HashMap<>();
            for (int codePoint : f.mappedCodePoints()) {
                if (f.spaceAdvance(codePoint).isPresent()) {
                    continue;
                }
                f.glyph(codePoint).ifPresent(glyph -> byAdvance
                    .computeIfAbsent((int) Math.ceil(glyph.advance(false)), key -> new ArrayList<>())
                    .add(codePoint));
            }
            return byAdvance;
        });
    }
}

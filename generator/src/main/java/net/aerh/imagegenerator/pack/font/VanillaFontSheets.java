package net.aerh.imagegenerator.pack.font;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The three vanilla client glyph sheets ({@code minecraft:font/ascii.png},
 * {@code minecraft:font/accented.png}, {@code minecraft:font/nonlatin_european.png}) bundled from
 * the vanilla 1.21.x client so a pack that references them WITHOUT shipping them still renders real
 * bitmap glyphs.
 *
 * <p>Real server packs override {@code minecraft:default} with bitmap providers
 * pointing at these vanilla sheets and rely on the client already having them; a pack shipped to a
 * player carries neither the sheet PNGs nor an explicit copy. Without this fallback those providers
 * are skipped (the {@link PackFont} absent-sheet deviation) and the pack's Latin body text falls
 * back to the bundled OTF - visibly wrong. Consulting these bundled copies FIRST, before the
 * skip-with-warn, restores vanilla-exact glyphs while a pack that DOES ship its own sheet still
 * wins (its provider is present, so this fallback is never consulted).
 *
 * <p>Provenance: the PNGs under {@code /minecraft/assets/font-fallback/} are the unmodified vanilla
 * assets extracted from the 1.21.x client jar ({@code assets/minecraft/textures/font/}). They were
 * verified byte-identical (SHA-256) to the {@code fontgen --emit-bitmap-sheets} output of the same
 * client version before bundling. Grids match the vanilla {@code minecraft:include/default}
 * metrics: ascii 128x128 (8x8 cells, height 8, ascent 7), accented 144x900 (9x12 cells, height 12,
 * ascent 10), nonlatin_european 128x536 (8x8 cells, height 8, ascent 7). These are Mojang's own
 * vanilla assets, not server-pack content, so bundling them is not the pack-redistribution concern.
 *
 * <p>The caller supplies the per-provider metrics from the PACK's own bitmap definition (so a pack
 * that re-declares {@code ascent} - as some packs' shift-down {@code default_offset} fonts do to shift text down -
 * keeps its shift); this class supplies only the sheet pixels. Decoded sheets are memoized and
 * shared read-only (each glyph copies its own cell, mirroring the pack sheet path), so the tiny
 * PNGs decode at most once per JVM.
 */
@Slf4j
public final class VanillaFontSheets {

    private static final String RESOURCE_ROOT = "/minecraft/assets/font-fallback/";

    /** Provider {@code file} path (minecraft namespace) to the bundled resource file name. */
    private static final Map<String, String> BUNDLED = Map.of(
        "font/ascii.png", "ascii.png",
        "font/accented.png", "accented.png",
        "font/nonlatin_european.png", "nonlatin_european.png");

    /** Memoized decode result per bundled path; empty when the resource is missing or undecodable. */
    private static final Map<String, Optional<BufferedImage>> DECODED = new ConcurrentHashMap<>();

    private VanillaFontSheets() {
    }

    /**
     * The bundled vanilla sheet for an absent pack bitmap provider, matched by exact resource
     * location. Only the {@code minecraft} namespace and the three bundled vanilla paths are
     * eligible; every other reference returns empty so the caller keeps its skip-with-warn.
     *
     * @param namespace the provider {@code file} reference's namespace (already normalized to
     *                  {@code minecraft} for a bare id by the caller)
     * @param path      the provider {@code file} reference's path, extension included (e.g.
     *                  {@code font/ascii.png})
     * @return the shared, read-only decoded sheet, or empty when the reference is not one of the
     *     bundled vanilla sheets or the bundled resource failed to load
     */
    public static Optional<BufferedImage> sheet(String namespace, String path) {
        if (!"minecraft".equals(namespace) || !BUNDLED.containsKey(path)) {
            return Optional.empty();
        }
        return DECODED.computeIfAbsent(path, VanillaFontSheets::decode);
    }

    private static Optional<BufferedImage> decode(String path) {
        String resource = RESOURCE_ROOT + BUNDLED.get(path);
        try (InputStream stream = VanillaFontSheets.class.getResourceAsStream(resource)) {
            if (stream == null) {
                log.warn("Bundled vanilla font sheet `{}` is missing from the classpath; "
                    + "packs referencing it will skip the provider", resource);
                return Optional.empty();
            }
            // ImageIO is exact for these sheets: vanilla glyph sheets are 1-bit-alpha pixel fonts
            // (every pixel is fully opaque or fully transparent), and the bitmap provider reads them
            // through getRGB, so no premultiplied-alpha channel shift can arise.
            BufferedImage sheet = ImageIO.read(stream);
            if (sheet == null) {
                log.warn("Bundled vanilla font sheet `{}` is not a decodable image", resource);
                return Optional.empty();
            }
            return Optional.of(sheet);
        } catch (IOException e) {
            log.warn("Bundled vanilla font sheet `{}` failed to decode: {}", resource, e.getMessage());
            return Optional.empty();
        }
    }
}

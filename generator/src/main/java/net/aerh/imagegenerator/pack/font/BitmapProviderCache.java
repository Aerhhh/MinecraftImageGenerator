package net.aerh.imagegenerator.pack.font;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;

/**
 * Shares built bitmap providers across the fonts of one pack, mirroring vanilla's resolve-once
 * splicing of glyph providers: real server packs commonly define many fonts that {@code reference}
 * one common base font, and without sharing each resolved font would re-decode the base sheets
 * and duplicate every glyph cell.
 *
 * <p>Providers are keyed by the fields that determine their glyphs - {@code file},
 * {@code height}, {@code ascent} and the codepoint grid - so definitions differing only in their
 * (already applied) filter still share. Values are held softly: a provider stays reachable while
 * any resolved font references it, and becomes collectable under memory pressure once no font
 * does.
 *
 * <p>Create one instance per pack and pass it to
 * {@link PackFont#create(String, List, PackFont.TextureLoader, BitmapProviderCache)}.
 */
public final class BitmapProviderCache {

    private record SheetKey(String file, int height, int ascent, List<String> charRows) {
    }

    private final Cache<SheetKey, BitmapFontProvider> providers = Caffeine.newBuilder()
        .softValues()
        .build();

    /**
     * The shared provider for a bitmap definition, building it (one sheet load + cell copies) on
     * first use. Log and error context uses the font id that triggered the build.
     */
    BitmapFontProvider bitmapProvider(FontProviderDefinition.Bitmap definition,
                                      PackFont.TextureLoader textures, String fontId) {
        SheetKey key = new SheetKey(definition.file(), definition.height(), definition.ascent(),
            definition.charRows());
        return providers.get(key,
            ignored -> BitmapFontProvider.create(definition, textures.load(definition.file()), fontId));
    }
}

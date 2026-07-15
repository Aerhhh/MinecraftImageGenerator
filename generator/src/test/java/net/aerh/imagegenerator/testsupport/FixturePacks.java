package net.aerh.imagegenerator.testsupport;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates a complete, ORIGINAL mini resource pack (namespace {@code testpack}) at test runtime.
 * Never contains third-party assets; nothing binary is committed to the repository.
 */
public final class FixturePacks {

    public static final String NAMESPACE = "testpack";
    public static final String THEME_NAMESPACE = "themepack";

    private FixturePacks() {
    }

    public static Path writeDefaultPack(Path root) {
        try {
            write(root, "pack.mcmeta", """
                {"pack":{"pack_format":88,"description":"test fixture"}}""");

            // Simple item: definition -> model -> 16x16 texture
            item(root, "simple", """
                {"model":{"type":"minecraft:model","model":"testpack:item/simple"}}""");
            model(root, "simple", "item/generated", "testpack:item/simple");
            texture(root, "simple", solid(16, 16, 0xFFFF0000));

            // In-hand variant model shared by dispatch fixtures
            model(root, "in_hand", "item/handheld", "testpack:item/in_hand");
            texture(root, "in_hand", solid(16, 16, 0xFF00FF00));

            // Condition with unprefixed type string
            item(root, "conditional", """
                {"model":{"type":"condition","property":"using_item",
                  "on_true":{"type":"model","model":"testpack:item/in_hand"},
                  "on_false":{"type":"model","model":"testpack:item/simple"}}}""");

            // Select on display_context
            item(root, "selecty", """
                {"model":{"type":"minecraft:select","property":"minecraft:display_context",
                  "cases":[{"when":["gui","ground"],"model":{"type":"minecraft:model","model":"testpack:item/simple"}}],
                  "fallback":{"type":"minecraft:model","model":"testpack:item/in_hand"}}}""");

            // Range dispatch resolving to fallback at value 0
            item(root, "ranged", """
                {"model":{"type":"range_dispatch","property":"cooldown",
                  "entries":[{"threshold":0.5,"model":{"type":"model","model":"testpack:item/in_hand"}}],
                  "fallback":{"type":"model","model":"testpack:item/simple"}}}""");

            // Composite: opaque red base + green overlay pixel at (0,0)
            item(root, "layered", """
                {"model":{"type":"composite","models":[
                  {"type":"model","model":"testpack:item/simple"},
                  {"type":"model","model":"testpack:item/overlay"}]}}""");
            model(root, "overlay", "item/generated", "testpack:item/overlay");
            BufferedImage overlay = transparent(16, 16);
            overlay.setRGB(0, 0, 0xFF00FF00);
            texture(root, "overlay", overlay);

            // Unsupported node type
            item(root, "special", """
                {"model":{"type":"minecraft:special","base":"minecraft:item/template_shulker_box"}}""");

            // Dangling model reference
            item(root, "broken_model_ref", """
                {"model":{"type":"minecraft:model","model":"testpack:item/missing_model"}}""");

            // Model without layer0 whose parent is outside the pack
            item(root, "no_layer0", """
                {"model":{"type":"minecraft:model","model":"testpack:item/no_layer0"}}""");
            write(root, "assets/testpack/models/item/no_layer0.json", """
                {"parent":"item/paper"}""");

            // Model whose layer0 points at a texture PNG that does not exist
            item(root, "broken_texture_ref", """
                {"model":{"type":"minecraft:model","model":"testpack:item/broken_texture_ref"}}""");
            model(root, "broken_texture_ref", "item/generated", "testpack:item/missing_texture");

            // Model whose layer0 is not a parseable resource reference (multiple colons)
            item(root, "malformed_ref", """
                {"model":{"type":"minecraft:model","model":"testpack:item/malformed_ref"}}""");
            model(root, "malformed_ref", "item/generated", "a:b:c");

            // Parent cycle: two models referencing each other, neither with layer0
            item(root, "cyclic", """
                {"model":{"type":"minecraft:model","model":"testpack:item/cycle_a"}}""");
            write(root, "assets/testpack/models/item/cycle_a.json", """
                {"parent":"testpack:item/cycle_b"}""");
            write(root, "assets/testpack/models/item/cycle_b.json", """
                {"parent":"testpack:item/cycle_a"}""");

            // Animated flipbook: 16x48, frames list starts at index 2 (blue frame)
            item(root, "animated", """
                {"model":{"type":"minecraft:model","model":"testpack:item/animated"}}""");
            model(root, "animated", "item/generated", "testpack:item/animated");
            BufferedImage flipbook = transparent(16, 48);
            fill(flipbook, 0, 0xFFFF0000);
            fill(flipbook, 1, 0xFF00FF00);
            fill(flipbook, 2, 0xFF0000FF);
            texture(root, "animated", flipbook);
            write(root, "assets/testpack/textures/item/animated.png.mcmeta", """
                {"animation":{"frametime":3,"frames":[2,0,1]}}""");

            // Emissive marker texture (alpha 252)
            item(root, "emissive", """
                {"model":{"type":"minecraft:model","model":"testpack:item/emissive"}}""");
            model(root, "emissive", "item/generated", "testpack:item/emissive");
            BufferedImage emissive = transparent(16, 16);
            emissive.setRGB(0, 0, (252 << 24) | 0x00FFAA00);
            texture(root, "emissive", emissive);

            // 32x32 texture
            item(root, "big", """
                {"model":{"type":"minecraft:model","model":"testpack:item/big"}}""");
            model(root, "big", "item/generated", "testpack:item/big");
            texture(root, "big", solid(32, 32, 0xFF123456));

            // Malformed item JSON (indexed as an error entry)
            write(root, "assets/testpack/items/item/malformed.json", "{nope");

            // Composite with zero models: resolves to zero renderable layers
            item(root, "empty_composite", """
                {"model":{"type":"composite","models":[]}}""");

            // Tooltip style with nine-slice mcmeta on both sprites; the frame has a transparent
            // center like real tooltip frames, so layering over the background is observable
            tooltipSprite(root, NAMESPACE, "fancy_background", solid(8, 8, 0xFF112233));
            write(root, "assets/testpack/textures/gui/sprites/tooltip/fancy_background.png.mcmeta", """
                {"gui":{"scaling":{"type":"nine_slice","width":8,"height":8,"border":2}}}""");
            tooltipSprite(root, NAMESPACE, "fancy_frame", ring(8, 8, 3, 0xFF445566));
            write(root, "assets/testpack/textures/gui/sprites/tooltip/fancy_frame.png.mcmeta", """
                {"gui":{"scaling":{"type":"nine_slice","width":8,"height":8,"border":3,"stretch_inner":true}}}""");

            // Tooltip style without mcmeta: scaling defaults to stretch
            tooltipSprite(root, NAMESPACE, "plain_background", solid(4, 4, 0xFF224466));
            tooltipSprite(root, NAMESPACE, "plain_frame", solid(4, 4, 0xFF664422));

            // Incomplete tooltip style: background sprite only
            tooltipSprite(root, NAMESPACE, "half_background", solid(4, 4, 0xFF808080));

            // Complete tooltip style whose background mcmeta declares an unknown scaling type
            tooltipSprite(root, NAMESPACE, "brokenmeta_background", solid(4, 4, 0xFF010101));
            write(root, "assets/testpack/textures/gui/sprites/tooltip/brokenmeta_background.png.mcmeta", """
                {"gui":{"scaling":{"type":"twelve_slice","width":4,"height":4}}}""");
            tooltipSprite(root, NAMESPACE, "brokenmeta_frame", solid(4, 4, 0xFF020202));

            // Animated tooltip background: flipbook and gui scaling in one mcmeta
            BufferedImage themeFlipbook = transparent(8, 24);
            fillFrame(themeFlipbook, 0, 8, 0xFFFF0000);
            fillFrame(themeFlipbook, 1, 8, 0xFF00FF00);
            fillFrame(themeFlipbook, 2, 8, 0xFF0000FF);
            tooltipSprite(root, NAMESPACE, "anim_background", themeFlipbook);
            write(root, "assets/testpack/textures/gui/sprites/tooltip/anim_background.png.mcmeta", """
                {"animation":{"frametime":2,"frames":[2,0,1]},
                 "gui":{"scaling":{"type":"nine_slice","width":8,"height":8,"border":1}}}""");
            tooltipSprite(root, NAMESPACE, "anim_frame", solid(8, 8, 0xFF030303));

            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write fixture pack", e);
        }
    }

    /**
     * Writes a pack containing ONLY tooltip theming (no item definitions): one complete style in
     * the {@code themepack} namespace plus a vanilla default-tooltip override, exactly how packs
     * restyle styleless tooltips in game.
     */
    public static Path writeTooltipOnlyPack(Path root) {
        try {
            write(root, "pack.mcmeta", """
                {"pack":{"pack_format":88,"description":"tooltip-only test fixture"}}""");

            tooltipSprite(root, THEME_NAMESPACE, "ruby_background", solid(8, 8, 0xFF990011));
            write(root, "assets/" + THEME_NAMESPACE + "/textures/gui/sprites/tooltip/ruby_background.png.mcmeta", """
                {"gui":{"scaling":{"type":"nine_slice","width":8,"height":8,"border":2}}}""");
            tooltipSprite(root, THEME_NAMESPACE, "ruby_frame", solid(8, 8, 0xFF660022));

            tooltipSprite(root, "minecraft", "background", solid(8, 8, 0xFF101010));
            write(root, "assets/minecraft/textures/gui/sprites/tooltip/background.png.mcmeta", """
                {"gui":{"scaling":{"type":"nine_slice","width":8,"height":8,"border":2}}}""");
            tooltipSprite(root, "minecraft", "frame", solid(8, 8, 0xFF202020));

            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write tooltip-only fixture pack", e);
        }
    }

    /**
     * Writes a pack exercising tooltip sprite sheets at real-pack scale, mirroring the animated
     * tooltip frame strips large server packs ship (many width-square frames stacked vertically,
     * far past the item texture cap).
     *
     * <p>Contents (all in the {@code testpack} namespace):
     * <ul>
     * <li>Style {@code tallstrip}: the background is a 146x2482 flipbook of 17 frames of
     *     146x146. Frame 0 is solid 0xFF112233 with four distinct corner marker pixels
     *     (top-left 0xFFAA0000, top-right 0xFF00AA00, bottom-left 0xFF0000AA, bottom-right
     *     0xFFAAAA00); frames 1..16 are solid near-black. Its mcmeta combines an animation
     *     section whose frames list is {@code [0,1,...,16,{"index":0,"time":100}]} (int entries
     *     plus a trailing object entry, the real-pack shape) with nine-slice gui scaling at the
     *     146 nominal size, a per-side border object (left 8, top 9, right 10, bottom 11) and
     *     {@code stretch_inner}. The frame sprite is a 146x146 ring without mcmeta.</li>
     * <li>Item {@code oversized}: definition -> model -> 146x2482 texture, the SAME dimensions
     *     as the strip, proving item textures stay under the strict item cap while tooltip
     *     sheets get the sheet cap.</li>
     * <li>Item {@code smuggled}: definition -> model whose layer0 references the tallstrip
     *     background texture UNDER THE TOOLTIP SPRITE PATH, proving the sheet cap is selected
     *     by usage rather than path.</li>
     * </ul>
     */
    public static Path writeTallAnimatedTooltipPack(Path root) {
        try {
            write(root, "pack.mcmeta", """
                {"pack":{"pack_format":88,"description":"tall animated tooltip test fixture"}}""");

            BufferedImage strip = transparent(146, 17 * 146);
            fillFrame(strip, 0, 146, 0xFF112233);
            strip.setRGB(0, 0, 0xFFAA0000);
            strip.setRGB(145, 0, 0xFF00AA00);
            strip.setRGB(0, 145, 0xFF0000AA);
            strip.setRGB(145, 145, 0xFFAAAA00);
            for (int i = 1; i < 17; i++) {
                fillFrame(strip, i, 146, 0xFF000000 | i);
            }
            tooltipSprite(root, NAMESPACE, "tallstrip_background", strip);
            StringBuilder framesList = new StringBuilder();
            for (int i = 0; i < 17; i++) {
                framesList.append(i).append(',');
            }
            framesList.append("{\"index\":0,\"time\":100}");
            write(root, "assets/testpack/textures/gui/sprites/tooltip/tallstrip_background.png.mcmeta",
                "{\"animation\":{\"frametime\":2,\"frames\":[" + framesList + "]},"
                    + "\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":146,\"height\":146,"
                    + "\"border\":{\"left\":8,\"top\":9,\"right\":10,\"bottom\":11},\"stretch_inner\":true}}}");
            tooltipSprite(root, NAMESPACE, "tallstrip_frame", ring(146, 146, 4, 0xFF445566));

            item(root, "oversized", """
                {"model":{"type":"minecraft:model","model":"testpack:item/oversized"}}""");
            model(root, "oversized", "item/generated", "testpack:item/oversized");
            texture(root, "oversized", transparent(146, 2482));

            // Item whose model references the strip ALREADY stored under the tooltip sprite
            // path: the decode cap is chosen by usage, not path, so resolving this ITEM must
            // still fail at the strict item cap even though the same file decodes as a sheet.
            item(root, "smuggled", """
                {"model":{"type":"minecraft:model","model":"testpack:item/smuggled"}}""");
            model(root, "smuggled", "item/generated", "testpack:gui/sprites/tooltip/tallstrip_background");

            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write tall animated tooltip fixture pack", e);
        }
    }

    /**
     * Writes a minimal valid pack (one item -> model -> texture chain) padded with filler JSON
     * files until exactly {@code assetFileCount} regular files exist under {@code assets/}. Used
     * by entry-cap wiring tests that need a precise asset file count; {@code pack.mcmeta} sits
     * outside {@code assets/} and does not count.
     */
    public static Path writeMinimalPack(Path root, int assetFileCount) {
        if (assetFileCount < 3) {
            throw new IllegalArgumentException("Minimal pack needs at least 3 asset files, got: " + assetFileCount);
        }
        try {
            write(root, "pack.mcmeta", """
                {"pack":{"pack_format":88,"description":"minimal test fixture"}}""");
            item(root, "simple", """
                {"model":{"type":"minecraft:model","model":"testpack:item/simple"}}""");
            model(root, "simple", "item/generated", "testpack:item/simple");
            texture(root, "simple", solid(16, 16, 0xFFFF0000));
            for (int i = 3; i < assetFileCount; i++) {
                write(root, "assets/testpack/filler/f" + i + ".json", "{}");
            }
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write minimal fixture pack", e);
        }
    }

    /**
     * Writes a pack containing ONLY fonts (no items, no tooltip sprites) - the shape of a
     * dedicated server font pack. Everything is synthetic and generated at test runtime.
     *
     * <p>Contents (all in the {@code testpack} namespace unless noted):
     * <ul>
     * <li>{@code pixel}: bitmap font, 16x8 sheet cut 2x1 ({@code chars: ["AB"]}, 8x8 cells,
     *     height 8, ascent 7, scale 1). Cell 'A' has one opaque pixel at cell (5, 0) - ink 6,
     *     advance 7. Cell 'B' is fully transparent - ink 0, advance 1.</li>
     * <li>{@code alias}: reference to {@code testpack:pixel} followed by a space provider
     *     ({@code " "} -> 4.5).</li>
     * <li>{@code minecraft:barefont}: space-only font in the {@code minecraft} namespace, for
     *     bare-id default-namespace resolution.</li>
     * <li>{@code broken}: malformed JSON.</li>
     * <li>{@code dangling}: reference to a nonexistent font.</li>
     * <li>{@code cycle_a} / {@code cycle_b}: mutual references.</li>
     * <li>{@code bigsheet}: bitmap font whose 2048x8 sheet exceeds the default item texture cap
     *     (1024) but not the font cap (8192); 'A' has ink 1, advance 2.</li>
     * <li>{@code ttf_only}: single TTF provider with {@code skip: "xyz"} - loads, renders
     *     nothing.</li>
     * <li>{@code notexture}: bitmap font whose sheet PNG does not exist.</li>
     * <li>{@code sub/deco}: nested-path space-only font, mirroring real ids like
     *     {@code minecraft:tooltip/emblem/frame}.</li>
     * <li>{@code Fancy.json}: an INVALID resource location (uppercase); must be skipped at index
     *     time, never advertised by {@code fontIds()}.</li>
     * </ul>
     */
    public static Path writeFontPack(Path root) {
        try {
            write(root, "pack.mcmeta", """
                {"pack":{"pack_format":88,"description":"font-only test fixture"}}""");

            BufferedImage pixelSheet = transparent(16, 8);
            pixelSheet.setRGB(5, 0, 0xFFFFFFFF);
            fontTexture(root, NAMESPACE, "pixel.png", pixelSheet);
            fontJson(root, NAMESPACE, "pixel", """
                {"providers":[{"type":"bitmap","file":"testpack:font/pixel.png",
                  "height":8,"ascent":7,"chars":["AB"]}]}""");

            fontJson(root, NAMESPACE, "alias", """
                {"providers":[{"type":"reference","id":"testpack:pixel"},
                  {"type":"space","advances":{" ":4.5}}]}""");

            fontJson(root, "minecraft", "barefont", """
                {"providers":[{"type":"space","advances":{" ":6.0}}]}""");

            write(root, "assets/testpack/font/broken.json", "{nope");

            fontJson(root, NAMESPACE, "dangling", """
                {"providers":[{"type":"reference","id":"testpack:nope"}]}""");

            fontJson(root, NAMESPACE, "cycle_a", """
                {"providers":[{"type":"reference","id":"testpack:cycle_b"}]}""");
            fontJson(root, NAMESPACE, "cycle_b", """
                {"providers":[{"type":"reference","id":"testpack:cycle_a"}]}""");

            BufferedImage bigSheet = transparent(2048, 8);
            bigSheet.setRGB(0, 0, 0xFFFFFFFF);
            fontTexture(root, NAMESPACE, "big.png", bigSheet);
            fontJson(root, NAMESPACE, "bigsheet", """
                {"providers":[{"type":"bitmap","file":"testpack:font/big.png",
                  "ascent":7,"chars":["AB"]}]}""");

            fontJson(root, NAMESPACE, "ttf_only", """
                {"providers":[{"type":"ttf","file":"testpack:font/fake.ttf","skip":"xyz"}]}""");

            fontJson(root, NAMESPACE, "notexture", """
                {"providers":[{"type":"bitmap","file":"testpack:font/missing.png",
                  "ascent":7,"chars":["A"]}]}""");

            fontJson(root, NAMESPACE, "sub/deco", """
                {"providers":[{"type":"space","advances":{" ":3.0}}]}""");

            // Uppercase file name: not a valid resource location, so vanilla (and this library)
            // must skip it at index time rather than advertise an unresolvable font id.
            write(root, "assets/testpack/font/Fancy.json", """
                {"providers":[{"type":"space","advances":{" ":1.0}}]}""");

            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write font fixture pack", e);
        }
    }

    /**
     * Writes a font pack tailored for TEXT PIPELINE rendering tests (tooltips drawing pack
     * glyphs). Everything is synthetic and generated at test runtime.
     *
     * <p>Contents:
     * <ul>
     * <li>{@code testpack:glyphs}: bitmap font, 9x3 sheet cut 3x1 (one chars row mapping
     *     U+E000, U+E001 and U+E002 to 3x3 cells; height 3, ascent 3, scale 1).
     *     U+E000 is solid red (ink 3, advance 4), U+E001 solid blue (ink 3, advance 4), U+E002
     *     green in its first two columns (ink 2, advance 3). A space provider follows with
     *     U+E00A -> -4.0, U+E00B -> 0.5, U+E00C -> 0.75 (quarter granularity, so per-step
     *     rounding in canvas px is detectable at any even pixelSize), U+E00D -> 3.0 and
     *     U+E00E -> -6.0 (reaches past the borderless 5 GUI px text origin).</li>
     * <li>{@code minecraft:default}: a reference to {@code testpack:glyphs}, so ordinary lore
     *     text picks the pack glyphs up without an explicit segment font id.</li>
     * <li>{@code testpack:tall}: bitmap font whose single U+E000 glyph is a solid magenta
     *     3x32 cell with height 32 and ascent 16, extending far above and below the 9 GUI px
     *     line.</li>
     * </ul>
     */
    public static Path writeTextFontPack(Path root) {
        try {
            write(root, "pack.mcmeta", """
                {"pack":{"pack_format":88,"description":"text font test fixture"}}""");

            BufferedImage glyphSheet = transparent(9, 3);
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    glyphSheet.setRGB(x, y, 0xFFFF0000);
                    glyphSheet.setRGB(3 + x, y, 0xFF0000FF);
                }
                glyphSheet.setRGB(6, y, 0xFF00FF00);
                glyphSheet.setRGB(7, y, 0xFF00FF00);
            }
            fontTexture(root, NAMESPACE, "glyphs.png", glyphSheet);
            fontJson(root, NAMESPACE, "glyphs", """
                {"providers":[
                  {"type":"bitmap","file":"testpack:font/glyphs.png",
                   "height":3,"ascent":3,"chars":["\\uE000\\uE001\\uE002"]},
                  {"type":"space","advances":{"\\uE00A":-4.0,"\\uE00B":0.5,"\\uE00C":0.75,
                   "\\uE00D":3.0,"\\uE00E":-6.0}}]}""");

            fontJson(root, "minecraft", "default", """
                {"providers":[{"type":"reference","id":"testpack:glyphs"}]}""");

            BufferedImage tallSheet = solid(3, 32, 0xFFFF00FF);
            fontTexture(root, NAMESPACE, "tall.png", tallSheet);
            fontJson(root, NAMESPACE, "tall", """
                {"providers":[{"type":"bitmap","file":"testpack:font/tall.png",
                  "height":32,"ascent":16,"chars":["\\uE000"]}]}""");

            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write text font fixture pack", e);
        }
    }

    /**
     * Writes a pack for CONTAINER COMPOSITOR tests: the title-glyph background technique over a
     * fully transparent container texture. Everything is synthetic and generated at test runtime.
     *
     * <p>Contents:
     * <ul>
     * <li>{@code minecraft:textures/gui/container/generic_54.png}: fully transparent 256x256
     *     (the MCC style - the pack blanks the chrome and draws menus via title glyphs).</li>
     * <li>{@code testpack:menu}: bitmap font with three glyph-art cells plus a space provider:
     *     <ul>
     *     <li>U+E100 "background": 32x32 solid 0xFF336699 cell, height 64, ascent 5 - scale 2,
     *         drawn 64x64 GUI px, advance 65.</li>
     *     <li>U+E101 "tall": 8x8 solid 0xFF993366 cell, height 40, ascent 30 - scale 5, drawn
     *         40x40 GUI px topping out ABOVE the title line, advance 41.</li>
     *     <li>U+E102 "deep": 8x8 solid 0xFF663399 cell, height 160, ascent 5 - scale 20, drawn
     *         160x160 GUI px reaching far below a 1-row GUI rect, advance 161.</li>
     *     <li>space advances: U+E10A -> -8.0, U+E10B -> 120.0.</li>
     *     </ul></li>
     * <li>Item {@code testpack:item/marker}: definition -> model -> 16x16 solid 0xFFAA5500
     *     texture, for slot-position and z-order assertions.</li>
     * </ul>
     */
    public static Path writeContainerPack(Path root) {
        try {
            write(root, "pack.mcmeta", """
                {"pack":{"pack_format":88,"description":"container test fixture"}}""");

            containerTexture(root, transparent(256, 256));

            fontTexture(root, NAMESPACE, "menu_bg.png", solid(32, 32, 0xFF336699));
            fontTexture(root, NAMESPACE, "menu_tall.png", solid(8, 8, 0xFF993366));
            fontTexture(root, NAMESPACE, "menu_deep.png", solid(8, 8, 0xFF663399));
            fontJson(root, NAMESPACE, "menu", """
                {"providers":[
                  {"type":"bitmap","file":"testpack:font/menu_bg.png",
                   "height":64,"ascent":5,"chars":["\\uE100"]},
                  {"type":"bitmap","file":"testpack:font/menu_tall.png",
                   "height":40,"ascent":30,"chars":["\\uE101"]},
                  {"type":"bitmap","file":"testpack:font/menu_deep.png",
                   "height":160,"ascent":5,"chars":["\\uE102"]},
                  {"type":"space","advances":{"\\uE10A":-8.0,"\\uE10B":120.0}}]}""");

            item(root, "marker", """
                {"model":{"type":"minecraft:model","model":"testpack:item/marker"}}""");
            model(root, "marker", "item/generated", "testpack:item/marker");
            texture(root, "marker", solid(16, 16, 0xFFAA5500));

            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write container fixture pack", e);
        }
    }

    /**
     * Writes a pack containing ONLY a painted {@code generic_54} container texture (no items,
     * fonts or tooltip sprites) at the default 256x256 size - both the pack-emptiness-check
     * coverage for container textures and the sampling fixture for the vanilla two-section
     * background stitch. See {@link #writeContainerArtPack(Path, int)}.
     */
    public static Path writeContainerArtPack(Path root) {
        return writeContainerArtPack(root, 256);
    }

    /**
     * Writes the container art pack at an arbitrary square texture size ({@code textureSize}
     * must be a multiple of 256, e.g. 512 for an HD pack): all marker positions scale with the
     * texture so the 256-normalized sampling must place them at IDENTICAL canvas positions for
     * every size.
     *
     * <p>The texture paints the full 6-row GUI region (0,0)-(176,222) (in 256-normalized texel
     * units) in 0xFF224488 with marker texels of one normalized unit each:
     * <ul>
     * <li>(0,0) = 0xFFFF0000 and (175,0) = 0xFF00FF00: chest-section top corners.</li>
     * <li>(0,34) = 0xFFFF00FF: the LAST chest-section row of a 1-row container
     *     ({@code rows * 18 + 17} rows for rows = 1).</li>
     * <li>(0,35) = 0xFF00FFFF: skipped by a 1-row render (between the sections), visible for
     *     larger row counts.</li>
     * <li>(0,125) = 0xFF7700FF: the seam row NO row count ever samples (the chest section ends
     *     at v = 124 even for 6 rows; the bottom section starts at v = 126).</li>
     * <li>(0,126) = 0xFFFFA500: the bottom section's first row.</li>
     * <li>(0,221) = 0xFFFFFF00: the bottom section's last row (the GUI rect's final row stays
     *     unpainted, exactly like the vanilla client).</li>
     * </ul>
     */
    public static Path writeContainerArtPack(Path root, int textureSize) {
        if (textureSize % 256 != 0) {
            throw new IllegalArgumentException("textureSize must be a multiple of 256, got: " + textureSize);
        }
        try {
            write(root, "pack.mcmeta", """
                {"pack":{"pack_format":88,"description":"container art test fixture"}}""");

            int unit = textureSize / 256;
            BufferedImage art = transparent(textureSize, textureSize);
            fillUnits(art, unit, 0, 0, 176, 222, 0xFF224488);
            fillUnits(art, unit, 0, 0, 1, 1, 0xFFFF0000);
            fillUnits(art, unit, 175, 0, 1, 1, 0xFF00FF00);
            fillUnits(art, unit, 0, 34, 1, 1, 0xFFFF00FF);
            fillUnits(art, unit, 0, 35, 1, 1, 0xFF00FFFF);
            fillUnits(art, unit, 0, 125, 1, 1, 0xFF7700FF);
            fillUnits(art, unit, 0, 126, 1, 1, 0xFFFFA500);
            fillUnits(art, unit, 0, 221, 1, 1, 0xFFFFFF00);
            containerTexture(root, art);

            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write container art fixture pack", e);
        }
    }

    /** Fills a rectangle given in 256-normalized texel units, scaled by {@code unit} px per unit. */
    private static void fillUnits(BufferedImage image, int unit, int x, int y, int width, int height, int argb) {
        for (int py = y * unit; py < (y + height) * unit; py++) {
            for (int px = x * unit; px < (x + width) * unit; px++) {
                image.setRGB(px, py, argb);
            }
        }
    }

    private static void containerTexture(Path root, BufferedImage image) throws IOException {
        Path path = root.resolve("assets/minecraft/textures/gui/container/generic_54.png");
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    private static void fontJson(Path root, String namespace, String name, String json) throws IOException {
        write(root, "assets/" + namespace + "/font/" + name + ".json", json);
    }

    private static void fontTexture(Path root, String namespace, String fileName, BufferedImage image) throws IOException {
        Path path = root.resolve("assets/" + namespace + "/textures/font/" + fileName);
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    private static void item(Path root, String name, String json) throws IOException {
        write(root, "assets/testpack/items/item/" + name + ".json", json);
    }

    private static void model(Path root, String name, String parent, String layer0) throws IOException {
        write(root, "assets/testpack/models/item/" + name + ".json",
            "{\"parent\":\"" + parent + "\",\"textures\":{\"layer0\":\"" + layer0 + "\"}}");
    }

    private static void texture(Path root, String name, BufferedImage image) throws IOException {
        Path path = root.resolve("assets/testpack/textures/item/" + name + ".png");
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    private static void tooltipSprite(Path root, String namespace, String name, BufferedImage image) throws IOException {
        Path path = root.resolve("assets/" + namespace + "/textures/gui/sprites/tooltip/" + name + ".png");
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static BufferedImage solid(int width, int height, int argb) {
        BufferedImage image = transparent(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private static BufferedImage transparent(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    /** Solid image whose interior (inside {@code border} px on every side) is transparent. */
    private static BufferedImage ring(int width, int height, int border, int argb) {
        BufferedImage image = solid(width, height, argb);
        for (int y = border; y < height - border; y++) {
            for (int x = border; x < width - border; x++) {
                image.setRGB(x, y, 0);
            }
        }
        return image;
    }

    private static void fill(BufferedImage flipbook, int frameIndex, int argb) {
        fillFrame(flipbook, frameIndex, 16, argb);
    }

    private static void fillFrame(BufferedImage flipbook, int frameIndex, int frameSize, int argb) {
        for (int y = frameIndex * frameSize; y < (frameIndex + 1) * frameSize; y++) {
            for (int x = 0; x < frameSize; x++) {
                flipbook.setRGB(x, y, argb);
            }
        }
    }
}

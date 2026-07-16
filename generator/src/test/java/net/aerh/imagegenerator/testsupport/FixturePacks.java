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
            packMcmeta(root, "test fixture");

            // Simple item: definition -> model -> 16x16 texture
            delegatingItem(root, "simple", "testpack:item/simple");
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

            // ONE model with two generated layers (the vanilla dyed-ball shape): red base
            // layer0, green overlay pixel layer1, both baked bottom-to-top like the client's
            // ItemModelGenerator.
            delegatingItem(root, "two_layer", "testpack:item/two_layer");
            write(root, "assets/testpack/models/item/two_layer.json", """
                {"parent":"item/generated","textures":{
                  "layer0":"testpack:item/simple","layer1":"testpack:item/overlay"}}""");

            // Per-index layer tinting: white textures so the tint colors show unmixed; tint 0
            // dyes layer0 red, tint 1 dyes the layer1 overlay pixel blue.
            texture(root, "white", solid(16, 16, 0xFFFFFFFF));
            BufferedImage whiteDot = transparent(16, 16);
            whiteDot.setRGB(0, 0, 0xFFFFFFFF);
            texture(root, "white_dot", whiteDot);
            write(root, "assets/testpack/models/item/two_layer_white.json", """
                {"parent":"item/generated","textures":{
                  "layer0":"testpack:item/white","layer1":"testpack:item/white_dot"}}""");
            item(root, "two_layer_tinted", """
                {"model":{"type":"minecraft:model","model":"testpack:item/two_layer_white",
                  "tints":[{"type":"minecraft:constant","value":16711680},
                           {"type":"minecraft:constant","value":255}]}}""");

            // Layer contiguity: a gap at layer1 stops the stack at layer0 like vanilla, so the
            // layer2 overlay must not render.
            delegatingItem(root, "gapped_layer", "testpack:item/gapped_layer");
            write(root, "assets/testpack/models/item/gapped_layer.json", """
                {"parent":"item/generated","textures":{
                  "layer0":"testpack:item/simple","layer2":"testpack:item/overlay"}}""");

            // Six contiguous generated layers: vanilla's ItemModelGenerator bakes layer0 up to
            // layer4 and ignores everything past layer4, so the green layer4 pixel paints and
            // the blue layer5 pixel (same position) must not.
            BufferedImage overlayBlue = transparent(16, 16);
            overlayBlue.setRGB(0, 0, 0xFF0000FF);
            texture(root, "overlay_blue", overlayBlue);
            delegatingItem(root, "six_layer", "testpack:item/six_layer");
            write(root, "assets/testpack/models/item/six_layer.json", """
                {"parent":"item/generated","textures":{
                  "layer0":"testpack:item/simple","layer1":"testpack:item/simple",
                  "layer2":"testpack:item/simple","layer3":"testpack:item/simple",
                  "layer4":"testpack:item/overlay","layer5":"testpack:item/overlay_blue"}}""");

            // Generated layers may be #key indirections into the texture map (vanilla resolves
            // references for generated layers exactly like element faces)...
            delegatingItem(root, "indirect_layer", "testpack:item/indirect_layer");
            write(root, "assets/testpack/models/item/indirect_layer.json", """
                {"parent":"item/generated","textures":{
                  "layer0":"#icon","icon":"testpack:item/simple"}}""");

            // ...and an indirection to a key the map never defines fails loudly at resolve time.
            delegatingItem(root, "broken_indirect_layer", "testpack:item/broken_indirect_layer");
            write(root, "assets/testpack/models/item/broken_indirect_layer.json", """
                {"parent":"item/generated","textures":{"layer0":"#void"}}""");

            // layer0 present but the IN-pack parent above it missing: the merged-chain walk
            // fails loudly on both public APIs (a broken parent must never silently drop
            // whatever it was meant to supply).
            delegatingItem(root, "layered_orphan", "testpack:item/layered_orphan");
            write(root, "assets/testpack/models/item/layered_orphan.json", """
                {"parent":"testpack:item/nowhere","textures":{"layer0":"testpack:item/simple"}}""");

            // Layer inheritance: the child declares layer0, its in-pack parent supplies layer1;
            // the merged chain texture map composes both.
            delegatingItem(root, "inherited_layer", "testpack:item/inherited_layer_child");
            write(root, "assets/testpack/models/item/inherited_layer_child.json", """
                {"parent":"testpack:item/inherited_layer_parent","textures":{
                  "layer0":"testpack:item/simple"}}""");
            write(root, "assets/testpack/models/item/inherited_layer_parent.json", """
                {"parent":"item/generated","textures":{"layer1":"testpack:item/overlay"}}""");

            // Unsupported node type
            item(root, "special", """
                {"model":{"type":"minecraft:special","base":"minecraft:item/template_shulker_box"}}""");

            // Dangling model reference
            delegatingItem(root, "broken_model_ref", "testpack:item/missing_model");

            // Model without layer0 whose parent is outside the pack
            delegatingItem(root, "no_layer0", "testpack:item/no_layer0");
            write(root, "assets/testpack/models/item/no_layer0.json", """
                {"parent":"item/paper"}""");

            // Model whose layer0 points at a texture PNG that does not exist
            delegatingItem(root, "broken_texture_ref", "testpack:item/broken_texture_ref");
            model(root, "broken_texture_ref", "item/generated", "testpack:item/missing_texture");

            // Model whose layer0 is not a parseable resource reference (multiple colons)
            delegatingItem(root, "malformed_ref", "testpack:item/malformed_ref");
            model(root, "malformed_ref", "item/generated", "a:b:c");

            // Parent cycle: two models referencing each other, neither with layer0
            delegatingItem(root, "cyclic", "testpack:item/cycle_a");
            write(root, "assets/testpack/models/item/cycle_a.json", """
                {"parent":"testpack:item/cycle_b"}""");
            write(root, "assets/testpack/models/item/cycle_b.json", """
                {"parent":"testpack:item/cycle_a"}""");

            // Animated flipbook: 16x48, frames list starts at index 2 (blue frame)
            delegatingItem(root, "animated", "testpack:item/animated");
            model(root, "animated", "item/generated", "testpack:item/animated");
            BufferedImage flipbook = transparent(16, 48);
            fill(flipbook, 0, 0xFFFF0000);
            fill(flipbook, 1, 0xFF00FF00);
            fill(flipbook, 2, 0xFF0000FF);
            texture(root, "animated", flipbook);
            write(root, "assets/testpack/textures/item/animated.png.mcmeta", """
                {"animation":{"frametime":3,"frames":[2,0,1]}}""");

            // Emissive marker texture (alpha 252)
            delegatingItem(root, "emissive", "testpack:item/emissive");
            model(root, "emissive", "item/generated", "testpack:item/emissive");
            BufferedImage emissive = transparent(16, 16);
            emissive.setRGB(0, 0, (252 << 24) | 0x00FFAA00);
            texture(root, "emissive", emissive);

            // 32x32 texture
            delegatingItem(root, "big", "testpack:item/big");
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
            packMcmeta(root, "tooltip-only test fixture");

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
            packMcmeta(root, "tall animated tooltip test fixture");

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

            delegatingItem(root, "oversized", "testpack:item/oversized");
            model(root, "oversized", "item/generated", "testpack:item/oversized");
            texture(root, "oversized", transparent(146, 2482));

            // Item whose model references the strip ALREADY stored under the tooltip sprite
            // path: the decode cap is chosen by usage, not path, so resolving this ITEM must
            // still fail at the strict item cap even though the same file decodes as a sheet.
            delegatingItem(root, "smuggled", "testpack:item/smuggled");
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
            packMcmeta(root, "minimal test fixture");
            delegatingItem(root, "simple", "testpack:item/simple");
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
     * <li>{@code notexture}: bitmap font whose sheet PNG does not exist - the provider skips
     *     and the font resolves empty.</li>
     * <li>{@code mixed_sheets}: an absent-sheet bitmap provider (chars {@code "AC"}) listed
     *     BEFORE the present {@code pixel.png} provider (chars {@code "AB"}) and a trailing
     *     space provider - the real-pack shape where fonts reference unbundled vanilla client
     *     sheets alongside their own. After the skip, 'A' and 'B' come from the present sheet,
     *     'C' is unmapped, and the space advance survives.</li>
     * <li>{@code sub/deco}: nested-path space-only font, mirroring real ids like
     *     {@code minecraft:tooltip/emblem/frame}.</li>
     * <li>{@code Fancy.json}: an INVALID resource location (uppercase); must be skipped at index
     *     time, never advertised by {@code fontIds()}.</li>
     * </ul>
     */
    public static Path writeFontPack(Path root) {
        try {
            packMcmeta(root, "font-only test fixture");

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

            fontJson(root, NAMESPACE, "mixed_sheets", """
                {"providers":[
                  {"type":"bitmap","file":"testpack:font/missing.png","ascent":7,"chars":["AC"]},
                  {"type":"bitmap","file":"testpack:font/pixel.png","height":8,"ascent":7,"chars":["AB"]},
                  {"type":"space","advances":{" ":2.0}}]}""");

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
            packMcmeta(root, "text font test fixture");

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
     * Writes a pack for CONTAINER COMPOSITOR and HUD LINE tests: the title-glyph background
     * technique over a fully transparent container texture. Everything is synthetic and
     * generated at test runtime.
     *
     * <p>Contents:
     * <ul>
     * <li>{@code minecraft:textures/gui/container/generic_54.png}: fully transparent 256x256
     *     (the MCC style - the pack blanks the chrome and draws menus via title glyphs).</li>
     * <li>{@code testpack:menu}: bitmap font with four glyph-art cells plus a space provider:
     *     <ul>
     *     <li>U+E100 "background": 32x32 solid 0xFF336699 cell, height 64, ascent 5 - scale 2,
     *         drawn 64x64 GUI px, advance 65.</li>
     *     <li>U+E101 "tall": 8x8 solid 0xFF993366 cell, height 40, ascent 30 - scale 5, drawn
     *         40x40 GUI px topping out ABOVE the title line, advance 41.</li>
     *     <li>U+E102 "deep": 8x8 solid 0xFF663399 cell, height 160, ascent 5 - scale 20, drawn
     *         160x160 GUI px reaching far below a 1-row GUI rect, advance 161.</li>
     *     <li>U+E103 "anchor": 8x8 solid 0xFF44CC88 cell, height 8, ascent 7 - scale 1, drawn
     *         8x8 GUI px with its cell top exactly ON the line top ({@code 7 - ascent = 0}),
     *         advance 9. Placement-pin glyph for line anchor assertions.</li>
     *     <li>space advances: U+E10A -> -8.0, U+E10B -> 120.0.</li>
     *     </ul></li>
     * <li>Item {@code testpack:item/marker}: definition -> model -> 16x16 solid 0xFFAA5500
     *     texture, for slot-position and z-order assertions.</li>
     * </ul>
     */
    public static Path writeContainerPack(Path root) {
        try {
            packMcmeta(root, "container test fixture");

            containerTexture(root, transparent(256, 256));

            fontTexture(root, NAMESPACE, "menu_bg.png", solid(32, 32, 0xFF336699));
            fontTexture(root, NAMESPACE, "menu_tall.png", solid(8, 8, 0xFF993366));
            fontTexture(root, NAMESPACE, "menu_deep.png", solid(8, 8, 0xFF663399));
            fontTexture(root, NAMESPACE, "menu_anchor.png", solid(8, 8, 0xFF44CC88));
            fontJson(root, NAMESPACE, "menu", """
                {"providers":[
                  {"type":"bitmap","file":"testpack:font/menu_bg.png",
                   "height":64,"ascent":5,"chars":["\\uE100"]},
                  {"type":"bitmap","file":"testpack:font/menu_tall.png",
                   "height":40,"ascent":30,"chars":["\\uE101"]},
                  {"type":"bitmap","file":"testpack:font/menu_deep.png",
                   "height":160,"ascent":5,"chars":["\\uE102"]},
                  {"type":"bitmap","file":"testpack:font/menu_anchor.png",
                   "height":8,"ascent":7,"chars":["\\uE103"]},
                  {"type":"space","advances":{"\\uE10A":-8.0,"\\uE10B":120.0}}]}""");

            delegatingItem(root, "marker", "testpack:item/marker");
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
            packMcmeta(root, "container art test fixture");

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

    /**
     * Writes a pack of ELEMENTS-BASED item models (the Wynncraft/MCC UI-quad shape): flat quads
     * with display.gui transforms, custom_model_data dispatch, tints and oversized_in_gui.
     * Everything is synthetic and generated at test runtime.
     *
     * <p>Textures (namespace {@code testpack}):
     * <ul>
     * <li>{@code item/paint}: 16x16, columns 0..7 solid red 0xFFFF0000, columns 8..15 solid
     *     blue 0xFF0000FF (horizontally asymmetric for mirror assertions).</li>
     * <li>{@code item/backpaint}: 16x16, columns 0..7 solid yellow 0xFFFFFF00, columns 8..15
     *     solid magenta 0xFFFF00FF (the north-face texture).</li>
     * <li>{@code item/white}, {@code item/green}, {@code item/blue}, {@code item/gray},
     *     {@code item/red}: 2x2 solids (white, 0xFF00FF00, 0xFF0000FF, 0xFF808080,
     *     0xFFFF0000).</li>
     * </ul>
     *
     * <p>Items (all under {@code testpack:item/...}):
     * <ul>
     * <li>{@code flat}: full-slot quad ([0,0,0]..[16,16,1]); south face #front (paint), north
     *     face #back (backpaint). Also the parent of the inheritance fixtures.</li>
     * <li>{@code mirrored}: child of the flat model adding display.gui rotation (0,180,0).</li>
     * <li>{@code tilted}: child adding rotation (0,2,0) - MCC's decorative tilt, identity.</li>
     * <li>{@code badspin}: child adding rotation (30,225,0) - resolve throws by default;
     *     full-gui-rotation renders show the north face as a rotated parallelogram (the
     *     rotated south normal faces away from the viewer).</li>
     * <li>{@code frontspin}: child adding rotation (30,45,10) - throws by default; full
     *     renders keep the south face toward the viewer, spun and foreshortened.</li>
     * <li>{@code retextured}: child overriding #front to the green texture (child texture map
     *     entry wins over the parent's).</li>
     * <li>{@code gauge}: range_dispatch on custom_model_data (index 0, scale 1): threshold 1 ->
     *     green quad, threshold 2 -> blue quad, fallback gray quad.</li>
     * <li>{@code flagged}: condition on custom_model_data: on_true green, on_false red.</li>
     * <li>{@code named}: select on custom_model_data: "ruby" -> red, fallback blue.</li>
     * <li>{@code colored}: white quad with tintindex 0 and a custom_model_data tint (index 0,
     *     default white).</li>
     * <li>{@code constant_tint}: white quad with a constant tint 0xFF8000.</li>
     * <li>{@code dyed}: white quad with a minecraft:dye tint (default 0x3366FF) - the default
     *     color applies, since no per-item dye data exists in this library.</li>
     * <li>{@code unknown_tint}: white quad with a minecraft:team tint - resolve throws.</li>
     * <li>{@code oversized}: flat model with display.gui scale 2 and oversized_in_gui true
     *     (32x32 GUI px centered on the slot). {@code clipped}: same model without the
     *     flag.</li>
     * <li>{@code rotated}: element rotation angle 45 about z - renders as a diamond through
     *     the orthographic pipeline (no gui_light declared, so the vanilla side default
     *     shades its south face at 0.8).</li>
     * <li>{@code mixed}: composite of the flat elements model and the plain layer0 sprite
     *     model - resolve throws.</li>
     * <li>{@code plain_sprite}: classic layer0 item (16x16 solid 0xFFAA5500) proving the sprite
     *     path is untouched.</li>
     * <li>{@code unmirrored}: child of the mirrored model whose own EMPTY display.gui entry
     *     overrides the parent's rotation wholesale - renders like {@code flat}.</li>
     * <li>{@code orphan}: elements model whose parent ref is missing from the pack - resolve
     *     throws. {@code deep}: 9-model parent chain past the 8-hop limit - resolve throws.</li>
     * <li>{@code player_head_frame}: ordinary elements item whose path merely contains
     *     "player_head".</li>
     * <li>{@code sprite_constant_tint} / {@code sprite_cmd_tint} / {@code sprite_dyed} /
     *     {@code sprite_team_tint}: layer0 sprite over the white texture with a constant
     *     0xFF8000 tint, a custom_model_data tint (index 0, white default), a minecraft:dye
     *     tint (default 0x3366FF, applied) and an unsupported team tint (renders untinted with
     *     a warning).</li>
     * <li>{@code sprite_named}: select on custom_model_data over LAYER0 SPRITE models: "ruby"
     *     -> red layer0 sprite, fallback -> blue layer0 sprite. The flat-sprite counterpart of
     *     {@code named}, for pinning data-driven sprite dispatch in composites.</li>
     * <li>{@code bare_sprite} / {@code bare_quad}: a layer0 sprite model and an elements model
     *     whose texture references are BARE ({@code item/bare}, no namespace). The texture at
     *     {@code assets/minecraft/textures/item/bare.png} is teal 0xFF00AA77; a decoy at
     *     {@code assets/testpack/textures/item/bare.png} is brown 0xFF773311 - so a resolver
     *     binding bare references to anything but the {@code minecraft} namespace is caught by
     *     color.</li>
     * <li>{@code worn} / {@code worn_raw}: range_dispatch on {@code minecraft:damage}. The
     *     normalized variant (vanilla default) dispatches thresholds 0.25 -> green and 0.75 ->
     *     blue with a gray fallback; the raw variant ({@code normalize: false}) dispatches
     *     threshold 3 -> red with a gray fallback.</li>
     * <li>Vanilla chain exits: the pack CLAIMS the {@code minecraft} namespace (a filler model)
     *     without shipping the builtin templates, the real-pack shape. {@code vanilla_exit} and
     *     {@code handheld_exit} end their chains at {@code minecraft:item/generated} /
     *     {@code item/handheld} with a layer0 present (render as flat sprites);
     *     {@code vanilla_dead_end} ends at another missing minecraft-namespace model (resolve
     *     throws); {@code generated_no_layer0} ends at {@code item/generated} without any
     *     layer0 (resolve throws).</li>
     * </ul>
     */
    public static Path writeElementsPack(Path root) {
        try {
            packMcmeta(root, "elements test fixture");

            BufferedImage paint = transparent(16, 16);
            BufferedImage backpaint = transparent(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    paint.setRGB(x, y, x < 8 ? 0xFFFF0000 : 0xFF0000FF);
                    backpaint.setRGB(x, y, x < 8 ? 0xFFFFFF00 : 0xFFFF00FF);
                }
            }
            texture(root, "paint", paint);
            texture(root, "backpaint", backpaint);
            texture(root, "white", solid(2, 2, 0xFFFFFFFF));
            texture(root, "green", solid(2, 2, 0xFF00FF00));
            texture(root, "blue", solid(2, 2, 0xFF0000FF));
            texture(root, "gray", solid(2, 2, 0xFF808080));
            texture(root, "red", solid(2, 2, 0xFFFF0000));

            write(root, "assets/testpack/models/item/elem_flat.json", """
                {"textures":{"front":"testpack:item/paint","back":"testpack:item/backpaint"},
                 "gui_light":"front",
                 "elements":[{"from":[0,0,0],"to":[16,16,1],
                   "faces":{"south":{"texture":"#front"},"north":{"texture":"#back"}}}]}""");
            delegatingItem(root, "flat", "testpack:item/elem_flat");

            write(root, "assets/testpack/models/item/elem_mirrored.json", """
                {"parent":"testpack:item/elem_flat",
                 "display":{"gui":{"rotation":[0,180,0]}}}""");
            delegatingItem(root, "mirrored", "testpack:item/elem_mirrored");

            write(root, "assets/testpack/models/item/elem_tilted.json", """
                {"parent":"testpack:item/elem_flat",
                 "display":{"gui":{"rotation":[0,2,0]}}}""");
            delegatingItem(root, "tilted", "testpack:item/elem_tilted");

            write(root, "assets/testpack/models/item/elem_badspin.json", """
                {"parent":"testpack:item/elem_flat",
                 "display":{"gui":{"rotation":[30,225,0]}}}""");
            delegatingItem(root, "badspin", "testpack:item/elem_badspin");

            write(root, "assets/testpack/models/item/elem_frontspin.json", """
                {"parent":"testpack:item/elem_flat",
                 "display":{"gui":{"rotation":[30,45,10]}}}""");
            delegatingItem(root, "frontspin", "testpack:item/elem_frontspin");

            write(root, "assets/testpack/models/item/elem_retextured.json", """
                {"parent":"testpack:item/elem_flat",
                 "textures":{"front":"testpack:item/green"}}""");
            delegatingItem(root, "retextured", "testpack:item/elem_retextured");

            solidQuadModel(root, "elem_green", "testpack:item/green");
            solidQuadModel(root, "elem_blue", "testpack:item/blue");
            solidQuadModel(root, "elem_gray", "testpack:item/gray");
            solidQuadModel(root, "elem_red", "testpack:item/red");

            // Animated element face texture: a 16x48 flipbook whose mcmeta frames list starts
            // at the blue frame; element faces must sample the cropped first frame exactly like
            // flat layer sprites do.
            BufferedImage elemFlipbook = transparent(16, 48);
            fill(elemFlipbook, 0, 0xFFFF0000);
            fill(elemFlipbook, 1, 0xFF00FF00);
            fill(elemFlipbook, 2, 0xFF0000FF);
            texture(root, "elem_flipbook", elemFlipbook);
            write(root, "assets/testpack/textures/item/elem_flipbook.png.mcmeta", """
                {"animation":{"frametime":3,"frames":[2,0,1]}}""");
            solidQuadModel(root, "elem_animated", "testpack:item/elem_flipbook");
            delegatingItem(root, "animated_quad", "testpack:item/elem_animated");

            item(root, "gauge", """
                {"model":{"type":"range_dispatch","property":"minecraft:custom_model_data","index":0,"scale":1.0,
                  "entries":[{"threshold":1.0,"model":{"type":"model","model":"testpack:item/elem_green"}},
                             {"threshold":2.0,"model":{"type":"model","model":"testpack:item/elem_blue"}}],
                  "fallback":{"type":"model","model":"testpack:item/elem_gray"}}}""");

            item(root, "flagged", """
                {"model":{"type":"condition","property":"minecraft:custom_model_data","index":0,
                  "on_true":{"type":"model","model":"testpack:item/elem_green"},
                  "on_false":{"type":"model","model":"testpack:item/elem_red"}}}""");

            item(root, "named", """
                {"model":{"type":"select","property":"minecraft:custom_model_data","index":0,
                  "cases":[{"when":"ruby","model":{"type":"model","model":"testpack:item/elem_red"}}],
                  "fallback":{"type":"model","model":"testpack:item/elem_blue"}}}""");

            write(root, "assets/testpack/models/item/elem_tintable.json", """
                {"textures":{"all":"testpack:item/white"},
                 "elements":[{"from":[0,0,0],"to":[16,16,1],
                   "faces":{"south":{"texture":"#all","tintindex":0}}}]}""");
            item(root, "colored", """
                {"model":{"type":"model","model":"testpack:item/elem_tintable",
                  "tints":[{"type":"minecraft:custom_model_data","index":0,"default":16777215}]}}""");
            item(root, "constant_tint", """
                {"model":{"type":"model","model":"testpack:item/elem_tintable",
                  "tints":[{"type":"minecraft:constant","value":16744448}]}}""");
            // 3368703 = 0x3366FF; the dye default must be visibly non-white to prove it applies.
            item(root, "dyed", """
                {"model":{"type":"model","model":"testpack:item/elem_tintable",
                  "tints":[{"type":"minecraft:dye","default":3368703}]}}""");
            item(root, "unknown_tint", """
                {"model":{"type":"model","model":"testpack:item/elem_tintable",
                  "tints":[{"type":"minecraft:team","default":0}]}}""");

            write(root, "assets/testpack/models/item/elem_wide.json", """
                {"parent":"testpack:item/elem_flat",
                 "display":{"gui":{"scale":[2,2,2]}}}""");
            item(root, "oversized", """
                {"model":{"type":"minecraft:model","model":"testpack:item/elem_wide"},
                 "oversized_in_gui":true}""");
            delegatingItem(root, "clipped", "testpack:item/elem_wide");

            write(root, "assets/testpack/models/item/elem_rotated.json", """
                {"textures":{"all":"testpack:item/white"},
                 "elements":[{"from":[0,0,0],"to":[16,16,1],
                   "rotation":{"angle":45,"axis":"z","origin":[8,8,8]},
                   "faces":{"south":{"texture":"#all"}}}]}""");
            delegatingItem(root, "rotated", "testpack:item/elem_rotated");

            model(root, "plain", "item/generated", "testpack:item/plain");
            texture(root, "plain", solid(16, 16, 0xFFAA5500));
            delegatingItem(root, "plain_sprite", "testpack:item/plain");

            item(root, "mixed", """
                {"model":{"type":"composite","models":[
                  {"type":"model","model":"testpack:item/elem_flat"},
                  {"type":"model","model":"testpack:item/plain"}]}}""");

            // Child whose own EMPTY display.gui entry overrides the parent's (0,180,0) mirror
            // wholesale (vanilla display entries never merge per component).
            write(root, "assets/testpack/models/item/elem_unmirrored.json", """
                {"parent":"testpack:item/elem_mirrored",
                 "display":{"gui":{}}}""");
            delegatingItem(root, "unmirrored", "testpack:item/elem_unmirrored");

            // Elements model whose parent ref points at a model missing from the pack.
            write(root, "assets/testpack/models/item/elem_orphan.json", """
                {"parent":"testpack:item/nope_parent",
                 "textures":{"all":"testpack:item/white"},
                 "elements":[{"from":[0,0,0],"to":[16,16,1],
                   "faces":{"south":{"texture":"#all"}}}]}""");
            delegatingItem(root, "orphan", "testpack:item/elem_orphan");

            // A 9-model parent chain (deeper than the resolver's 8-hop limit).
            write(root, "assets/testpack/models/item/elem_deep_0.json", """
                {"textures":{"all":"testpack:item/white"},
                 "elements":[{"from":[0,0,0],"to":[16,16,1],
                   "faces":{"south":{"texture":"#all"}}}],
                 "parent":"testpack:item/elem_deep_1"}""");
            for (int i = 1; i < 8; i++) {
                write(root, "assets/testpack/models/item/elem_deep_" + i + ".json",
                    "{\"parent\":\"testpack:item/elem_deep_" + (i + 1) + "\"}");
            }
            write(root, "assets/testpack/models/item/elem_deep_8.json", """
                {"display":{"gui":{"scale":[2,2,2]}}}""");
            delegatingItem(root, "deep", "testpack:item/elem_deep_0");

            // A pack item whose PATH contains "player_head": must render as an ordinary
            // elements model, never fall into the dedicated vanilla head pipeline.
            delegatingItem(root, "player_head_frame", "testpack:item/elem_flat");

            // Flat layer0 sprite items carrying tint sources: constant, custom_model_data and
            // an unsupported (dye) source over a white 2x2 texture.
            model(root, "white_layer0", "item/generated", "testpack:item/white");
            item(root, "sprite_constant_tint", """
                {"model":{"type":"model","model":"testpack:item/white_layer0",
                  "tints":[{"type":"minecraft:constant","value":16744448}]}}""");
            item(root, "sprite_cmd_tint", """
                {"model":{"type":"model","model":"testpack:item/white_layer0",
                  "tints":[{"type":"minecraft:custom_model_data","index":0,"default":16777215}]}}""");
            item(root, "sprite_dyed", """
                {"model":{"type":"model","model":"testpack:item/white_layer0",
                  "tints":[{"type":"minecraft:dye","default":3368703}]}}""");
            item(root, "sprite_team_tint", """
                {"model":{"type":"model","model":"testpack:item/white_layer0",
                  "tints":[{"type":"minecraft:team","default":0}]}}""");

            // Flat-sprite custom_model_data dispatch: the same select shape as "named", but over
            // layer0 sprite models instead of elements models.
            model(root, "red_layer0", "item/generated", "testpack:item/red");
            model(root, "blue_layer0", "item/generated", "testpack:item/blue");
            item(root, "sprite_named", """
                {"model":{"type":"select","property":"minecraft:custom_model_data","index":0,
                  "cases":[{"when":"ruby","model":{"type":"model","model":"testpack:item/red_layer0"}}],
                  "fallback":{"type":"model","model":"testpack:item/blue_layer0"}}}""");

            // Bare (namespace free) texture references: vanilla resolves them in the minecraft
            // namespace. The decoy under testpack/ catches any resolver that binds bare
            // references to the model's own namespace instead.
            textureAt(root, "minecraft", "bare", solid(2, 2, 0xFF00AA77));
            textureAt(root, NAMESPACE, "bare", solid(2, 2, 0xFF773311));
            model(root, "bare_layer0_model", "item/generated", "item/bare");
            delegatingItem(root, "bare_sprite", "testpack:item/bare_layer0_model");
            solidQuadModel(root, "elem_bare", "item/bare");
            delegatingItem(root, "bare_quad", "testpack:item/elem_bare");

            // Damage-driven dispatch: normalized (the vanilla default) and raw variants.
            item(root, "worn", """
                {"model":{"type":"range_dispatch","property":"minecraft:damage",
                  "entries":[{"threshold":0.25,"model":{"type":"model","model":"testpack:item/elem_green"}},
                             {"threshold":0.75,"model":{"type":"model","model":"testpack:item/elem_blue"}}],
                  "fallback":{"type":"model","model":"testpack:item/elem_gray"}}}""");
            item(root, "worn_raw", """
                {"model":{"type":"range_dispatch","property":"minecraft:damage","normalize":false,
                  "entries":[{"threshold":3.0,"model":{"type":"model","model":"testpack:item/elem_red"}}],
                  "fallback":{"type":"model","model":"testpack:item/elem_gray"}}}""");

            // Vanilla chain exits: claim the minecraft namespace WITHOUT shipping the builtin
            // flat templates (the real-pack shape - server packs override minecraft-namespace
            // assets while their models still end chains at item/generated or item/handheld).
            write(root, "assets/minecraft/models/item/filler.json", "{}");
            write(root, "assets/testpack/models/item/gen_child.json", """
                {"parent":"minecraft:item/generated",
                 "textures":{"layer0":"testpack:item/white"}}""");
            delegatingItem(root, "vanilla_exit", "testpack:item/gen_child");
            write(root, "assets/testpack/models/item/handheld_child.json", """
                {"parent":"minecraft:item/handheld",
                 "textures":{"layer0":"testpack:item/green"}}""");
            delegatingItem(root, "handheld_exit", "testpack:item/handheld_child");
            write(root, "assets/testpack/models/item/other_vanilla_child.json", """
                {"parent":"minecraft:item/other_template",
                 "textures":{"layer0":"testpack:item/white"}}""");
            delegatingItem(root, "vanilla_dead_end", "testpack:item/other_vanilla_child");
            write(root, "assets/testpack/models/item/gen_bare_child.json", """
                {"parent":"minecraft:item/generated"}""");
            delegatingItem(root, "generated_no_layer0", "testpack:item/gen_bare_child");

            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write elements fixture pack", e);
        }
    }

    /** A full-slot single-quad model (south face only) over one solid texture. */
    private static void solidQuadModel(Path root, String name, String textureRef) throws IOException {
        write(root, "assets/testpack/models/item/" + name + ".json",
            "{\"textures\":{\"all\":\"" + textureRef + "\"},"
                + "\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,1],"
                + "\"faces\":{\"south\":{\"texture\":\"#all\"}}}]}");
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

    /**
     * An item definition that plainly delegates to one model -
     * {@code {"model":{"type":"minecraft:model","model":"<modelRef>"}}} - the most common
     * fixture shape, shared so the boilerplate JSON is written exactly once.
     */
    private static void delegatingItem(Path root, String name, String modelRef) throws IOException {
        item(root, name, "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + modelRef + "\"}}");
    }

    /** The {@code pack.mcmeta} every fixture pack starts with; only the description varies. */
    private static void packMcmeta(Path root, String description) throws IOException {
        write(root, "pack.mcmeta", "{\"pack\":{\"pack_format\":88,\"description\":\"" + description + "\"}}");
    }

    private static void model(Path root, String name, String parent, String layer0) throws IOException {
        write(root, "assets/testpack/models/item/" + name + ".json",
            "{\"parent\":\"" + parent + "\",\"textures\":{\"layer0\":\"" + layer0 + "\"}}");
    }

    private static void texture(Path root, String name, BufferedImage image) throws IOException {
        textureAt(root, "testpack", name, image);
    }

    /** Like {@link #texture} but under an explicit namespace. */
    private static void textureAt(Path root, String namespace, String name, BufferedImage image) throws IOException {
        Path path = root.resolve("assets/" + namespace + "/textures/item/" + name + ".png");
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

    /** A solid-color image; public so renderer tests build synthetic textures the same way. */
    public static BufferedImage solid(int width, int height, int argb) {
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

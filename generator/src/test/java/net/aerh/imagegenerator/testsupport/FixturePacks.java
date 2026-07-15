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

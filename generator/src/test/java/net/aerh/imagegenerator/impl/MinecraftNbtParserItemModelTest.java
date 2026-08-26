package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static net.aerh.imagegenerator.testsupport.ImageAssertions.assertPixelsEqual;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The {@code minecraft:item_model} component path through {@link MinecraftNbtParser}: an item
 * carrying the component is addressed by its model definition rather than by its item id.
 */
class MinecraftNbtParserItemModelTest {

    /** The {@code minecraft:dyed_color} value used by the dye fixture below. */
    private static final int DYE_COLOR = 10511680;

    private static final String SKIN_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path packDir;

    private PackRepository repository;
    private PackId packId;

    @BeforeEach
    void registerFixturePack() {
        FixturePacks.writeDefaultPack(packDir);
        repository = new PackRepository();
        packId = repository.register("test:default", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
    }

    @Test
    void itemModelComponentAddressesTheModelInsteadOfTheItemId() {
        BufferedImage image = renderItem("""
            {"id":"minecraft:paper","components":{"minecraft:item_model":"testpack:item/simple"}}""");

        assertEquals(0xFFFF0000, image.getRGB(128, 128),
            "the item_model component must resolve testpack:item/simple (solid red), not the paper texture");
    }

    @Test
    void itemModelComponentIsExposedOnTheParseResult() {
        MinecraftNbtParser.ParsedNbt parsed = MinecraftNbtParser.parse("""
            {"id":"minecraft:paper","components":{"minecraft:item_model":"hypixel_skyblock:item/safari_belt"}}""");

        assertEquals("hypixel_skyblock:item/safari_belt", parsed.getParsedItemModel());
        assertEquals("minecraft:paper", parsed.getParsedItemId(), "the item id stays available alongside the model");
    }

    @Test
    void itemWithoutTheComponentKeepsTheItemIdRender() {
        String nbt = """
            {"id":"minecraft:paper","components":{}}""";

        assertNull(MinecraftNbtParser.parse(nbt).getParsedItemModel());
        assertPixelsEqual(vanillaRender("paper"), renderItem(nbt), "no item_model must render the paper item texture");
    }

    @Test
    void blankItemModelFallsBackToTheItemId() {
        MinecraftNbtParser.ParsedNbt parsed = MinecraftNbtParser.parse("""
            {"id":"minecraft:paper","components":{"minecraft:item_model":"   "}}""");

        assertNull(parsed.getParsedItemModel(), "a blank component must not address a model");
    }

    @Test
    void nonStringItemModelFallsBackToTheItemId() {
        MinecraftNbtParser.ParsedNbt parsed = MinecraftNbtParser.parse("""
            {"id":"minecraft:paper","components":{"minecraft:item_model":{"model":"testpack:item/simple"}}}""");

        assertNull(parsed.getParsedItemModel(), "a malformed component must not fail the parse");
    }

    @Test
    void itemModelRefIsExposedVerbatimSoTheGeneratorAppliesNamespaceDefaulting() {
        String nbt = """
            {"id":"minecraft:paper","components":{"minecraft:item_model":"diamond_sword"}}""";

        assertEquals("diamond_sword", MinecraftNbtParser.parse(nbt).getParsedItemModel(),
            "a bare ref is not rewritten by the parser");
        assertPixelsEqual(vanillaRender("diamond_sword"), renderItem(nbt),
            "a bare ref defaults to the minecraft namespace and falls back to the vanilla spritesheet");
    }

    @Test
    void playerHeadWithAnItemModelRendersTheModelRatherThanTheHead() {
        assertInstanceOf(MinecraftItemGenerator.Builder.class, visualBuilder(playerHeadNbt("testpack:item/simple")),
            "an item_model replaces the model vanilla would pick, head included");
        assertEquals(0xFFFF0000, renderItem(playerHeadNbt("testpack:item/simple")).getRGB(128, 128));
    }

    @Test
    void playerHeadWithAnItemModelKeepsTheProfileTextureForFallback() {
        MinecraftNbtParser.ParsedNbt parsed = MinecraftNbtParser.parse(playerHeadNbt("testpack:item/simple"));

        assertEquals(SKIN_HASH, parsed.getBase64Texture(),
            "callers that cannot resolve the model still need the head texture to fall back to");
    }

    @Test
    void playerHeadWithoutAnItemModelStillRendersTheHead() {
        assertInstanceOf(MinecraftPlayerHeadGenerator.Builder.class, visualBuilder(playerHeadNbt(null)),
            "the head render must be untouched when no item_model is present");
    }

    @Test
    void dyedItemWithAnItemModelKeepsBothTheModelAndTheDye() {
        String nbt = """
            {"id":"minecraft:leather_helmet","components":{
              "minecraft:item_model":"testpack:item/simple","minecraft:dyed_color":10511680}}""";

        assertEquals("testpack:item/simple", MinecraftNbtParser.parse(nbt).getParsedItemModel(),
            "the dye path must not drop the item model");

        BufferedImage dyedModel = new MinecraftItemGenerator.Builder()
            .withItemModel("testpack:item/simple")
            .withData(String.format("#%06X", DYE_COLOR))
            .withPack(packId)
            .withPackRepository(repository)
            .build()
            .generate()
            .getImage();

        assertPixelsEqual(dyedModel, renderItem(nbt), "the dyed render must come from the model, not the item id");
    }

    @Test
    void legacyTagFormatHasNoItemModel() {
        MinecraftNbtParser.ParsedNbt parsed = MinecraftNbtParser.parse("""
            {"id":"minecraft:paper","tag":{"display":{"Name":"Legacy"}}}""");

        assertNull(parsed.getParsedItemModel(), "the item_model component does not exist before 1.20.5");
    }

    /** A {@code player_head} carrying a profile texture, optionally with an item model too. */
    private static String playerHeadNbt(String itemModel) {
        String skin = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + SKIN_HASH + "\"}}}";
        String profile = Base64.getEncoder().encodeToString(skin.getBytes(StandardCharsets.UTF_8));
        String model = itemModel == null ? "" : "\"minecraft:item_model\":\"" + itemModel + "\",";

        return "{\"id\":\"minecraft:player_head\",\"components\":{" + model
            + "\"minecraft:profile\":{\"properties\":[{\"name\":\"textures\",\"value\":\"" + profile + "\"}]}}}";
    }

    /** Renders the item generator the parser produced against the fixture pack. */
    private BufferedImage renderItem(String nbt) {
        return itemBuilder(nbt).withPack(packId).withPackRepository(repository).build().generate().getImage();
    }

    /** Renders an item id straight from the vanilla spritesheet, bypassing the parser. */
    private BufferedImage vanillaRender(String itemId) {
        return new MinecraftItemGenerator.Builder()
            .withItem(itemId)
            .withPack(packId)
            .withPackRepository(repository)
            .build()
            .generate()
            .getImage();
    }

    private MinecraftItemGenerator.Builder itemBuilder(String nbt) {
        return assertInstanceOf(MinecraftItemGenerator.Builder.class, visualBuilder(nbt));
    }

    /** The generator the parser chose to draw the item itself with, ahead of the tooltip. */
    private ClassBuilder<? extends Generator> visualBuilder(String nbt) {
        for (ClassBuilder<? extends Generator> generator : MinecraftNbtParser.parse(nbt).getGenerators()) {
            if (!(generator instanceof MinecraftTooltipGenerator.Builder)) {
                return generator;
            }
        }
        throw new AssertionError("the parser produced no item generator for: " + nbt);
    }
}

package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.impl.MinecraftItemGenerator;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Choosing how an item is addressed: {@code item_id} and {@code item_model} are mutually
 * exclusive.
 */
class ItemAddressingTest {

    private static final String MODEL = "hypixel_skyblock:item/island_relevant/safari/safari_belt";

    @TempDir
    Path packDir;

    private PackRepository repository;
    private PackId packId;

    @BeforeEach
    void registerFixturePack() {
        FixturePacks.writeDefaultPack(packDir);
        repository = new PackRepository();
        packId = repository.register("test:pack", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
    }

    @Test
    void rejectsItemIdAndItemModelTogether() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> ItemAddressing.requireSingleItemAddress("paper", MODEL));

        assertEquals("The item_id and item_model options are mutually exclusive; use one or the other!",
            exception.getMessage());
    }

    @Test
    void acceptsEitherOptionAlone() {
        ItemAddressing.requireSingleItemAddress("paper", null);
        ItemAddressing.requireSingleItemAddress(null, MODEL);
        ItemAddressing.requireSingleItemAddress(null, null);
    }

    @Test
    void treatsBlankOptionsAsAbsent() {
        ItemAddressing.requireSingleItemAddress("  ", MODEL);
        ItemAddressing.requireSingleItemAddress("paper", "  ");
    }

    @Test
    void requiresOneOfTheTwoOptionsWhereTheItemIsTheWholeRender() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> ItemAddressing.requireAnItemAddress(null, "  "));

        assertEquals("Set either the item_id or the item_model option to pick what to render!", exception.getMessage());
        ItemAddressing.requireAnItemAddress("paper", null);
        ItemAddressing.requireAnItemAddress(null, MODEL);
    }

    // MinecraftItemGenerator exposes no getItemModel()/getItemId() accessors (no @Getter on the
    // class or its fields), so these two observe addressItem through the rendered pixels instead
    // of through builder.build().getItemModel()/getItemId() as the brief's snippet assumed.

    @Test
    void addressItemPrefersModelWhenPresent() {
        MinecraftItemGenerator.Builder builder = ItemAddressing.addressItem(
            new MinecraftItemGenerator.Builder().withPack(packId).withPackRepository(repository),
            "stone", "testpack:item/simple");

        BufferedImage image = builder.build().generate().getImage();

        assertEquals(0xFFFF0000, image.getRGB(128, 128), "the item_model address rendered the pack's red sprite, not stone");
    }

    @Test
    void addressItemFallsBackToItemId() {
        MinecraftItemGenerator.Builder builder = ItemAddressing.addressItem(new MinecraftItemGenerator.Builder(), "stone", " ");

        BufferedImage viaFallback = builder.build().generate().getImage();
        BufferedImage plainStone = new MinecraftItemGenerator.Builder().withItem("stone").build().generate().getImage();

        assertEquals(plainStone.getRGB(10, 10), viaFallback.getRGB(10, 10), "a blank item_model falls back to the item_id");
    }

    @Test
    void stripMinecraftNamespaceOnlyStripsTheDefaultNamespace() {
        assertEquals("paper", ItemAddressing.stripMinecraftNamespace("minecraft:paper"));
        assertEquals("paper", ItemAddressing.stripMinecraftNamespace("paper"));
        assertEquals("hypixel_skyblock:item/x", ItemAddressing.stripMinecraftNamespace("hypixel_skyblock:item/x"));
    }
}

package net.aerh.imagegenerator.tooling.minecraft;

import lib.minecraft.renderer.call.result.AtlasResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemIconRendererTest {

    private static final int RED = 0xffff0000;
    private static final int GREEN = 0xff00ff00;

    @TempDir
    Path output;

    @Test
    void itemIdsComeFromTheItemDefinitionsInTheJar() throws IOException {
        Path jar = output.resolve("client.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String entry : List.of(
                "assets/minecraft/items/stone.json",
                "assets/minecraft/items/chest.json",
                "assets/minecraft/items/acacia_boat.json",
                "assets/minecraft/items/README.txt",
                "assets/minecraft/items/nested/not_an_item.json",
                "assets/minecraft/models/item/stone.json",
                "assets/minecraft/models/item/iron_helmet_gold_trim.json",
                "assets/minecraft/textures/item/stone.png"
            )) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.closeEntry();
            }
        }

        assertEquals(List.of("minecraft:acacia_boat", "minecraft:chest", "minecraft:stone"), ItemIconRenderer.itemIdsFromJar(jar));
    }

    @Test
    void aJarWithoutItemDefinitionsIsAnError() throws IOException {
        Path jar = output.resolve("old-client.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("assets/minecraft/models/item/stone.json"));
            zip.closeEntry();
        }

        IOException error = assertThrows(IOException.class, () -> ItemIconRenderer.itemIdsFromJar(jar));
        assertTrue(error.getMessage().contains("assets/minecraft/items/"), error.getMessage());
    }

    @Test
    void vanillaIdsDropTheNamespace() {
        assertEquals("acacia_boat.png", ItemIconRenderer.iconFileName("minecraft:acacia_boat"));
    }

    @Test
    void otherNamespacesStayInTheFileName() {
        assertEquals("pack_fancy_sword.png", ItemIconRenderer.iconFileName("pack:fancy_sword"));
        assertEquals("bare.png", ItemIconRenderer.iconFileName("bare"));
    }

    @Test
    void batchesSplitIdsInOrderWithAShortTail() {
        List<List<String>> batches = ItemIconRenderer.batches(List.of("a", "b", "c", "d", "e"), 2);

        assertEquals(List.of(List.of("a", "b"), List.of("c", "d"), List.of("e")), batches);
        assertEquals(List.of(), ItemIconRenderer.batches(List.of(), 2));
    }

    @Test
    void batchSizeMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> ItemIconRenderer.batches(List.of("a"), 0));
    }

    @Test
    void tilesAreSlicedIntoOneFilePerId() throws IOException {
        BufferedImage sheet = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        fill(sheet, 0, 0, 2, 2, RED);
        fill(sheet, 2, 0, 2, 2, GREEN);
        List<AtlasResult.Tile> tiles = List.of(
            tile("minecraft:red_dye", AtlasResult.Tile.Kind.ITEM, AtlasResult.Tile.Source.BLOCK_MODEL, 0, 0),
            tile("minecraft:green_block", AtlasResult.Tile.Kind.BLOCK, AtlasResult.Tile.Source.BLOCK_MODEL, 1, 0)
        );

        int written = ItemIconRenderer.writeTiles(sheet, tiles, output);

        assertEquals(2, written);
        BufferedImage red = ImageIO.read(output.resolve("red_dye.png").toFile());
        BufferedImage green = ImageIO.read(output.resolve("green_block.png").toFile());
        assertEquals(2, red.getWidth());
        assertEquals(RED, red.getRGB(1, 1));
        assertEquals(GREEN, green.getRGB(0, 0));
    }

    @Test
    void emptyTilesAreNotWritten() throws IOException {
        BufferedImage sheet = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        List<AtlasResult.Tile> tiles = List.of(
            tile("minecraft:air", AtlasResult.Tile.Kind.ITEM, AtlasResult.Tile.Source.EMPTY, 0, 0)
        );

        assertEquals(0, ItemIconRenderer.writeTiles(sheet, tiles, output));
        assertFalse(Files.exists(output.resolve("air.png")));
    }

    @Test
    void tilesOutsideTheSheetAreAnError() {
        BufferedImage sheet = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        List<AtlasResult.Tile> tiles = List.of(
            tile("minecraft:stone", AtlasResult.Tile.Kind.BLOCK, AtlasResult.Tile.Source.BLOCK_MODEL, 1, 0)
        );

        IOException error = assertThrows(IOException.class, () -> ItemIconRenderer.writeTiles(sheet, tiles, output));
        assertTrue(error.getMessage().contains("minecraft:stone"), error.getMessage());
    }

    private static AtlasResult.Tile tile(String id, AtlasResult.Tile.Kind kind, AtlasResult.Tile.Source source, int col, int row) {
        return new AtlasResult.Tile(id, kind, source, col, row, col * 2, row * 2, 2, 2, List.of());
    }

    private static void fill(BufferedImage image, int x0, int y0, int width, int height, int argb) {
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                image.setRGB(x, y, argb);
            }
        }
    }
}

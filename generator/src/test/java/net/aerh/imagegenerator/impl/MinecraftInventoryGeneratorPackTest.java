package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.item.GeneratedObject;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftInventoryGeneratorPackTest {

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
    void inventoryRendersPackItemInSlot() {
        // NOTE: the DSL requires "material:slot" order (InventoryStringParser.findSlotSeparatorIndex
        // skips any colon followed by a letter, treating it as part of a namespaced ID like
        // "testpack:item/simple"). A "slot:material" fixture such as "1:testpack:item/simple" has no
        // colon followed by a digit, so the parser reports a missing slot separator. See
        // InventoryStringParserTest#namespacedPackItemRefRequiresMaterialFirstOrder for a parser-level
        // regression test documenting this.
        GeneratedObject inventory = new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(1)
            .withPack(packId)
            .withPackRepository(repository)
            .withInventoryString("testpack:item/simple:1")
            .build()
            .generate();
        BufferedImage image = inventory.getImage();
        assertNotNull(image);
        boolean foundRed = false;
        for (int y = 0; y < image.getHeight() && !foundRed; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == 0xFFFF0000) {
                    foundRed = true;
                    break;
                }
            }
        }
        assertTrue(foundRed, "pack sprite (solid red) appears in the rendered inventory");
    }

    @Test
    void inventoryWithoutPackStillRendersVanilla() {
        GeneratedObject inventory = new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(1)
            .withInventoryString("stone:1")
            .build()
            .generate();
        assertNotNull(inventory.getImage());
    }

    /**
     * Pins the retained 7-arg public constructor: it must keep delegating to the extended
     * constructor with a null pack (vanilla rendering). Guards against a future refactor
     * silently breaking the delegation line.
     */
    @Test
    void legacySevenArgConstructorRendersVanilla() {
        MinecraftInventoryGenerator generator =
            new MinecraftInventoryGenerator(1, 1, null, "stone:1", true, true, false);
        GeneratedObject inventory = generator.generate();
        BufferedImage image = inventory.getImage();
        assertNotNull(image);
        assertTrue(image.getWidth() > 0, "rendered inventory has a positive width");
        assertTrue(image.getHeight() > 0, "rendered inventory has a positive height");
    }
}

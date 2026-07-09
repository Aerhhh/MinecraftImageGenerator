package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.item.InventoryItem;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins within-render deduplication of identical slot items. Individually specified duplicates
 * ("iron_block,enchant:1%%iron_block,enchant:2%%...") must cost one item generation, not one
 * per slot; without dedupe, each duplicate re-runs the full effect pipeline (182 glint frames
 * at item resolution), which is what made individually-specified enchanted inventories time
 * out while the equivalent bulk range ("iron_block,enchant:[1-2]") rendered fine.
 */
class MinecraftInventoryGeneratorSlotDedupeTest {

    /**
     * Characterization golden: the individual and bulk forms of the same inventory must stay
     * pixel-identical. This held before dedupe existed (both forms already produced the same
     * pixels, just at very different cost) and guards that dedupe never changes output.
     */
    @Test
    void individuallySpecifiedDuplicatesRenderIdenticalToBulkRange() {
        GeneratedObject individual = new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(2)
            .withInventoryString("iron_block,enchant:1%%iron_block,enchant:2")
            .withAnimateGlint(true)
            .build()
            .generate();

        GeneratedObject bulk = new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(2)
            .withInventoryString("iron_block,enchant:[1-2]")
            .withAnimateGlint(true)
            .build()
            .generate();

        assertEquals(bulk.getAnimationFrames().size(), individual.getAnimationFrames().size());
        assertEquals(bulk.getFrameDelayMs(), individual.getFrameDelayMs());

        for (int frameIndex = 0; frameIndex < bulk.getAnimationFrames().size(); frameIndex++) {
            ImageAssertions.assertPixelsEqual(bulk.getAnimationFrames().get(frameIndex),
                individual.getAnimationFrames().get(frameIndex), "frame " + frameIndex);
        }
    }

    @Test
    void duplicateSlotItemsShareOneGeneratedVisual() {
        MinecraftInventoryGenerator generator = new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(2)
            .withInventoryString("iron_block,enchant:1%%iron_block,enchant:2")
            .withAnimateGlint(true)
            .build();

        InventoryItem first = new InventoryItem(1, 1, "iron_block", "enchant", null);
        InventoryItem second = new InventoryItem(2, 1, "iron_block", "enchant", null);
        generator.processItem(first, null);
        generator.processItem(second, null);

        assertNotNull(first.getAnimationFrames());
        assertSame(first.getAnimationFrames(), second.getAnimationFrames(),
            "duplicate item specs reuse the generated animation frames instead of regenerating");
        assertSame(first.getItemImage(), second.getItemImage(),
            "duplicate item specs reuse the generated item image");
        assertEquals(first.getFrameDelayMs(), second.getFrameDelayMs());
    }

    /**
     * Paired guard: same material with different modifiers or durability is a different visual
     * and must never collide in the dedupe key.
     */
    @Test
    void distinctSlotItemSpecsDoNotShareVisuals() {
        MinecraftInventoryGenerator generator = new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(3)
            .withInventoryString("iron_block,enchant:1%%iron_block:2%%iron_block,50:3")
            .withAnimateGlint(true)
            .build();

        InventoryItem enchanted = new InventoryItem(1, 1, "iron_block", "enchant", null);
        InventoryItem plain = new InventoryItem(2, 1, "iron_block", null, null);
        InventoryItem damaged = new InventoryItem(3, 1, "iron_block", null, 50);
        generator.processItem(enchanted, null);
        generator.processItem(plain, null);
        generator.processItem(damaged, null);

        assertNotNull(enchanted.getAnimationFrames(), "enchanted item renders animated glint frames");
        assertNull(plain.getAnimationFrames(), "plain item renders statically");
        assertNotSame(enchanted.getItemImage(), plain.getItemImage(),
            "different modifiers must not share a cached visual");
        assertNotSame(plain.getItemImage(), damaged.getItemImage(),
            "different durability must not share a cached visual");
    }
}

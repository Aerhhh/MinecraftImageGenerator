package net.aerh.imagegenerator.parser.inventory;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.item.InventoryItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryStringParserTest {

    private final InventoryStringParser parser = new InventoryStringParser(9);

    @Test
    void materialFirstSlotLastIsTheSupportedOrder() {
        ArrayList<InventoryItem> items = parser.parse("stone:1");
        assertEquals(1, items.size());
        assertEquals("stone", items.getFirst().getItemName());
        assertArrayEquals(new int[] {1}, items.getFirst().getSlot());
    }

    /**
     * {@link InventoryStringParser#findSlotSeparatorIndex} skips any {@code :} whose next
     * non-whitespace character is a letter, treating it as part of a namespaced ID (its own doc
     * comment gives {@code minecraft:stone} as the example). That heuristic supports a namespaced
     * pack item reference as the material component (e.g. {@code testpack:item/simple}) as long as
     * the numeric slot component comes last: {@code testpack:item/simple:1}. This is documented
     * behavior, not a bug; it is why {@code MinecraftInventoryGeneratorPackTest} builds its DSL
     * fixture as "material:slot" rather than "slot:material".
     */
    @Test
    void namespacedPackItemRefRequiresMaterialFirstOrder() {
        ArrayList<InventoryItem> items = parser.parse("testpack:item/simple:1");
        assertEquals(1, items.size());
        assertEquals("testpack:item/simple", items.getFirst().getItemName());
        assertArrayEquals(new int[] {1}, items.getFirst().getSlot());
    }

    /**
     * Documents the limitation: a "slot:material" ordering (slot first) is NOT supported when the
     * material contains a namespace, because every {@code :} in the string is followed by a letter
     * and none is ever recognized as the slot separator. This is the exact shape that broke the
     * original "1:testpack:item/simple" fixture proposed for Task 14.
     */
    @Test
    void slotFirstOrderingWithNamespacedMaterialHasNoValidSeparator() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> parser.parse("1:testpack:item/simple"));
        assertEquals(
            "Incorrect amount of components present in item: `1:testpack:item/simple` "
                + "(missing a valid slot separator `:`)",
            exception.getMessage());
    }

    /**
     * Even without a namespace, "slot:material" ordering fails for the same reason: the only
     * {@code :} is followed by a letter ("stone" starts with 's'), so it is skipped as a suspected
     * namespace separator and no slot separator is ever found.
     */
    @Test
    void slotFirstOrderingWithPlainMaterialHasNoValidSeparator() {
        assertThrows(GeneratorException.class, () -> parser.parse("1:stone"));
    }
}

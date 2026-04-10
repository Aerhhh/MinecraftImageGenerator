package net.aerh.jigsaw.core.generator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link InventoryStringParser} and {@link InventoryRequest.Builder#withInventoryString(String)}.
 */
class InventoryStringParserTest {

    private static final int TOTAL_SLOTS = 54; // 6x9

    private InventoryStringParser parser() {
        return new InventoryStringParser(TOTAL_SLOTS);
    }

    // --- Basic parsing ---

    @Test
    void parse_nullInputThrows() {
        assertThatThrownBy(() -> parser().parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void parse_blankInputReturnsEmptyList() {
        assertThat(parser().parse("   ")).isEmpty();
    }

    @Test
    void parse_simpleToken_singleSlot() {
        List<InventoryItem> items = parser().parse("diamond_sword:1");
        assertThat(items).hasSize(1);
        InventoryItem item = items.getFirst();
        assertThat(item.itemId()).isEqualTo("diamond_sword");
        assertThat(item.slot()).isEqualTo(0); // 1-indexed in string, 0-indexed in record
        assertThat(item.stackCount()).isEqualTo(1);
    }

    @Test
    void parse_simpleToken_slotWithAmount() {
        List<InventoryItem> items = parser().parse("arrow:5,32");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().slot()).isEqualTo(4); // slot 5 -> index 4
        assertThat(items.getFirst().stackCount()).isEqualTo(32);
    }

    // --- Multi-token (%%  separator) ---

    @Test
    void parse_multipleTokensProduceMultipleItems() {
        List<InventoryItem> items = parser().parse("diamond_sword:1 %% arrow:5");
        assertThat(items).hasSize(2);
    }

    @Test
    void parse_emptyTokensAreSkipped() {
        List<InventoryItem> items = parser().parse("diamond_sword:1 %%  %% arrow:2");
        assertThat(items).hasSize(2);
    }

    // --- Array slot spec ---

    @Test
    void parse_arraySlot_multipleSlots() {
        // [1,3,5] places in slots 1, 3, 5 (0-indexed: 0, 2, 4)
        List<InventoryItem> items = parser().parse("stone:[1,3,5]");
        assertThat(items).hasSize(3);
        assertThat(items.stream().map(InventoryItem::slot)).containsExactlyInAnyOrder(0, 2, 4);
    }

    @Test
    void parse_arraySlot_uniformAmount() {
        List<InventoryItem> items = parser().parse("stone:[1,2,3]16");
        assertThat(items).allSatisfy(item -> assertThat(item.stackCount()).isEqualTo(16));
    }

    // --- Map slot spec ---

    @Test
    void parse_mapSlot_assignsDifferentAmountsPerSlot() {
        // {1:5,2:10} -> slot 1 with amount 5, slot 2 with amount 10
        List<InventoryItem> items = parser().parse("stone:{1:5,2:10}");
        assertThat(items).hasSize(2);

        InventoryItem slot1 = items.stream().filter(i -> i.slot() == 0).findFirst().orElseThrow();
        InventoryItem slot2 = items.stream().filter(i -> i.slot() == 1).findFirst().orElseThrow();

        assertThat(slot1.stackCount()).isEqualTo(5);
        assertThat(slot2.stackCount()).isEqualTo(10);
    }

    // --- Slot range ---

    @Test
    void parse_slotRange_inArraySyntax() {
        // [1-3] expands to slots 1, 2, 3 (0-indexed: 0, 1, 2)
        List<InventoryItem> items = parser().parse("stone:[1-3]");
        assertThat(items).hasSize(3);
        assertThat(items.stream().map(InventoryItem::slot)).containsExactlyInAnyOrder(0, 1, 2);
    }

    @Test
    void parse_slotRange_inMapSyntax() {
        List<InventoryItem> items = parser().parse("stone:{1-3:4}");
        assertThat(items).hasSize(3);
        assertThat(items).allSatisfy(item -> assertThat(item.stackCount()).isEqualTo(4));
    }

    // --- Enchanted modifier ---

    @Test
    void parse_enchantModifier_setsEnchantedTrue() {
        List<InventoryItem> items = parser().parse("diamond_sword,enchant:1");
        assertThat(items.getFirst().enchanted()).isTrue();
    }

    @Test
    void parse_noEnchantModifier_enchantedIsFalse() {
        List<InventoryItem> items = parser().parse("diamond_sword:1");
        assertThat(items.getFirst().enchanted()).isFalse();
    }

    // --- Durability modifier ---

    @Test
    void parse_durabilityModifier_setsPercent() {
        List<InventoryItem> items = parser().parse("diamond_pickaxe,50:1");
        assertThat(items.getFirst().durabilityPercent()).isPresent();
        assertThat(items.getFirst().durabilityPercent().get()).isEqualTo(50.0);
    }

    @Test
    void parse_durabilityClampedToZeroMin() {
        // -50 is parsed as an integer, clamped to the minimum of 0
        List<InventoryItem> items = parser().parse("diamond_pickaxe,-50:1");
        assertThat(items.getFirst().durabilityPercent()).isPresent();
        assertThat(items.getFirst().durabilityPercent().get()).isEqualTo(0.0);
    }

    @Test
    void parse_durabilityClampedToHundredMax() {
        List<InventoryItem> items = parser().parse("diamond_pickaxe,150:1");
        assertThat(items.getFirst().durabilityPercent()).isPresent();
        assertThat(items.getFirst().durabilityPercent().get()).isEqualTo(100.0);
    }

    // --- Amount clamping ---

    @Test
    void parse_amountClampedToMax64() {
        List<InventoryItem> items = parser().parse("stone:1,999");
        assertThat(items.getFirst().stackCount()).isEqualTo(64);
    }

    @Test
    void parse_amountClampedToMin1() {
        List<InventoryItem> items = parser().parse("stone:1,0");
        assertThat(items.getFirst().stackCount()).isEqualTo(1);
    }

    // --- Slot clamping ---

    @Test
    void parse_slotClampedToTotalSlots() {
        // Slot 999 should clamp to totalSlots (54)
        List<InventoryItem> items = parser().parse("stone:999");
        assertThat(items.getFirst().slot()).isEqualTo(TOTAL_SLOTS - 1); // 54 -> index 53
    }

    // --- Missing slot separator ---

    @Test
    void parse_missingSlotSeparatorThrows() {
        assertThatThrownBy(() -> parser().parse("diamond_sword"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- withInventoryString integration ---

    @Test
    void withInventoryString_addsItemsToBuilder() {
        InventoryRequest request = InventoryRequest.builder()
                .rows(6)
                .slotsPerRow(9)
                .withInventoryString("diamond_sword:1 %% arrow:5,16")
                .build();

        assertThat(request.items()).hasSize(2);
    }

    @Test
    void withInventoryString_blankStringProducesNoItems() {
        InventoryRequest request = InventoryRequest.builder()
                .rows(6)
                .slotsPerRow(9)
                .withInventoryString("   ")
                .build();

        assertThat(request.items()).isEmpty();
    }

    @Test
    void withInventoryString_nullThrows() {
        assertThatThrownBy(() -> InventoryRequest.builder()
                .rows(6)
                .slotsPerRow(9)
                .withInventoryString(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withInventoryString_canBeCalledMultipleTimes() {
        InventoryRequest request = InventoryRequest.builder()
                .rows(6)
                .slotsPerRow(9)
                .withInventoryString("diamond_sword:1")
                .withInventoryString("arrow:2")
                .build();

        assertThat(request.items()).hasSize(2);
    }

    @Test
    void withInventoryString_namespacedIdIsPreserved() {
        List<InventoryItem> items = parser().parse("minecraft:stone:1");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().itemId()).isEqualTo("minecraft:stone");
    }
}

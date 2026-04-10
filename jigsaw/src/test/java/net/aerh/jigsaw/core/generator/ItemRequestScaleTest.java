package net.aerh.jigsaw.core.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemRequestScaleTest {

    @Test
    void withInheritedScale_appliesWhenDefault() {
        ItemRequest request = ItemRequest.builder().itemId("diamond_sword").build();

        ItemRequest inherited = (ItemRequest) request.withInheritedScale(4);
        assertThat(inherited.scale()).isEqualTo(4);
        assertThat(inherited.itemId()).isEqualTo("diamond_sword");
    }

    @Test
    void withInheritedScale_preservesExplicitScale() {
        ItemRequest request = ItemRequest.builder().itemId("diamond_sword").scale(8).build();

        ItemRequest inherited = (ItemRequest) request.withInheritedScale(4);
        assertThat(inherited.scale()).isEqualTo(8);
    }

    @Test
    void withInheritedScale_preservesAllOtherFields() {
        ItemRequest request = ItemRequest.builder()
                .itemId("leather_chestplate")
                .enchanted(true)
                .hovered(true)
                .durabilityPercent(0.5)
                .dyeColor(0xFF0000)
                .build();

        ItemRequest inherited = (ItemRequest) request.withInheritedScale(4);
        assertThat(inherited.scale()).isEqualTo(4);
        assertThat(inherited.itemId()).isEqualTo("leather_chestplate");
        assertThat(inherited.enchanted()).isTrue();
        assertThat(inherited.hovered()).isTrue();
        assertThat(inherited.durabilityPercent()).hasValue(0.5);
        assertThat(inherited.dyeColor()).hasValue(0xFF0000);
    }
}

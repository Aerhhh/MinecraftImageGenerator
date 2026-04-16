package net.aerh.jigsaw.core.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryRequestScaleTest {

    @Test
    void withInheritedScale_appliesWhenDefault() {
        InventoryRequest request = InventoryRequest.builder().build();

        InventoryRequest inherited = (InventoryRequest) request.withInheritedScale(4);
        assertThat(inherited.scale()).isEqualTo(4);
    }

    @Test
    void withInheritedScale_preservesExplicitScale() {
        InventoryRequest request = InventoryRequest.builder().scale(8).build();

        InventoryRequest inherited = (InventoryRequest) request.withInheritedScale(4);
        assertThat(inherited.scale()).isEqualTo(8);
    }
}

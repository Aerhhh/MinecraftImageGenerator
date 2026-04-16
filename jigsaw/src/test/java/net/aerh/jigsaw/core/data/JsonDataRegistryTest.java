package net.aerh.jigsaw.core.data;

import net.aerh.jigsaw.api.data.RegistryKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonDataRegistryTest {

    record TestItem(String name, int value) {}

    private static final RegistryKey<TestItem> KEY = RegistryKey.of("test_items", TestItem.class);

    private JsonDataRegistry<TestItem> registry;

    @BeforeEach
    void setUp() {
        registry = new JsonDataRegistry<>(KEY, TestItem[].class, "test_data.json", TestItem::name);
    }

    @Test
    void loadsItemsFromJson() {
        assertThat(registry.size()).isEqualTo(3);
    }

    @Test
    void getByExactKey() {
        assertThat(registry.get("alpha")).isPresent()
                .hasValueSatisfying(item -> {
                    assertThat(item.name()).isEqualTo("alpha");
                    assertThat(item.value()).isEqualTo(1);
                });
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertThat(registry.get("ALPHA")).isPresent();
        assertThat(registry.get("Alpha")).isPresent();
        assertThat(registry.get("BETA")).isPresent();
    }

    @Test
    void unknownKeyReturnsEmpty() {
        assertThat(registry.get("nonexistent")).isEmpty();
    }

    @Test
    void valuesReturnsAllItems() {
        assertThat(registry.values()).hasSize(3);
    }

    @Test
    void keyReturnsRegistryKey() {
        assertThat(registry.key()).isEqualTo(KEY);
    }

    @Test
    void registerAddsItem() {
        TestItem extra = new TestItem("delta", 4);
        registry.register("delta", extra);
        assertThat(registry.size()).isEqualTo(4);
        assertThat(registry.get("delta")).isPresent().hasValue(extra);
    }

    @Test
    void registerOverwritesExisting() {
        TestItem replacement = new TestItem("alpha", 99);
        registry.register("alpha", replacement);
        assertThat(registry.get("alpha")).isPresent()
                .hasValueSatisfying(item -> assertThat(item.value()).isEqualTo(99));
        assertThat(registry.size()).isEqualTo(3);
    }

    @Test
    void isEmptyReturnsFalseWhenLoaded() {
        assertThat(registry.isEmpty()).isFalse();
    }
}

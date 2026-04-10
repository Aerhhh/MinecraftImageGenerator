package net.aerh.jigsaw.api.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistryKeyTest {

    @Test
    void registryKey_equalityOnSameNameAndType() {
        RegistryKey<String> key1 = RegistryKey.of("items", String.class);
        RegistryKey<String> key2 = RegistryKey.of("items", String.class);
        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    void registryKey_differentNamesAreNotEqual() {
        RegistryKey<String> key1 = RegistryKey.of("items", String.class);
        RegistryKey<String> key2 = RegistryKey.of("blocks", String.class);
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void registryKey_propertiesAccessible() {
        RegistryKey<Integer> key = RegistryKey.of("counts", Integer.class);
        assertThat(key.name()).isEqualTo("counts");
        assertThat(key.type()).isEqualTo(Integer.class);
    }

    @Test
    void registryKey_nullNameThrowsNullPointerException() {
        assertThatThrownBy(() -> RegistryKey.of(null, String.class))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    void registryKey_nullTypeThrowsNullPointerException() {
        assertThatThrownBy(() -> RegistryKey.of("items", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
    }
}

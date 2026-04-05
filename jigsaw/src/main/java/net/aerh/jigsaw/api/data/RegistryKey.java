package net.aerh.jigsaw.api.data;

import java.util.Objects;

public record RegistryKey<T>(String name, Class<T> type) {

    public RegistryKey {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    public static <T> RegistryKey<T> of(String name, Class<T> type) {
        return new RegistryKey<>(name, type);
    }
}

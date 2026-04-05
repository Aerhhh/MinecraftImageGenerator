package net.aerh.jigsaw.api.data;

import java.util.Collection;
import java.util.Optional;

public interface DataRegistry<T> {

    Optional<T> get(String id);

    Collection<T> values();

    RegistryKey<T> key();

    void register(String id, T value);

    int size();

    boolean isEmpty();
}

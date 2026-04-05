package net.aerh.jigsaw.core.nbt;

import net.aerh.jigsaw.spi.NbtFormatHandler;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link ServiceLoader} can discover all registered {@link NbtFormatHandler}
 * implementations via the META-INF/services file.
 */
class ServiceLoaderDiscoveryTest {

    @Test
    void serviceLoader_discoversAtLeastOneNbtFormatHandler() {
        ServiceLoader<NbtFormatHandler> loader = ServiceLoader.load(NbtFormatHandler.class);
        long count = StreamSupport.stream(loader.spliterator(), false).count();
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void serviceLoader_discoversComponentsHandler() {
        ServiceLoader<NbtFormatHandler> loader = ServiceLoader.load(NbtFormatHandler.class);
        boolean found = StreamSupport.stream(loader.spliterator(), false)
                .anyMatch(h -> h.getClass().getName()
                        .equals("net.aerh.jigsaw.core.nbt.handler.ComponentsNbtFormatHandler"));
        assertThat(found).isTrue();
    }

    @Test
    void serviceLoader_discoversPostFlatteningHandler() {
        ServiceLoader<NbtFormatHandler> loader = ServiceLoader.load(NbtFormatHandler.class);
        boolean found = StreamSupport.stream(loader.spliterator(), false)
                .anyMatch(h -> h.getClass().getName()
                        .equals("net.aerh.jigsaw.core.nbt.handler.PostFlatteningNbtFormatHandler"));
        assertThat(found).isTrue();
    }

    @Test
    void serviceLoader_discoversPreFlatteningHandler() {
        ServiceLoader<NbtFormatHandler> loader = ServiceLoader.load(NbtFormatHandler.class);
        boolean found = StreamSupport.stream(loader.spliterator(), false)
                .anyMatch(h -> h.getClass().getName()
                        .equals("net.aerh.jigsaw.core.nbt.handler.PreFlatteningNbtFormatHandler"));
        assertThat(found).isTrue();
    }

    @Test
    void serviceLoader_discoversDefaultHandler() {
        ServiceLoader<NbtFormatHandler> loader = ServiceLoader.load(NbtFormatHandler.class);
        boolean found = StreamSupport.stream(loader.spliterator(), false)
                .anyMatch(h -> h.getClass().getName()
                        .equals("net.aerh.jigsaw.core.nbt.handler.DefaultNbtFormatHandler"));
        assertThat(found).isTrue();
    }

    @Test
    void serviceLoader_allDiscoveredHandlersHaveNonNullId() {
        ServiceLoader<NbtFormatHandler> loader = ServiceLoader.load(NbtFormatHandler.class);
        StreamSupport.stream(loader.spliterator(), false)
                .forEach(h -> assertThat(h.id()).isNotNull().isNotBlank());
    }
}

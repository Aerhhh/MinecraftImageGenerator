package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackRepositoryTest {

    @TempDir
    Path packDir;

    private PackRepository repository;

    @BeforeEach
    void setUp() {
        FixturePacks.writeDefaultPack(packDir);
        repository = new PackRepository();
    }

    private PackSource fixtureSource() {
        return PackSource.directory(packDir, PackLimits.fromSystemProperties());
    }

    @Test
    void registersAndResolves() {
        PackId id = repository.register("test:pack", fixtureSource());
        assertEquals(PackId.parse("test:pack"), id);
        assertTrue(repository.resolve(id, "testpack:item/simple").isPresent());
        assertEquals(Set.of(id), repository.registeredPacks());
    }

    @Test
    void unknownItemInRegisteredPackResolvesEmpty() {
        PackId id = repository.register("test:pack", fixtureSource());
        assertEquals(Optional.empty(), repository.resolve(id, "testpack:item/nope"));
    }

    @Test
    void rejectsDuplicateRegistration() {
        repository.register("test:pack", fixtureSource());
        assertThrows(IllegalArgumentException.class, () -> repository.register("test:pack", fixtureSource()));
    }

    @Test
    void rejectsReservedVanillaId() {
        assertThrows(IllegalArgumentException.class, () -> repository.register("minecraft:minecraft", fixtureSource()));
    }

    @Test
    void resolvingUnregisteredPackThrows() {
        assertThrows(PackResolveException.class,
            () -> repository.resolve(PackId.parse("no:pack"), "testpack:item/simple"));
    }

    @Test
    void globalReturnsStableInstance() {
        assertSame(PackRepository.global(), PackRepository.global());
    }

    @Test
    void resolvesTooltipStylesThroughRegisteredPack() {
        PackId id = repository.register("test:pack", fixtureSource());
        assertTrue(repository.resolveTooltipSprites(id, "testpack:fancy").isPresent());
        assertTrue(repository.tooltipStyles(id).contains("testpack:fancy"));
        assertEquals(Optional.empty(), repository.resolveDefaultTooltipSprites(id),
            "fixture pack does not override the vanilla default tooltip");
    }

    @Test
    void tooltipResolutionOnUnregisteredPackThrows() {
        PackId id = PackId.parse("no:pack");
        assertThrows(PackResolveException.class, () -> repository.resolveTooltipSprites(id, "testpack:fancy"));
        assertThrows(PackResolveException.class, () -> repository.resolveDefaultTooltipSprites(id));
        assertThrows(PackResolveException.class, () -> repository.tooltipStyles(id));
    }

    @Test
    void registerNullSourceThrowsNpe() {
        assertThrows(NullPointerException.class, () -> repository.register("x:y", null));
    }

    @Test
    void registerWithCustomLimitsGovernsTextureDecodeForThatPack() {
        // maxTextureDim=8 is smaller than the fixture pack's 16x16 "simple" item texture: proves
        // the limits passed to THIS register() call - not the global default - reach the pack's
        // texture decode path.
        PackLimits tinyTextureLimits = new PackLimits(100, 1024, 8, 1024);
        PackSource source = PackSource.directory(packDir, tinyTextureLimits);
        PackId id = repository.register("test:tinylimits", source, tinyTextureLimits);
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> repository.resolve(id, "testpack:item/simple"));
        assertTrue(exception.getMessage().contains("simple"));
    }

    @Test
    void twoArgRegisterDelegatesToSystemPropertyDefaults() {
        PackId id = repository.register("test:pack", fixtureSource());
        assertTrue(repository.resolve(id, "testpack:item/simple").isPresent(),
            "16x16 fixture texture resolves fine under the default 1024px maxTextureDim");
    }

    @Test
    void duplicateRegistrationClosesTheLosingSource() {
        repository.register("test:pack", fixtureSource());
        AtomicInteger closeCount = new AtomicInteger();
        PackSource losingSource = new CountingCloseSource(fixtureSource(), closeCount);
        assertThrows(IllegalArgumentException.class, () -> repository.register("test:pack", losingSource));
        assertEquals(1, closeCount.get());
    }

    @Test
    void reservedVanillaIdRegistrationClosesTheSource() {
        AtomicInteger closeCount = new AtomicInteger();
        PackSource source = new CountingCloseSource(fixtureSource(), closeCount);
        assertThrows(IllegalArgumentException.class, () -> repository.register("minecraft:minecraft", source));
        assertEquals(1, closeCount.get());
    }

    @Test
    void concurrentRegistrationHasExactlyOneWinner() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> attempt = () -> {
                PackSource source = fixtureSource();
                assertTrue(start.await(10, TimeUnit.SECONDS), "start latch timed out");
                try {
                    repository.register("race:pack", source);
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            start.countDown();
            int successes = 0;
            if (first.get(10, TimeUnit.SECONDS)) {
                successes++;
            }
            if (second.get(10, TimeUnit.SECONDS)) {
                successes++;
            }
            assertEquals(1, successes);
            assertEquals(Set.of(PackId.parse("race:pack")), repository.registeredPacks());
        } finally {
            executor.shutdownNow();
        }
    }

    /** Delegating PackSource that counts close() calls, for asserting failed-registration cleanup. */
    private static final class CountingCloseSource implements PackSource {

        private final PackSource delegate;
        private final AtomicInteger closeCount;

        CountingCloseSource(PackSource delegate, AtomicInteger closeCount) {
            this.delegate = delegate;
            this.closeCount = closeCount;
        }

        @Override
        public byte[] read(String assetPath) {
            return delegate.read(assetPath);
        }

        @Override
        public boolean exists(String assetPath) {
            return delegate.exists(assetPath);
        }

        @Override
        public List<String> list(String prefix) {
            return delegate.list(prefix);
        }

        @Override
        public void close() throws IOException {
            closeCount.incrementAndGet();
            delegate.close();
        }
    }
}

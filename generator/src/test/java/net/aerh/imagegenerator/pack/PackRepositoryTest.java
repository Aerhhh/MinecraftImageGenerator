package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackRepositoryTest {

    @TempDir
    Path packDir;

    @TempDir
    Path miniPackDir;

    @TempDir
    Path zipDir;

    private PackRepository repository;

    @BeforeEach
    void setUp() {
        FixturePacks.writeDefaultPack(packDir);
        repository = new PackRepository();
    }

    private PackSource fixtureSource() {
        return PackSource.directory(packDir, PackLimits.fromSystemProperties());
    }

    /** The default fixture pack zipped up, for zip-backed lifecycle tests. */
    private Path zipFixturePack() throws IOException {
        Path zip = zipDir.resolve("fixture-pack.zip");
        try (OutputStream fileOut = Files.newOutputStream(zip);
             ZipOutputStream out = new ZipOutputStream(fileOut);
             Stream<Path> files = Files.walk(packDir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new ZipEntry(packDir.relativize(file).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(file));
                out.closeEntry();
            }
        }
        return zip;
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
    void packExceedingSmallEntryCapFailsLoudlyAtRegister() {
        // 6 asset files against a cap of 5: proves the maxEntries wiring actually bites during
        // registration (index time), not lazily at resolve.
        FixturePacks.writeMinimalPack(miniPackDir, 6);
        PackLimits smallCap = new PackLimits(5, 8L * 1024 * 1024, 1024, 64L * 1024 * 1024);
        PackSource source = PackSource.directory(miniPackDir, smallCap);
        PackLoadException exception = assertThrows(PackLoadException.class,
            () -> repository.register("test:overcap", source, smallCap));
        assertTrue(exception.getMessage().contains("max entry count"),
            "the failure must come from the entry-cap guard, not some unrelated register error");
    }

    @Test
    void packLargerThanSmallCapLoadsWithExplicitHigherLimitsOnBothSourceAndRegister() {
        // The large-server-pack recipe (see PackLimits javadoc): one raised PackLimits instance
        // passed to BOTH the source factory and register. 6 files over a cap of 5 stands in for
        // a large production server pack (~36k files) over the 20k default; the wiring is identical.
        FixturePacks.writeMinimalPack(miniPackDir, 6);
        PackLimits raised = new PackLimits(10, 8L * 1024 * 1024, 1024, 64L * 1024 * 1024);
        PackId id = repository.register("test:bigpack", PackSource.directory(miniPackDir, raised), raised);
        assertTrue(repository.resolve(id, "testpack:item/simple").isPresent(),
            "the pack indexes and resolves under the raised entry cap");
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
    void unregisterReleasesThePackAndReportsWhetherItExisted() {
        AtomicInteger closeCount = new AtomicInteger();
        repository.register("test:pack", new CountingCloseSource(fixtureSource(), closeCount));
        assertTrue(repository.unregister("test:pack"), "a registered pack unregisters");
        assertEquals(1, closeCount.get(), "unregister closes the owned source");
        assertEquals(Set.of(), repository.registeredPacks());
        assertFalse(repository.unregister("test:pack"), "a second unregister reports absence");
    }

    @Test
    void unregisterMalformedIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> repository.unregister("not-a-pack-id"));
    }

    @Test
    void resolveAfterUnregisterThrowsLikeAnyUnregisteredPack() {
        // Deliberate deviation from the wave 7 spec's "resolve after unregister = empty/vanilla
        // fallback" wording: an unregistered id throws exactly like a never-registered one.
        // Returning empty would be indistinguishable from "the pack lacks this item" and would
        // silently swallow lifecycle bugs in callers holding stale PackIds.
        PackId id = repository.register("test:pack", fixtureSource());
        assertTrue(repository.resolve(id, "testpack:item/simple").isPresent());
        repository.unregister("test:pack");
        assertThrows(PackResolveException.class, () -> repository.resolve(id, "testpack:item/simple"));
    }

    @Test
    void unregisterFreesTheIdForReRegistration() {
        PackId id = repository.register("test:pack", fixtureSource());
        repository.unregister("test:pack");
        PackId reRegistered = repository.register("test:pack", fixtureSource());
        assertEquals(id, reRegistered);
        assertTrue(repository.resolve(reRegistered, "testpack:item/simple").isPresent(),
            "the re-registered pack resolves normally");
    }

    @Test
    void concurrentResolveDuringUnregisterNeverThrowsConcurrentModification() throws Exception {
        // Resolvers hammer the repository while the main thread unregisters and re-registers
        // the pack. Legal outcomes per resolve: success, or the ordinary unregistered-pack
        // PackResolveException - anything else (ConcurrentModificationException included)
        // fails the test.
        PackId id = repository.register("race:pack", fixtureSource());
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> resolvers = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                resolvers.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS), "start latch timed out");
                    int successes = 0;
                    for (int iteration = 0; iteration < 200; iteration++) {
                        try {
                            if (repository.resolve(id, "testpack:item/simple").isPresent()) {
                                successes++;
                            }
                        } catch (PackResolveException expected) {
                            // The pack is momentarily unregistered - the documented outcome.
                        }
                    }
                    return successes;
                }));
            }
            start.countDown();
            for (int cycle = 0; cycle < 50; cycle++) {
                repository.unregister("race:pack");
                repository.register("race:pack", fixtureSource());
            }
            for (Future<Integer> resolver : resolvers) {
                // get() rethrows any unexpected exception type as an ExecutionException,
                // failing the test loudly.
                resolver.get(30, TimeUnit.SECONDS);
            }
            assertTrue(repository.resolve(id, "testpack:item/simple").isPresent(),
                "the final registration is intact after the churn");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentResolveDuringUnregisterOfZipPackOnlyThrowsPackResolveException() throws Exception {
        // The zip-backed variant of the race above, and the one that actually exercises a
        // closing source: unlike a directory source (whose close() is a no-op), release()
        // CLOSES the ZipFile, so a resolve that passed requireRegistered can hit "zip file
        // closed" mid-read. The contract still holds: every resolve either succeeds or throws
        // PackResolveException - never ZipFile's raw IllegalStateException.
        Path zip = zipFixturePack();
        PackLimits limits = PackLimits.fromSystemProperties();
        PackId id = repository.register("race:zip", PackSource.zip(zip, limits), limits);
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> resolvers = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                resolvers.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS), "start latch timed out");
                    int successes = 0;
                    for (int iteration = 0; iteration < 200; iteration++) {
                        try {
                            if (repository.resolve(id, "testpack:item/simple").isPresent()) {
                                successes++;
                            }
                        } catch (PackResolveException expected) {
                            // Unregistered, or reading the just-closed zip - both documented.
                        }
                    }
                    return successes;
                }));
            }
            start.countDown();
            for (int cycle = 0; cycle < 50; cycle++) {
                repository.unregister("race:zip");
                repository.register("race:zip", PackSource.zip(zip, limits), limits);
            }
            for (Future<Integer> resolver : resolvers) {
                // get() rethrows any unexpected exception type (IllegalStateException included)
                // as an ExecutionException, failing the test loudly.
                resolver.get(30, TimeUnit.SECONDS);
            }
            assertTrue(repository.resolve(id, "testpack:item/simple").isPresent(),
                "the final registration is intact after the churn");
        } finally {
            executor.shutdownNow();
            // Release the surviving registration: its ZipFile must close, or Windows keeps the
            // zip locked and the @TempDir cleanup fails the test from the outside.
            repository.unregister("race:zip");
        }
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

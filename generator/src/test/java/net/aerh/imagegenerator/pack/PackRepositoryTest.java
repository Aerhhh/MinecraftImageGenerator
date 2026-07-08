package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

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
}

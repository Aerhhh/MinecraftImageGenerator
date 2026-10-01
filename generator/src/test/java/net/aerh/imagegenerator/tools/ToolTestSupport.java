package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.tools.pack.PackDefinition;
import net.aerh.imagegenerator.tools.pack.PackRegistrationConfig;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pack services for tool tests: an empty one for vanilla renders and one with the default fixture pack. */
final class ToolTestSupport {

    static final PackId FIXTURE_PACK = PackId.parse("tools:fixture");

    /** The tooltip-only pack: style {@code themepack:ruby} plus a default tooltip override. */
    static final PackId THEMED_PACK = PackId.parse("tools:themed");

    private ToolTestSupport() {
    }

    static ResourcePackService vanillaService() {
        return new ResourcePackService(new PackRepository());
    }

    private static ResourcePackService fixtureService;
    private static ResourcePackService themedFixtureService;

    /**
     * One shared service per JVM: the pack is read-only after registration, tests never mutate it,
     * and pack sources stay open for the JVM's lifetime, so rebuilding it per test only costs time
     * and file handles. Fixtures live under target/ because a temp-dir cleanup cannot delete an
     * open pack on Windows.
     */
    static synchronized ResourcePackService fixtureService() throws IOException {
        if (fixtureService == null) {
            Path root = Files.createDirectories(Path.of("target", "pack-fixtures"));
            Path packDir = Files.createTempDirectory(root, "tools-");
            FixturePacks.writeDefaultPack(packDir);

            ResourcePackService service = new ResourcePackService(new PackRepository());
            service.registerConfiguredPacks(PackRegistrationConfig.of(null,
                PackDefinition.of(FIXTURE_PACK.toString(), packDir.toString())));
            requireRegistered(service, FIXTURE_PACK);
            fixtureService = service;
        }
        return fixtureService;
    }

    /**
     * One shared service per JVM whose only pack is the tooltip-only fixture ({@link #THEMED_PACK}),
     * kept apart from {@link #fixtureService()} so tests of the item fixture pack never see it.
     */
    static synchronized ResourcePackService themedFixtureService() throws IOException {
        if (themedFixtureService == null) {
            Path root = Files.createDirectories(Path.of("target", "pack-fixtures"));
            Path packDir = Files.createTempDirectory(root, "themed-");
            FixturePacks.writeTooltipOnlyPack(packDir);

            ResourcePackService service = new ResourcePackService(new PackRepository());
            service.registerConfiguredPacks(PackRegistrationConfig.of(null,
                PackDefinition.of(THEMED_PACK.toString(), packDir.toString())));
            requireRegistered(service, THEMED_PACK);
            themedFixtureService = service;
        }
        return themedFixtureService;
    }

    /** Fails loudly when a fixture pack did not register, since the service only logs that. */
    private static void requireRegistered(ResourcePackService service, PackId pack) {
        if (!service.packRepository().registeredPacks().contains(pack)) {
            throw new IllegalStateException("Fixture pack '" + pack + "' failed to register; see the log for the cause");
        }
    }
}

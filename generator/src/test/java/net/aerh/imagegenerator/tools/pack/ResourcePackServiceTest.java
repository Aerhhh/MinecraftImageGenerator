package net.aerh.imagegenerator.tools.pack;

import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLineage;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.text.TextColorRemap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackServiceTest {

    private static final String PACK_ID = "nerdbot:test";
    private static final String ITEM_REF = "testpack:item/simple";
    private static final String ITEM_DEFINITION = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"testpack:item/simple\"}}";

    private Path tempDir;
    private PackRepository repository;
    private ResourcePackService service;

    @BeforeEach
    void setUp() throws IOException {
        // Tests never unregister their packs, so registered zip sources stay open for the JVM's
        // lifetime and on Windows a @TempDir cleanup would fail to delete them. Fixtures live
        // under target/ instead, where the next mvn clean removes them. Each test uses its own
        // new PackRepository() so registrations never leak between tests.
        Path fixtureRoot = Path.of("target", "pack-fixtures");
        Files.createDirectories(fixtureRoot);
        tempDir = Files.createTempDirectory(fixtureRoot, "run-");

        repository = new PackRepository();
        service = new ResourcePackService(repository);
    }

    @Test
    void registersZipPackAndResolvesItems() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        PackId packId = PackId.parse(PACK_ID);
        assertTrue(repository.registeredPacks().contains(packId));
        assertTrue(repository.resolve(packId, ITEM_REF).isPresent());
    }

    @Test
    void registersDirectoryPack() throws IOException {
        Path packDir = tempDir.resolve("extracted");
        writeFixturePackFiles(packDir);

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, packDir)));

        PackId packId = PackId.parse(PACK_ID);
        assertTrue(repository.registeredPacks().contains(packId));
        assertTrue(repository.resolve(packId, ITEM_REF).isPresent());
    }

    @Test
    void indexesItemRefsForAutocomplete() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        assertEquals(List.of(ITEM_REF), service.allItemRefs());
    }

    @Test
    void brokenPackIsSkippedAndOthersStillRegister() throws IOException {
        Path broken = createZip("broken.zip", Map.of("pack.mcmeta", "{\"pack\":{\"pack_format\":88,\"description\":\"Empty\"}}"));
        Path valid = createFixturePackZip("valid.zip");

        service.registerConfiguredPacks(config(null, packDefinition("nerdbot:broken", broken), packDefinition(PACK_ID, valid)));

        assertEquals(1, repository.registeredPacks().size());
        assertTrue(repository.registeredPacks().contains(PackId.parse(PACK_ID)));
    }

    @Test
    void missingPathIsSkipped() throws IOException {
        Path valid = createFixturePackZip("valid.zip");

        service.registerConfiguredPacks(config(null,
            packDefinition("nerdbot:missing", tempDir.resolve("does-not-exist.zip")),
            packDefinition(PACK_ID, valid)));

        assertEquals(1, repository.registeredPacks().size());
    }

    @Test
    void invalidPackIdIsSkipped() throws IOException {
        Path valid = createFixturePackZip("valid.zip");

        service.registerConfiguredPacks(config(null,
            packDefinition("not a valid id", valid),
            packDefinition(PACK_ID, valid)));

        assertEquals(1, repository.registeredPacks().size());
    }

    @Test
    void blankDefinitionIsSkipped() throws IOException {
        Path valid = createFixturePackZip("valid.zip");

        service.registerConfiguredPacks(config(null,
            packDefinition(null, valid),
            packDefinition("  ", valid),
            packDefinition(PACK_ID, valid)));

        assertEquals(1, repository.registeredPacks().size());
    }

    @Test
    void reRegistrationIsIdempotent() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");
        PackRegistrationConfig config = config(null, packDefinition(PACK_ID, zip));

        service.registerConfiguredPacks(config);
        service.registerConfiguredPacks(config);

        assertEquals(1, repository.registeredPacks().size());
    }

    @Test
    void nullConfigIsNoOp() {
        service.registerConfiguredPacks(null);

        assertTrue(repository.registeredPacks().isEmpty());
        assertNull(service.resolvePackOption(null));
    }

    @Test
    void resolvePackOptionDefaultsToVanillaWithoutDefaultPack() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        assertNull(service.resolvePackOption(null));
        assertNull(service.resolvePackOption("  "));
    }

    @Test
    void resolvePackOptionUsesConfiguredDefaultPack() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(PACK_ID, packDefinition(PACK_ID, zip)));

        assertEquals(PackId.parse(PACK_ID), service.resolvePackOption(null));
    }

    @Test
    void resolvePackOptionFallsBackToVanillaWhenDefaultPackNotRegistered() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config("nerdbot:unregistered", packDefinition(PACK_ID, zip)));

        assertNull(service.resolvePackOption(null));
    }

    @Test
    void resolvePackOptionVanillaKeywordOverridesDefault() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(PACK_ID, packDefinition(PACK_ID, zip)));

        assertNull(service.resolvePackOption("vanilla"));
        assertNull(service.resolvePackOption("VANILLA"));
        assertNull(service.resolvePackOption("minecraft:minecraft"));
    }

    @Test
    void resolvePackOptionAcceptsRegisteredPack() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        assertEquals(PackId.parse(PACK_ID), service.resolvePackOption(PACK_ID));
        assertEquals(PackId.parse(PACK_ID), service.resolvePackOption("NerdBot:Test"));
        assertEquals(PackId.parse(PACK_ID), service.resolvePackOption(" " + PACK_ID + " "));
    }

    @Test
    void resolvePackOptionRejectsUnknownPack() {
        assertThrows(GeneratorException.class, () -> service.resolvePackOption("nerdbot:unknown"));
    }

    @Test
    void resolvePackOptionRejectsInvalidFormat() {
        assertThrows(GeneratorException.class, () -> service.resolvePackOption("not a pack id"));
        assertThrows(GeneratorException.class, () -> service.resolvePackOption("too:many:colons"));
    }

    @Test
    void packOptionChoicesListsVanillaFirst() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        assertEquals(List.of(ResourcePackService.VANILLA_OPTION, PACK_ID), service.packOptionChoices());
    }

    @Test
    void searchItemRefsMatchesCaseInsensitively() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        assertEquals(List.of(ITEM_REF), service.searchItemRefs("SIMPLE"));
        assertEquals(List.of(ITEM_REF), service.searchItemRefs("testpack:"));
        assertTrue(service.searchItemRefs("no-such-item").isEmpty());
    }

    @Test
    void nullEntriesAreRejectedAtConstruction() {
        List<PackDefinition> packs = new ArrayList<>();
        packs.add(null);

        assertThrows(NullPointerException.class, () -> new PackRegistrationConfig(packs, null));
    }

    @Test
    void allItemRefsDedupesAcrossPacksSharingANamespace() throws IOException {
        Path first = createFixturePackZip("first.zip");
        Path second = createFixturePackZip("second.zip");

        service.registerConfiguredPacks(config(null,
            packDefinition("nerdbot:first", first),
            packDefinition("nerdbot:second", second)));

        // Both packs ship the same testpack namespace, so the shared ref appears once
        assertEquals(List.of(ITEM_REF), service.allItemRefs());
        assertEquals(List.of(ITEM_REF), service.searchItemRefs("simple"));
    }

    @Test
    void allItemRefsRefreshesAfterEachRegistration() throws IOException {
        Path first = createFixturePackZip("first.zip");
        service.registerConfiguredPacks(config(null, packDefinition("cache:one", first)));
        assertEquals(List.of(ITEM_REF), service.allItemRefs());

        Path second = createFixturePackZip("second.zip");
        service.registerConfiguredPacks(config(null, packDefinition("cache:two", second)));

        assertEquals(List.of(ITEM_REF), service.allItemRefs(), "the same ref from two packs is listed once");
        assertEquals(List.of(ITEM_REF), service.searchItemRefs("SIMPLE"));
    }

    @Test
    void indexesOnlyItemDefinitionAssetPaths() throws IOException {
        // A pack whose asset listing mixes item definitions (at varying depths) with non-item files.
        // Only paths shaped assets/<namespace>/items/<path>.json become refs; everything else is ignored.
        Map<String, Object> files = new java.util.LinkedHashMap<>();
        files.put("pack.mcmeta", "{\"pack\":{\"pack_format\":88,\"description\":\"Edge fixture\"}}");
        files.put("assets/testpack/items/item/simple.json", ITEM_DEFINITION);
        files.put("assets/testpack/models/item/simple.json", "{\"textures\":{\"layer0\":\"testpack:item/simple\"}}");
        files.put("assets/testpack/textures/item/simple.png", pngBytes());
        // Accepted: a deeply nested item definition (mirrors real packs like item/category/sub/name)
        files.put("assets/testpack/items/item/category/deep.json", ITEM_DEFINITION);
        // Rejected: a directory that merely starts with "items", not the items/ folder
        files.put("assets/testpack/itemsfoo/decoy.json", "{}");
        // Rejected: an empty item path (no name before .json)
        files.put("assets/testpack/items/.json", "{}");
        // Rejected: a non-json asset that lives under items/
        files.put("assets/testpack/items/item/notes.txt", "ignored");

        Path zip = createZip("edge.zip", files);
        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        assertEquals(
            List.of("testpack:item/category/deep", "testpack:item/simple"),
            service.allItemRefs());
    }

    @Test
    void itemRefsReturnsPerPack() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        assertEquals(List.of(ITEM_REF), service.itemRefs(PackId.parse(PACK_ID)));
        assertTrue(service.itemRefs(PackId.parse("nerdbot:absent")).isEmpty());
    }

    @Test
    void itemRefsForOptionReturnsSelectedPackRefs() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        assertEquals(List.of(ITEM_REF), service.itemRefsForOption(PACK_ID));
    }

    @Test
    void itemRefsForOptionReturnsEmptyForVanilla() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(PACK_ID, packDefinition(PACK_ID, zip)));

        assertTrue(service.itemRefsForOption("vanilla").isEmpty());
        assertTrue(service.itemRefsForOption("minecraft:minecraft").isEmpty());
    }

    @Test
    void itemRefsForOptionUsesDefaultPackWhenOmitted() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(PACK_ID, packDefinition(PACK_ID, zip)));

        assertEquals(List.of(ITEM_REF), service.itemRefsForOption(null));
    }

    @Test
    void itemRefsForOptionOmittedWithNoDefaultOffersNoPackRefs() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        // Omitting the pack option with no default resolves to vanilla, so no pack refs are offered;
        // suggesting them would let a user pick a ref that then fails to render against vanilla.
        assertTrue(service.itemRefsForOption(null).isEmpty());
        assertTrue(service.itemRefsForOption("  ").isEmpty());
    }

    @Test
    void itemRefsForOptionUnknownOrHalfTypedOffersEverything() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        // A half-typed or unrecognised pack value is ambiguous, so offer everything rather than nothing
        assertEquals(List.of(ITEM_REF), service.itemRefsForOption("nerdbot:notregistered"));
        assertEquals(List.of(ITEM_REF), service.itemRefsForOption("half-typed"));
    }

    @Test
    void tooltipStyleForReturnsConfiguredMapping() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");
        PackDefinition definition = new PackDefinition(PACK_ID, zip.toString(), Map.of("EPIC", "testpack:fancy"), Map.of());

        service.registerConfiguredPacks(config(null, definition));

        PackId packId = PackId.parse(PACK_ID);
        assertEquals("testpack:fancy", service.tooltipStyleFor(packId, Rarity.byName("epic")),
            "rarity keys match case-insensitively by rarity name");
        assertNull(service.tooltipStyleFor(packId, Rarity.byName("legendary")), "unmapped rarity has no style");
        assertNull(service.tooltipStyleFor(packId, null), "no rarity, no style");
        assertNull(service.tooltipStyleFor(null, Rarity.byName("epic")), "vanilla has no styles");
        assertNull(service.tooltipStyleFor(PackId.parse("nerdbot:absent"), Rarity.byName("epic")));
    }

    @Test
    void textColorRemapForParsesHexPairs() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");
        PackDefinition definition = new PackDefinition(PACK_ID, zip.toString(), Map.of(),
            Map.of("#AA0000", "#D13228", "#FFAA00", "#FF9000"));

        service.registerConfiguredPacks(config(null, definition));

        TextColorRemap expected = TextColorRemap.builder()
            .remap(0xAA0000, 0xD13228)
            .remap(0xFFAA00, 0xFF9000)
            .build();
        assertEquals(expected, service.textColorRemapFor(PackId.parse(PACK_ID)),
            "config hex pairs must parse into an equal remap table");
        assertNull(service.textColorRemapFor(null));
        assertNull(service.textColorRemapFor(PackId.parse("nerdbot:absent")));
    }

    @Test
    void emptyThemeConfigYieldsNoStyleAndNoRemap() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        PackId packId = PackId.parse(PACK_ID);
        assertNull(service.tooltipStyleFor(packId, Rarity.byName("epic")));
        assertNull(service.textColorRemapFor(packId));
    }

    @Test
    void invalidRemapHexColorSkipsThePack() throws IOException {
        Path bad = createFixturePackZip("bad.zip");
        Path good = createFixturePackZip("good.zip");
        PackDefinition badDefinition = new PackDefinition("nerdbot:bad", bad.toString(), Map.of(), Map.of("AA0000", "#D13228"));

        service.registerConfiguredPacks(config(null, badDefinition, packDefinition(PACK_ID, good)));

        assertEquals(1, repository.registeredPacks().size(),
            "a typo in the theme config must not silently register the pack without its theme");
        assertTrue(repository.registeredPacks().contains(PackId.parse(PACK_ID)));
    }

    @Test
    void blankStyleMappingSkipsThePack() throws IOException {
        Path bad = createFixturePackZip("bad.zip");
        PackDefinition badDefinition = new PackDefinition("nerdbot:bad", bad.toString(), Map.of("epic", "  "), Map.of());

        service.registerConfiguredPacks(config(null, badDefinition));

        assertTrue(repository.registeredPacks().isEmpty());
    }

    @Test
    void nullStyleValueSkipsThePackAndOthersStillRegister() throws IOException {
        Path bad = createFixturePackZip("bad.zip");
        Path good = createFixturePackZip("good.zip");
        Map<String, String> nullStyle = new HashMap<>();
        nullStyle.put("legendary", null);
        PackDefinition badDefinition = new PackDefinition("nerdbot:bad", bad.toString(), nullStyle, Map.of());

        service.registerConfiguredPacks(config(null, badDefinition, packDefinition(PACK_ID, good)));

        assertEquals(1, repository.registeredPacks().size(),
            "a null style value in the config must skip that pack, like any other invalid theme");
        assertTrue(repository.registeredPacks().contains(PackId.parse(PACK_ID)));
    }

    @Test
    void configuredStyleMissingFromPackStillRegisters() throws IOException {
        // The style may exist in a future pack version; startup warns but stays up, and the
        // library fails loudly at render time if the style is actually requested.
        Path zip = createFixturePackZip("fixture.zip");
        PackDefinition definition = new PackDefinition(PACK_ID, zip.toString(), Map.of("epic", "testpack:nope"), Map.of());

        service.registerConfiguredPacks(config(null, definition));

        assertTrue(repository.registeredPacks().contains(PackId.parse(PACK_ID)));
        assertEquals("testpack:nope", service.tooltipStyleFor(PackId.parse(PACK_ID), Rarity.byName("epic")));
    }

    @Test
    void tooltipStyleChoicesFollowPackOptionSemantics() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(null, packDefinition(PACK_ID, zip)));

        assertEquals(List.of("testpack:fancy"), service.tooltipStyleChoices(PACK_ID));
        assertTrue(service.tooltipStyleChoices("vanilla").isEmpty());
        assertTrue(service.tooltipStyleChoices("minecraft:minecraft").isEmpty());
        assertTrue(service.tooltipStyleChoices(null).isEmpty(), "omitted with no default resolves to vanilla");
        assertEquals(List.of("testpack:fancy"), service.tooltipStyleChoices("half-typed"),
            "ambiguous pack value offers every style rather than none");
    }

    @Test
    void tooltipStyleChoicesUseDefaultPackWhenOmitted() throws IOException {
        Path zip = createFixturePackZip("fixture.zip");

        service.registerConfiguredPacks(config(PACK_ID, packDefinition(PACK_ID, zip)));

        assertEquals(List.of("testpack:fancy"), service.tooltipStyleChoices(null));
    }

    @Test
    void variantOfIsAppliedToTheRegisteredPack() throws IOException {
        Path zip = createFixturePackZip("alpha.zip");

        service.registerConfiguredPacks(config(null, variantDefinition("hypixel:alpha", zip, "hypixel:skyblock")));

        PackId alpha = PackId.parse("hypixel:alpha");
        assertEquals(new PackLineage(alpha, PackId.parse("hypixel:skyblock")), repository.lineageOf(alpha));
    }

    @Test
    void variantOfIsTrimmedAndLowercasedLikeThePackId() throws IOException {
        Path zip = createFixturePackZip("alpha.zip");

        service.registerConfiguredPacks(config(null, variantDefinition("hypixel:alpha", zip, "  Hypixel:SkyBlock ")));

        assertEquals(PackId.parse("hypixel:skyblock"), repository.lineageOf(PackId.parse("hypixel:alpha")).variantOf());
    }

    @Test
    void blankOrMissingVariantOfMeansNoVariant() throws IOException {
        Path blank = createFixturePackZip("blank.zip");
        Path missing = createFixturePackZip("missing.zip");

        service.registerConfiguredPacks(config(null,
            variantDefinition("nerdbot:blank", blank, "   "),
            packDefinition("nerdbot:missing", missing)));

        PackId blankId = PackId.parse("nerdbot:blank");
        PackId missingId = PackId.parse("nerdbot:missing");
        assertEquals(PackLineage.of(blankId), repository.lineageOf(blankId));
        assertEquals(PackLineage.of(missingId), repository.lineageOf(missingId));
    }

    @Test
    void malformedVariantOfSkipsThePackAndOthersStillRegister() throws IOException {
        Path bad = createFixturePackZip("bad.zip");
        Path good = createFixturePackZip("good.zip");

        service.registerConfiguredPacks(config(null,
            variantDefinition("nerdbot:bad", bad, "not a pack id"),
            packDefinition(PACK_ID, good)));

        assertEquals(java.util.Set.of(PackId.parse(PACK_ID)), repository.registeredPacks(),
            "a typo in variantOf must not silently register the pack without its overrides");
    }

    @Test
    void selfVariantOfSkipsThePack() throws IOException {
        Path bad = createFixturePackZip("bad.zip");

        service.registerConfiguredPacks(config(null, variantDefinition("nerdbot:bad", bad, "NerdBot:Bad")));

        assertTrue(repository.registeredPacks().isEmpty());
    }

    @Test
    void vanillaVariantOfSkipsThePack() throws IOException {
        Path bad = createFixturePackZip("bad.zip");

        service.registerConfiguredPacks(config(null, variantDefinition("nerdbot:bad", bad, "minecraft:minecraft")));

        assertTrue(repository.registeredPacks().isEmpty());
    }

    @Test
    void reloadAppliesTheDefinitionsVariantOf() throws IOException {
        Path first = createFixturePackZip("first.zip");
        Path second = createFixturePackZip("second.zip");
        PackId alpha = PackId.parse("hypixel:alpha");
        service.registerConfiguredPacks(config(null, packDefinition("hypixel:alpha", first)));
        assertEquals(PackLineage.of(alpha), repository.lineageOf(alpha));

        PackReloadResult result = service.reloadPack(variantDefinition("hypixel:alpha", second, "hypixel:skyblock"),
            second, new PackExpectations(88, 0.0, List.of()));

        assertTrue(result instanceof PackReloadResult.Applied, String.valueOf(result));
        assertEquals(new PackLineage(alpha, PackId.parse("hypixel:skyblock")), repository.lineageOf(alpha));
    }

    @Test
    void reloadWithAnInvalidVariantOfIsNotAttemptedAndKeepsTheLivePack() throws IOException {
        Path first = createFixturePackZip("first.zip");
        Path second = createFixturePackZip("second.zip");
        PackId alpha = PackId.parse("hypixel:alpha");
        service.registerConfiguredPacks(config(null, variantDefinition("hypixel:alpha", first, "hypixel:skyblock")));

        PackReloadResult result = service.reloadPack(variantDefinition("hypixel:alpha", second, "hypixel:alpha"),
            second, new PackExpectations(88, 0.0, List.of()));

        assertTrue(result instanceof PackReloadResult.NotAttempted notAttempted
            && notAttempted.reason().contains("variantOf"), String.valueOf(result));
        assertEquals(new PackLineage(alpha, PackId.parse("hypixel:skyblock")), repository.lineageOf(alpha),
            "the live pack keeps its lineage");
    }

    private static PackDefinition variantDefinition(String id, Path path, String variantOf) {
        return new PackDefinition(id, path.toString(), Map.of(), Map.of(), variantOf);
    }

    private static PackRegistrationConfig config(String defaultPack, PackDefinition... packs) {
        return PackRegistrationConfig.of(defaultPack, packs);
    }

    private static PackDefinition packDefinition(String id, Path path) {
        return PackDefinition.of(id, path.toString());
    }

    private Path createFixturePackZip(String fileName) throws IOException {
        return createZip(fileName, fixturePackFiles());
    }

    private Path createZip(String fileName, Map<String, ?> files) throws IOException {
        Path zipPath = tempDir.resolve(fileName);

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, ?> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue() instanceof byte[] bytes ? bytes : ((String) entry.getValue()).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }

        return zipPath;
    }

    private static void writeFixturePackFiles(Path root) throws IOException {
        for (Map.Entry<String, ?> entry : fixturePackFiles().entrySet()) {
            Path target = root.resolve(entry.getKey());
            Files.createDirectories(target.getParent());

            if (entry.getValue() instanceof byte[] bytes) {
                Files.write(target, bytes);
            } else {
                Files.writeString(target, (String) entry.getValue());
            }
        }
    }

    /**
     * A minimal, original 1.21.4+ format pack: one item definition referencing one model
     * referencing one texture, plus one complete tooltip style ({@code testpack:fancy}),
     * mirroring the structure of real packs like Hypixel's.
     */
    private static Map<String, ?> fixturePackFiles() throws IOException {
        return Map.of(
            "pack.mcmeta", "{\"pack\":{\"pack_format\":88,\"description\":\"NerdBot test fixture\"}}",
            "assets/testpack/items/item/simple.json", ITEM_DEFINITION,
            "assets/testpack/models/item/simple.json", "{\"textures\":{\"layer0\":\"testpack:item/simple\"}}",
            "assets/testpack/textures/item/simple.png", pngBytes(),
            "assets/testpack/textures/gui/sprites/tooltip/fancy_background.png", pngBytes(),
            "assets/testpack/textures/gui/sprites/tooltip/fancy_frame.png", pngBytes()
        );
    }

    private static byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.MAGENTA);
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}

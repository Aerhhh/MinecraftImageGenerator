package net.aerh.imagegenerator.tooling.minecraft;

import lib.minecraft.renderer.AtlasRenderer;
import lib.minecraft.renderer.call.request.AtlasOptions;
import lib.minecraft.renderer.call.result.AtlasResult;
import lib.minecraft.renderer.content.client.ClientAcquisition;
import lib.minecraft.renderer.content.client.ClientAssets;
import lib.minecraft.renderer.content.client.ClientOptions;
import lib.minecraft.renderer.content.index.RendererContext;
import net.aerh.imagegenerator.tooling.ToolingPaths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Renders every item of a client version as the icon its inventory slot shows, one PNG per id, with the
 * asset-renderer library running in this JVM. The library reads the client JAR the download step cached, so
 * there is nothing to clone, build or license.
 * <p>
 * The item list is the game's own registry: since 26.1 the client ships one definition file per item under
 * {@code assets/minecraft/items/}, which covers block items such as stone and chests as well as plain items.
 * The work goes through the library's {@code AtlasRenderer}, which already knows which ids are drawn as flat
 * sprites, as isometric blocks, or as block entities. Ids are rendered in batches so one pass holds a small
 * sheet rather than every icon at once; each sheet is then cut into the individual files the spritesheet
 * step packs.
 */
public final class ItemIconRenderer {

    /** Ids per atlas pass; at 256px tiles a pass holds a few dozen megabytes of pixels. */
    static final int BATCH_SIZE = 128;

    private static final int COLUMNS = 16;
    private static final String VANILLA_NAMESPACE = "minecraft:";
    private static final String ITEM_DEFINITIONS_DIR = "assets/minecraft/items/";
    private static final Pattern ITEM_DEFINITION = Pattern.compile(Pattern.quote(ITEM_DEFINITIONS_DIR) + "([^/]+)\\.json");

    /**
     * What a render produced: how many icons were written, and every item the library could not draw with
     * the reason it gave, or no reason when it simply produced no tile for the id.
     */
    public record Report(int written, List<String> skipped) {
    }

    private final ToolingPaths paths;

    public ItemIconRenderer(ToolingPaths paths) {
        this.paths = paths;
    }

    /**
     * Renders every item of {@code version} into {@code outputDir}, creating it if needed.
     *
     * @param outputDir  where the PNG files go, one per id, named without the {@code minecraft:} namespace
     * @param version    the client version whose JAR is in the work directory's cache
     * @param renderSize the icon size in pixels
     *
     * @throws IOException if the JAR is not cached, holds no item definitions, or an icon cannot be written
     */
    public Report renderAll(Path outputDir, String version, int renderSize) throws IOException {
        if (renderSize <= 0) {
            throw new IllegalArgumentException("Render size must be positive, got " + renderSize);
        }

        Path jar = paths.jarPath(version);
        if (!Files.isRegularFile(jar)) {
            throw new IOException("Client JAR for " + version + " is not cached at " + jar + "; run --download first");
        }

        List<String> ids = itemIdsFromJar(jar);
        System.out.println("Loading client " + version + " from " + jar);
        ClientOptions options = ClientOptions.builder()
            .version(version)
            .cacheRoot(paths.rendererCache().toFile())
            .build();
        RendererContext context = RendererContext.load(new ClientAssets(options, ClientAcquisition.readVanillaPack(jar)));
        System.out.println("Rendering " + ids.size() + " items at " + renderSize + "px...");

        Files.createDirectories(outputDir);
        AtlasRenderer renderer = new AtlasRenderer(context);
        Set<String> drawn = new HashSet<>();
        List<String> skipped = new ArrayList<>();
        int written = 0;
        int done = 0;

        for (List<String> batch : batches(ids, BATCH_SIZE)) {
            Set<String> wanted = new HashSet<>(batch);
            AtlasResult result = renderer.render(AtlasOptions.builder()
                .filter(wanted::contains)
                .tileSize(renderSize)
                .columns(COLUMNS)
                .progressLogging(false)
                .build());

            written += writeTiles(result.image().toBufferedImage(), result.sidecar().tiles(), outputDir);
            for (AtlasResult.Tile tile : result.sidecar().tiles()) {
                drawn.add(tile.id());
            }
            for (AtlasResult.Skipped row : result.sidecar().skipped()) {
                drawn.add(row.id());
                skipped.add(row.id() + " (" + row.reason() + ")");
            }

            done += batch.size();
            System.out.println("  rendered " + done + "/" + ids.size());
        }

        for (String id : ids) {
            if (!drawn.contains(id)) {
                skipped.add(id + " (the renderer produced no tile for it)");
            }
        }

        for (String row : skipped) {
            System.err.println("  skipped " + row);
        }
        System.out.println("Wrote " + written + " icons to " + outputDir
            + (skipped.isEmpty() ? "" : " (" + skipped.size() + " skipped)"));
        return new Report(written, List.copyOf(skipped));
    }

    /**
     * The item ids a client JAR registers, read from its one-definition-per-item directory, namespaced and
     * sorted. Model files, which also exist for variants such as armour trims, are not items and are not read.
     *
     * @throws IOException if the JAR cannot be read or has no item definitions, which means a client older
     *                     than 26.1
     */
    static List<String> itemIdsFromJar(Path jar) throws IOException {
        Set<String> ids = new LinkedHashSet<>();

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            zip.stream().forEach(entry -> {
                Matcher matcher = ITEM_DEFINITION.matcher(entry.getName());
                if (matcher.matches()) {
                    ids.add(VANILLA_NAMESPACE + matcher.group(1));
                }
            });
        }

        if (ids.isEmpty()) {
            throw new IOException("No item definitions under " + ITEM_DEFINITIONS_DIR + " in " + jar
                + "; the icon renderer needs a 26.1 or newer client");
        }

        return ids.stream().sorted().toList();
    }

    /**
     * The file an id is written to: vanilla ids drop their namespace, so {@code minecraft:stone} becomes
     * {@code stone.png} as the previous renderer named them; any other namespace is kept with its colon
     * replaced, since a colon is not a usable file name character.
     */
    static String iconFileName(String id) {
        String name = id.startsWith(VANILLA_NAMESPACE) ? id.substring(VANILLA_NAMESPACE.length()) : id.replace(':', '_');
        return name + ".png";
    }

    static List<List<String>> batches(List<String> ids, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Batch size must be positive, got " + size);
        }

        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += size) {
            batches.add(List.copyOf(ids.subList(start, Math.min(ids.size(), start + size))));
        }
        return batches;
    }

    /**
     * Cuts each tile out of a rendered sheet into its own PNG. A tile whose source is {@code EMPTY} drew
     * nothing (air, for instance) and is not written.
     *
     * @return how many files were written
     *
     * @throws IOException if a tile lies outside the sheet or a file cannot be written
     */
    static int writeTiles(BufferedImage sheet, List<AtlasResult.Tile> tiles, Path outputDir) throws IOException {
        int written = 0;

        for (AtlasResult.Tile tile : tiles) {
            if (tile.source() == AtlasResult.Tile.Source.EMPTY) {
                continue;
            }

            if (tile.x() < 0 || tile.y() < 0
                || tile.x() + tile.width() > sheet.getWidth() || tile.y() + tile.height() > sheet.getHeight()) {
                throw new IOException("Tile for " + tile.id() + " at (" + tile.x() + ", " + tile.y() + ") "
                    + tile.width() + "x" + tile.height() + " lies outside the "
                    + sheet.getWidth() + "x" + sheet.getHeight() + " sheet");
            }

            BufferedImage icon = new BufferedImage(tile.width(), tile.height(), BufferedImage.TYPE_INT_ARGB);
            int[] pixels = sheet.getRGB(tile.x(), tile.y(), tile.width(), tile.height(), null, 0, tile.width());
            icon.setRGB(0, 0, tile.width(), tile.height(), pixels, 0, tile.width());

            Path file = outputDir.resolve(iconFileName(tile.id()));
            if (!ImageIO.write(icon, "png", file.toFile())) {
                throw new IOException("No PNG writer is available for " + file);
            }
            written++;
        }

        return written;
    }
}

package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Base class for texture atlas generation.
 * <p>
 * Packs the entries a subclass loads into one or more fixed-grid pages described by an {@link AtlasLayout},
 * writes each page as a PNG and records every entry's page and position in a JSON array. The first page keeps
 * the configured file name; further pages get a {@code -1}, {@code -2} suffix before the extension.
 */
public abstract class AtlasGenerator {

    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    protected final AtlasLayout layout;
    protected final Path atlasOutputPath;
    protected final Path jsonOutputPath;

    protected AtlasGenerator(AtlasLayout layout, Path atlasOutputPath, Path jsonOutputPath) {
        this.layout = layout;
        this.atlasOutputPath = atlasOutputPath;
        this.jsonOutputPath = jsonOutputPath;
    }

    protected BufferedImage resizeToLayoutSize(BufferedImage source) {
        return ImageUtil.resizeImageRaw(source, layout.tileSize(), layout.tileSize());
    }

    protected abstract List<AtlasEntry> loadTextures();

    protected abstract JsonObject entryToJson(AtlasEntry entry);

    /**
     * Loads, packs and writes the atlas. Nothing to pack is an {@link IllegalStateException} and a file that
     * cannot be written an {@link UncheckedIOException}: a run that reports success without producing the atlas,
     * or leaves a half-written set of files behind, would let a build package stale or partial assets.
     */
    public void generate() {
        List<AtlasEntry> entries = loadTextures();
        System.out.println("Loaded " + entries.size() + " textures.");

        if (entries.isEmpty()) {
            throw new IllegalStateException("No textures to pack into " + atlasOutputPath);
        }

        int capacity = layout.capacity();
        int pageCount = 0;

        for (int start = 0; start < entries.size(); pageCount++) {
            int end = (int) Math.min(entries.size(), (long) start + capacity);
            List<AtlasEntry> batch = entries.subList(start, end);

            for (AtlasEntry entry : batch) {
                entry.page = pageCount;
            }

            saveAtlas(packAtlas(batch), pageOutputPath(pageCount));
            start = end;
        }

        removeStalePages(pageCount);
        saveCoordinatesJson(entries);

        System.out.println("Atlas generation complete!");
    }

    protected BufferedImage packAtlas(List<AtlasEntry> entries) {
        int columns = layout.columns();
        int rows = (entries.size() + columns - 1) / columns;
        int tileSize = layout.tileSize();

        BufferedImage atlas = new BufferedImage(layout.maxWidth(), rows * layout.pitch(), BufferedImage.TYPE_INT_ARGB);
        int[] atlasPixels = ((DataBufferInt) atlas.getRaster().getDataBuffer()).getData();

        for (int i = 0; i < entries.size(); i++) {
            AtlasEntry entry = entries.get(i);
            entry.x = (i % columns) * layout.pitch() + layout.padding();
            entry.y = (i / columns) * layout.pitch() + layout.padding();

            int[] texturePixels = ((DataBufferInt) entry.image.getRaster().getDataBuffer()).getData();
            for (int py = 0; py < tileSize; py++) {
                int atlasOffset = (entry.y + py) * atlas.getWidth() + entry.x;
                System.arraycopy(texturePixels, py * tileSize, atlasPixels, atlasOffset, tileSize);
            }

            double progress = (i + 1) * 100.0 / entries.size();
            System.out.printf("\rPacking textures: %d/%d (%.1f%%)", i + 1, entries.size(), progress);
        }
        System.out.println();

        return atlas;
    }

    /**
     * The file a given page is written to: page 0 is {@code atlasOutputPath} itself, later pages get the page
     * number appended to the file name, before its extension if it has one. Only the file name is inspected, so
     * dots in directory names are left alone.
     */
    protected Path pageOutputPath(int page) {
        if (page == 0) {
            return atlasOutputPath;
        }

        NameParts parts = nameParts();
        return atlasOutputPath.resolveSibling(parts.stem() + "-" + page + parts.extension());
    }

    private NameParts nameParts() {
        String fileName = atlasOutputPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0
            ? new NameParts(fileName, "")
            : new NameParts(fileName.substring(0, dot), fileName.substring(dot));
    }

    private record NameParts(String stem, String extension) {
    }

    private void saveAtlas(BufferedImage atlas, Path path) {
        try {
            createParentDirectories(path);
            System.out.println("Saving atlas to: " + path.toAbsolutePath());
            if (!ImageIO.write(atlas, "png", path.toFile())) {
                throw new IOException("no PNG writer is available");
            }
            System.out.println("Atlas saved successfully!");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save atlas " + path, e);
        }
    }

    /**
     * Deletes every numbered page file beyond the last page just written. They can only be left over from an
     * earlier run that needed more pages, and a consumer would otherwise treat them as current. The directory is
     * scanned rather than counted upward, so a gap in the numbering does not hide later pages.
     */
    private void removeStalePages(int pageCount) {
        NameParts parts = nameParts();
        Pattern numberedPage = Pattern.compile(Pattern.quote(parts.stem()) + "-(\\d{1,9})" + Pattern.quote(parts.extension()));
        int firstStale = Math.max(pageCount, 1);
        Path directory = atlasOutputPath.toAbsolutePath().getParent();

        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                Matcher matcher = numberedPage.matcher(file.getFileName().toString());
                if (matcher.matches() && Integer.parseInt(matcher.group(1)) >= firstStale) {
                    Files.delete(file);
                    System.out.println("Removed stale atlas page: " + file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to remove stale atlas pages next to " + atlasOutputPath, e);
        }
    }

    protected void saveCoordinatesJson(List<AtlasEntry> entries) {
        JsonArray jsonArray = new JsonArray();
        for (AtlasEntry entry : entries) {
            JsonObject object = entryToJson(entry);
            object.addProperty("page", entry.page);
            object.addProperty("atlas", pageOutputPath(entry.page).getFileName().toString());
            jsonArray.add(object);
        }

        try {
            createParentDirectories(jsonOutputPath);
            try (Writer writer = Files.newBufferedWriter(jsonOutputPath)) {
                GSON.toJson(jsonArray, writer);
            }
            System.out.println("Coordinates JSON saved to: " + jsonOutputPath.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save coordinates JSON " + jsonOutputPath, e);
        }
    }

    private static void createParentDirectories(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Atlas entry with name, image, page, position, and optional metadata.
     */
    public static class AtlasEntry {
        public final String name;
        public final BufferedImage image;
        public final Map<String, String> metadata;
        public int page;
        public int x;
        public int y;

        public AtlasEntry(String name, BufferedImage image) {
            this.name = name;
            this.image = image;
            this.metadata = new HashMap<>();
        }

        public AtlasEntry withMeta(String key, String value) {
            metadata.put(key, value);
            return this;
        }

        public String getMeta(String key) {
            return metadata.get(key);
        }
    }
}

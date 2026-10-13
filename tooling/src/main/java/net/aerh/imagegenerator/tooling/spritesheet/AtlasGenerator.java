package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for texture atlas generation.
 */
public abstract class AtlasGenerator {

    protected static final int IMAGE_SIZE = 256;
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    protected final int atlasWidth;
    protected final AtlasLayout layout;
    protected final String atlasOutputPath;
    protected final String jsonOutputPath;

    protected AtlasGenerator(int atlasWidth, String atlasOutputPath, String jsonOutputPath) {
        this(new AtlasLayout(IMAGE_SIZE, atlasWidth, Integer.MAX_VALUE, 0), atlasOutputPath, jsonOutputPath);
    }

    protected AtlasGenerator(AtlasLayout layout, String atlasOutputPath, String jsonOutputPath) {
        this.layout = layout;
        this.atlasWidth = layout.maxWidth();
        this.atlasOutputPath = atlasOutputPath;
        this.jsonOutputPath = jsonOutputPath;
    }

    protected static BufferedImage resizeToAtlasSize(BufferedImage source) {
        return ImageUtil.resizeImageRaw(source, IMAGE_SIZE, IMAGE_SIZE);
    }

    protected BufferedImage resizeToLayoutSize(BufferedImage source) {
        return ImageUtil.resizeImageRaw(source, layout.tileSize(), layout.tileSize());
    }

    protected abstract List<AtlasEntry> loadTextures();

    protected abstract JsonObject entryToJson(AtlasEntry entry);

    public void generate() {
        List<AtlasEntry> entries = loadTextures();
        System.out.println("Loaded " + entries.size() + " textures.");

        if (entries.isEmpty()) {
            System.err.println("No textures to pack!");
            return;
        }

        int capacity = layout.capacity();
        for (int start = 0, page = 0; start < entries.size(); page++) {
            int end = (int) Math.min(entries.size(), (long) start + capacity);
            List<AtlasEntry> batch = entries.subList(start, end);
            BufferedImage atlas = packAtlas(batch);
            for (AtlasEntry entry : batch) {
                entry.page = page;
                entry.atlasWidth = atlas.getWidth();
                entry.atlasHeight = atlas.getHeight();
            }
            saveAtlas(atlas, page);
            start = end;
        }
        saveCoordinatesJson(entries);

        System.out.println("Atlas generation complete!");
    }

    protected BufferedImage packAtlas(List<AtlasEntry> entries) {
        int texturesPerRow = layout.columns();
        int rows = (entries.size() + texturesPerRow - 1) / texturesPerRow;
        int atlasHeight = rows * layout.pitch();

        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        int[] atlasPixels = ((DataBufferInt) atlas.getRaster().getDataBuffer()).getData();

        for (int i = 0; i < entries.size(); i++) {
            AtlasEntry entry = entries.get(i);
            int col = i % texturesPerRow;
            int row = i / texturesPerRow;
            entry.x = col * layout.pitch() + layout.padding();
            entry.y = row * layout.pitch() + layout.padding();

            int[] texturePixels = ((DataBufferInt) entry.image.getRaster().getDataBuffer()).getData();
            for (int py = 0; py < layout.tileSize(); py++) {
                int atlasOffset = (entry.y + py) * atlasWidth + entry.x;
                int textureOffset = py * layout.tileSize();
                System.arraycopy(texturePixels, textureOffset, atlasPixels, atlasOffset, layout.tileSize());
            }

            double progress = (i + 1) * 100.0 / entries.size();
            System.out.printf("\rPacking textures: %d/%d (%.1f%%)", i + 1, entries.size(), progress);
        }
        System.out.println();

        return atlas;
    }

    protected void saveAtlas(BufferedImage atlas) {
        saveAtlasToPath(atlas, atlasOutputPath);
    }

    protected void saveAtlas(BufferedImage atlas, int page) {
        if (page == 0) saveAtlas(atlas);
        else saveAtlasToPath(atlas, pageOutputPath(page));
    }

    private void saveAtlasToPath(BufferedImage atlas, String path) {
        File outputFile = new File(path);
        File parentDir = outputFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            System.err.println("Failed to create directory: " + parentDir.getAbsolutePath());
            return;
        }

        try {
            System.out.println("Saving atlas to: " + outputFile.getAbsolutePath());
            ImageIO.write(atlas, "png", outputFile);
            System.out.println("Atlas saved successfully!");
        } catch (IOException e) {
            System.err.println("Failed to save atlas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    protected String pageOutputPath(int page) {
        if (page == 0) return atlasOutputPath;
        int dot = atlasOutputPath.lastIndexOf('.');
        return dot < 0 ? atlasOutputPath + "-" + page
            : atlasOutputPath.substring(0, dot) + "-" + page + atlasOutputPath.substring(dot);
    }

    protected void saveCoordinatesJson(List<AtlasEntry> entries) {
        File jsonFile = new File(jsonOutputPath);
        File parentDir = jsonFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            System.err.println("Failed to create directory: " + parentDir.getAbsolutePath());
            return;
        }

        JsonArray jsonArray = new JsonArray();
        for (AtlasEntry entry : entries) {
            JsonObject object = entryToJson(entry);
            object.addProperty("page", entry.page);
            object.addProperty("atlas", new File(pageOutputPath(entry.page)).getName());
            object.addProperty("atlasWidth", entry.atlasWidth);
            object.addProperty("atlasHeight", entry.atlasHeight);
            jsonArray.add(object);
        }

        try (FileWriter writer = new FileWriter(jsonFile)) {
            GSON.toJson(jsonArray, writer);
            System.out.println("Coordinates JSON saved to: " + jsonFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save coordinates JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Atlas entry with name, image, position, and optional metadata.
     */
    public static class AtlasEntry {
        public final String name;
        public final BufferedImage image;
        public final Map<String, String> metadata;
        public int page;
        public int atlasWidth;
        public int atlasHeight;
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

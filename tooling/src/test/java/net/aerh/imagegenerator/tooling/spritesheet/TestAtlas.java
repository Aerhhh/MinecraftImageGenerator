package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.List;

/**
 * An atlas generator fed a fixed list of entries, so tests can exercise packing and writing without a directory
 * of PNGs to load first.
 */
class TestAtlas extends AtlasGenerator {

    private final List<AtlasEntry> entries;

    TestAtlas(AtlasLayout layout, List<AtlasEntry> entries, Path atlasOutputPath, Path jsonOutputPath) {
        super(layout, atlasOutputPath, jsonOutputPath);
        this.entries = entries;
    }

    @Override
    protected List<AtlasEntry> loadTextures() {
        return entries;
    }

    @Override
    protected JsonObject entryToJson(AtlasEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("name", entry.name);
        json.addProperty("x", entry.x);
        json.addProperty("y", entry.y);
        json.addProperty("size", layout.tileSize());
        return json;
    }
}

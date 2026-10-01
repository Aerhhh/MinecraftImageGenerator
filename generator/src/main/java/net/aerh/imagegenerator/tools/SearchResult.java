package net.aerh.imagegenerator.tools;

import java.util.List;

/** Item ids from the vanilla spritesheet and item refs from registered packs that match a query. */
public record SearchResult(List<String> spritesheetResults, List<String> packResults) {

    public SearchResult {
        spritesheetResults = List.copyOf(spritesheetResults);
        packResults = List.copyOf(packResults);
    }

    public boolean isEmpty() {
        return spritesheetResults.isEmpty() && packResults.isEmpty();
    }
}

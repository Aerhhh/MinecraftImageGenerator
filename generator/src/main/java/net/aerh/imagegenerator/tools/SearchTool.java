package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.exception.GeneratorValidationException;
import net.aerh.imagegenerator.spritesheet.Spritesheet;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.Required;

import java.util.List;
import java.util.Objects;

/** Searches vanilla item ids and registered pack item refs by substring. */
public final class SearchTool {

    private final ResourcePackService packService;

    public SearchTool(ResourcePackService packService) {
        this.packService = Objects.requireNonNull(packService, "packService");
    }

    /**
     * @throws GeneratorValidationException when the query is missing
     */
    public SearchResult search(String query) {
        Required.check(query, "query");
        List<String> spritesheetResults = Spritesheet.searchForTexture(query).stream().map(pair -> pair.first()).toList();
        List<String> packResults = packService.searchItemRefs(query);
        return new SearchResult(spritesheetResults, packResults);
    }
}

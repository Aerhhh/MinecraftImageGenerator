package net.aerh.imagegenerator.tools.pack;

import java.util.List;

/**
 * What a candidate pack must satisfy before it may replace the live one.
 *
 * @param expectedPackFormat The format the pack was published as; its pack.mcmeta must cover it
 * @param minItemRatio       The candidate must index at least this share of the live pack's items (0 to 1)
 * @param sampleItems        Item refs that must exist in the candidate and render to visible pixels
 */
public record PackExpectations(int expectedPackFormat, double minItemRatio, List<String> sampleItems) {

    public PackExpectations {
        if (expectedPackFormat <= 0) {
            throw new IllegalArgumentException("expectedPackFormat must be positive, got: " + expectedPackFormat);
        }
        if (minItemRatio < 0 || minItemRatio > 1 || Double.isNaN(minItemRatio)) {
            throw new IllegalArgumentException("minItemRatio must be between 0 and 1, got: " + minItemRatio);
        }
        sampleItems = sampleItems == null ? List.of() : List.copyOf(sampleItems);
    }
}

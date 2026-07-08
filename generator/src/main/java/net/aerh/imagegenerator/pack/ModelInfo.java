package net.aerh.imagegenerator.pack;

/**
 * The subset of an item model JSON relevant to flat GUI rendering: the parent reference and the
 * layer0 texture reference. Either may be null.
 */
public record ModelInfo(String parentRef, String layer0Ref) {
}

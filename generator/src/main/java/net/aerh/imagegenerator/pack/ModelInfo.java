package net.aerh.imagegenerator.pack;

import org.jetbrains.annotations.Nullable;

/**
 * The subset of an item model JSON relevant to flat GUI rendering: the parent reference and the
 * layer0 texture reference. Either may be null.
 */
public record ModelInfo(@Nullable String parentRef, @Nullable String layer0Ref) {
}

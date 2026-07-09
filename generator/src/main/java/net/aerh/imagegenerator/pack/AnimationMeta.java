package net.aerh.imagegenerator.pack;

import org.jetbrains.annotations.Nullable;

/**
 * The subset of a texture {@code .mcmeta} animation section needed to extract a static first
 * frame from a vertical flipbook. The first frame is the first entry of the frames list, which is
 * not always index 0.
 */
record AnimationMeta(int firstFrameIndex, @Nullable Integer frameWidth, @Nullable Integer frameHeight) {
}

package net.aerh.imagegenerator.pack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The animation section of a texture {@code .mcmeta} file, parsed to the full vanilla model:
 * the default per-frame time ({@code frametime}, ticks), the explicit frames list (int entries
 * and {@code {index, time}} objects, where a per-frame time overrides the default), the
 * optional frame-size overrides and the {@code interpolate} flag (parsed and carried; rendering
 * treats animations as nearest-frame, a documented approximation).
 *
 * @param frames               the explicit frame sequence, or null when the mcmeta declares no
 *                             frames list - the default order is then every frame of the
 *                             flipbook top to bottom, each lasting {@code defaultFrameTimeTicks}
 * @param defaultFrameTimeTicks the {@code frametime} value in ticks (vanilla default 1)
 * @param frameWidth           explicit frame width, or null (defaults to the texture width)
 * @param frameHeight          explicit frame height, or null (defaults to the frame width - the
 *                             square-frames vertical-flipbook convention shared with
 *                             {@link TextureDecoder#firstFrame})
 * @param interpolate          the parsed {@code interpolate} flag; rendering ignores it
 */
record AnimationMeta(@Nullable List<FrameEntry> frames, int defaultFrameTimeTicks,
                     @Nullable Integer frameWidth, @Nullable Integer frameHeight, boolean interpolate) {

    /** One entry of an explicit frames list: the flipbook index and its duration in ticks. */
    record FrameEntry(int index, int timeTicks) {
    }

    /**
     * Compatibility shape predating full animation parsing: a single known first frame index
     * plus the frame-size overrides. A zero index maps to the absent-frames default order.
     */
    AnimationMeta(int firstFrameIndex, @Nullable Integer frameWidth, @Nullable Integer frameHeight) {
        this(firstFrameIndex == 0 ? null : List.of(new FrameEntry(firstFrameIndex, 1)), 1,
            frameWidth, frameHeight, false);
    }

    /**
     * The flipbook index of the first frame the animation shows - the first entry of the frames
     * list, which is not always index 0; index 0 when the list is absent (default order).
     */
    int firstFrameIndex() {
        return frames == null || frames.isEmpty() ? 0 : frames.getFirst().index();
    }
}

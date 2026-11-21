package net.aerh.imagegenerator.image;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.GeneratorTimeoutException;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.AnimationTimeline;
import net.aerh.imagegenerator.util.AnimatedGifEncoder;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class GeneratorImageBuilder {

    private static final int IMAGE_PADDING_PX = 25;
    private static final int IMAGE_BORDER_PADDING_PX = 15;

    /** System property: how long one {@link #build()} may take, in milliseconds. */
    public static final String TIMEOUT_PROPERTY = "image.generator.timeout.ms";
    /** System property: how many builds may render at once; further builds wait for a slot. Read once, at class load. */
    public static final String MAX_CONCURRENT_RENDERS_PROPERTY = "image.generator.maxConcurrentRenders";

    private static final long DEFAULT_TIMEOUT_MS = 20_000L;

    private static final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private static final Semaphore RENDER_SLOTS = new Semaphore(maxConcurrentRenders(), true);
    private static final ThreadLocal<Boolean> INSIDE_RENDER = ThreadLocal.withInitial(() -> false);
    private final List<Generator> generators;
    private @Nullable GenerationContext context;

    /**
     * Default constructor for the {@link GeneratorImageBuilder} class.
     */
    public GeneratorImageBuilder() {
        this.generators = new ArrayList<>();
    }

    /**
     * Set the {@link GenerationContext} for this builder.
     *
     * @param context The {@link GenerationContext context} to use, or null.
     *
     * @return The {@link GeneratorImageBuilder builder} instance.
     */
    public GeneratorImageBuilder withContext(@Nullable GenerationContext context) {
        this.context = context;
        return this;
    }

    /**
     * Add a {@link Generator} to the list of {@link Generator generators}.
     * The {@link Generator} will be added to the end of the list, and thus will be drawn last
     * or wherever the generator is positioned in the list.
     *
     * @param generator The {@link Generator generator} to add.
     *
     * @return The {@link GeneratorImageBuilder builder} instance.
     */
    public GeneratorImageBuilder addGenerator(Generator generator) {
        this.generators.add(generator);
        return this;
    }

    /**
     * Add a {@link Generator} at a specific position in the list of {@link Generator generators}.
     * The list is 0-indexed.
     *
     * @param position  The position to add the {@link Generator generator} at. 0-indexed.
     * @param generator The {@link Generator generator} to add.
     *
     * @return The {@link GeneratorImageBuilder builder} instance.
     */
    public GeneratorImageBuilder addGenerator(int position, Generator generator) {
        this.generators.add(position, generator);
        return this;
    }

    /**
     * Build the final image from the list of {@link Generator generators}.
     * The image will be generated in the order that the generators were added.
     * If the generation fails a {@link GeneratorException} is thrown; if it times out, or no render slot
     * frees up in time, a {@link GeneratorTimeoutException} (a {@link GeneratorException}) is thrown and
     * the render task is cancelled.
     *
     * @return The final {@link GeneratedObject image}.
     *
     * @throws GeneratorException if the generation fails or times out.
     */
    public GeneratedObject build() {
        try {
            return runTimed(this::buildInternal);
        } catch (ExecutionException exception) {
            throw new GeneratorException(exception.getCause().getMessage(), exception);
        }
    }

    /** Work that {@link #withinTimeout(TimedWork)} runs under the render timeout. */
    @FunctionalInterface
    public interface TimedWork<T> {
        T run() throws IOException;
    }

    /**
     * Runs a whole request under one render timeout and one render slot: the parsing that turns
     * request text into generators as well as the {@link #build()} that draws them. On its own,
     * {@link #build()} only times the drawing, leaving the parsing before it unbounded.
     * <p>
     * A {@link #build()} called by the work shares this call's slot, and is cancelled with it when
     * the timeout passes. Called from inside a render, the work simply runs in place, since the
     * render it belongs to is already timed. Exceptions thrown by the work reach the caller unchanged.
     *
     * @throws GeneratorTimeoutException if the work times out, or no render slot frees up in time
     * @throws IOException               if the work throws one
     */
    public static <T> T withinTimeout(TimedWork<T> work) throws IOException {
        if (INSIDE_RENDER.get()) {
            return work.run();
        }
        try {
            return runTimed(work::run);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new GeneratorException(cause.getMessage(), exception);
        }
    }

    /**
     * Runs the work on a render task, waiting at most the render timeout for a slot and the result.
     *
     * @throws ExecutionException if the work itself throws
     */
    private static <T> T runTimed(Callable<T> work) throws ExecutionException {
        long timeoutMs = Long.getLong(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT_MS);
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

        // A build started from inside a render task already holds a slot; taking a second one
        // could deadlock once every slot is held by such a parent.
        boolean nested = INSIDE_RENDER.get();
        if (!nested) {
            acquireRenderSlot(timeoutMs);
        }

        Future<T> future;
        try {
            future = executorService.submit(() -> {
                INSIDE_RENDER.set(true);
                try {
                    return work.call();
                } finally {
                    // Released by the task, not the waiting caller, so a render that is still
                    // winding down after a timeout keeps counting against the limit.
                    if (!nested) {
                        RENDER_SLOTS.release();
                    }
                }
            });
        } catch (RuntimeException exception) {
            if (!nested) {
                RENDER_SLOTS.release();
            }
            throw exception;
        }

        try {
            long remainingNanos = Math.max(0, deadlineNanos - System.nanoTime());
            return future.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new GeneratorTimeoutException("Image generation timed out", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new GeneratorException("Image generation was interrupted", exception);
        }
    }

    /**
     * Waits for a free render slot, for at most the render timeout.
     *
     * @throws GeneratorTimeoutException if no slot frees up in time
     */
    private static void acquireRenderSlot(long timeoutMs) {
        try {
            if (!RENDER_SLOTS.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new GeneratorTimeoutException("Image generation timed out waiting for a free render slot");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneratorException("Image generation was interrupted", exception);
        }
    }

    private static int maxConcurrentRenders() {
        int configured = Integer.getInteger(MAX_CONCURRENT_RENDERS_PROPERTY, 0);
        return configured > 0 ? configured : Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Internal method used to build the final output image from the list of {@link Generator generators}.
     *
     * @return The final {@link GeneratedObject image}.
     */
    private GeneratedObject buildInternal() throws GeneratorException {
        // Generate all objects first
        List<GeneratedObject> generatedObjects = new ArrayList<>();
        for (Generator generator : generators) {
            RenderCancellation.check();
            try {
                generatedObjects.add(generator.generate(this.context));
            } catch (Exception e) {
                throw new GeneratorException("Error generating object from generator: " + e.getMessage(), e);
            }
        }

        boolean isAnimated = generatedObjects.stream().anyMatch(GeneratedObject::isAnimated);

        if (isAnimated) {
            try {
                return buildAnimatedInternal(generatedObjects);
            } catch (IOException e) {
                log.error("Failed to build animated GIF", e);
                throw new GeneratorException("Failed to build animated GIF: " + e.getMessage(), e);
            }
        } else {
            return buildStaticInternal(generatedObjects);
        }
    }

    /**
     * Builds a static composite image from non-animated GeneratedObjects.
     */
    private GeneratedObject buildStaticInternal(List<GeneratedObject> generatedObjects) throws GeneratorException {
        if (generatedObjects.isEmpty()) {
            throw new GeneratorException("No generators provided to build an image.");
        }

        int totalWidth = 0;
        int maxHeight = 0;

        // Calculate dimensions
        for (int i = 0; i < generatedObjects.size(); i++) {
            GeneratedObject generatedObject = generatedObjects.get(i);
            BufferedImage generatedImage = generatedObject.getImage();

            if (generatedImage == null) {
                throw new GeneratorException("Generated image is null for " + generatedObject.getClass().getSimpleName());
            }

            totalWidth += generatedImage.getWidth();
            if (i < generatedObjects.size() - 1) { // Add padding except for the last image
                totalWidth += IMAGE_PADDING_PX;
            }
            maxHeight = Math.max(maxHeight, generatedImage.getHeight());
        }

        if (totalWidth <= 0 || maxHeight <= 0) {
            throw new GeneratorException("Calculated image dimensions are invalid (width=" + totalWidth + ", height=" + maxHeight + ")");
        }

        RenderLimits.checkCanvas((long) totalWidth + 2 * IMAGE_BORDER_PADDING_PX, (long) maxHeight + 2 * IMAGE_BORDER_PADDING_PX);

        BufferedImage finalImage = new BufferedImage(totalWidth + 2 * IMAGE_BORDER_PADDING_PX, maxHeight + 2 * IMAGE_BORDER_PADDING_PX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = finalImage.createGraphics();
        try {
            int currentX = IMAGE_BORDER_PADDING_PX;

            // Draw images
            for (GeneratedObject generatedObject : generatedObjects) {
                BufferedImage generatedImage = generatedObject.getImage();
                int yOffset = IMAGE_BORDER_PADDING_PX + (maxHeight - generatedImage.getHeight()) / 2;
                graphics.drawImage(generatedImage, currentX, yOffset, null);
                currentX += generatedImage.getWidth() + IMAGE_PADDING_PX;
            }
        } finally {
            graphics.dispose();
        }

        return new GeneratedObject(finalImage);
    }

    /**
     * Builds an animated GIF from {@link GeneratedObject} instances.
     */
    private GeneratedObject buildAnimatedInternal(List<GeneratedObject> generatedObjects) throws IOException, GeneratorException {
        if (generatedObjects.isEmpty()) {
            throw new GeneratorException("No generators provided to build an image.");
        }

        // When any object carries PER-FRAME delays (tick-timed pack texture animations), the
        // uniform maxFrames/modulo path below would collapse its variable timing to one delay -
        // a 5-second hold becoming 100 ms. Those composites go through the shared timeline
        // instead. Composites of only uniform-delay animations (the enchant glint) keep the
        // established path below, byte-identical.
        boolean perFrameDelays = generatedObjects.stream()
            .anyMatch(obj -> obj.isAnimated() && obj.getFrameDelaysMs() != null);
        if (perFrameDelays) {
            return buildTickTimedComposite(generatedObjects);
        }

        int maxFrames = 1;
        int frameDelayMs = 50; // Default delay
        boolean delaySet = false;

        // Find max frames and first delay
        for (GeneratedObject obj : generatedObjects) {
            if (obj.isAnimated()) {
                maxFrames = Math.max(maxFrames, obj.getAnimationFrames().size());
                if (!delaySet && obj.getFrameDelayMs() > 0) {
                    frameDelayMs = obj.getFrameDelayMs();
                    delaySet = true;
                }
            }
        }

        // Calculate dimensions
        int totalWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < generatedObjects.size(); i++) {
            GeneratedObject generatedObject = generatedObjects.get(i);
            BufferedImage frameImage = generatedObject.getImage(); // Use the stored first frame/static image

            if (frameImage == null) {
                throw new GeneratorException("Generated image (frame 0) is null for " + generatedObject.getClass().getSimpleName());
            }

            totalWidth += frameImage.getWidth();
            if (i < generatedObjects.size() - 1) { // Add padding except for the last image
                totalWidth += IMAGE_PADDING_PX;
            }
            maxHeight = Math.max(maxHeight, frameImage.getHeight());
        }

        if (totalWidth <= 0 || maxHeight <= 0) {
            throw new GeneratorException("Calculated animated image dimensions are invalid (width=" + totalWidth + ", height=" + maxHeight + ")");
        }

        RenderLimits.checkFrames((long) totalWidth + 2 * IMAGE_BORDER_PADDING_PX, (long) maxHeight + 2 * IMAGE_BORDER_PADDING_PX, maxFrames);

        List<BufferedImage> compositeFrames = new ArrayList<>();

        // Generate each frame
        for (int frameIndex = 0; frameIndex < maxFrames; frameIndex++) {
            RenderCancellation.check();
            BufferedImage compositeFrame = new BufferedImage(totalWidth + 2 * IMAGE_BORDER_PADDING_PX, maxHeight + 2 * IMAGE_BORDER_PADDING_PX, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = compositeFrame.createGraphics();
            try {
                int currentX = IMAGE_BORDER_PADDING_PX;

                for (GeneratedObject obj : generatedObjects) {
                    BufferedImage currentFrameImage;

                    if (obj.isAnimated()) {
                        List<BufferedImage> frames = obj.getAnimationFrames();

                        if (frames == null || frames.isEmpty()) {
                            throw new GeneratorException("Animated object has null or empty frames list: " + obj.getClass().getSimpleName());
                        }
                        currentFrameImage = frames.get(frameIndex % frames.size());
                    } else {
                        currentFrameImage = obj.getImage();
                    }

                    int yOffset = IMAGE_BORDER_PADDING_PX + (maxHeight - currentFrameImage.getHeight()) / 2;
                    graphics.drawImage(currentFrameImage, currentX, yOffset, null);
                    currentX += currentFrameImage.getWidth() + IMAGE_PADDING_PX;
                }
            } finally {
                graphics.dispose();
            }

            compositeFrames.add(compositeFrame);
        }

        byte[] gifData = ImageUtil.toGifBytes(compositeFrames, frameDelayMs, true);

        return new GeneratedObject(gifData, compositeFrames, frameDelayMs);
    }

    /**
     * Composites objects whose animations carry PER-FRAME delays (tick-timed pack texture
     * animations) onto one shared {@link AnimationTimeline}, so a variable-delay animation - a
     * shiny hold, mixed 2-tick and 100-tick frames - keeps its authored timing instead of
     * collapsing to a single uniform delay. Each animated object is a timeline source over its
     * own frames (per-frame delays converted to ticks; any uniform-delay animation in the mix
     * expands to one tick duration per frame); static objects draw their single image in every
     * step. The honest per-step delays go through {@link AnimatedGifEncoder}, which itself falls
     * back to the uniform encoder when every step happens to share a delay.
     */
    private GeneratedObject buildTickTimedComposite(List<GeneratedObject> generatedObjects)
        throws IOException, GeneratorException {
        List<GeneratedObject> animated = generatedObjects.stream().filter(GeneratedObject::isAnimated).toList();
        List<List<Integer>> sources = new ArrayList<>(animated.size());
        for (GeneratedObject obj : animated) {
            sources.add(obj.timelineTicks());
        }
        AnimationTimeline timeline = AnimationTimeline.of(sources);

        // Dimensions from the stored first frame / static image, exactly as the uniform path.
        int totalWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < generatedObjects.size(); i++) {
            BufferedImage frameImage = generatedObjects.get(i).getImage();
            if (frameImage == null) {
                throw new GeneratorException("Generated image (frame 0) is null for "
                    + generatedObjects.get(i).getClass().getSimpleName());
            }
            totalWidth += frameImage.getWidth();
            if (i < generatedObjects.size() - 1) {
                totalWidth += IMAGE_PADDING_PX;
            }
            maxHeight = Math.max(maxHeight, frameImage.getHeight());
        }
        if (totalWidth <= 0 || maxHeight <= 0) {
            throw new GeneratorException("Calculated animated image dimensions are invalid (width="
                + totalWidth + ", height=" + maxHeight + ")");
        }

        RenderLimits.checkFrames((long) totalWidth + 2 * IMAGE_BORDER_PADDING_PX, (long) maxHeight + 2 * IMAGE_BORDER_PADDING_PX, timeline.steps().size());

        List<BufferedImage> compositeFrames = new ArrayList<>(timeline.steps().size());
        for (AnimationTimeline.Step step : timeline.steps()) {
            RenderCancellation.check();
            BufferedImage compositeFrame = new BufferedImage(totalWidth + 2 * IMAGE_BORDER_PADDING_PX, maxHeight + 2 * IMAGE_BORDER_PADDING_PX, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = compositeFrame.createGraphics();
            try {
                int currentX = IMAGE_BORDER_PADDING_PX;
                int animatedIndex = 0;

                for (GeneratedObject obj : generatedObjects) {
                    BufferedImage currentFrameImage;
                    if (obj.isAnimated()) {
                        List<BufferedImage> frames = obj.getAnimationFrames();
                        if (frames == null || frames.isEmpty()) {
                            throw new GeneratorException("Animated object has null or empty frames list: "
                                + obj.getClass().getSimpleName());
                        }
                        currentFrameImage = frames.get(step.framePositions().get(animatedIndex));
                        animatedIndex++;
                    } else {
                        currentFrameImage = obj.getImage();
                    }

                    int yOffset = IMAGE_BORDER_PADDING_PX + (maxHeight - currentFrameImage.getHeight()) / 2;
                    graphics.drawImage(currentFrameImage, currentX, yOffset, null);
                    currentX += currentFrameImage.getWidth() + IMAGE_PADDING_PX;
                }
            } finally {
                graphics.dispose();
            }

            compositeFrames.add(compositeFrame);
        }

        List<Integer> delaysMs = timeline.stepDelaysMillis();
        byte[] gifData = AnimatedGifEncoder.encode(compositeFrames, delaysMs);
        return new GeneratedObject(gifData, compositeFrames, delaysMs);
    }
}

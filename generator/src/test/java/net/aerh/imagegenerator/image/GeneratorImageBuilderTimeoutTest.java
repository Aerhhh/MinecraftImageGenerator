package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.exception.GeneratorTimeoutException;
import net.aerh.imagegenerator.item.GeneratedObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The build timeout must actually end the render: the task is interrupted, the render loops notice
 * through {@link RenderCancellation#check()}, and a render that outlived its caller is not cached.
 */
class GeneratorImageBuilderTimeoutTest {

    @BeforeEach
    void shortTimeout() {
        System.setProperty(GeneratorImageBuilder.TIMEOUT_PROPERTY, "200");
    }

    @AfterEach
    void restoreTimeout() {
        System.clearProperty(GeneratorImageBuilder.TIMEOUT_PROPERTY);
    }

    /** Spins like a render loop until cancelled, then records that it stopped. */
    private static final class SpinningGenerator implements Generator {
        final CountDownLatch stopped = new CountDownLatch(1);
        final AtomicBoolean finishedNormally = new AtomicBoolean();

        @Override
        public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
            try {
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                while (System.nanoTime() < end) {
                    RenderCancellation.check();
                }
                finishedNormally.set(true);
                return new GeneratedObject(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
            } finally {
                stopped.countDown();
            }
        }
    }

    /** Ignores cancellation, as a render stuck in code without a check would, then returns normally. */
    private static final class OverrunningGenerator implements Generator {
        final CountDownLatch returned = new CountDownLatch(1);

        @Override
        public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(600);
            while (System.nanoTime() < end) {
                Thread.onSpinWait();
            }
            returned.countDown();
            return new GeneratedObject(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        }
    }

    @Test
    void timeoutThrowsTimeoutExceptionAndStopsTheRender() throws InterruptedException {
        SpinningGenerator generator = new SpinningGenerator();

        GeneratorTimeoutException thrown = assertThrows(GeneratorTimeoutException.class,
            () -> new GeneratorImageBuilder().addGenerator(generator).build());

        assertEquals("Image generation timed out", thrown.getMessage());
        assertTrue(generator.stopped.await(5, TimeUnit.SECONDS), "the cancelled render must stop promptly");
        assertTrue(!generator.finishedNormally.get(), "the render must be cut short, not run to completion");
    }

    @Test
    void renderThatFinishesAfterCancellationIsNotHandedToTheCache() throws InterruptedException {
        // generate() checks for cancellation between render and the cache write, so a render that
        // ignored the interrupt and ran to completion is still discarded rather than cached.
        OverrunningGenerator generator = new OverrunningGenerator();
        Throwable[] thrown = new Throwable[1];
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                generator.generate(null);
            } catch (Throwable throwable) {
                thrown[0] = throwable;
            }
        });
        worker.interrupt();
        worker.join(5_000);

        assertEquals(0, generator.returned.getCount(), "the render itself ran to completion");
        assertTrue(thrown[0] instanceof GeneratorTimeoutException,
            "a cancelled generate() must throw instead of caching, got: " + thrown[0]);
    }

    @Test
    void buildStartedInsideARenderDoesNotNeedASecondSlot() {
        Generator inner = context -> new GeneratedObject(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));
        Generator outer = context -> new GeneratorImageBuilder().addGenerator(inner).build();

        System.setProperty(GeneratorImageBuilder.TIMEOUT_PROPERTY, "10000");
        GeneratedObject result = new GeneratorImageBuilder().addGenerator(outer).build();
        assertTrue(result.getImage().getWidth() > 4);
    }
}

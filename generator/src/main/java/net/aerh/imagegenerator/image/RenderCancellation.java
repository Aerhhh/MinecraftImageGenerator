package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.exception.GeneratorTimeoutException;

/**
 * The cooperative cancellation point for renders. {@link GeneratorImageBuilder#build()} interrupts
 * its render task when the timeout passes, but an interrupt alone stops nothing: image loops never
 * block, so they would run to completion and keep their memory. The render loops call
 * {@link #check()} once per frame, line or item so a cancelled render ends promptly.
 */
public final class RenderCancellation {

    private RenderCancellation() {
    }

    /**
     * Ends the current render if its thread has been interrupted. The interrupt flag is left set so
     * callers further up the stack can see it too.
     *
     * @throws GeneratorTimeoutException if the current thread is interrupted
     */
    public static void check() {
        if (Thread.currentThread().isInterrupted()) {
            throw new GeneratorTimeoutException("Image generation was cancelled");
        }
    }
}

package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.exception.GeneratorBusyException;
import net.aerh.imagegenerator.exception.GeneratorTimeoutException;
import net.aerh.imagegenerator.exception.GeneratorValidationException;
import org.jetbrains.annotations.Nullable;

/**
 * The pixel budget shared by every render running in this JVM. {@link RenderLimits} bounds one
 * image, but several renders at their limit at once still add up to more than the heap, so each
 * render also reserves its pixels here and gives them all back when it ends.
 *
 * <p>A render that needs more than is free waits for other renders to finish. It is refused with a
 * {@link GeneratorBusyException} only when waiting could not help: every other render holding
 * pixels is itself waiting. A render that on its own needs more than the whole budget is refused
 * outright.</p>
 *
 * <p>Reservations belong to the render started by {@link GeneratorImageBuilder#build()} or
 * {@link GeneratorImageBuilder#withinTimeout}; builds nested inside it share its reservation.
 * A generator called directly, outside any build, reserves nothing.</p>
 *
 * <p>The budget is read from {@value #MAX_CONCURRENT_PIXELS_PROPERTY} on each reservation. It
 * defaults to half the maximum heap, at four bytes per pixel.</p>
 */
public final class RenderBudget {

    public static final String MAX_CONCURRENT_PIXELS_PROPERTY = "generator.render.maxConcurrentPixels";

    private static final Object LOCK = new Object();
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private static long inFlightPixels;
    private static int activeScopes;
    private static int waitingScopes;

    private RenderBudget() {
    }

    /** One render's reservations. Shared by the tasks of nested builds, released when the last one ends. */
    static final class Scope {
        private long reservedPixels;
        private int users;
        private int waiters;
    }

    /** The most pixels all running renders may hold together. */
    public static long maxConcurrentPixels() {
        Long configured = Long.getLong(MAX_CONCURRENT_PIXELS_PROPERTY);
        if (configured != null && configured > 0) {
            return configured;
        }
        return Math.max(1, Runtime.getRuntime().maxMemory() / 8);
    }

    /** The pixels currently reserved by running renders. */
    public static long inFlightPixels() {
        synchronized (LOCK) {
            return inFlightPixels;
        }
    }

    /** The scope of the render running on this thread, or null outside a render. */
    static @Nullable Scope current() {
        return CURRENT.get();
    }

    /**
     * Makes this thread part of a render: the parent's when a build is nested inside another
     * render, otherwise a new one. Must be paired with {@link #exit(Scope)}.
     */
    static Scope enter(@Nullable Scope parent) {
        Scope scope = parent != null ? parent : new Scope();
        synchronized (LOCK) {
            if (scope.users++ == 0) {
                activeScopes++;
            }
        }
        CURRENT.set(scope);
        return scope;
    }

    /** Leaves the render; when its last task leaves, everything it reserved is given back. */
    static void exit(Scope scope) {
        CURRENT.remove();
        synchronized (LOCK) {
            if (--scope.users == 0) {
                inFlightPixels -= scope.reservedPixels;
                scope.reservedPixels = 0;
                activeScopes--;
                LOCK.notifyAll();
            }
        }
    }

    /**
     * Reserves pixels for the render running on this thread, waiting for other renders to finish
     * if they are not free yet. Does nothing outside a render.
     *
     * @throws GeneratorValidationException if this render alone would need more than the whole budget
     * @throws GeneratorBusyException       if the pixels are not free and no running render can free them
     * @throws GeneratorTimeoutException    if the render is cancelled while waiting
     */
    static void reserve(long pixels) {
        Scope scope = CURRENT.get();
        if (scope == null || pixels <= 0) {
            return;
        }
        long budget = maxConcurrentPixels();
        synchronized (LOCK) {
            if (pixels > budget - scope.reservedPixels) {
                throw new GeneratorValidationException(
                    "Image is too large to render: it needs more than the %s pixels all renders may use together",
                    String.valueOf(budget));
            }
            while (pixels > budget - inFlightPixels) {
                // Waiting only helps while some other render can still finish and give pixels back.
                int waitingIfThisWaits = waitingScopes + (scope.waiters == 0 ? 1 : 0);
                if (waitingIfThisWaits >= activeScopes) {
                    throw new GeneratorBusyException("The renderer is busy with other large images, try again shortly");
                }
                if (scope.waiters++ == 0) {
                    waitingScopes++;
                }
                try {
                    LOCK.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new GeneratorTimeoutException("Image generation was cancelled");
                } finally {
                    if (--scope.waiters == 0) {
                        waitingScopes--;
                    }
                }
            }
            inFlightPixels += pixels;
            scope.reservedPixels += pixels;
        }
    }
}

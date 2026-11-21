package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.exception.GeneratorBusyException;
import net.aerh.imagegenerator.exception.GeneratorTimeoutException;
import net.aerh.imagegenerator.exception.GeneratorValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The budget shared by all running renders: reservations are per render, are given back when the
 * render ends, make a second render wait while the first can still finish, and refuse instead of
 * waiting when nothing could ever free the pixels.
 */
class RenderBudgetTest {

    @BeforeEach
    void smallBudget() {
        System.setProperty(RenderBudget.MAX_CONCURRENT_PIXELS_PROPERTY, "1000");
    }

    @AfterEach
    void restoreBudget() {
        System.clearProperty(RenderBudget.MAX_CONCURRENT_PIXELS_PROPERTY);
    }

    @Test
    void reservingOutsideARenderDoesNothing() {
        long before = RenderBudget.inFlightPixels();
        RenderBudget.reserve(5_000);
        assertEquals(before, RenderBudget.inFlightPixels());
    }

    @Test
    void reservationsAreReleasedWhenTheRenderEnds() {
        long before = RenderBudget.inFlightPixels();
        RenderBudget.Scope scope = RenderBudget.enter(null);
        try {
            RenderBudget.reserve(300);
            RenderBudget.reserve(200);
            assertEquals(before + 500, RenderBudget.inFlightPixels());
        } finally {
            RenderBudget.exit(scope);
        }
        assertEquals(before, RenderBudget.inFlightPixels());
        assertNull(RenderBudget.current());
    }

    @Test
    void aRenderLargerThanTheWholeBudgetIsRefusedOutright() {
        RenderBudget.Scope scope = RenderBudget.enter(null);
        try {
            RenderBudget.reserve(600);
            assertThrows(GeneratorValidationException.class, () -> RenderBudget.reserve(600));
        } finally {
            RenderBudget.exit(scope);
        }
    }

    @Test
    void aNestedBuildSharesItsParentsReservation() throws InterruptedException {
        long before = RenderBudget.inFlightPixels();
        RenderBudget.Scope parent = RenderBudget.enter(null);
        try {
            Thread child = Thread.ofVirtual().start(() -> {
                RenderBudget.Scope scope = RenderBudget.enter(parent);
                try {
                    RenderBudget.reserve(400);
                } finally {
                    RenderBudget.exit(scope);
                }
            });
            child.join(5_000);
            assertEquals(before + 400, RenderBudget.inFlightPixels(), "the child's pixels stay reserved while the parent runs");
        } finally {
            RenderBudget.exit(parent);
        }
        assertEquals(before, RenderBudget.inFlightPixels());
    }

    @Test
    void aSecondRenderWaitsForTheFirstToFinish() throws InterruptedException {
        CountDownLatch firstHolds = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondReserved = new CountDownLatch(1);

        Thread first = Thread.ofVirtual().start(() -> {
            RenderBudget.Scope scope = RenderBudget.enter(null);
            try {
                RenderBudget.reserve(800);
                firstHolds.countDown();
                releaseFirst.await();
            } catch (InterruptedException ignored) {
                // test teardown
            } finally {
                RenderBudget.exit(scope);
            }
        });
        assertTrue(firstHolds.await(5, TimeUnit.SECONDS));

        Thread second = Thread.ofVirtual().start(() -> {
            RenderBudget.Scope scope = RenderBudget.enter(null);
            try {
                RenderBudget.reserve(800);
                secondReserved.countDown();
            } finally {
                RenderBudget.exit(scope);
            }
        });

        assertTrue(!secondReserved.await(300, TimeUnit.MILLISECONDS), "the second render must wait while the first holds the pixels");
        releaseFirst.countDown();
        assertTrue(secondReserved.await(5, TimeUnit.SECONDS), "the second render must go ahead once the first ends");
        first.join(5_000);
        second.join(5_000);
    }

    @Test
    void aRenderIsRefusedWhenEveryOtherRenderIsWaitingToo() throws InterruptedException {
        CountDownLatch bothHold = new CountDownLatch(2);
        CountDownLatch firstMayAsk = new CountDownLatch(1);
        AtomicReference<Throwable> firstOutcome = new AtomicReference<>();
        AtomicReference<Throwable> secondOutcome = new AtomicReference<>();
        CountDownLatch firstWaiting = new CountDownLatch(1);

        Thread first = Thread.ofVirtual().start(() -> {
            RenderBudget.Scope scope = RenderBudget.enter(null);
            try {
                RenderBudget.reserve(500);
                bothHold.countDown();
                firstMayAsk.await();
                firstWaiting.countDown();
                RenderBudget.reserve(400);
            } catch (Throwable throwable) {
                firstOutcome.set(throwable);
            } finally {
                RenderBudget.exit(scope);
            }
        });
        Thread second = Thread.ofVirtual().start(() -> {
            RenderBudget.Scope scope = RenderBudget.enter(null);
            try {
                RenderBudget.reserve(400);
                bothHold.countDown();
                firstWaiting.await();
                // Give the first render time to block in its second reservation.
                Thread.sleep(300);
                RenderBudget.reserve(400);
            } catch (Throwable throwable) {
                secondOutcome.set(throwable);
            } finally {
                RenderBudget.exit(scope);
            }
        });

        assertTrue(bothHold.await(5, TimeUnit.SECONDS));
        firstMayAsk.countDown();
        first.join(5_000);
        second.join(5_000);

        assertInstanceOf(GeneratorBusyException.class, secondOutcome.get(), "waiting could not help the second render");
        assertNull(firstOutcome.get(), "the first render goes ahead once the second gives its pixels back");
    }

    @Test
    void aCancelledRenderStopsWaiting() throws InterruptedException {
        CountDownLatch holderReady = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicReference<Throwable> outcome = new AtomicReference<>();

        Thread holder = Thread.ofVirtual().start(() -> {
            RenderBudget.Scope scope = RenderBudget.enter(null);
            try {
                RenderBudget.reserve(900);
                holderReady.countDown();
                releaseHolder.await();
            } catch (InterruptedException ignored) {
                // test teardown
            } finally {
                RenderBudget.exit(scope);
            }
        });
        assertTrue(holderReady.await(5, TimeUnit.SECONDS));

        Thread waiter = Thread.ofVirtual().start(() -> {
            RenderBudget.Scope scope = RenderBudget.enter(null);
            try {
                RenderBudget.reserve(900);
            } catch (Throwable throwable) {
                outcome.set(throwable);
            } finally {
                RenderBudget.exit(scope);
            }
        });
        Thread.sleep(200);
        waiter.interrupt();
        waiter.join(5_000);
        releaseHolder.countDown();
        holder.join(5_000);

        assertInstanceOf(GeneratorTimeoutException.class, outcome.get());
    }
}

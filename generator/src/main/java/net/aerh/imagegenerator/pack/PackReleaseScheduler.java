package net.aerh.imagegenerator.pack;

import net.hypixel.nerdbot.marmalade.concurrent.ExecutorFactory;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Decides when a pack replaced by {@link PackRepository#replace(PreparedPack)} releases its caches
 * and closes its source. Renders that resolved the old pack before the swap may still be reading
 * its zip, so the default waits a grace period first.
 */
@FunctionalInterface
public interface PackReleaseScheduler {

    String GRACE_PROPERTY = "generator.pack.releaseGraceMs";
    long DEFAULT_GRACE_MS = 30_000L;

    void schedule(Runnable release);

    /** Releases on the calling thread straight away. */
    static PackReleaseScheduler immediate() {
        return Runnable::run;
    }

    /** Releases on a shared daemon thread after {@code grace}; a zero grace releases immediately. */
    static PackReleaseScheduler delayed(Duration grace) {
        if (grace.isNegative()) {
            throw new IllegalArgumentException("Release grace must not be negative, got: " + grace);
        }

        if (grace.isZero()) {
            return immediate();
        }

        long graceMs = grace.toMillis();
        return release -> Holder.EXECUTOR.schedule(release, graceMs, TimeUnit.MILLISECONDS);
    }

    /**
     * {@link #delayed(Duration)} with the grace from {@value #GRACE_PROPERTY}, default {@value #DEFAULT_GRACE_MS} ms.
     * A value that is not a valid number, or is negative, falls back to the default with a warning.
     */
    static PackReleaseScheduler fromSystemProperties() {
        long graceMs = DEFAULT_GRACE_MS;
        String raw = System.getProperty(GRACE_PROPERTY);

        if (raw != null) {
            try {
                graceMs = Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                LoggerFactory.getLogger(PackReleaseScheduler.class)
                    .warn("Ignoring invalid {}={}, using the default of {} ms", GRACE_PROPERTY, raw, DEFAULT_GRACE_MS);
            }
        }

        if (graceMs < 0) {
            LoggerFactory.getLogger(PackReleaseScheduler.class)
                .warn("Ignoring negative {}={}, using the default of {} ms", GRACE_PROPERTY, graceMs, DEFAULT_GRACE_MS);
            graceMs = DEFAULT_GRACE_MS;
        }

        return delayed(Duration.ofMillis(graceMs));
    }

    /** Lazily created so repositories that never replace a pack never start the thread. */
    final class Holder {
        private static final ScheduledExecutorService EXECUTOR = ExecutorFactory.newScheduledDaemon("pack-release");

        private Holder() {
        }
    }
}

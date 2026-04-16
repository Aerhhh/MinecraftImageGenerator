package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when a rendering operation exceeds its configured time limit.
 *
 * <p>The timeout duration in milliseconds is accessible via {@link #getTimeoutMs()} and is also
 * stored in the diagnostic context under the key {@code "timeoutMs"}.
 */
public class RenderTimeoutException extends RenderException {

    private final long timeoutMs;

    /**
     * Constructs a {@code RenderTimeoutException} for the given timeout duration.
     *
     * @param timeoutMs the time limit in milliseconds that was exceeded
     */
    public RenderTimeoutException(long timeoutMs) {
        super("Render timed out after " + timeoutMs + "ms", Map.of("timeoutMs", timeoutMs));
        this.timeoutMs = timeoutMs;
    }

    /**
     * Returns the timeout duration in milliseconds that was exceeded.
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }
}

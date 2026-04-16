package net.aerh.jigsaw.api.generator;

import java.util.function.Consumer;

/**
 * Callback interface for sending diagnostic or progress messages during rendering.
 *
 * <p>Implementations decide how messages are delivered (e.g. logged, sent to a Discord channel,
 * or discarded). The {@code forceEphemeral} flag is a hint that the message should only be
 * visible to the requesting user; implementations that do not support ephemeral delivery may
 * safely ignore it.
 *
 * @see GenerationContext
 */
@FunctionalInterface
public interface GenerationFeedback {

    /**
     * Sends a feedback message.
     *
     * @param message        the message text; never {@code null}
     * @param forceEphemeral {@code true} if the message should be visible only to the requester
     */
    void send(String message, boolean forceEphemeral);

    /**
     * Returns a no-op feedback instance that silently discards all messages.
     *
     * @return a no-op {@code GenerationFeedback}
     */
    static GenerationFeedback noop() {
        return (msg, eph) -> {};
    }

    /**
     * Adapts a simple {@link Consumer} into a {@code GenerationFeedback} that ignores the
     * {@code forceEphemeral} flag.
     *
     * @param consumer the consumer to adapt; must not be {@code null}
     * @return a {@code GenerationFeedback} backed by the consumer
     */
    static GenerationFeedback fromConsumer(Consumer<String> consumer) {
        return (msg, eph) -> consumer.accept(msg);
    }
}

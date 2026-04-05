package net.aerh.jigsaw.api.effect;

/**
 * A single transformation step applied to an item's image during rendering.
 * <p>
 * Effects are applied in ascending {@link #priority()} order. Lower numbers run first.
 *
 * @see EffectContext
 */
public interface ImageEffect {

    /**
     * Unique identifier for this effect (e.g. {@code "mig:glint"}).
     */
    String id();

    /**
     * Execution order relative to other effects. Lower values run earlier.
     */
    int priority();

    /**
     * Returns {@code true} if this effect should be applied given the current context.
     */
    boolean appliesTo(EffectContext context);

    /**
     * Applies this effect to the context and returns the resulting (potentially new) context.
     * Implementations must not mutate the supplied {@code context}; use {@link EffectContext#withImage}
     * or {@link EffectContext#withMetadata} to derive modified copies.
     */
    EffectContext apply(EffectContext context);
}

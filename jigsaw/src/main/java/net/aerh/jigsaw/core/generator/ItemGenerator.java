package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.effect.EffectPipeline;
import net.aerh.jigsaw.exception.RenderException;
import net.aerh.jigsaw.exception.UnknownItemException;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Objects;

/**
 * Renders a Minecraft item sprite, optionally applying effects.
 *
 * <p>The rendering steps are:
 * <ol>
 *   <li>Load the sprite from the {@link SpriteProvider}; throw {@link RenderException} wrapping
 *       {@link UnknownItemException} if the sprite is not found.</li>
 *   <li>If {@link ItemRequest#bigImage()} is set, upscale 10x via {@link ImageUtil#upscaleImage}.</li>
 *   <li>Build an {@link EffectContext} from the request and run the {@link EffectPipeline}.</li>
 *   <li>Convert the resulting {@link EffectContext} to a {@link GeneratorResult}.</li>
 * </ol>
 */
public final class ItemGenerator implements Generator<ItemRequest, GeneratorResult> {

    private static final double BIG_IMAGE_SCALE = 10.0;
    private static final String DURABILITY_META_KEY = "durabilityPercent";

    private final SpriteProvider spriteProvider;
    private final EffectPipeline effectPipeline;

    /**
     * Creates a new {@link ItemGenerator}.
     *
     * @param spriteProvider the sprite provider to load item textures from; must not be {@code null}
     * @param effectPipeline the pipeline of effects to apply; must not be {@code null}
     */
    public ItemGenerator(SpriteProvider spriteProvider, EffectPipeline effectPipeline) {
        this.spriteProvider = Objects.requireNonNull(spriteProvider, "spriteProvider must not be null");
        this.effectPipeline = Objects.requireNonNull(effectPipeline, "effectPipeline must not be null");
    }

    /**
     * Renders the item described by the request, applying all configured effects.
     *
     * @param input   the item request; must not be {@code null}
     * @param context the generation context; must not be {@code null}
     *
     * @return a static image or animated image depending on whether the glint effect is applied
     *
     * @throws RenderException if the item sprite is not found or rendering fails
     */
    @Override
    public GeneratorResult render(ItemRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        BufferedImage sprite = spriteProvider.getSprite(input.itemId())
                .orElseThrow(() -> {
                    UnknownItemException cause = new UnknownItemException(input.itemId());
                    return new RenderException(
                            "Unknown item: " + input.itemId(),
                            Map.of("itemId", input.itemId()),
                            cause
                    );
                });

        if (input.bigImage()) {
            sprite = ImageUtil.upscaleImage(sprite, BIG_IMAGE_SCALE);
        }

        EffectContext.Builder ctxBuilder = EffectContext.builder()
                .image(sprite)
                .itemId(input.itemId())
                .enchanted(input.enchanted())
                .hovered(input.hovered());

        input.durabilityPercent().ifPresent(d -> ctxBuilder.metadata(
                Map.of(DURABILITY_META_KEY, d)
        ));

        EffectContext effectCtx = effectPipeline.execute(ctxBuilder.build());
        return toGeneratorResult(effectCtx);
    }

    /**
     * Returns the input type accepted by this generator.
     *
     * @return {@link ItemRequest}
     */
    @Override
    public Class<ItemRequest> inputType() {
        return ItemRequest.class;
    }

    /**
     * Returns the output type produced by this generator.
     *
     * @return {@link GeneratorResult}
     */
    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }

    private static GeneratorResult toGeneratorResult(EffectContext ctx) {
        if (!ctx.animationFrames().isEmpty()) {
            return new GeneratorResult.AnimatedImage(ctx.animationFrames(), ctx.frameDelayMs());
        }
        return new GeneratorResult.StaticImage(ctx.image());
    }
}

package net.aerh.imagegenerator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.effect.EffectContext;
import net.aerh.imagegenerator.effect.EffectPipeline;
import net.aerh.imagegenerator.effect.impl.DurabilityBarEffect;
import net.aerh.imagegenerator.effect.impl.GlintImageEffect;
import net.aerh.imagegenerator.effect.impl.HoverImageEffect;
import net.aerh.imagegenerator.effect.impl.OverlayApplicationEffect;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.AnimationTimeline;
import net.aerh.imagegenerator.pack.CustomModelData;
import net.aerh.imagegenerator.pack.ItemDamage;
import net.aerh.imagegenerator.pack.PackAnimatedVisual;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackItemVisual;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSprites;
import net.aerh.imagegenerator.spritesheet.OverlayLoader;
import net.aerh.imagegenerator.spritesheet.Spritesheet;
import net.aerh.imagegenerator.util.AnimatedGifEncoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class MinecraftItemGenerator implements Generator {

    /**
     * Canvas px per GUI px for elements renders, matching the established 256-per-16 sprite
     * canvas convention ({@link PackSprites#scaleToCanvas}).
     */
    private static final int ELEMENTS_PX_PER_GUI_PX = 16;

    private final String itemId;
    @Nullable
    private final String itemModel;
    private final CustomModelData customModelData;
    // Nullable and non-transient: absent damage must key differently from damage 0/max in the
    // reflective render cache key, exactly like customModelData.
    @Nullable
    private final ItemDamage itemDamage;
    private final boolean fullGuiRotations;
    // Final non-transient so the flag enters the reflective render cache key.
    private final boolean animatedTextures;
    private final String data;
    private final String color;
    private final boolean enchanted;
    private final boolean hoverEffect;
    private final boolean bigImage;
    private final Integer durabilityPercent;
    private final OverlayLoader overlayLoader;
    private final EffectPipeline effectPipeline;
    @Nullable
    private final PackId packId;
    @ToString.Exclude
    private final transient PackRepository packRepository;

    @Override
    public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
        log.debug("Rendering item '{}' ({})", displayId(), this);

        if (animatedTextures && PackId.isActive(packId)) {
            GeneratedObject animated = renderAnimatedTextures();
            if (animated != null) {
                return animated;
            }
        }

        // Load base item texture: selected pack first, then the vanilla spritesheet
        BufferedImage itemImage = resolveBaseTexture();

        // Create initial effect context
        EffectContext.Builder contextBuilder = new EffectContext.Builder()
            .withImage(itemImage)
            .withItemId(displayId())
            .withEnchanted(enchanted)
            .withHovered(hoverEffect)
            .putMetadata("data", data)
            .putMetadata("color", color);

        if (durabilityPercent != null) {
            contextBuilder.putMetadata("durabilityPercent", durabilityPercent);
        }

        EffectContext context = contextBuilder.build();

        // Execute effect pipeline (overlay, glint, hover, durability)
        context = effectPipeline.execute(context);

        // Apply big image scaling if requested
        BufferedImage finalImage = context.getImage();
        if (bigImage && finalImage != null && finalImage.getHeight() <= 16 && finalImage.getWidth() <= 16) {
            if (context.isAnimated()) {
                List<BufferedImage> scaledFrames = context.getAnimationFrames().stream()
                    .map(frame -> ImageUtil.upscaleImage(frame, 10))
                    .toList();
                context = new EffectContext.Builder()
                    .withAnimationFrames(scaledFrames, context.getFrameDelayMs())
                    .withItemId(displayId())
                    .withEnchanted(enchanted)
                    .withHovered(hoverEffect)
                    .withMetadata(context.getMetadata())
                    .build();
                finalImage = scaledFrames.getFirst();
            } else {
                finalImage = ImageUtil.upscaleImage(finalImage, 10);
            }
        }

        if (context.isAnimated()) {
            try {
                byte[] gifData = ImageUtil.toGifBytes(
                    context.getAnimationFrames(),
                    context.getFrameDelayMs(),
                    true
                );
                log.debug("Rendered animated item '{}' ({} frames, delay {}ms)",
                    displayId(), context.getAnimationFrames().size(), context.getFrameDelayMs());
                return new GeneratedObject(gifData, context.getAnimationFrames(), context.getFrameDelayMs());
            } catch (IOException e) {
                throw new GeneratorException("Failed to encode animation", e);
            }
        }

        log.debug("Rendered static item '{}' (dimensions {}x{})",
            displayId(), finalImage.getWidth(), finalImage.getHeight());
        return new GeneratedObject(finalImage);
    }

    /** The identifier used for logs, effect metadata and error messages. */
    private String displayId() {
        return itemModel != null ? itemModel : itemId;
    }

    /**
     * The animated-textures render: when the pack visual uses at least one animated texture,
     * every timeline step renders one canvas (static effects applied per frame) and the result
     * is the GIF form of {@link GeneratedObject} with per-frame delays. Null when the item is
     * absent from the pack or none of its textures animate - the caller renders the static
     * image exactly as before.
     *
     * <p>The enchant glint is NOT applied on this path (its 33 ms cycle cannot join the
     * tick-based texture timeline); an enchanted item logs a warning and renders unglinted.
     * Hover, overlay and durability effects apply per frame as usual.
     */
    @Nullable
    private GeneratedObject renderAnimatedTextures() {
        String packRef = itemModel != null ? namespacedItemModel() : itemId;
        PackAnimatedVisual animation = packRepository.resolveItemVisualAnimation(
            packId, packRef, customModelData, itemDamage, ELEMENTS_PX_PER_GUI_PX, fullGuiRotations).orElse(null);
        if (animation == null) {
            return null;
        }
        if (enchanted) {
            log.warn("Item '{}': the enchant glint is not applied while animated pack textures drive the output",
                displayId());
        }
        List<BufferedImage> frames = new ArrayList<>(animation.steps().size());
        for (PackItemVisual visual : animation.steps()) {
            BufferedImage base = switch (visual) {
                case PackItemVisual.Sprite sprite -> PackSprites.scaleToCanvas(sprite.sprite(), 256);
                case PackItemVisual.ElementsRaster raster -> raster.image();
            };
            frames.add(applyStaticEffects(base));
        }
        if (bigImage && frames.getFirst().getWidth() <= 16 && frames.getFirst().getHeight() <= 16) {
            frames = frames.stream().map(frame -> ImageUtil.upscaleImage(frame, 10)).toList();
        }
        List<Integer> delaysMs = animation.stepTicks().stream()
            .map(AnimationTimeline::ticksToMillis)
            .toList();
        try {
            byte[] gifData = AnimatedGifEncoder.encode(frames, delaysMs);
            log.debug("Rendered texture-animated item '{}' ({} frames)", displayId(), frames.size());
            return new GeneratedObject(gifData, frames, delaysMs);
        } catch (IOException e) {
            throw new GeneratorException("Failed to encode animation", e);
        }
    }

    /**
     * Runs the effect pipeline over one animation frame with the glint suppressed (see
     * {@link #renderAnimatedTextures()}); every other effect sees the same context the static
     * path builds.
     */
    private BufferedImage applyStaticEffects(BufferedImage base) {
        EffectContext.Builder contextBuilder = new EffectContext.Builder()
            .withImage(base)
            .withItemId(displayId())
            .withEnchanted(false)
            .withHovered(hoverEffect)
            .putMetadata("data", data)
            .putMetadata("color", color);
        if (durabilityPercent != null) {
            contextBuilder.putMetadata("durabilityPercent", durabilityPercent);
        }
        return effectPipeline.execute(contextBuilder.build()).getImage();
    }

    /**
     * Loads the base item visual: the selected pack first (flat sprites scale onto the 256
     * canvas exactly as before; elements models rasterize at the same 16-px-per-GUI-px target,
     * unclipped for {@code oversized_in_gui} items), then the vanilla spritesheet.
     *
     * <p>The item model path ({@link Builder#withItemModel}) addresses
     * {@code assets/<ns>/items/<path>.json} like the {@code minecraft:item_model} component: a
     * bare reference defaults to the {@code minecraft} namespace, and a pack miss falls back to
     * the vanilla spritesheet keyed by the reference's path.
     */
    private BufferedImage resolveBaseTexture() {
        boolean usingPack = PackId.isActive(packId);
        String packRef = itemModel != null ? namespacedItemModel() : itemId;
        if (usingPack) {
            var visual = packRepository.resolveItemVisual(packId, packRef, customModelData, itemDamage,
                ELEMENTS_PX_PER_GUI_PX, fullGuiRotations);
            if (visual.isPresent()) {
                return switch (visual.get()) {
                    case PackItemVisual.Sprite sprite -> PackSprites.scaleToCanvas(sprite.sprite(), 256);
                    case PackItemVisual.ElementsRaster raster -> raster.image();
                };
            }
        }
        String vanillaKey = itemModel != null ? vanillaKeyForItemModel() : itemId;
        BufferedImage vanilla = vanillaKey != null ? Spritesheet.getTexture(vanillaKey.toLowerCase()) : null;
        if (vanilla == null) {
            if (usingPack) {
                throw new GeneratorException("Item with ID `%s` not found in pack `%s` or vanilla",
                    displayId(), packId.toString());
            }
            throw new GeneratorException("Item with ID `%s` not found", displayId());
        }
        return vanilla;
    }

    /** The item model reference with the default {@code minecraft} namespace made explicit. */
    private String namespacedItemModel() {
        return itemModel.indexOf(':') < 0 ? "minecraft:" + itemModel : itemModel;
    }

    /**
     * The vanilla spritesheet key for an item model reference: the path of a
     * {@code minecraft}-namespace reference (vanilla item models are keyed by item id), null
     * for foreign namespaces (no vanilla fallback exists for pack-namespace item models).
     */
    @Nullable
    private String vanillaKeyForItemModel() {
        String namespaced = namespacedItemModel();
        int colon = namespaced.indexOf(':');
        return namespaced.startsWith("minecraft:") ? namespaced.substring(colon + 1) : null;
    }

    public static class Builder extends net.hypixel.nerdbot.marmalade.pattern.Builder<MinecraftItemGenerator> implements ClassBuilder<MinecraftItemGenerator> {
        private String itemId;
        private String itemModel;
        private CustomModelData customModelData;
        private ItemDamage itemDamage;
        private boolean fullGuiRotations;
        private boolean animatedTextures;
        private String data;
        private String color;
        private boolean enchanted;
        private boolean hoverEffect;
        private boolean bigImage;
        private Integer durabilityPercent;
        private OverlayLoader overlayLoader;
        private EffectPipeline effectPipeline;
        private PackId packId;
        private PackRepository packRepository;

        public MinecraftItemGenerator.Builder withItem(String itemId) {
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("itemId must not be blank");
            }
            this.itemId = itemId.replace("minecraft:", "");
            return this;
        }

        /**
         * Addresses the item by its {@code minecraft:item_model} component value, resolving
         * {@code assets/<ns>/items/<path>.json} directly with the same rules as vanilla item
         * ids: a bare reference defaults to the {@code minecraft} namespace, and a pack miss
         * falls back to the vanilla spritesheet keyed by the reference's path. Mutually
         * exclusive with {@link #withItem(String)}.
         */
        public MinecraftItemGenerator.Builder withItemModel(String itemModel) {
            if (itemModel == null || itemModel.isBlank()) {
                throw new IllegalArgumentException("itemModel must not be blank");
            }
            this.itemModel = itemModel;
            return this;
        }

        /**
         * Supplies the {@code minecraft:custom_model_data} component value the item model
         * definition evaluates against ({@code range_dispatch} floats, {@code condition} flags,
         * {@code select} strings and tint colors). Defaults to {@link CustomModelData#EMPTY}.
         */
        public MinecraftItemGenerator.Builder withCustomModelData(CustomModelData customModelData) {
            this.customModelData = Objects.requireNonNull(customModelData, "customModelData");
            return this;
        }

        /**
         * Supplies the item's damage state ({@code minecraft:damage} / {@code max_damage}
         * component values), read by {@code range_dispatch} nodes with
         * {@code property: minecraft:damage} - normalized to the 0..1 damage fraction by
         * default, raw when the node declares {@code normalize: false}. Unset evaluates the
         * property at 0. Purely a model-selection input: the durability BAR stays controlled
         * by {@link #withDurability(int)}.
         *
         * @param damage    current damage, 0 (pristine) to {@code maxDamage}
         * @param maxDamage the item's maximum damage
         * @throws IllegalArgumentException when either value is negative or damage exceeds
         *                                  maxDamage
         */
        public MinecraftItemGenerator.Builder withItemDamage(int damage, int maxDamage) {
            this.itemDamage = new ItemDamage(damage, maxDamage);
            return this;
        }

        /**
         * Opts elements-model renders into the true orthographic projection of arbitrary
         * {@code display.gui} rotations - the vanilla GUI presentation of 3D models (no
         * perspective), so [30, 225, 0]-style block angles show three shaded faces instead of
         * failing loudly. Identity and (0, 180, 0)-mirror rotations (within the 5-degree
         * decorative-tilt tolerance) keep their exact flat renders with or without the flag.
         * Default false: rotations beyond those keep throwing PackResolveException.
         */
        public MinecraftItemGenerator.Builder withFullGuiRotations(boolean fullGuiRotations) {
            this.fullGuiRotations = fullGuiRotations;
            return this;
        }

        /**
         * Opts the render into animated pack textures (default false): when the resolved pack
         * visual - layer0 sprite layers or elements-model face textures - uses at least one
         * texture with an animation mcmeta, the item renders one canvas per timeline step and
         * {@code generate()} returns the GIF form of {@link GeneratedObject} with per-frame
         * delays (1 tick = 50 ms; timelines cap per
         * {@link net.aerh.imagegenerator.pack.AnimationTimeline}). Items without animated
         * textures render the static image exactly as before. The {@code interpolate} flag is
         * honored as a per-tick keyframe cross-fade (see
         * {@link net.aerh.imagegenerator.pack.PackAnimation}), and the enchant glint is not applied
         * while animated textures drive the output.
         */
        public MinecraftItemGenerator.Builder withAnimatedTextures(boolean animatedTextures) {
            this.animatedTextures = animatedTextures;
            return this;
        }

        public MinecraftItemGenerator.Builder withData(String data) {
            this.data = data;
            return this;
        }

        public MinecraftItemGenerator.Builder withColor(String color) {
            this.color = color;
            return this;
        }

        public MinecraftItemGenerator.Builder isEnchanted(boolean enchanted) {
            this.enchanted = enchanted;
            return this;
        }

        public MinecraftItemGenerator.Builder withHoverEffect(boolean hoverEffect) {
            this.hoverEffect = hoverEffect;
            return this;
        }

        public MinecraftItemGenerator.Builder isBigImage(boolean bigImage) {
            this.bigImage = bigImage;
            return this;
        }

        public MinecraftItemGenerator.Builder isBigImage() {
            return isBigImage(true);
        }

        public MinecraftItemGenerator.Builder withDurability(int durabilityPercent) {
            if (durabilityPercent < 0 || durabilityPercent > 100) {
                throw new IllegalArgumentException("durabilityPercent must be between 0 and 100");
            }
            this.durabilityPercent = durabilityPercent;
            return this;
        }

        /**
         * Inject custom overlay loader
         *
         * @param loader Overlay loader
         * @return This builder
         */
        public MinecraftItemGenerator.Builder withOverlayLoader(OverlayLoader loader) {
            this.overlayLoader = loader;
            return this;
        }

        /**
         * Inject custom effect pipeline
         *
         * @param pipeline Effect pipeline
         * @return This builder
         */
        public MinecraftItemGenerator.Builder withEffectPipeline(EffectPipeline pipeline) {
            this.effectPipeline = pipeline;
            return this;
        }

        /**
         * Selects the resource pack to resolve this item from. Null or {@link PackId#VANILLA}
         * renders from the built-in vanilla spritesheet exactly as before. Rendering with a pack
         * that was never registered, or an item the pack cannot resolve, throws
         * PackResolveException from {@code generate()}.
         */
        public MinecraftItemGenerator.Builder withPack(@Nullable PackId packId) {
            this.packId = packId;
            return this;
        }

        /** Convenience overload accepting {@code "namespace:name"}. */
        public MinecraftItemGenerator.Builder withPack(String packId) {
            return withPack(PackId.parse(packId));
        }

        /**
         * Inject a custom pack repository (tests); defaults to {@link PackRepository#global()}.
         * Cache note: the repository instance is not part of the render cache key; do not share
         * {@code generator.cache.enabled=true} across repositories holding different content
         * under the same pack ID.
         */
        public MinecraftItemGenerator.Builder withPackRepository(PackRepository packRepository) {
            this.packRepository = packRepository;
            return this;
        }

        @Override
        protected void validate() {
            if (itemId != null && itemModel != null) {
                throw new IllegalArgumentException("withItem and withItemModel are mutually exclusive");
            }
            if (itemModel != null) {
                return;
            }
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("itemId must not be blank");
            }
        }

        @Override
        protected MinecraftItemGenerator construct() {
            // Use default overlay loader if not provided
            if (overlayLoader == null) {
                overlayLoader = OverlayLoader.getInstance();
            }

            // Build default effect pipeline if not provided
            if (effectPipeline == null) {
                effectPipeline = buildDefaultEffectPipeline();
            }

            if (packRepository == null) {
                packRepository = PackRepository.global();
            }

            if (customModelData == null) {
                customModelData = CustomModelData.EMPTY;
            }

            return new MinecraftItemGenerator(
                itemId, itemModel, customModelData, itemDamage, fullGuiRotations, animatedTextures, data, color,
                enchanted, hoverEffect, bigImage, durabilityPercent, overlayLoader, effectPipeline,
                packId, packRepository
            );
        }

        private EffectPipeline buildDefaultEffectPipeline() {
            BufferedImage glintTexture = loadGlintTexture();

            return new EffectPipeline.Builder()
                .addEffect(new OverlayApplicationEffect(overlayLoader))
                .addEffect(new GlintImageEffect(glintTexture))
                .addEffect(new HoverImageEffect())
                .addEffect(new DurabilityBarEffect())
                .build();
        }

        private BufferedImage loadGlintTexture() {
            try (InputStream stream = getClass().getResourceAsStream("/minecraft/assets/textures/glint.png")) {
                if (stream == null) {
                    throw new IOException("glint.png not found");
                }
                return ImageIO.read(stream);
            } catch (IOException e) {
                throw new GeneratorException("Failed to load glint texture", e);
            }
        }
    }
}

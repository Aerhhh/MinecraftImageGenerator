package net.aerh.imagegenerator.impl.tooltip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import net.hypixel.nerdbot.marmalade.Range;
import net.hypixel.nerdbot.marmalade.Tuple;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.image.MinecraftTooltip;
import net.aerh.imagegenerator.impl.nbt.NbtTextComponentUtil;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.TooltipSprites;
import net.aerh.imagegenerator.parser.ParseContext;
import net.aerh.imagegenerator.text.PackGlyphDispatcher;
import net.aerh.imagegenerator.text.TextColorRemap;
import net.aerh.imagegenerator.parser.text.RarityFooterParser;
import net.aerh.imagegenerator.text.ChatFormat;
import net.aerh.imagegenerator.text.segment.LineSegment;
import net.aerh.imagegenerator.text.wrapper.TextWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class MinecraftTooltipGenerator implements Generator {

    public static final int DEFAULT_MAX_LINE_LENGTH = 36;

    private final String name;
    private final Rarity rarity;
    private final String itemLore;
    private final String type;
    private final int alpha;
    private final int padding;
    private final boolean normalItem;
    private final boolean centeredText;
    private final int maxLineLength;
    private final boolean renderBorder;
    private final int scaleFactor;
    // packId, tooltipStyle, and textColorRemap are final non-transient so they enter the render
    // cache key; the repository reference is transient so instances never split it.
    private final PackId packId;
    private final String tooltipStyle;
    private final TextColorRemap textColorRemap;
    private final transient PackRepository packRepository;

    @Override
    public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) throws GeneratorException {
        log.debug("Rendering tooltip for '{}'", name);

        // Configure tooltip rendering before generating
        TooltipSettings settings = new TooltipSettings(
            name,
            type,
            alpha,
            padding,
            !normalItem,
            maxLineLength,
            renderBorder,
            centeredText,
            scaleFactor,
            generationContext != null && generationContext.aprilFools()
        );

        MinecraftTooltip tooltip = parseLore(itemLore, settings).render();

        if (tooltip.isAnimated()) {
            try {
                byte[] gifData = ImageUtil.toGifBytes(tooltip.getAnimationFrames(), tooltip.getFrameDelayMs(), true);
                log.debug("Rendered animated tooltip for '{}' ({} frames, delay {}ms)", name, tooltip.getAnimationFrames().size(), tooltip.getFrameDelayMs());
                return new GeneratedObject(gifData, tooltip.getAnimationFrames(), tooltip.getFrameDelayMs());
            } catch (IOException e) {
                throw new GeneratorException("Failed to generate animated tooltip GIF", e);
            }
        } else {
            BufferedImage tooltipImage = tooltip.getImage();
            log.debug("Rendered static tooltip for '{}' (dimensions {}x{})", name, tooltipImage.getWidth(), tooltipImage.getHeight());
            return new GeneratedObject(tooltipImage);
        }
    }

    /**
     * Parses the lore of the item and applies the {@link TooltipSettings} settings.
     *
     * @param input    The lore to parse
     * @param settings The {@link TooltipSettings} to apply
     *
     * @return The parsed {@link MinecraftTooltip}
     */
    public MinecraftTooltip parseLore(String input, TooltipSettings settings) {
        log.debug("Parsing lore for item: {} with TooltipSettings: {}", name, settings);

        ParseContext parseContext = ParseContext.of(packId);

        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .withPadding(settings.getPadding())
            .hasFirstLinePadding(settings.hasFirstLinePadding())
            .setRenderBorder(settings.isRenderBorder())
            .isTextCentered(settings.isCenteredText())
            .withAlpha(Range.between(0, 255).fit(settings.getAlpha()))
            .withScaleFactor(settings.getScaleFactor())
            .withAprilFools(settings.isAprilFools())
            .withThemeSprites(resolveThemeSprites())
            .withTextColorRemap(textColorRemap)
            .withPackFontSource(PackGlyphDispatcher.FontSource.forPack(packId, packRepository));

        if (settings.getName() != null && !settings.getName().isEmpty()) {
            String name = settings.getName();

            if (rarity != null && rarity != Rarity.byName("NONE")) {
                name = rarity.getColorCode() + name;
            }

            builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(name, parseContext), '&'));
        }

        List<List<LineSegment>> segments = new ArrayList<>();

        for (String line : TextWrapper.wrapString(input, settings.getMaxLineLength(), parseContext)) {
            segments.add(LineSegment.fromLegacy(line, '&'));
        }

        for (List<LineSegment> line : segments) {
            builder.withLines(line);
        }

        if (rarity != null && rarity != Rarity.byName("NONE")) {
            String formattedType = settings.getType() == null || settings.getType().isEmpty() ? "" : " " + settings.getType();
            builder.withLines(LineSegment.fromLegacy(rarity.getFormattedDisplay() + formattedType, '&'));
        }

        return builder.build();
    }

    /**
     * Resolves the pack tooltip sprites for this render. Missing-style policy is loud: a style
     * that the pack does not define throws instead of silently falling back (vanilla would render
     * the missing texture). A pack without an explicit style still themes the styleless tooltip
     * through its {@code minecraft:tooltip/background} + {@code frame} override when present.
     */
    private TooltipSprites resolveThemeSprites() {
        if (!PackId.isActive(packId)) {
            if (tooltipStyle != null) {
                throw new GeneratorException("Tooltip style `%s` requires a resource pack; select one with withPack",
                    tooltipStyle);
            }
            return null;
        }
        PackRepository repository = repository();
        if (tooltipStyle != null) {
            return repository.resolveTooltipSprites(packId, tooltipStyle)
                .orElseThrow(() -> new GeneratorException("Tooltip style `%s` not found in pack `%s`",
                    tooltipStyle, packId.toString()));
        }
        return repository.resolveDefaultTooltipSprites(packId).orElse(null);
    }

    private PackRepository repository() {
        return packRepository != null ? packRepository : PackRepository.global();
    }

    public enum TooltipSide {
        LEFT,
        RIGHT
    }

    public static class Builder implements ClassBuilder<MinecraftTooltipGenerator> {
        @Getter
        private String itemName;
        @Getter
        private Rarity rarity;
        @Getter
        private String itemLore;
        private String type;
        private Integer alpha = MinecraftTooltip.DEFAULT_ALPHA;
        private Integer padding = 0;
        private boolean firstLinePadding = true;
        private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
        private transient boolean bypassMaxLineLength;
        private boolean centered;
        private boolean renderBorder = true;
        private transient int scaleFactor = 1;
        // pack and tooltipStyle are non-transient on purpose: buildSlashCommand round-trips them
        // as "pack:" and "tooltip_style:" options. The repository seam and the color remap are
        // transient: neither is a user-facing command option.
        private PackId pack;
        private String tooltipStyle;
        private transient PackRepository packRepository;
        private transient TextColorRemap textColorRemap;

        public MinecraftTooltipGenerator.Builder withName(String itemName) {
            this.itemName = itemName;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withRarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withItemLore(String itemLore) {
            this.itemLore = itemLore;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withType(String type) {
            this.type = type;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withAlpha(int alpha) {
            this.alpha = alpha;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withPadding(int padding) {
            this.padding = padding;
            return this;
        }

        public MinecraftTooltipGenerator.Builder hasFirstLinePadding(boolean firstLinePadding) {
            this.firstLinePadding = firstLinePadding;
            return this;
        }

        public MinecraftTooltipGenerator.Builder isTextCentered(boolean centered) {
            this.centered = centered;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withMaxLineLength(int maxLineLength) {
            if (bypassMaxLineLength) {
                this.maxLineLength = maxLineLength;
            } else {
                this.maxLineLength = MinecraftTooltip.LINE_LENGTH.fit(maxLineLength);
            }
            return this;
        }

        public MinecraftTooltipGenerator.Builder bypassMaxLineLength(boolean bypassMaxLineLength) {
            this.bypassMaxLineLength = bypassMaxLineLength;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withRenderBorder(boolean renderBorder) {
            this.renderBorder = renderBorder;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withPack(String pack) {
            this.pack = pack != null ? PackId.parse(pack) : null;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withPack(PackId packId) {
            this.pack = packId;
            return this;
        }

        /**
         * Selects the tooltip style to render with, as the {@code minecraft:tooltip_style}
         * component value (any resource location, e.g. {@code hypixel_skyblock:epic}; a bare
         * ref defaults to the {@code minecraft} namespace). Requires a pack.
         */
        public MinecraftTooltipGenerator.Builder withTooltipStyle(String tooltipStyle) {
            this.tooltipStyle = tooltipStyle;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withPackRepository(PackRepository packRepository) {
            this.packRepository = packRepository;
            return this;
        }

        /**
         * Applies a shader-equivalent text color replacement table to every drawn segment
         * (text, shadow, strikethrough, underline). Consumers own the table; it typically
         * mirrors the selected pack's core text shader palette swap.
         */
        public MinecraftTooltipGenerator.Builder withTextColorRemap(TextColorRemap textColorRemap) {
            this.textColorRemap = textColorRemap;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withScaleFactor(int scaleFactor) {
            this.scaleFactor = Math.max(1, scaleFactor);
            return this;
        }

        private static String formatCommandValue(Object value) {
            if (value == null) {
                return null;
            }

            if (value instanceof Boolean bool) {
                return capitalize(bool.toString());
            }

            if (value instanceof Rarity rarityValue) {
                return rarityValue.getName();
            }

            if (value instanceof String stringValue) {
                return formatCommandString(stringValue);
            }

            return value.toString();
        }

        private void parseComponents(JsonObject components) {
            // Parse minecraft:custom_name component
            if (components.has("minecraft:custom_name")) {
                JsonElement customNameElement = components.get("minecraft:custom_name");
                if (customNameElement != null && customNameElement.isJsonObject()) {
                    this.itemName = NbtTextComponentUtil.toFormattedString(customNameElement.getAsJsonObject());
                }
            }

            // Parse minecraft:lore component
            if (components.has("minecraft:lore")) {
                JsonArray loreArray = components.getAsJsonArray("minecraft:lore");
                StringBuilder loreBuilder = new StringBuilder();

                // Each element is its own lore line, joined by a single "\n". Blank lines are bare
                // empty-string elements (the components-format way of writing a paragraph break), so
                // every element must contribute a line - skipping non-objects would collapse "\n\n"
                // into "\n" and swallow non-empty plain-string lines too.
                for (int i = 0; i < loreArray.size(); i++) {
                    if (i > 0) {
                        loreBuilder.append("\\n");
                    }
                    loreBuilder.append(parseLoreLine(loreArray.get(i)));
                }

                this.itemLore = loreBuilder.toString();
            }
        }

        /**
         * Renders a single {@code minecraft:lore} element to an ampersand-formatted line. Objects are
         * full text components; primitives (including the bare {@code ""} used for blank lines) are
         * parsed as string text components. Anything else yields an empty line so line positions - and
         * therefore blank-line breaks - are preserved.
         *
         * @param element the lore element to render.
         *
         * @return the ampersand-formatted line, possibly empty.
         */
        private static String parseLoreLine(JsonElement element) {
            if (element.isJsonObject()) {
                return NbtTextComponentUtil.toFormattedString(element.getAsJsonObject());
            }

            if (element.isJsonPrimitive()) {
                return NbtTextComponentUtil.parseTextValue(element.getAsString());
            }

            return "";
        }

        public String getDyeColor(JsonObject nbtJson) {
            return extractDyeColor(nbtJson);
        }

        private String extractDyeColor(JsonObject nbtJson) {
            // Try components format first (1.20.5+)
            if (nbtJson.has("components")) {
                JsonObject components = nbtJson.getAsJsonObject("components");
                if (components.has("minecraft:dyed_color")) {
                    return convertToHexColor(components.get("minecraft:dyed_color").getAsInt(), "component");
                }
            }

            // Try legacy format
            if (nbtJson.has("tag")) {
                JsonObject tag = nbtJson.getAsJsonObject("tag");
                if (tag.has("display") && tag.getAsJsonObject("display").has("color")) {
                    return convertToHexColor(tag.getAsJsonObject("display").get("color").getAsInt(), "legacy");
                }
            }

            return null;
        }

        private String convertToHexColor(int dyeColor, String format) {
            String hexColor = "#" + String.format("%06X", dyeColor & 0xFFFFFF);
            log.debug("Extracted {} dye color: {} -> {}", format, dyeColor, hexColor);
            return hexColor;
        }

        public MinecraftTooltipGenerator.Builder parseNbtJson(JsonObject nbtJson) {
            this.firstLinePadding = false;
            this.centered = false;
            this.rarity = Rarity.byName("NONE");
            this.itemLore = "";
            this.renderBorder = true;
            this.alpha = MinecraftTooltip.DEFAULT_ALPHA;
            this.padding = 0;

            // Check if using new component format (1.20.5+)
            if (nbtJson.has("components")) {
                parseComponents(nbtJson.getAsJsonObject("components"));
            } else if (nbtJson.has("tag")) {
                // Legacy format support (pre-1.13 plain § strings and 1.13-1.20.4 JSON text component strings)
                JsonElement tagElement = nbtJson.get("tag");
                if (tagElement != null && tagElement.isJsonObject()) {
                    JsonObject tagObject = tagElement.getAsJsonObject();
                    if (tagObject.has("display") && tagObject.get("display").isJsonObject()) {
                        JsonObject displayObject = tagObject.getAsJsonObject("display");

                        // Parse Name if present
                        if (displayObject.has("Name")) {
                            String rawName = displayObject.get("Name").getAsString();
                            this.itemName = NbtTextComponentUtil.parseTextValue(rawName);
                        }

                        // Parse Lore if present
                        if (displayObject.has("Lore")) {
                            JsonArray loreArray = displayObject.getAsJsonArray("Lore");
                            StringBuilder loreBuilder = new StringBuilder();

                            for (int i = 0; i < loreArray.size(); i++) {
                                if (i > 0) {
                                    loreBuilder.append("\\n");
                                }

                                String rawLine = loreArray.get(i).getAsString();
                                loreBuilder.append(NbtTextComponentUtil.parseTextValue(rawLine));
                            }

                            this.itemLore = loreBuilder.toString();
                        }
                    }
                }
            }

            Tuple<String, Rarity, String> footer = RarityFooterParser.extract(this.itemLore);
            this.itemLore = footer.value1();

            if (footer.value2() != null) {
                this.rarity = footer.value2();
            }

            if (footer.value3() != null && (this.type == null || this.type.isBlank())) {
                this.type = footer.value3();
            }

            return this;
        }

        private static String capitalize(String text) {
            if (text == null || text.isEmpty()) return text;
            return text.substring(0, 1).toUpperCase() + text.substring(1);
        }

        private static String formatCommandString(String text) {
            if (text == null) {
                return "";
            }

            String normalized = TextWrapper.normalizeNewlines(text);
            return normalized.replace("\n", "\\n");
        }

        /**
         * Builds a slash command from the current state of the builder.
         *
         * @return A properly formatted slash command string.
         */
        // TODO support player head textures
        public String buildSlashCommand() {
            String baseCommand = System.getProperty("generator.base.command", "gen");
            StringBuilder commandBuilder = new StringBuilder("/" + baseCommand + " item ");
            Field[] fields = this.getClass().getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);

                    if (Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }

                    Object value = field.get(this);
                    if (value != null && !(value instanceof String string && string.isEmpty())) {
                        String serializedValue = formatCommandValue(value);
                        if (serializedValue == null || serializedValue.isEmpty()) {
                            continue;
                        }

                        String paramName = convertCamelCaseToSnakeCase(field.getName());
                        commandBuilder.append(paramName).append(": ").append(serializedValue).append(" ");
                    }
                } catch (IllegalAccessException e) {
                    throw new GeneratorException("Failed to build slash command", e);
                }
            }

            return commandBuilder.toString().trim();
        }

        private static String convertCamelCaseToSnakeCase(String camelCase) {
            if (camelCase == null) return null;
            return camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
        }

        @Override
        public MinecraftTooltipGenerator build() {
            return new MinecraftTooltipGenerator(
                itemName,
                rarity,
                itemLore,
                type,
                alpha,
                padding,
                !firstLinePadding, // normalItem is inverse of firstLinePadding
                centered,
                maxLineLength,
                renderBorder,
                scaleFactor,
                pack,
                tooltipStyle,
                textColorRemap,
                packRepository
            );
        }
    }
}

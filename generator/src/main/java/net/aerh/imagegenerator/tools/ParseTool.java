package net.aerh.imagegenerator.tools;

import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.NbtParseException;
import net.aerh.imagegenerator.impl.MinecraftNbtParser;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.tools.nbt.ParsedNbtRendering;
import net.aerh.imagegenerator.tools.nbt.ParsedRender;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.SlashCommandText;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/** Parses item NBT, renders it with the item model fallback ladder, and rebuilds the matching command. */
@Slf4j
public final class ParseTool {

    /** Shown for input Gson cannot read at all; the wording is the bot's. */
    public static final String BADLY_FORMATTED_MESSAGE = "You provided badly formatted NBT!";

    private final ResourcePackService packService;

    public ParseTool(ResourcePackService packService) {
        this.packService = Objects.requireNonNull(packService, "packService");
    }

    /**
     * @throws NbtParseException  if the NBT cannot be parsed, including input Gson rejects outright
     * @throws GeneratorException if the render fails, or the parse produced no tooltip
     */
    public ParseResult render(ParseRequest request, @Nullable GenerationContext context) throws IOException {
        ParsedRender render;
        try {
            render = ParsedNbtRendering.renderWithFallback(packService, request.nbt(), request.packId(), context);
        } catch (JsonParseException exception) {
            throw new NbtParseException(BADLY_FORMATTED_MESSAGE);
        }
        MinecraftNbtParser.ParsedNbt parsedNbt = render.parsedNbt();
        String itemModel = parsedNbt.getParsedItemModel();

        Optional<ClassBuilder<? extends Generator>> tooltipGenerator = parsedNbt.getGenerators()
            .stream()
            .filter(gen -> gen instanceof MinecraftTooltipGenerator.Builder)
            .findFirst();
        if (tooltipGenerator.isEmpty()) {
            log.error("An error occurred while parsing the NBT string, there doesn't seem to be a tooltip but no nbt parser exception occurred.");
            throw new GeneratorException("An error occurred.");
        }

        String slashCommand = SlashCommandText.appendParsedItemOptions(
            ((MinecraftTooltipGenerator.Builder) tooltipGenerator.get()).buildSlashCommand(),
            parsedNbt.getParsedItemId(),
            itemModel,
            parsedNbt.getBase64Texture(),
            parsedNbt.isEnchanted()
        );
        slashCommand = SlashCommandText.escapeNewlines(slashCommand);

        String packName = request.packId() == null ? null : request.packId().toString();
        String notice = ParsedNbtRendering.fallbackNotice(render.fallback(), packName, itemModel, parsedNbt.getParsedItemId());

        return new ParseResult(render.image(), parsedNbt, render.fallback(), slashCommand, notice);
    }
}

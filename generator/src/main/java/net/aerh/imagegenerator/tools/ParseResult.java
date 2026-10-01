package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.impl.MinecraftNbtParser;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.tools.nbt.ItemModelFallback;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a parse render produces: the image, the parsed NBT, which stand-in was drawn if the
 * pack could not produce the item model, the one-line {@code /gen item} command that reproduces
 * the render, and a notice to show when a stand-in was used.
 */
public record ParseResult(GeneratedObject image, MinecraftNbtParser.ParsedNbt parsedNbt, ItemModelFallback fallback,
                          String slashCommand, @Nullable String fallbackNotice) {
}

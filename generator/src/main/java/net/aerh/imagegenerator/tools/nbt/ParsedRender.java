package net.aerh.imagegenerator.tools.nbt;

import net.aerh.imagegenerator.impl.MinecraftNbtParser;
import net.aerh.imagegenerator.item.GeneratedObject;

/**
 * The outcome of rendering a parsed NBT payload: the image, the parse result it came from, and
 * which stand-in, if any, was drawn in place of the addressed item model.
 */
public record ParsedRender(GeneratedObject image, MinecraftNbtParser.ParsedNbt parsedNbt, ItemModelFallback fallback) {
}

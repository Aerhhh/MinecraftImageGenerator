package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonObject;

/**
 * Interface used for extracting metadata from a specific Minecraft NBT format.
 * <p>
 * Each implementation targets a specific version of Minecraft's item NBT structure:
 * <ul>
 *   <li>{@link ComponentsNbtFormatHandler} - 1.20.5+ (structured {@code components} object,
 *       introduced in snapshot 24w09a)</li>
 *   <li>{@link PostFlatteningNbtFormatHandler} - 1.13-1.20.4 ({@code tag.display} with JSON
 *       text component strings; {@code display.Name} became JSON in 18w01a,
 *       {@code display.Lore} became JSON in 18w43a/1.14)</li>
 *   <li>{@link PreFlatteningNbtFormatHandler} - pre-1.13 ({@code tag.display} with plain
 *       section symbol strings and the {@code ench} key with numeric enchantment IDs)</li>
 * </ul>
 * <p>
 * <b>Adding a new handler:</b>
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Register the handler in {@code MinecraftNbtParser.FORMAT_HANDLERS}</li>
 *   <li>Handlers are tried in list order</li>
 * </ol>
 *
 * @see NbtFormatMetadata
 * @see <a href="https://minecraft.wiki/w/Java_Edition_Flattening">The Flattening (1.13)</a>
 * @see <a href="https://minecraft.wiki/w/Data_component_format">Data Component Format (1.20.5+)</a>
 * @see <a href="https://minecraft.wiki/w/Item_format/Before_1.20.5">Item Format before 1.20.5</a>
 */
public interface NbtFormatHandler {

    /**
     * Determines if this handler can parse the provided NBT structure.
     * <p>
     * Implementations should check for format-specific keys (e.g. {@code components}, {@code tag})
     * and, where necessary, inspect values to distinguish between formats that share the same keys.
     *
     * @param nbt root NBT object
     *
     * @return true if this handler supports the structure
     */
    boolean supports(JsonObject nbt);

    /**
     * Extracts metadata specific to this NBT format (player head texture, max line length, etc).
     *
     * @param nbt root NBT object
     *
     * @return metadata describing this format's fields, or {@link NbtFormatMetadata#EMPTY} when none apply
     */
    default NbtFormatMetadata extractMetadata(JsonObject nbt) {
        return NbtFormatMetadata.EMPTY;
    }
}
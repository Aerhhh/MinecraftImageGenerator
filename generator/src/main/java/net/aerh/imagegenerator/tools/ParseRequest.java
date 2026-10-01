package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.support.Required;
import org.jetbrains.annotations.Nullable;

/**
 * Options of the parse tool. {@code nbt} is JSON or SNBT already read from text or a file; callers
 * enforce their own upload limits before building the request.
 */
public record ParseRequest(String nbt, @Nullable PackId packId) {

    public ParseRequest {
        Required.check(nbt, "nbt");
    }
}

package net.aerh.imagegenerator.tools.pack;

import net.aerh.imagegenerator.pack.PackId;

/** What happened when a pack was reloaded from a new file. */
public sealed interface PackReloadResult {

    /** The new pack is live. {@code previousItemCount} is 0 when the pack was not registered before. */
    record Applied(PackId packId, int itemCount, int previousItemCount) implements PackReloadResult {
    }

    /** The new pack was not applied and the live pack is unchanged. */
    record Rejected(String reason) implements PackReloadResult {
    }

    /**
     * The pack was never tried because the caller's configuration is invalid (for example a bad
     * theme). Nothing is wrong with the pack itself, so callers must not remember it as rejected.
     */
    record NotAttempted(String reason) implements PackReloadResult {
    }
}

package net.aerh.imagegenerator.pack;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The pack ids whose pack-specific data applies to a pack: its own id, then the id of the pack it
 * is a variant of. A variant is a pack registered under its own id that shares another pack's
 * glyph layout and conventions, for example an alpha build of a server pack that reuses the live
 * pack's {@code packOverrides} entries and emissive alpha convention.
 *
 * <p>Only one level is followed. The {@code variantOf} id is a plain key: the pack it names does
 * not have to be registered.
 *
 * @param id        the pack's own id
 * @param variantOf the pack this one is a variant of, or {@code null} for none
 */
public record PackLineage(PackId id, @Nullable PackId variantOf) {

    public PackLineage {
        Objects.requireNonNull(id, "id");
        if (id.equals(variantOf)) {
            throw new IllegalArgumentException("Pack " + id + " cannot be a variant of itself");
        }
        if (PackId.VANILLA.equals(variantOf)) {
            throw new IllegalArgumentException("Pack " + id + " cannot be a variant of the built-in vanilla pack");
        }
    }

    /** A lineage with no variant: only the pack's own id applies. */
    public static PackLineage of(PackId id) {
        return new PackLineage(id, null);
    }

    /** The ids to look pack-specific data up under, most specific first. */
    public List<PackId> lookupOrder() {
        return variantOf == null ? List.of(id) : List.of(id, variantOf);
    }

    /**
     * Finds the override for this pack in a {@code packOverrides} map keyed by pack id string:
     * the pack's own key first, then its {@code variantOf} key.
     *
     * @param overrides the overrides, or {@code null} when the entry has none
     *
     * @return the override, or {@code null} when no key in the lineage has one
     */
    @Nullable
    public String findOverride(@Nullable Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        for (PackId packId : lookupOrder()) {
            String override = overrides.get(packId.toString());
            if (override != null) {
                return override;
            }
        }
        return null;
    }

    /**
     * Whether the pack's own id or its {@code variantOf} id is in the given set of pack id strings.
     */
    public boolean isAnyOf(Set<String> packIds) {
        return lookupOrder().stream().anyMatch(packId -> packIds.contains(packId.toString()));
    }
}

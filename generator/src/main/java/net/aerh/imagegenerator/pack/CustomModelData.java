package net.aerh.imagegenerator.pack;

import java.util.List;
import java.util.Objects;

/**
 * The value of the {@code minecraft:custom_model_data} item component: four independent lists
 * that item model definitions index into via {@code range_dispatch}, {@code select},
 * {@code condition} and tint sources with {@code property/type: custom_model_data}.
 *
 * <p>All lists are defensively copied and immutable; missing entries (an index beyond a list's
 * size) evaluate per the vanilla rules: {@code range_dispatch} and {@code select} fall back,
 * {@code condition} reads {@code false}, and tints use their declared default color.
 *
 * @param floats  values read by {@code range_dispatch} nodes ({@code floats[index] * scale})
 * @param flags   values read by {@code condition} nodes
 * @param strings values read by {@code select} nodes
 * @param colors  packed {@code 0xRRGGBB} values read by {@code custom_model_data} tint sources
 */
public record CustomModelData(List<Float> floats, List<Boolean> flags, List<String> strings, List<Integer> colors) {

    /** No data: every lookup misses and evaluates to its default. */
    public static final CustomModelData EMPTY = new CustomModelData(List.of(), List.of(), List.of(), List.of());

    public CustomModelData {
        Objects.requireNonNull(floats, "floats");
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(strings, "strings");
        Objects.requireNonNull(colors, "colors");
        floats = List.copyOf(floats);
        flags = List.copyOf(flags);
        strings = List.copyOf(strings);
        colors = List.copyOf(colors);
    }
}

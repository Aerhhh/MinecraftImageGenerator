package net.aerh.imagegenerator.testsupport;

import net.aerh.imagegenerator.pack.CustomModelData;

import java.util.List;

/**
 * Single-list {@link CustomModelData} factories shared by the model evaluation tests, so the
 * shorthand lives in exactly one place.
 */
public final class CustomModelDatas {

    private CustomModelDatas() {
    }

    public static CustomModelData floats(Float... values) {
        return new CustomModelData(List.of(values), List.of(), List.of(), List.of());
    }

    public static CustomModelData flags(Boolean... values) {
        return new CustomModelData(List.of(), List.of(values), List.of(), List.of());
    }

    public static CustomModelData strings(String... values) {
        return new CustomModelData(List.of(), List.of(), List.of(values), List.of());
    }

    public static CustomModelData colors(Integer... values) {
        return new CustomModelData(List.of(), List.of(), List.of(), List.of(values));
    }
}

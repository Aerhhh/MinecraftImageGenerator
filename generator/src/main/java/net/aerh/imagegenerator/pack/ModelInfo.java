package net.aerh.imagegenerator.pack;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The subset of an item model JSON relevant to GUI rendering: the parent reference, the full
 * texture map (with {@code layer0} kept as a dedicated accessor for the flat sprite path),
 * elements geometry, the {@code display.gui} transform and {@code gui_light}.
 *
 * <p>Inheritance semantics along the parent chain (child-most wins): the elements list, the
 * whole {@code display.gui} entry and {@code gui_light} each come from the first model in the
 * chain that declares them; texture map entries merge per key with the child's value winning.
 *
 * @param elements     the model's own elements list, or null when the model declares none
 * @param guiTransform the model's own {@code display.gui} entry, or null when absent
 * @param guiLight     {@code "front"} or {@code "side"}, or null when absent
 */
record ModelInfo(@Nullable String parentRef, @Nullable String layer0Ref,
                 Map<String, String> textures, @Nullable List<ModelElement> elements,
                 @Nullable GuiTransform guiTransform, @Nullable String guiLight) {
}

package net.aerh.imagegenerator.pack;

/**
 * A parsed item definition file ({@code assets/&lt;ns&gt;/items/&lt;path&gt;.json}): the model
 * node tree plus the root {@code oversized_in_gui} flag (default false), which disables slot
 * clipping for elements renders.
 */
record ItemDefinition(ItemModelNode model, boolean oversizedInGui) {
}

package net.aerh.imagegenerator.pack;

/**
 * Parsed contents of a texture {@code .png.mcmeta} file. A single file may legally carry
 * both sections; either is null when its section is absent.
 */
record McMeta(AnimationMeta animation, GuiScaling guiScaling) {
}

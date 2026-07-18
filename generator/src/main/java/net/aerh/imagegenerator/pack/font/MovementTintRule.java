package net.aerh.imagegenerator.pack.font;

import java.awt.Color;
import java.util.Set;

/**
 * The CPU emulation of a server pack's custom core text shader "no-tint" behavior. Such a shader
 * intercepts text glyphs whose RUN color is a reserved movement marker and, instead of the vanilla
 * {@code texel * runColor} multiply, forces the run color's RGB to white ({@code COLOR = vec3(1)})
 * so the glyph keeps its NATIVE texel color. This lets a pack ship full-color glyph art (emblems,
 * item icons) inside the font system and drive per-glyph animation by choosing a marker run color,
 * without the run color tinting the art.
 *
 * <p>A marker is matched on the raw run color's green and blue channels exactly like the shader's
 * {@code isMovement(color, G, B)} test: green equals {@link #markerGreen()} and blue is one of the
 * defined {@link #markerBlues()}. The red channel (the shader's animation progress) and the alpha
 * channel are ignored by the match. The match is on the RUN color, not on any multiplied pixel, so
 * the decision is per-glyph.
 *
 * <p>For a matched run the emulation replaces the multiplicative tint with {@code (255, 255, 255,
 * runAlpha)}: the glyph's native RGB survives ({@code texel * 255 / 255}) while the run alpha still
 * multiplies the texel alpha ({@code texelAlpha * runAlpha / 255}), matching the shader's
 * {@code transform.color = vec4(vec3(1), runAlpha)}. The animation transforms themselves (scale
 * pulses, slides, and the like) are movement over time and have no effect on a single static frame,
 * so they are intentionally not emulated.
 */
public record MovementTintRule(int markerGreen, Set<Integer> markerBlues) {

    /** Defensively copies the blue table so callers cannot mutate the rule after construction. */
    public MovementTintRule {
        markerBlues = Set.copyOf(markerBlues);
    }

    /**
     * Whether a run color is one of the shader's movement markers: green equals
     * {@link #markerGreen()} and blue is in {@link #markerBlues()}. Red and alpha are ignored, and a
     * {@code null} run color never matches.
     */
    public boolean neutralizesTint(Color runColor) {
        return runColor != null && runColor.getGreen() == markerGreen && markerBlues.contains(runColor.getBlue());
    }

    /**
     * The tint actually multiplied into a glyph's texel for a given run color. A movement marker run
     * yields {@code (255, 255, 255, runAlpha)} - the shader's white RGB with the run alpha kept, so
     * the native texel color survives while the texel alpha still scales by the run alpha. Every
     * other run color (marker mismatch, or {@code null}) passes through unchanged, so ordinary text
     * still tints exactly as before.
     */
    public Color effectiveTint(Color runColor) {
        if (!neutralizesTint(runColor)) {
            return runColor;
        }
        return new Color(255, 255, 255, runColor.getAlpha());
    }
}

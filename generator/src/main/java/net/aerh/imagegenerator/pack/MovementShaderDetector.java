package net.aerh.imagegenerator.pack;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackLoadException;
import net.aerh.imagegenerator.pack.font.MovementTintRule;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects whether a pack ships the custom core text shader chain that neutralizes the run-color
 * tint for reserved "movement marker" glyphs, and derives the {@link MovementTintRule} from the
 * shader's own GLSL rather than hardcoding the marker values.
 *
 * <p>The gate is the presence of every file in the shader chain the vanilla client would compile
 * to run the effect: the custom text pipeline shader plus the movement include and its config. When
 * they are all present, the marker green channel is read from
 * {@code const int MOVEMENT_MARKER = <G>;} in the include, and the accepted blue channels from the
 * {@code MOVEMENT(MOVEMENT_MARKER, <B>)} entries in the config. A pack lacking any chain file, or a
 * chain whose GLSL does not declare a marker or any entries, produces no rule, so vanilla-style
 * multiplicative tinting stands (this is what a pack WITHOUT the shader expects: a colored run tints
 * the glyph).
 */
@Slf4j
@UtilityClass
class MovementShaderDetector {

    /**
     * Every file that must exist for the movement text shader to run in the vanilla client: the
     * custom text pipeline fragment shader, the two render-stage text includes it pulls in, and the
     * movement include plus its config. A pack missing any of these does not run the effect.
     */
    private static final List<String> REQUIRED_SHADER_FILES = List.of(
        "assets/minecraft/shaders/core/pipeline/text.fsh",
        "assets/minecraft/shaders/include/render/text.vsh",
        "assets/minecraft/shaders/include/render/text.fsh",
        "assets/minecraft/shaders/include/movement.glsl",
        "assets/minecraft/shaders/include/config/movement.glsl");

    /** The include declaring {@code const int MOVEMENT_MARKER = <G>;} (the marker green channel). */
    private static final String MARKER_DEFINITION_FILE = "assets/minecraft/shaders/include/movement.glsl";

    /** The config listing {@code MOVEMENT(MOVEMENT_MARKER, <B>)} entries (the marker blue channels). */
    private static final String MARKER_TABLE_FILE = "assets/minecraft/shaders/include/config/movement.glsl";

    // The digit runs are bounded (a color channel is at most three digits; the bound just keeps a
    // pathological file from overflowing int on parse) - an over-long run simply fails to match and
    // yields no rule.
    private static final Pattern MARKER_GREEN =
        Pattern.compile("const\\s+int\\s+MOVEMENT_MARKER\\s*=\\s*(\\d{1,9})\\s*;");
    private static final Pattern MARKER_BLUE_ENTRY =
        Pattern.compile("MOVEMENT\\s*\\(\\s*MOVEMENT_MARKER\\s*,\\s*(\\d{1,9})\\s*\\)");

    /**
     * The movement tint rule for a pack, or empty when the pack does not ship the shader chain (or
     * ships it without a parseable marker or entry table). A read failure of a chain file present in
     * the listing (e.g. it exceeds the entry byte cap) is treated as "no shader" with a warning,
     * never a hard failure, mirroring the decode path's tolerant policy.
     */
    static Optional<MovementTintRule> detect(PackSource source) {
        for (String file : REQUIRED_SHADER_FILES) {
            if (!source.exists(file)) {
                return Optional.empty();
            }
        }
        try {
            Optional<Integer> markerGreen = firstInt(MARKER_GREEN, read(source, MARKER_DEFINITION_FILE));
            if (markerGreen.isEmpty()) {
                return Optional.empty();
            }
            TreeSet<Integer> markerBlues = allInts(MARKER_BLUE_ENTRY, read(source, MARKER_TABLE_FILE));
            if (markerBlues.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new MovementTintRule(markerGreen.get(), markerBlues));
        } catch (PackLoadException e) {
            log.warn("Pack movement shader present but a chain file could not be read ({}); "
                + "treating the pack as un-shaded", e.getMessage());
            return Optional.empty();
        }
    }

    private static String read(PackSource source, String path) {
        return new String(source.read(path), StandardCharsets.UTF_8);
    }

    private static Optional<Integer> firstInt(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
    }

    private static TreeSet<Integer> allInts(Pattern pattern, String text) {
        TreeSet<Integer> values = new TreeSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            values.add(Integer.parseInt(matcher.group(1)));
        }
        return values;
    }
}

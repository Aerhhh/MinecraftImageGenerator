package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.support.Required;
import net.aerh.imagegenerator.tools.support.Strings;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Options of the dialogue tool. Defaults are those of the Discord {@code /gen dialogue} commands.
 * In {@link Mode#SINGLE} {@code npcNames} is one name; in {@link Mode#MULTI} it is a comma
 * separated list and each dialogue line starts with a speaker index.
 */
public record DialogueRequest(Mode mode, String npcNames, String dialogue, int maxLineLength, boolean abiphone,
                              @Nullable String skinValue, boolean renderBackground, @Nullable PackId packId) {

    public enum Mode { SINGLE, MULTI }

    public static final int DEFAULT_MAX_LINE_LENGTH = 91;
    public static final boolean DEFAULT_ABIPHONE = false;
    public static final boolean DEFAULT_RENDER_BACKGROUND = false;

    public DialogueRequest {
        Required.check(mode, "mode");
        Required.check(npcNames, "npc_names");
        Required.check(dialogue, "dialogue");
        skinValue = Strings.blankToNull(skinValue);
    }

    public static Builder single(String npcName, String dialogue) {
        return new Builder(Mode.SINGLE, npcName, dialogue);
    }

    public static Builder multi(String npcNames, String dialogue) {
        return new Builder(Mode.MULTI, npcNames, dialogue);
    }

    /** Passing null to a defaulted option (the boxed number and boolean setters) leaves its value unchanged; passing null to an optional text or pack setter clears it. */
    public static final class Builder {
        private final Mode mode;
        private final String npcNames;
        private final String dialogue;
        private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
        private boolean abiphone = DEFAULT_ABIPHONE;
        private String skinValue;
        private boolean renderBackground = DEFAULT_RENDER_BACKGROUND;
        private PackId packId;

        private Builder(Mode mode, String npcNames, String dialogue) {
            this.mode = mode;
            this.npcNames = npcNames;
            this.dialogue = dialogue;
        }

        public Builder maxLineLength(@Nullable Integer maxLineLength) {
            this.maxLineLength = Objects.requireNonNullElse(maxLineLength, this.maxLineLength);
            return this;
        }

        public Builder abiphone(@Nullable Boolean abiphone) {
            this.abiphone = Objects.requireNonNullElse(abiphone, this.abiphone);
            return this;
        }

        public Builder skinValue(@Nullable String skinValue) {
            this.skinValue = skinValue;
            return this;
        }

        public Builder renderBackground(@Nullable Boolean renderBackground) {
            this.renderBackground = Objects.requireNonNullElse(renderBackground, this.renderBackground);
            return this;
        }

        public Builder packId(@Nullable PackId packId) {
            this.packId = packId;
            return this;
        }

        public DialogueRequest build() {
            return new DialogueRequest(mode, npcNames, dialogue, maxLineLength, abiphone, skinValue, renderBackground, packId);
        }
    }
}

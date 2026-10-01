package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.support.Required;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Options of the recipe tool. Defaults are those of the Discord {@code /gen recipe} command. */
public record RecipeRequest(String recipe, boolean renderBackground, @Nullable PackId packId) {

    public static final boolean DEFAULT_RENDER_BACKGROUND = true;

    public RecipeRequest {
        Required.check(recipe, "recipe");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Passing null to a defaulted option (the boxed boolean setter) leaves its value unchanged; passing null to the optional pack setter clears it. */
    public static final class Builder {
        private String recipe;
        private boolean renderBackground = DEFAULT_RENDER_BACKGROUND;
        private PackId packId;

        public Builder recipe(String recipe) {
            this.recipe = recipe;
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

        public RecipeRequest build() {
            return new RecipeRequest(recipe, renderBackground, packId);
        }
    }
}

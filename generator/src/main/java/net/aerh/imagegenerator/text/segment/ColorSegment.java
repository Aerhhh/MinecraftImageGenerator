package net.aerh.imagegenerator.text.segment;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.text.Colors;
import net.aerh.imagegenerator.text.LegacyCode;
import net.aerh.imagegenerator.text.MinecraftFont;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Getter
@Setter
@ToString
public class ColorSegment {

    protected @NotNull String text;
    protected ChatColor color = ChatColor.Legacy.GRAY;
    protected @NotNull MinecraftFont font = MinecraftFont.DEFAULT;
    /**
     * Optional resource-pack font id (any resource location, e.g. {@code "mypack:chat"}); null when
     * the segment uses a built-in {@link MinecraftFont}. When set it takes precedence over
     * {@link #font} for pack glyph lookup; rendering falls back to {@link #font} for codepoints
     * the pack font does not supply (or when no pack is active).
     */
    protected @Nullable String packFontId;
    protected boolean italic, bold, underlined, obfuscated, strikethrough;
    /**
     * Whether this segment draws its drop shadow. Defaults to {@code true} (the vanilla behavior).
     * Set {@code false} to mirror a {@code shadow_color} component whose alpha byte is zero
     * (e.g. {@code 16777215} = {@code 0x00FFFFFF}), which disables the drop shadow for that run.
     * Only the shadow pass is affected; foreground draw and advances are unchanged.
     */
    protected boolean shadowEnabled = true;

    public ColorSegment(@NotNull String text) {
        this.setText(text);
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull LineSegment fromLegacy(@NotNull String legacyText) {
        return fromLegacy(legacyText, '&');
    }

    /**
     * This function takes in a legacy text string and converts it into a {@link ColorSegment}.
     * <p>
     * Legacy text strings use the {@link LegacyCode#SECTION_SYMBOL}. Many keyboards do not have this symbol however,
     * which is probably why it was chosen. To get around this, it is common practice to substitute
     * the symbol for another, then translate it later. Often '&' is used, but this can differ from person
     * to person. In case the string does not have a {@link LegacyCode#SECTION_SYMBOL}, the method also checks for the
     * {@param characterSubstitute}
     *
     * @param legacyText       The text to make into an object
     * @param symbolSubstitute The character substitute
     *
     * @return A TextObject representing the legacy text.
     */
    public static @NotNull LineSegment fromLegacy(@NotNull String legacyText, char symbolSubstitute) {
        return fromLegacyHandler(legacyText, symbolSubstitute, () -> new ColorSegment(""));
    }

    protected static @NotNull LineSegment fromLegacyHandler(@NotNull String legacyText, char symbolSubstitute, @NotNull Supplier<? extends ColorSegment> segmentSupplier) {
        LineSegment.Builder builder = LineSegment.builder();
        ColorSegment currentObject = segmentSupplier.get();
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < legacyText.length(); i++) {
            char charAtIndex = legacyText.charAt(i);

            if (charAtIndex == LegacyCode.SECTION_SYMBOL || charAtIndex == symbolSubstitute) {
                if ((i + 1) > legacyText.length() - 1)
                    continue; // do nothing.

                // peek at the next character.
                char peek = legacyText.charAt(i + 1);
                ChatColor hexColor = peek == '#' ? Colors.tryParseHexAt(legacyText, i + 1) : null;

                if (hexColor != null) {
                    // Hex colors behave exactly like named color codes: flush the pending
                    // text, then start a fresh segment (dropping formatting, keeping the font).
                    MinecraftFont currentFont = currentObject.getFont();

                    if (!text.isEmpty()) {
                        currentObject.setText(text.toString());
                        builder.withSegments(currentObject);
                        text.setLength(0);
                    }

                    currentObject = segmentSupplier.get();
                    currentObject.setColor(hexColor);
                    currentObject.setFont(currentFont);
                    i += Colors.HEX_CODE_LENGTH; // skip #RRGGBB; the loop increment skips the symbol
                } else if (LegacyCode.isValid(peek)) {
                    i += 1; // if valid

                    // Preserve the current font before creating a new segment
                    MinecraftFont currentFont = currentObject.getFont();

                    if (!text.isEmpty()) {
                        currentObject.setText(text.toString()); // create a new text object
                        builder.withSegments(currentObject); // append the current object.
                        currentObject = segmentSupplier.get(); // reset the current object.
                        currentObject.setFont(currentFont); // carry forward the font
                        text.setLength(0); // reset the buffer
                    }

                    LegacyCode code = Objects.requireNonNull(LegacyCode.of(peek));

                    switch (code) {
                        case FONT_GALACTIC:
                            currentObject.setFont(MinecraftFont.GALACTIC);
                            break;
                        case FONT_ILLAGERALT:
                            currentObject.setFont(MinecraftFont.ILLAGERALT);
                            break;
                        case OBFUSCATED:
                            currentObject.setObfuscated(true);
                            break;
                        case BOLD:
                            currentObject.setBold(true);
                            break;
                        case STRIKETHROUGH:
                            currentObject.setStrikethrough(true);
                            break;
                        case ITALIC:
                            currentObject.setItalic(true);
                            break;
                        case UNDERLINE:
                            currentObject.setUnderlined(true);
                            break;
                        case RESET:
                            // Reset everything.
                            currentObject.setColor(ChatColor.Legacy.GRAY);
                            currentObject.setFont(MinecraftFont.DEFAULT);
                            currentObject.setObfuscated(false);
                            currentObject.setBold(false);
                            currentObject.setItalic(false);
                            currentObject.setUnderlined(false);
                            currentObject.setStrikethrough(false);
                            break;
                        default:
                            // emulate Minecraft's behavior of dropping styles that do not yet have an object.
                            currentFont = currentObject.getFont(); // capture font before reset
                            currentObject = segmentSupplier.get();
                            currentObject.setColor(code.chatColor());
                            currentObject.setFont(currentFont); // preserve the active font
                            break;
                    }
                } else {
                    text.append(charAtIndex);
                }
            } else {
                text.append(charAtIndex);
            }
        }

        // whatever we were working on when the loop exited
        currentObject.setText(text.toString());
        builder.withSegments(currentObject);

        return builder.build();
    }

    public Optional<ChatColor> getColor() {
        return Optional.ofNullable(this.color);
    }

    public void setColor(@NotNull ChatColor color) {
        this.color = color;
    }

    public void setText(@NotNull String value) {
        // Handle Unescaped Windows Apostrophe
        this.text = value
            .replaceAll("(?<!\\\\)'", "’") // Handle Unescaped Windows Apostrophe
            .replaceAll("\\\\'", "'"); // Remove Escaped Backslash
    }

    public @NotNull JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("text", this.getText());
        // Lowercase keeps MIG's canonical hex form (#rrggbb); the library's Custom color emits
        // uppercase. Named colors are already lowercase, so this is a no-op for them.
        this.getColor().ifPresent(color -> object.addProperty("color", color.toJsonString().toLowerCase(java.util.Locale.ROOT)));

        if (this.packFontId != null) {
            object.addProperty("font", this.packFontId);
        } else if (this.font != MinecraftFont.DEFAULT) {
            object.addProperty("font", this.font.getResourceLocation());
        }
        if (this.isItalic()) object.addProperty("italic", true);
        if (this.isBold()) object.addProperty("bold", true);
        if (this.isUnderlined()) object.addProperty("underlined", true);
        if (this.isObfuscated()) object.addProperty("obfuscated", true);
        if (this.isStrikethrough()) object.addProperty("strikethrough", true);
        // A disabled shadow round-trips as the canonical alpha-0 white; enabled shadows (the
        // default) emit nothing, so segments that never touched shadow_color stay byte-identical.
        if (!this.shadowEnabled) object.addProperty("shadow_color", 16777215);

        return object;
    }

    public @NotNull String toLegacy() {
        return this.toLegacy(LegacyCode.SECTION_SYMBOL);
    }

    /**
     * Takes an {@link ColorSegment} and transforms it into a legacy string.
     *
     * @param substitute The substitute character to use if you do not want to use {@link LegacyCode#SECTION_SYMBOL}
     *
     * @return A legacy string representation of a text object
     */
    public @NotNull String toLegacy(char substitute) {
        return this.toLegacyBuilder(substitute).toString();
    }

    protected @NotNull StringBuilder toLegacyBuilder() {
        return this.toLegacyBuilder(LegacyCode.SECTION_SYMBOL);
    }

    protected @NotNull StringBuilder toLegacyBuilder(char symbol) {
        StringBuilder builder = new StringBuilder();
        this.getColor().ifPresent(color -> builder.append(symbol).append(legacyCodeOf(color)));
        if (this.isObfuscated()) builder.append(symbol).append(LegacyCode.OBFUSCATED.getCode());
        if (this.isBold()) builder.append(symbol).append(LegacyCode.BOLD.getCode());
        if (this.isStrikethrough()) builder.append(symbol).append(LegacyCode.STRIKETHROUGH.getCode());
        if (this.isUnderlined()) builder.append(symbol).append(LegacyCode.UNDERLINE.getCode());
        if (this.isItalic()) builder.append(symbol).append(LegacyCode.ITALIC.getCode());

        this.getColor().ifPresent(color -> {
            builder.setLength(0);
            builder.append(symbol).append(LegacyCode.RESET.getCode());
        });

        if (!this.getText().isEmpty()) {
            builder.append(this.getText());
        }

        return builder;
    }

    /**
     * The in-band legacy code for a color: the single-character code for a named
     * {@link ChatColor.Legacy} color, or the {@code #RRGGBB} hex literal for a custom color.
     */
    private static @NotNull String legacyCodeOf(@NotNull ChatColor color) {
        return color.code().map(String::valueOf).orElseGet(color::toJsonString);
    }

    public @Nullable TextSegment toTextObject() {
        return TextSegment.fromJson(this.toJson());
    }

    public static class Builder implements ClassBuilder<ColorSegment> {
        protected String text = "";
        protected ChatColor color = ChatColor.Legacy.GRAY;
        protected MinecraftFont font = MinecraftFont.DEFAULT;
        protected String packFontId;
        protected boolean italic, bold, underlined, obfuscated, strikethrough;
        protected boolean shadowEnabled = true;

        public Builder isBold() {
            return this.isBold(true);
        }

        public Builder isBold(boolean value) {
            this.bold = value;
            return this;
        }

        public Builder isItalic() {
            return this.isItalic(true);
        }

        public Builder isItalic(boolean value) {
            this.italic = value;
            return this;
        }

        public Builder isObfuscated() {
            return this.isObfuscated(true);
        }

        public Builder isObfuscated(boolean value) {
            this.obfuscated = value;
            return this;
        }

        public Builder isStrikethrough() {
            return this.isStrikethrough(true);
        }

        public Builder isStrikethrough(boolean value) {
            this.strikethrough = value;
            return this;
        }

        public Builder isUnderlined() {
            return this.isUnderlined(true);
        }

        public Builder isUnderlined(boolean value) {
            this.underlined = value;
            return this;
        }

        public Builder withColor(@NotNull ChatColor color) {
            this.color = color;
            return this;
        }

        public Builder withFont(@NotNull MinecraftFont font) {
            this.font = font;
            return this;
        }

        /**
         * Sets a resource-pack font id (any resource location, e.g. {@code "mypack:chat"}). Takes
         * precedence over {@link #withFont} for pack glyph lookup when a pack is active; see
         * {@link ColorSegment#packFontId}.
         */
        public Builder withPackFontId(@Nullable String packFontId) {
            this.packFontId = packFontId;
            return this;
        }

        public Builder withText(@NotNull String text) {
            this.text = text;
            return this;
        }

        /**
         * Whether this segment draws its drop shadow (default {@code true}); see
         * {@link ColorSegment#shadowEnabled}.
         */
        public Builder withShadowEnabled(boolean shadowEnabled) {
            this.shadowEnabled = shadowEnabled;
            return this;
        }

        @Override
        public @NotNull ColorSegment build() {
            ColorSegment colorSegment = new ColorSegment(this.text);
            colorSegment.setColor(this.color);
            colorSegment.setFont(this.font);
            colorSegment.setPackFontId(this.packFontId);
            colorSegment.setObfuscated(this.obfuscated);
            colorSegment.setItalic(this.italic);
            colorSegment.setBold(this.bold);
            colorSegment.setUnderlined(this.underlined);
            colorSegment.setStrikethrough(this.strikethrough);
            colorSegment.setShadowEnabled(this.shadowEnabled);
            return colorSegment;
        }
    }
}

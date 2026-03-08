package net.aerh.imagegenerator.text.segment;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.text.ChatFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Supplier;

@Getter
@Setter
@ToString
public class ColorSegment {

    private static final Color DEFAULT_FOREGROUND = ChatFormat.GRAY.getColor();
    private static final Color DEFAULT_SHADOW = ChatFormat.GRAY.getBackgroundColor();

    protected @NotNull String text;
    protected @NotNull Color foregroundColor = DEFAULT_FOREGROUND;
    protected @NotNull Color shadowColor = DEFAULT_SHADOW;
    protected boolean italic, bold, underlined, obfuscated, strikethrough;

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
     * Legacy text strings use the {@link ChatFormat#SECTION_SYMBOL}. Many keyboards do not have this symbol however,
     * which is probably why it was chosen. To get around this, it is common practice to substitute
     * the symbol for another, then translate it later. Often '&' is used, but this can differ from person
     * to person. In case the string does not have a {@link ChatFormat#SECTION_SYMBOL}, the method also checks for the
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

            if (charAtIndex == ChatFormat.SECTION_SYMBOL || charAtIndex == symbolSubstitute) {
                if ((i + 1) > legacyText.length() - 1)
                    continue; // do nothing.

                // peek at the next character.
                char peek = legacyText.charAt(i + 1);

                // Check for hex color: &#RRGGBB (7 chars after &)
                if (peek == '#' && i + 7 < legacyText.length()) {
                    String hexStr = legacyText.substring(i + 1, i + 8); // #RRGGBB
                    Color hexColor = parseHexColor(hexStr);
                    if (hexColor != null) {
                        if (!text.isEmpty()) {
                            currentObject.setText(text.toString());
                            builder.withSegments(currentObject);
                            currentObject = segmentSupplier.get();
                            text.setLength(0);
                        }
                        currentObject = segmentSupplier.get();
                        currentObject.setColor(hexColor);
                        i += 7; // skip &#RRGGBB
                        continue;
                    }
                }

                // Check for BungeeCord hex: &x&R&R&G&G&B&B (13 chars total from &x)
                if ((peek == 'x' || peek == 'X') && i + 13 <= legacyText.length()) {
                    Color bungeeColor = parseBungeeHexColor(legacyText, i, symbolSubstitute);
                    if (bungeeColor != null) {
                        if (!text.isEmpty()) {
                            currentObject.setText(text.toString());
                            builder.withSegments(currentObject);
                            currentObject = segmentSupplier.get();
                            text.setLength(0);
                        }
                        currentObject = segmentSupplier.get();
                        currentObject.setColor(bungeeColor);
                        i += 13; // skip &x&R&R&G&G&B&B
                        continue;
                    }
                }

                if (ChatFormat.isValid(peek)) {
                    i += 1; // if valid
                    if (!text.isEmpty()) {
                        currentObject.setText(text.toString()); // create a new text object
                        builder.withSegments(currentObject); // append the current object.
                        currentObject = segmentSupplier.get(); // reset the current object.
                        text.setLength(0); // reset the buffer
                    }

                    ChatFormat color = Objects.requireNonNull(ChatFormat.of(peek));

                    switch (color) {
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
                            currentObject.setColor(ChatFormat.GRAY);
                            currentObject.setObfuscated(false);
                            currentObject.setBold(false);
                            currentObject.setItalic(false);
                            currentObject.setUnderlined(false);
                            currentObject.setStrikethrough(false);
                            break;
                        default:
                            // emulate Minecraft's behavior of dropping styles that do not yet have an object.
                            currentObject = segmentSupplier.get();
                            currentObject.setColor(color);
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

    /**
     * Sets the color from a {@link ChatFormat} enum value, using its predefined foreground and shadow colors.
     */
    public void setColor(@NotNull ChatFormat format) {
        if (format.isColor()) {
            this.foregroundColor = format.getColor();
            this.shadowColor = format.getBackgroundColor();
        }
    }

    /**
     * Sets the color from an arbitrary {@link Color}, computing the shadow automatically
     * using Minecraft's formula (each RGB channel divided by 4).
     */
    public void setColor(@NotNull Color color) {
        this.foregroundColor = color;
        this.shadowColor = ChatFormat.computeShadowColor(color);
    }

    public void setText(@NotNull String value) {
        // Handle Unescaped Windows Apostrophe
        this.text = value
            .replaceAll("(?<!\\\\)'", "\u2019") // Handle Unescaped Windows Apostrophe
            .replaceAll("\\\\'", "\u2019"); // Remove Escaped Backslash
    }

    public @NotNull JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("text", this.getText());

        ChatFormat named = ChatFormat.fromColor(this.foregroundColor);
        if (named != null) {
            object.addProperty("color", named.toJsonString());
        } else {
            object.addProperty("color", toHexString(this.foregroundColor));
        }

        if (this.isItalic()) object.addProperty("italic", true);
        if (this.isBold()) object.addProperty("bold", true);
        if (this.isUnderlined()) object.addProperty("underlined", true);
        if (this.isObfuscated()) object.addProperty("obfuscated", true);
        if (this.isStrikethrough()) object.addProperty("strikethrough", true);

        return object;
    }

    public @NotNull String toLegacy() {
        return this.toLegacy(ChatFormat.SECTION_SYMBOL);
    }

    /**
     * Takes an {@link ColorSegment} and transforms it into a legacy string.
     *
     * @param substitute The substitute character to use if you do not want to use {@link ChatFormat#SECTION_SYMBOL}
     *
     * @return A legacy string representation of a text object
     */
    public @NotNull String toLegacy(char substitute) {
        return this.toLegacyBuilder(substitute).toString();
    }

    protected @NotNull StringBuilder toLegacyBuilder() {
        return this.toLegacyBuilder(ChatFormat.SECTION_SYMBOL);
    }

    protected @NotNull StringBuilder toLegacyBuilder(char symbol) {
        StringBuilder builder = new StringBuilder();

        ChatFormat named = ChatFormat.fromColor(this.foregroundColor);
        if (named != null) {
            builder.append(symbol).append(named.getCode());
        } else {
            // Emit BungeeCord hex format: &x&R&R&G&G&B&B
            String hex = toHexString(this.foregroundColor).substring(1); // remove #
            builder.append(symbol).append('x');
            for (char c : hex.toCharArray()) {
                builder.append(symbol).append(c);
            }
        }

        if (this.isObfuscated()) builder.append(symbol).append(ChatFormat.OBFUSCATED.getCode());
        if (this.isBold()) builder.append(symbol).append(ChatFormat.BOLD.getCode());
        if (this.isStrikethrough()) builder.append(symbol).append(ChatFormat.STRIKETHROUGH.getCode());
        if (this.isUnderlined()) builder.append(symbol).append(ChatFormat.UNDERLINE.getCode());
        if (this.isItalic()) builder.append(symbol).append(ChatFormat.ITALIC.getCode());

        if (!this.getText().isEmpty()) {
            builder.append(this.getText());
        }

        return builder;
    }

    public @Nullable TextSegment toTextObject() {
        return TextSegment.fromJson(this.toJson());
    }

    /**
     * Parses a hex color string like "#FF5555" into a Color, or null if invalid.
     */
    static @Nullable Color parseHexColor(@NotNull String hex) {
        if (!hex.startsWith("#") || hex.length() != 7) {
            return null;
        }
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a BungeeCord hex color format: &x&R&R&G&G&B&B starting at position i.
     * Returns null if the format doesn't match.
     */
    private static @Nullable Color parseBungeeHexColor(String text, int startPos, char symbol) {
        // Expected: &x&R&R&G&G&B&B (14 chars total)
        if (startPos + 13 > text.length()) {
            return null;
        }

        char s1 = text.charAt(startPos); // &
        char x = text.charAt(startPos + 1); // x
        if ((s1 != '&' && s1 != ChatFormat.SECTION_SYMBOL && s1 != symbol) || (x != 'x' && x != 'X')) {
            return null;
        }

        StringBuilder hexBuilder = new StringBuilder("#");
        for (int j = 0; j < 6; j++) {
            int pairStart = startPos + 2 + (j * 2);
            char sym = text.charAt(pairStart);
            if (sym != '&' && sym != ChatFormat.SECTION_SYMBOL && sym != symbol) {
                return null;
            }
            char hexChar = text.charAt(pairStart + 1);
            if (!isHexDigit(hexChar)) {
                return null;
            }
            hexBuilder.append(hexChar);
        }

        return parseHexColor(hexBuilder.toString());
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    static @NotNull String toHexString(@NotNull Color color) {
        return String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }

    public static class Builder implements ClassBuilder<ColorSegment> {
        protected String text = "";
        protected Color foregroundColor = DEFAULT_FOREGROUND;
        protected Color shadowColor = DEFAULT_SHADOW;
        protected boolean italic, bold, underlined, obfuscated, strikethrough;

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

        public Builder withColor(@NotNull ChatFormat color) {
            if (color.isColor()) {
                this.foregroundColor = color.getColor();
                this.shadowColor = color.getBackgroundColor();
            }
            return this;
        }

        public Builder withColor(@NotNull Color color) {
            this.foregroundColor = color;
            this.shadowColor = ChatFormat.computeShadowColor(color);
            return this;
        }

        public Builder withText(@NotNull String text) {
            this.text = text;
            return this;
        }

        @Override
        public @NotNull ColorSegment build() {
            ColorSegment colorSegment = new ColorSegment(this.text);
            colorSegment.foregroundColor = this.foregroundColor;
            colorSegment.shadowColor = this.shadowColor;
            colorSegment.setObfuscated(this.obfuscated);
            colorSegment.setItalic(this.italic);
            colorSegment.setBold(this.bold);
            colorSegment.setUnderlined(this.underlined);
            colorSegment.setStrikethrough(this.strikethrough);
            return colorSegment;
        }
    }
}

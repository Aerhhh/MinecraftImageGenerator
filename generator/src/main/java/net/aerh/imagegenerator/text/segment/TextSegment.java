package net.aerh.imagegenerator.text.segment;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Setter;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.impl.nbt.NbtTextComponentUtil;
import net.aerh.imagegenerator.text.ChatFormat;
import net.aerh.imagegenerator.text.event.ClickEvent;
import net.aerh.imagegenerator.text.event.HoverEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Optional;

@Setter
public final class TextSegment extends ColorSegment {

    private ClickEvent clickEvent;
    private HoverEvent hoverEvent;

    public TextSegment(@NotNull String text) {
        super(text);
    }

    public static @Nullable TextSegment fromJson(@NotNull String jsonString) {
        return fromJson(JsonParser.parseString(jsonString).getAsJsonObject());
    }

    public static @Nullable TextSegment fromJson(@NotNull JsonObject jsonObject) {
        if (jsonObject.has("text")) {
            TextSegment textSegment = new TextSegment(jsonObject.get("text").getAsString());
            if (jsonObject.has("clickEvent"))
                textSegment.setClickEvent(ClickEvent.fromJson(jsonObject.get("clickEvent").getAsJsonObject()));
            if (jsonObject.has("hoverEvent"))
                textSegment.setHoverEvent(HoverEvent.fromJson(jsonObject.get("hoverEvent").getAsJsonObject()));
            if (jsonObject.has("color")) {
                String colorStr = jsonObject.get("color").getAsString();
                if (colorStr.startsWith("#")) {
                    Color hexColor = ColorSegment.parseHexColor(colorStr);
                    if (hexColor != null) {
                        textSegment.setColor(hexColor);
                    }
                } else {
                    ChatFormat format = ChatFormat.of(colorStr);
                    if (format != null && format.isColor()) {
                        textSegment.setColor(format);
                    }
                }
            }
            if (jsonObject.has("obfuscated"))
                textSegment.setObfuscated(NbtTextComponentUtil.parseBooleanStrict(jsonObject.get("obfuscated")));
            if (jsonObject.has("italic"))
                textSegment.setItalic(NbtTextComponentUtil.parseBooleanStrict(jsonObject.get("italic")));
            if (jsonObject.has("bold"))
                textSegment.setBold(NbtTextComponentUtil.parseBooleanStrict(jsonObject.get("bold")));
            if (jsonObject.has("underlined"))
                textSegment.setUnderlined(NbtTextComponentUtil.parseBooleanStrict(jsonObject.get("underlined")));
            if (jsonObject.has("strikethrough"))
                textSegment.setStrikethrough(NbtTextComponentUtil.parseBooleanStrict(jsonObject.get("strikethrough")));

            return textSegment;
        }

        // invalid object
        return null;
    }

    /**
     * This function takes in a legacy text string and converts it into a {@link TextSegment}.
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
        return fromLegacyHandler(legacyText, symbolSubstitute, () -> new TextSegment(""));
    }

    @Override
    public @NotNull JsonObject toJson() {
        JsonObject object = super.toJson(); // ColorSegment#toJson
        this.getClickEvent().ifPresent(clickEvent -> object.add("clickEvent", clickEvent.toJson()));
        this.getHoverEvent().ifPresent(hoverEvent -> object.add("hoverEvent", hoverEvent.toJson()));
        return object;
    }

    public Optional<ClickEvent> getClickEvent() {
        return Optional.ofNullable(clickEvent);
    }

    public Optional<HoverEvent> getHoverEvent() {
        return Optional.ofNullable(hoverEvent);
    }

    @Override
    public String toString() {
        return "TextSegment{" +
            "clickEvent=" + clickEvent +
            ", hoverEvent=" + hoverEvent +
            ", text='" + text + '\'' +
            ", foregroundColor=" + foregroundColor +
            ", italic=" + italic +
            ", bold=" + bold +
            ", underlined=" + underlined +
            ", obfuscated=" + obfuscated +
            ", strikethrough=" + strikethrough +
            '}';
    }

    public static class Builder implements ClassBuilder<TextSegment> {

        protected String text = "";
        protected Color foregroundColor;
        protected Color shadowColor;
        protected boolean italic, bold, underlined, obfuscated, strikethrough;
        private ClickEvent clickEvent;
        private HoverEvent hoverEvent;

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

        public Builder withClickEvent(@NotNull ClickEvent clickEvent) {
            this.clickEvent = clickEvent;
            return this;
        }

        public Builder withHoverEvent(@NotNull HoverEvent hoverEvent) {
            this.hoverEvent = hoverEvent;
            return this;
        }

        public Builder withText(@NotNull String text) {
            this.text = text;
            return this;
        }

        @Override
        public @NotNull TextSegment build() {
            TextSegment textSegment = new TextSegment(this.text);
            textSegment.setClickEvent(this.clickEvent);
            textSegment.setHoverEvent(hoverEvent);
            if (this.foregroundColor != null) {
                textSegment.foregroundColor = this.foregroundColor;
                textSegment.shadowColor = this.shadowColor != null ? this.shadowColor : ChatFormat.computeShadowColor(this.foregroundColor);
            }
            textSegment.setObfuscated(this.obfuscated);
            textSegment.setItalic(this.italic);
            textSegment.setBold(this.bold);
            textSegment.setUnderlined(this.underlined);
            textSegment.setStrikethrough(this.strikethrough);
            return textSegment;
        }
    }
}

package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.data.Icon;
import net.aerh.imagegenerator.parser.ParseContext;
import net.aerh.imagegenerator.parser.StringParser;

import java.util.regex.Matcher;

public class IconParser implements StringParser {

    @Override
    public String parse(String input) {
        return parse(input, ParseContext.empty());
    }

    @Override
    public String parse(String input, ParseContext context) {
        if (input.isBlank()) {
            return input;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(input);

        while (matcher.find()) {
            String match = matcher.group(0);
            String icon = matcher.group(1);
            String extraData = matcher.group(2);
            Icon iconEnum = Icon.byName(icon);

            if (iconEnum == null) {
                continue;
            }

            input = input.replace(match, parseIcon(iconEnum, extraData, context));
        }

        return input;
    }

    private String parseIcon(Icon icon, String extra, ParseContext context) {
        String character = icon.getIcon(context.packId());

        if (extra == null) {
            return character;
        }

        try {
            int amount = Integer.parseInt(extra);
            return character.repeat(amount);
        } catch (NumberFormatException e) {
            return character;
        }
    }
}

package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.data.Flavor;
import net.aerh.imagegenerator.data.Icon;
import net.aerh.imagegenerator.data.Stat;
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

            if (ICON_REFERENCE_NAME.equalsIgnoreCase(icon)) {
                String replacement = parseIconReference(extraData, context);
                if (replacement != null) {
                    input = input.replace(match, replacement);
                }
                continue;
            }

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

    /**
     * Resolves the icon-only reference form {@code %%icon:<name>%%} (optionally
     * {@code %%icon:<name>:<count>%%}) to just the icon character of the named stat, icon, or
     * flavor entry.
     *
     * @param extra   the target name, optionally followed by a repeat count
     * @param context the {@link ParseContext} carrying pack-conditional state
     *
     * @return the icon character(s), or {@code null} to leave the placeholder untouched when the
     *     target is missing, unknown, has no icon character, or the count is not positive
     */
    private String parseIconReference(String extra, ParseContext context) {
        if (extra == null || extra.isEmpty()) {
            return null;
        }

        String target = extra;
        int count = 1;
        int separator = extra.lastIndexOf(':');

        if (separator != -1) {
            try {
                count = Integer.parseInt(extra.substring(separator + 1));
                target = extra.substring(0, separator);
            } catch (NumberFormatException e) {
                // no trailing count; the whole extra is the target name
            }
        }

        String character = resolveIconCharacter(target, context);

        if (character == null || character.isEmpty() || count < 1) {
            return null;
        }

        return character.repeat(count);
    }

    private String resolveIconCharacter(String target, ParseContext context) {
        Stat stat = Stat.byName(target);
        if (stat != null) {
            return stat.getIcon(context.packId());
        }

        Icon icon = Icon.byName(target);
        if (icon != null) {
            return icon.getIcon(context.packId());
        }

        Flavor flavor = Flavor.byName(target);
        if (flavor != null) {
            return flavor.getIcon(context.packId());
        }

        return null;
    }
}

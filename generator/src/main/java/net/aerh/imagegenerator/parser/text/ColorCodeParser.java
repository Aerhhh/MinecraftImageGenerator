package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.parser.StringParser;
import net.aerh.imagegenerator.text.ChatFormat;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ColorCodeParser implements StringParser {

    private static final Map<ChatFormat, Pattern> PATTERNS = Arrays.stream(ChatFormat.VALUES)
        .collect(Collectors.toMap(
            chatFormat -> chatFormat,
            chatFormat -> Pattern.compile("%%" + chatFormat.name() + "%%", Pattern.CASE_INSENSITIVE)
        ));

    private static final Pattern HEX_PATTERN = Pattern.compile("%%(#[0-9a-fA-F]{6})%%");

    @Override
    public String parse(String input) {
        for (ChatFormat value : ChatFormat.VALUES) {
            Pattern pattern = PATTERNS.get(value);
            input = pattern.matcher(input).replaceAll(String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + value.getCode());
        }

        return HEX_PATTERN.matcher(input).replaceAll(ChatFormat.AMPERSAND_SYMBOL + "$1");
    }
}
package net.aerh.imagegenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.json.JsonLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class ParseType {

    private static final List<ParseType> PARSE_TYPES = new ArrayList<>();

    static {
        try {
            PARSE_TYPES.addAll(JsonLoader.loadFromJson(ParseType[].class, Objects.requireNonNull(ParseType.class.getClassLoader().getResource("data/parse_types.json"))));
            log.info("Loaded {} parse types!", PARSE_TYPES.size());
        } catch (Exception e) {
            log.error("Failed to load parse type data", e);
        }
        ExternalDataLoader.mergeExternal(PARSE_TYPES, ParseType[].class, "parse_types.json", ParseType::getName);
    }

    private String name;
    private String formatWithDetails;
    private String formatWithoutDetails;

    public static ParseType byName(String name) {
        return PARSE_TYPES.stream()
            .filter(parseType -> parseType.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}

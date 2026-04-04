package net.aerh.imagegenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.registry.DataRegistry;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class ArmorType {

    private static final DataRegistry<ArmorType> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<ArmorType[]> getArrayType() {
            return ArmorType[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/armor_types.json";
        }

        @Override
        protected String getExternalFileName() {
            return "armor_types.json";
        }

        @Override
        protected Function<ArmorType, String> getNameExtractor() {
            return ArmorType::getMaterialName;
        }
    };

    private static final Set<String> COLORABLE_ARMOR_NAMES;

    static {
        try {
            REGISTRY.load();
        } catch (IOException e) {
            log.error("Failed to load armor type data", e);
        }

        COLORABLE_ARMOR_NAMES = REGISTRY.getAll().stream()
            .filter(ArmorType::isSupportsCustomColoring)
            .map(ArmorType::getMaterialName)
            .collect(Collectors.toSet());
    }

    private String materialName;
    private boolean supportsCustomColoring;

    /**
     * Checks if the given overlay name represents colorable armor.
     *
     * @param overlayName The overlay name to check
     * @return true if the armor supports custom coloring, false otherwise
     */
    public static boolean isColorableArmor(String overlayName) {
        if (overlayName == null) {
            return false;
        }

        String lowerCaseName = overlayName.toLowerCase();
        boolean isColorable = COLORABLE_ARMOR_NAMES.stream()
            .anyMatch(lowerCaseName::contains);

        log.debug("Checking overlay name '{}' for colorable armor: {}", overlayName, isColorable);
        return isColorable;
    }

    /**
     * Gets the armor type from an overlay name.
     *
     * @param overlayName The overlay name to parse
     * @return The matching ArmorType or null if none found
     */
    public static ArmorType fromOverlayName(String overlayName) {
        if (overlayName == null) {
            return null;
        }

        String lowerCaseName = overlayName.toLowerCase();
        return REGISTRY.findFirst(type -> lowerCaseName.contains(type.getMaterialName())).orElse(null);
    }

    public static List<ArmorType> getAll() {
        return REGISTRY.getAll();
    }
}

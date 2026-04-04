package net.aerh.imagegenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.text.ChatFormat;
import net.hypixel.nerdbot.marmalade.registry.DataRegistry;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Stat implements FormattableEntry {

    private static final DataRegistry<Stat> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<Stat[]> getArrayType() {
            return Stat[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/stats.json";
        }

        @Override
        protected String getExternalFileName() {
            return "stats.json";
        }

        @Override
        protected Function<Stat, String> getNameExtractor() {
            return Stat::getName;
        }
    };

    static {
        try {
            REGISTRY.load();
        } catch (IOException e) {
            log.error("Failed to load stat data", e);
        }
    }

    private String icon;
    private String name;
    private String stat;
    private String display;
    private ChatFormat color;
    private ChatFormat subColor;
    private String parseType;
    @Nullable
    private Float powerScalingMultiplier;

    public static Stat byName(String name) {
        return REGISTRY.byName(name).orElse(null);
    }

    public static List<Stat> getStats() {
        return REGISTRY.getAll();
    }

    public static Stat byStatText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String normalized = text.trim();

        return REGISTRY.findFirst(stat -> {
            if (stat.getStat() != null && stat.getStat().equalsIgnoreCase(normalized)) {
                return true;
            }

            if (stat.getDisplay() != null) {
                String display = stat.getDisplay().trim();
                String strippedDisplay = display.replaceAll("^[^A-Za-z0-9]+", "").trim();
                return strippedDisplay.equalsIgnoreCase(normalized);
            }

            return false;
        }).orElse(null);
    }

    /**
     * In some cases, stats can have multiple colors.
     * One for the number and another for the stat.
     *
     * @return Secondary {@link ChatFormat} of the stat
     */
    public ChatFormat getSecondaryColor() {
        if (subColor != null) {
            return subColor;
        } else {
            return color;
        }
    }
}

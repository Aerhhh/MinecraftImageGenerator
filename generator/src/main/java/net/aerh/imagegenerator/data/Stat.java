package net.aerh.imagegenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.pack.PackId;
import net.hypixel.nerdbot.marmalade.registry.DataRegistry;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
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
    private ChatColor.Legacy color;
    private ChatColor.Legacy subColor;
    private String parseType;
    @Nullable
    private Float powerScalingMultiplier;
    /**
     * Pack-conditional replacement icon characters keyed by pack ID string
     * (e.g. {@code "hypixel:skyblock"}). Icon-only: no other field is overridable.
     */
    @Nullable
    private Map<String, String> packOverrides;

    /**
     * Resolves the icon character for the given pack: the exact-pack-ID override when one exists,
     * otherwise the base {@link #icon}.
     *
     * @param packId the active pack, or {@code null} for none
     *
     * @return the icon character to render
     */
    @Override
    public String getIcon(@Nullable PackId packId) {
        if (packId != null && packOverrides != null) {
            String override = packOverrides.get(packId.toString());
            if (override != null) {
                return override;
            }
        }
        return icon;
    }

    /**
     * Resolves the display text for the given pack. When an icon override is active the display is
     * derived as {@code overrideIcon + " " + stat} so it stays consistent with the swapped icon;
     * otherwise the stored hand-tuned {@link #display} is returned unchanged.
     *
     * @param packId the active pack, or {@code null} for none
     *
     * @return the display text
     */
    @Override
    public String getDisplay(@Nullable PackId packId) {
        if (packId != null && packOverrides != null) {
            String override = packOverrides.get(packId.toString());
            if (override != null) {
                return stat != null ? override + " " + stat : override;
            }
        }
        return display;
    }

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
     * @return Secondary {@link ChatColor.Legacy} of the stat
     */
    public ChatColor.Legacy getSecondaryColor() {
        if (subColor != null) {
            return subColor;
        } else {
            return color;
        }
    }
}

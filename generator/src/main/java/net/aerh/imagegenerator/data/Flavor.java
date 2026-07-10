package net.aerh.imagegenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.text.ChatFormat;
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
public class Flavor implements FormattableEntry {

    private static final DataRegistry<Flavor> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<Flavor[]> getArrayType() {
            return Flavor[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/flavor.json";
        }

        @Override
        protected String getExternalFileName() {
            return "flavor.json";
        }

        @Override
        protected Function<Flavor, String> getNameExtractor() {
            return Flavor::getName;
        }
    };

    static {
        try {
            REGISTRY.load();
        } catch (IOException e) {
            log.error("Failed to load flavor text data", e);
        }
    }

    private String icon;
    private String name;
    private String stat;
    private String display;
    private ChatFormat color;
    @Nullable
    private ChatFormat subColor;
    private String parseType;
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
        String override = resolveOverride(packId);
        return override != null ? override : icon;
    }

    /**
     * Resolves the display text for the given pack. When an icon override is active every
     * occurrence of the base icon character is swapped, covering entries whose text embeds the
     * icon beyond the leading position (e.g. "X This armor piece is undead X!").
     *
     * @param packId the active pack, or {@code null} for none
     *
     * @return the display text
     */
    @Override
    public String getDisplay(@Nullable PackId packId) {
        return swapIcon(display, resolveOverride(packId));
    }

    /**
     * Resolves the stat text for the given pack, swapping embedded base icon characters the same
     * way as {@link #getDisplay(PackId)}.
     *
     * @param packId the active pack, or {@code null} for none
     *
     * @return the stat text
     */
    @Override
    public String getStat(@Nullable PackId packId) {
        return swapIcon(stat, resolveOverride(packId));
    }

    @Nullable
    private String resolveOverride(@Nullable PackId packId) {
        if (packId != null && packOverrides != null) {
            return packOverrides.get(packId.toString());
        }
        return null;
    }

    private String swapIcon(String text, @Nullable String override) {
        if (text == null || override == null || icon == null || icon.isEmpty()) {
            return text;
        }
        return text.replace(icon, override);
    }

    public static Flavor byName(String name) {
        return REGISTRY.byName(name).orElse(null);
    }

    public static List<Flavor> getFlavors() {
        return REGISTRY.getAll();
    }

    public ChatFormat getSecondaryColor() {
        if (subColor != null) {
            return subColor;
        } else {
            return color;
        }
    }
}
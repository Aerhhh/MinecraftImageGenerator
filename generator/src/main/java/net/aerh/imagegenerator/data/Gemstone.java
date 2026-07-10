package net.aerh.imagegenerator.data;

import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.pack.PackId;
import net.hypixel.nerdbot.marmalade.json.serializer.ColorDeserializer;
import net.hypixel.nerdbot.marmalade.registry.DataRegistry;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Gemstone {

    private static final DataRegistry<Gemstone> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<Gemstone[]> getArrayType() {
            return Gemstone[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/gemstones.json";
        }

        @Override
        protected String getExternalFileName() {
            return "gemstones.json";
        }

        @Override
        protected Function<Gemstone, String> getNameExtractor() {
            return Gemstone::getName;
        }

        @Override
        protected UnaryOperator<GsonBuilder> customizeGson() {
            return builder -> builder.registerTypeAdapter(Color.class, new ColorDeserializer());
        }
    };

    static {
        try {
            REGISTRY.load();
        } catch (IOException e) {
            log.error("Failed to load gemstone data", e);
        }
    }

    private String name;
    private String icon;
    private String formattedIcon;
    private Color color;
    private Map<String, String> formattedTiers;
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
    public String getIcon(@Nullable PackId packId) {
        String override = resolveOverride(packId);
        return override != null ? override : icon;
    }

    /**
     * Resolves the formatted (color-coded) icon for the given pack. When an icon override is
     * active it is derived by swapping the base icon character inside {@link #formattedIcon} so
     * the hand-tuned color codes are preserved.
     *
     * @param packId the active pack, or {@code null} for none
     *
     * @return the formatted icon to render
     */
    public String getFormattedIcon(@Nullable PackId packId) {
        String override = resolveOverride(packId);
        if (override == null || formattedIcon == null || icon == null || icon.isEmpty()) {
            return formattedIcon;
        }
        return formattedIcon.replace(icon, override);
    }

    @Nullable
    private String resolveOverride(@Nullable PackId packId) {
        if (packId != null && packOverrides != null) {
            return packOverrides.get(packId.toString());
        }
        return null;
    }

    public static Gemstone byName(String name) {
        return REGISTRY.byName(name).orElse(null);
    }

    public static List<Gemstone> getGemstones() {
        return REGISTRY.getAll();
    }
}

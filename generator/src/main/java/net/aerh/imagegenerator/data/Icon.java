package net.aerh.imagegenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
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
public class Icon {

    private static final DataRegistry<Icon> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<Icon[]> getArrayType() {
            return Icon[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/icons.json";
        }

        @Override
        protected String getExternalFileName() {
            return "icons.json";
        }

        @Override
        protected Function<Icon, String> getNameExtractor() {
            return Icon::getName;
        }
    };

    static {
        try {
            REGISTRY.load();
        } catch (IOException e) {
            log.error("Failed to load icon data", e);
        }
    }

    private String name;
    private String icon;
    /**
     * Pack-conditional replacement characters keyed by pack ID string (e.g. {@code "hypixel:skyblock"}).
     * When the keyed pack is active, {@link #getIcon(PackId)} returns the override instead of {@link #icon}.
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
        if (packId != null && packOverrides != null) {
            String override = packOverrides.get(packId.toString());
            if (override != null) {
                return override;
            }
        }
        return icon;
    }

    public static Icon byName(String name) {
        return REGISTRY.byName(name).orElse(null);
    }

    public static List<Icon> getIcons() {
        return REGISTRY.getAll();
    }
}

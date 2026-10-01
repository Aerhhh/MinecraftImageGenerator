package net.aerh.imagegenerator.tools.pack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The packs to register with a {@link ResourcePackService} and which one applies when a request
 * names none.
 *
 * @param packs       Packs to register, in order
 * @param defaultPack The pack id applied when a request does not name one; null or blank means vanilla
 */
public record PackRegistrationConfig(List<PackDefinition> packs, @Nullable String defaultPack) {

    public PackRegistrationConfig {
        packs = packs == null ? List.of() : List.copyOf(packs);
    }

    public static PackRegistrationConfig of(@Nullable String defaultPack, PackDefinition... packs) {
        return new PackRegistrationConfig(List.of(packs), defaultPack);
    }
}

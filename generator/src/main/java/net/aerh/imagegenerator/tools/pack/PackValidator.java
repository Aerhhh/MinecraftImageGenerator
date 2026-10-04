package net.aerh.imagegenerator.tools.pack;

import net.aerh.imagegenerator.pack.ItemState;
import net.aerh.imagegenerator.pack.PackFormatRange;
import net.aerh.imagegenerator.pack.PackItemVisual;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PreparedPack;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks a prepared pack before it goes live, cheapest check first, stopping at the first failure:
 * the declared format covers the advertised one, enough items indexed compared with the live pack,
 * and every sample item exists and renders to at least one visible pixel.
 */
public final class PackValidator {

    private static final int SAMPLE_PIXELS_PER_GUI_PX = 4;

    private PackValidator() {
    }

    /**
     * @param currentItemCount Items indexed by the live pack, or 0 when there is none (the floor is then 1)
     */
    public static PackValidation validate(PreparedPack candidate, PackExpectations expectations, int currentItemCount) {
        Optional<PackFormatRange> declared = candidate.declaredFormat();
        if (declared.isEmpty()) {
            return new PackValidation.Invalid("pack.mcmeta is missing or does not declare a pack format");
        }

        int expected = expectations.expectedPackFormat();
        if (!declared.get().contains(expected)) {
            return new PackValidation.Invalid("pack.mcmeta declares formats " + declared.get().min() + " to "
                + declared.get().max() + " but the pack was published as format " + expected);
        }

        List<String> itemRefs = candidate.itemRefs();
        int floor = Math.max(1, (int) Math.ceil(currentItemCount * expectations.minItemRatio()));
        if (itemRefs.size() < floor) {
            return new PackValidation.Invalid("the pack indexed " + itemRefs.size() + " items but at least " + floor
                + " are required (" + Math.round(expectations.minItemRatio() * 100) + "% of the live pack's " + currentItemCount + ")");
        }

        Set<String> available = new HashSet<>(itemRefs);
        PackRepository preview = candidate.previewRepository();

        for (String sample : expectations.sampleItems()) {
            if (!available.contains(sample)) {
                return new PackValidation.Invalid("sample item " + sample + " is not in the pack");
            }

            try {
                Optional<PackItemVisual> visual = preview.resolveItemVisual(candidate.id(), sample, ItemState.EMPTY, SAMPLE_PIXELS_PER_GUI_PX);
                if (visual.isEmpty() || !hasVisiblePixel(imageOf(visual.get()))) {
                    return new PackValidation.Invalid("sample item " + sample + " rendered no visible pixels");
                }
            } catch (RuntimeException e) {
                return new PackValidation.Invalid("sample item " + sample + " failed to render: " + e.getMessage());
            }
        }

        return new PackValidation.Valid();
    }

    private static BufferedImage imageOf(PackItemVisual visual) {
        return switch (visual) {
            case PackItemVisual.Sprite sprite -> sprite.sprite();
            case PackItemVisual.ElementsRaster raster -> raster.image();
        };
    }

    private static boolean hasVisiblePixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

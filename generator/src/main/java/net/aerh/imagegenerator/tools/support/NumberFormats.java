package net.aerh.imagegenerator.tools.support;

import java.text.DecimalFormat;

/** Number formatting shared by the tools; copied from NerdBot so the output stays identical. */
public final class NumberFormats {

    /** Whole numbers with thousands separators, for example {@code 1,234}. */
    public static final DecimalFormat COMMA_SEPARATED = new DecimalFormat("#,###");

    private NumberFormats() {
    }

    /** A byte count in the largest binary unit that fits, for example {@code 64 kB}. */
    public static String formatSize(long size) {
        if (size <= 0) {
            return "0";
        }

        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}

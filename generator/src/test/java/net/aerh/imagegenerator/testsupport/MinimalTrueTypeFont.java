package net.aerh.imagegenerator.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a tiny, ORIGINAL TrueType font in memory at test runtime, so the TTF-rendering tests use
 * a synthetic fixture with a known geometry and never commit or redistribute any real font. The
 * font is deliberately minimal: every mapped glyph is a filled axis-aligned rectangle (or a blank
 * cell) with a caller-chosen advance, which is all the Tier-1 rasterizer needs and keeps the
 * expected pixels exactly predictable.
 *
 * <p>It emits the standard minimal sfnt table set ({@code head, hhea, maxp, hmtx, cmap, loca, glyf,
 * name, post, OS/2}) that {@link java.awt.Font#createFont} accepts. Coordinates are in font design
 * units; choosing {@code unitsPerEm} equal to the render ppem makes one font unit map to one device
 * pixel, so a rectangle's device pixel bounds equal its design bounds.
 */
public final class MinimalTrueTypeFont {

    /**
     * One glyph: a filled rectangle {@code [xMin,yMin,xMax,yMax]} in font units (y up from the
     * baseline) advancing {@code advance} units, or a {@code blank} glyph that draws nothing but
     * still advances (e.g. the space).
     */
    public record Glyph(int advance, int xMin, int yMin, int xMax, int yMax, boolean blank) {

        /** A filled rectangle glyph. */
        public static Glyph box(int advance, int xMin, int yMin, int xMax, int yMax) {
            return new Glyph(advance, xMin, yMin, xMax, yMax, false);
        }

        /** A blank (inkless) glyph advancing {@code advance} units. */
        public static Glyph blank(int advance) {
            return new Glyph(advance, 0, 0, 0, 0, true);
        }
    }

    private MinimalTrueTypeFont() {
    }

    /**
     * Builds a TrueType font mapping each entry's codepoint to a glyph. Glyph index 0 ({@code
     * .notdef}) is a blank cell; the entries take indices 1, 2, ... in iteration order.
     *
     * @param unitsPerEm design units per em (use the intended render ppem for a 1:1 unit-to-pixel
     *                   mapping)
     * @param glyphs     codepoint to glyph, iteration order fixing the glyph indices
     * @return the encoded {@code .ttf} bytes
     */
    public static byte[] build(int unitsPerEm, LinkedHashMap<Integer, Glyph> glyphs) {
        List<Glyph> ordered = new ArrayList<>();
        ordered.add(Glyph.blank(0)); // .notdef
        LinkedHashMap<Integer, Integer> codepointToGlyphId = new LinkedHashMap<>();
        int glyphId = 1;
        for (Map.Entry<Integer, Glyph> entry : glyphs.entrySet()) {
            codepointToGlyphId.put(entry.getKey(), glyphId++);
            ordered.add(entry.getValue());
        }
        int numGlyphs = ordered.size();

        GlyfAndLoca glyf = buildGlyf(ordered);
        byte[] cmap = buildCmap(codepointToGlyphId);
        byte[] hmtx = buildHmtx(ordered);
        int ascent = maxYMax(ordered);
        int descent = minYMin(ordered);
        byte[] head = buildHead(unitsPerEm, ordered);
        byte[] hhea = buildHhea(numGlyphs, ascent, descent, maxAdvance(ordered));
        byte[] maxp = buildMaxp(numGlyphs);
        byte[] name = buildName();
        byte[] post = buildPost();
        byte[] os2 = buildOs2(ascent, descent);

        LinkedHashMap<String, byte[]> tables = new LinkedHashMap<>();
        tables.put("OS/2", os2);
        tables.put("cmap", cmap);
        tables.put("glyf", glyf.glyf());
        tables.put("head", head);
        tables.put("hhea", hhea);
        tables.put("hmtx", hmtx);
        tables.put("loca", glyf.loca());
        tables.put("maxp", maxp);
        tables.put("name", name);
        tables.put("post", post);
        return assemble(tables);
    }

    private record GlyfAndLoca(byte[] glyf, byte[] loca) {
    }

    private static GlyfAndLoca buildGlyf(List<Glyph> glyphs) {
        List<byte[]> glyphBytes = new ArrayList<>();
        int[] offsets = new int[glyphs.size() + 1];
        int offset = 0;
        for (int i = 0; i < glyphs.size(); i++) {
            offsets[i] = offset;
            byte[] bytes = encodeGlyph(glyphs.get(i));
            glyphBytes.add(bytes);
            offset += bytes.length;
        }
        offsets[glyphs.size()] = offset;

        ByteArrayOutputStream glyf = new ByteArrayOutputStream();
        for (byte[] bytes : glyphBytes) {
            glyf.writeBytes(bytes);
        }
        // Short loca stores offset / 2, so every glyph offset must be even; encodeGlyph pads to an
        // even length to guarantee it.
        DataWriter loca = new DataWriter();
        for (int off : offsets) {
            loca.u16(off / 2);
        }
        return new GlyfAndLoca(glyf.toByteArray(), loca.toByteArray());
    }

    private static byte[] encodeGlyph(Glyph glyph) {
        if (glyph.blank()) {
            return new byte[0];
        }
        DataWriter w = new DataWriter();
        w.i16(1); // numberOfContours
        w.i16(glyph.xMin());
        w.i16(glyph.yMin());
        w.i16(glyph.xMax());
        w.i16(glyph.yMax());
        w.u16(3); // endPtsOfContours: single contour ending at point index 3
        w.u16(0); // instructionLength
        // Four on-curve points (bit 0 set), coordinates as int16 deltas (no short/same bits).
        for (int i = 0; i < 4; i++) {
            w.u8(0x01);
        }
        int[] xs = {glyph.xMin(), glyph.xMax(), glyph.xMax(), glyph.xMin()};
        int[] ys = {glyph.yMin(), glyph.yMin(), glyph.yMax(), glyph.yMax()};
        int prev = 0;
        for (int x : xs) {
            w.i16(x - prev);
            prev = x;
        }
        prev = 0;
        for (int y : ys) {
            w.i16(y - prev);
            prev = y;
        }
        byte[] bytes = w.toByteArray();
        if (bytes.length % 2 != 0) {
            byte[] padded = new byte[bytes.length + 1];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            return padded;
        }
        return bytes;
    }

    private static byte[] buildCmap(LinkedHashMap<Integer, Integer> codepointToGlyphId) {
        List<int[]> segments = new ArrayList<>(); // {startCode, endCode, idDelta}
        List<Integer> codepoints = new ArrayList<>(codepointToGlyphId.keySet());
        codepoints.sort(Integer::compareTo);
        int i = 0;
        while (i < codepoints.size()) {
            int start = codepoints.get(i);
            int startGlyph = codepointToGlyphId.get(start);
            int end = start;
            int j = i + 1;
            while (j < codepoints.size()
                && codepoints.get(j) == end + 1
                && codepointToGlyphId.get(codepoints.get(j)) == codepointToGlyphId.get(end) + 1) {
                end = codepoints.get(j);
                j++;
            }
            segments.add(new int[]{start, end, (startGlyph - start) & 0xFFFF});
            i = j;
        }
        segments.add(new int[]{0xFFFF, 0xFFFF, 1}); // required terminating segment

        int segCount = segments.size();
        int segCountX2 = segCount * 2;
        int searchRange = 2 * Integer.highestOneBit(segCount);
        int entrySelector = Integer.numberOfTrailingZeros(searchRange / 2);
        int rangeShift = segCountX2 - searchRange;

        DataWriter sub = new DataWriter();
        sub.u16(4); // format
        int length = 16 + segCount * 8; // header + 4 arrays (endCode, startCode, idDelta, idRangeOffset) + reservedPad
        sub.u16(length);
        sub.u16(0); // language
        sub.u16(segCountX2);
        sub.u16(searchRange);
        sub.u16(entrySelector);
        sub.u16(rangeShift);
        for (int[] seg : segments) {
            sub.u16(seg[1]); // endCode
        }
        sub.u16(0); // reservedPad
        for (int[] seg : segments) {
            sub.u16(seg[0]); // startCode
        }
        for (int[] seg : segments) {
            sub.u16(seg[2]); // idDelta
        }
        for (int ignored = 0; ignored < segCount; ignored++) {
            sub.u16(0); // idRangeOffset (all glyphs via idDelta)
        }
        byte[] subtable = sub.toByteArray();

        DataWriter cmap = new DataWriter();
        cmap.u16(0); // version
        cmap.u16(1); // numTables
        cmap.u16(3); // platformID Windows
        cmap.u16(1); // encodingID Unicode BMP
        cmap.u32(12); // offset to subtable (4 + 8)
        cmap.write(subtable);
        return cmap.toByteArray();
    }

    private static byte[] buildHmtx(List<Glyph> glyphs) {
        DataWriter w = new DataWriter();
        for (Glyph glyph : glyphs) {
            w.u16(glyph.advance());
            w.i16(glyph.blank() ? 0 : glyph.xMin()); // left side bearing
        }
        return w.toByteArray();
    }

    private static byte[] buildHead(int unitsPerEm, List<Glyph> glyphs) {
        DataWriter w = new DataWriter();
        w.u32(0x00010000); // version
        w.u32(0x00010000); // fontRevision
        w.u32(0); // checkSumAdjustment (patched after assembly)
        w.u32(0x5F0F3CF5); // magicNumber
        w.u16(0x000B); // flags
        w.u16(unitsPerEm);
        w.u32(0); // created (hi)
        w.u32(0); // created (lo)
        w.u32(0); // modified (hi)
        w.u32(0); // modified (lo)
        w.i16(minXMin(glyphs));
        w.i16(minYMin(glyphs));
        w.i16(maxXMax(glyphs));
        w.i16(maxYMax(glyphs));
        w.u16(0); // macStyle
        w.u16(8); // lowestRecPPEM
        w.i16(2); // fontDirectionHint
        w.i16(0); // indexToLocFormat: short
        w.i16(0); // glyphDataFormat
        return w.toByteArray();
    }

    private static byte[] buildHhea(int numGlyphs, int ascent, int descent, int advanceMax) {
        DataWriter w = new DataWriter();
        w.u32(0x00010000); // version
        w.i16(ascent);
        w.i16(descent);
        w.i16(0); // lineGap
        w.u16(advanceMax);
        w.i16(0); // minLeftSideBearing
        w.i16(0); // minRightSideBearing
        w.i16(advanceMax); // xMaxExtent
        w.i16(1); // caretSlopeRise
        w.i16(0); // caretSlopeRun
        w.i16(0); // caretOffset
        w.i16(0);
        w.i16(0);
        w.i16(0);
        w.i16(0);
        w.i16(0); // metricDataFormat
        w.u16(numGlyphs); // numberOfHMetrics (one metric per glyph)
        return w.toByteArray();
    }

    private static byte[] buildMaxp(int numGlyphs) {
        DataWriter w = new DataWriter();
        w.u32(0x00010000); // version 1.0
        w.u16(numGlyphs);
        w.u16(4); // maxPoints
        w.u16(1); // maxContours
        w.u16(0); // maxCompositePoints
        w.u16(0); // maxCompositeContours
        w.u16(2); // maxZones
        w.u16(0); // maxTwilightPoints
        w.u16(0); // maxStorage
        w.u16(0); // maxFunctionDefs
        w.u16(0); // maxInstructionDefs
        w.u16(0); // maxStackElements
        w.u16(0); // maxSizeOfInstructions
        w.u16(0); // maxComponentElements
        w.u16(0); // maxComponentDepth
        return w.toByteArray();
    }

    private static byte[] buildName() {
        String[][] records = {
            {"1", "TestPixel"},   // family
            {"2", "Regular"},     // subfamily
            {"4", "TestPixel"},   // full name
            {"6", "TestPixel"},   // postscript name
        };
        DataWriter storage = new DataWriter();
        int[] offsets = new int[records.length];
        int[] lengths = new int[records.length];
        for (int i = 0; i < records.length; i++) {
            byte[] utf16 = records[i][1].getBytes(StandardCharsets.UTF_16BE);
            offsets[i] = storage.size();
            lengths[i] = utf16.length;
            storage.write(utf16);
        }
        DataWriter w = new DataWriter();
        w.u16(0); // format
        w.u16(records.length);
        int stringOffset = 6 + records.length * 12;
        w.u16(stringOffset);
        for (int i = 0; i < records.length; i++) {
            w.u16(3); // platformID Windows
            w.u16(1); // encodingID Unicode BMP
            w.u16(0x0409); // languageID en-US
            w.u16(Integer.parseInt(records[i][0])); // nameID
            w.u16(lengths[i]);
            w.u16(offsets[i]);
        }
        w.write(storage.toByteArray());
        return w.toByteArray();
    }

    private static byte[] buildPost() {
        DataWriter w = new DataWriter();
        w.u32(0x00030000); // version 3.0 (no glyph names)
        w.u32(0); // italicAngle
        w.i16(0); // underlinePosition
        w.i16(0); // underlineThickness
        w.u32(0); // isFixedPitch
        w.u32(0);
        w.u32(0);
        w.u32(0);
        w.u32(0);
        return w.toByteArray();
    }

    private static byte[] buildOs2(int ascent, int descent) {
        DataWriter w = new DataWriter();
        w.u16(4); // version
        w.i16(0); // xAvgCharWidth
        w.u16(400); // usWeightClass
        w.u16(5); // usWidthClass
        w.u16(0); // fsType
        for (int i = 0; i < 13; i++) {
            w.i16(0); // subscript/superscript/strikeout fields
        }
        w.i16(0); // sFamilyClass
        for (int i = 0; i < 10; i++) {
            w.u8(0); // panose
        }
        w.u32(0);
        w.u32(0);
        w.u32(0);
        w.u32(0); // ulUnicodeRange 1-4
        w.write(new byte[]{'T', 'E', 'S', 'T'}); // achVendID
        w.u16(0x0040); // fsSelection (REGULAR)
        w.u16(0x20); // usFirstCharIndex
        w.u16(0xFFFF); // usLastCharIndex
        w.i16(ascent); // sTypoAscender
        w.i16(descent); // sTypoDescender
        w.i16(0); // sTypoLineGap
        w.u16(Math.max(0, ascent)); // usWinAscent
        w.u16(Math.max(0, -descent)); // usWinDescent
        w.u32(0); // ulCodePageRange1
        w.u32(0); // ulCodePageRange2
        w.i16(0); // sxHeight
        w.i16(ascent); // sCapHeight
        w.u16(0x20); // usDefaultChar
        w.u16(0x20); // usBreakChar
        w.u16(0); // usMaxContext
        return w.toByteArray();
    }

    private static byte[] assemble(LinkedHashMap<String, byte[]> tables) {
        int numTables = tables.size();
        int searchRange = 16 * Integer.highestOneBit(numTables);
        int entrySelector = Integer.numberOfTrailingZeros(searchRange / 16);
        int rangeShift = numTables * 16 - searchRange;

        int directorySize = 12 + numTables * 16;
        int offset = directorySize;
        LinkedHashMap<String, Integer> tableOffsets = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : tables.entrySet()) {
            tableOffsets.put(entry.getKey(), offset);
            offset += align4(entry.getValue().length);
        }
        int fileLength = offset;

        byte[] file = new byte[fileLength];
        DataWriter dir = new DataWriter();
        dir.u32(0x00010000); // sfnt version
        dir.u16(numTables);
        dir.u16(searchRange);
        dir.u16(entrySelector);
        dir.u16(rangeShift);
        for (Map.Entry<String, byte[]> entry : tables.entrySet()) {
            byte[] tag = entry.getKey().getBytes(StandardCharsets.US_ASCII);
            byte[] padded = new byte[]{' ', ' ', ' ', ' '};
            System.arraycopy(tag, 0, padded, 0, Math.min(4, tag.length));
            dir.write(padded);
            dir.u32(checksum(entry.getValue()));
            dir.u32(tableOffsets.get(entry.getKey()));
            dir.u32(entry.getValue().length);
        }
        byte[] directory = dir.toByteArray();
        System.arraycopy(directory, 0, file, 0, directory.length);
        for (Map.Entry<String, byte[]> entry : tables.entrySet()) {
            byte[] data = entry.getValue();
            System.arraycopy(data, 0, file, tableOffsets.get(entry.getKey()), data.length);
        }

        long fileChecksum = checksum(file);
        long adjustment = (0xB1B0AFBAL - fileChecksum) & 0xFFFFFFFFL;
        int headOffset = tableOffsets.get("head");
        writeU32(file, headOffset + 8, adjustment);
        return file;
    }

    private static long checksum(byte[] data) {
        long sum = 0;
        for (int i = 0; i < data.length; i += 4) {
            long word = 0;
            for (int b = 0; b < 4; b++) {
                word <<= 8;
                if (i + b < data.length) {
                    word |= data[i + b] & 0xFF;
                }
            }
            sum += word;
        }
        return sum & 0xFFFFFFFFL;
    }

    private static void writeU32(byte[] data, int offset, long value) {
        data[offset] = (byte) ((value >>> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    private static int align4(int value) {
        return (value + 3) & ~3;
    }

    private static int minXMin(List<Glyph> glyphs) {
        return glyphs.stream().filter(g -> !g.blank()).mapToInt(Glyph::xMin).min().orElse(0);
    }

    private static int minYMin(List<Glyph> glyphs) {
        return glyphs.stream().filter(g -> !g.blank()).mapToInt(Glyph::yMin).min().orElse(0);
    }

    private static int maxXMax(List<Glyph> glyphs) {
        return glyphs.stream().filter(g -> !g.blank()).mapToInt(Glyph::xMax).max().orElse(0);
    }

    private static int maxYMax(List<Glyph> glyphs) {
        return glyphs.stream().filter(g -> !g.blank()).mapToInt(Glyph::yMax).max().orElse(0);
    }

    private static int maxAdvance(List<Glyph> glyphs) {
        return glyphs.stream().mapToInt(Glyph::advance).max().orElse(0);
    }

    /** Big-endian byte writer for the sfnt encoding. */
    private static final class DataWriter {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final DataOutputStream data = new DataOutputStream(out);

        void u8(int value) {
            write(value);
        }

        void u16(int value) {
            write(value >>> 8);
            write(value);
        }

        void i16(int value) {
            u16(value & 0xFFFF);
        }

        void u32(long value) {
            write((int) (value >>> 24));
            write((int) (value >>> 16));
            write((int) (value >>> 8));
            write((int) value);
        }

        void write(byte[] bytes) {
            out.writeBytes(bytes);
        }

        private void write(int b) {
            out.write(b & 0xFF);
        }

        int size() {
            return out.size();
        }

        byte[] toByteArray() {
            try {
                data.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return out.toByteArray();
        }
    }
}

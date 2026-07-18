package net.aerh.imagegenerator.testsupport;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal GIF byte-stream reader for tests pinning encoded frame timing: walks the block
 * structure and collects every Graphic Control Extension's delay (in centiseconds, the GIF
 * native unit; 1 tick = 5 cs).
 */
public final class GifBytes {

    private GifBytes() {
    }

    /** The per-frame delays of an encoded GIF, in centiseconds, in frame order. */
    public static List<Integer> frameDelaysCentiseconds(byte[] gif) {
        if (gif.length < 13 || gif[0] != 'G' || gif[1] != 'I' || gif[2] != 'F') {
            throw new IllegalArgumentException("Not a GIF stream");
        }
        int position = 13;
        int packed = gif[10] & 0xFF;
        if ((packed & 0x80) != 0) {
            position += 3 * (1 << ((packed & 0x07) + 1));
        }
        List<Integer> delays = new ArrayList<>();
        while (position < gif.length) {
            int block = gif[position++] & 0xFF;
            if (block == 0x3B) {
                break;
            }
            if (block == 0x21) {
                int label = gif[position++] & 0xFF;
                if (label == 0xF9) {
                    // Sub-block: size (4), packed flags, delay lo, delay hi, transparent index.
                    delays.add((gif[position + 2] & 0xFF) | ((gif[position + 3] & 0xFF) << 8));
                }
                position = skipSubBlocks(gif, position);
            } else if (block == 0x2C) {
                position += 8;
                int localPacked = gif[position++] & 0xFF;
                if ((localPacked & 0x80) != 0) {
                    position += 3 * (1 << ((localPacked & 0x07) + 1));
                }
                position++; // LZW minimum code size
                position = skipSubBlocks(gif, position);
            } else {
                throw new IllegalArgumentException("Unexpected GIF block 0x" + Integer.toHexString(block));
            }
        }
        return delays;
    }

    /**
     * The number of Application Extension blocks (label {@code 0xFF}) in the stream - the
     * NETSCAPE looping block among them. A well-formed looping GIF carries exactly one, before
     * the first frame; more than one is redundant.
     */
    public static int applicationExtensionCount(byte[] gif) {
        if (gif.length < 13 || gif[0] != 'G' || gif[1] != 'I' || gif[2] != 'F') {
            throw new IllegalArgumentException("Not a GIF stream");
        }
        int position = 13;
        int packed = gif[10] & 0xFF;
        if ((packed & 0x80) != 0) {
            position += 3 * (1 << ((packed & 0x07) + 1));
        }
        int count = 0;
        while (position < gif.length) {
            int block = gif[position++] & 0xFF;
            if (block == 0x3B) {
                break;
            }
            if (block == 0x21) {
                int label = gif[position++] & 0xFF;
                if (label == 0xFF) {
                    count++;
                }
                position = skipSubBlocks(gif, position);
            } else if (block == 0x2C) {
                position += 8;
                int localPacked = gif[position++] & 0xFF;
                if ((localPacked & 0x80) != 0) {
                    position += 3 * (1 << ((localPacked & 0x07) + 1));
                }
                position++; // LZW minimum code size
                position = skipSubBlocks(gif, position);
            } else {
                throw new IllegalArgumentException("Unexpected GIF block 0x" + Integer.toHexString(block));
            }
        }
        return count;
    }

    private static int skipSubBlocks(byte[] gif, int position) {
        while (true) {
            int size = gif[position++] & 0xFF;
            if (size == 0) {
                return position;
            }
            position += size;
        }
    }
}

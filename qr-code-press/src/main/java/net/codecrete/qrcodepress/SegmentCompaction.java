/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits data into the sequence of data segments with the shortest bit stream.
 * <p>
 * Each byte is first assigned the mode that encodes it in the fewest bits, and consecutive bytes
 * with the same mode are collected into blocks. Since every segment costs a mode indicator and a
 * character count indicator, a block is not always worth a segment of its own: a short numeric run
 * between two alphanumeric runs is cheaper as part of the alphanumeric segment around it. A dynamic
 * program therefore assigns each block the segment mode minimizing the length of the entire bit
 * stream, and consecutive blocks sharing a mode become one segment.
 * </p>
 * <p>
 * The result is the shortest bit stream any segmentation of the data achieves, not merely a shorter
 * one. It depends on the QR code version, as the width of the character count indicator does, but
 * only through it: the versions 1&ndash;9, 10&ndash;26 and 27&ndash;40 each share their indicator
 * widths, so all versions of a group yield the same segmentation.
 * </p>
 */
final class SegmentCompaction {

    private SegmentCompaction() {
        // non-instantiable
    }

    /**
     * Builds the segments encoding the specified data with the shortest bit stream.
     * <p>
     * The data is not copied. The resulting segments refer to it, so it must be owned by the
     * library.
     * </p>
     * <p>
     * Kanji mode is used only if {@code considerKanjiMode} is {@code true}. As many scanners
     * assume that data in Kanji mode is Shift-JIS text, it should be enabled only if the data
     * really is Shift-JIS text.
     * </p>
     *
     * @param data              the data to encode
     * @param version           the QR code version (1&ndash;40)
     * @param considerKanjiMode {@code true} if Kanji mode may be used
     * @return the segments
     */
    static List<DataSegment> buildSegments(ByteSlice data, int version, boolean considerKanjiMode) {
        var blocks = buildBlocks(data, considerKanjiMode);
        if (blocks.length == 0)
            return new ArrayList<>();

        assignModes(blocks, version);

        // Consecutive blocks assigned the same mode form a single segment, so the assigned modes
        // are counted first and the list never has to grow. One element of spare capacity for the ECI segment.
        var segments = new ArrayList<DataSegment>(countModeChanges(blocks) + 1);
        var offset = 0;
        var length = 0;
        for (var i = 0; i < blocks.length; i += 1) {
            length += blocks[i].length();
            if (i + 1 == blocks.length || blocks[i + 1].mode() != blocks[i].mode()) {
                segments.add(blocks[i].mode().newSegment(data.slice(offset, length)));
                offset += length;
                length = 0;
            }
        }

        return segments;
    }

    // region Blocks

    /**
     * Builds the blocks of consecutive bytes sharing the mode that encodes them most compactly.
     *
     * @param data              the data to encode
     * @param considerKanjiMode {@code true} if Kanji mode may be used
     * @return the blocks, in the order of the data
     */
    private static Block[] buildBlocks(ByteSlice data, boolean considerKanjiMode) {
        if (data.length() == 0)
            return new Block[0];

        var modes = bestModes(data, considerKanjiMode);

        // create blocks
        var modeChanges = countModeChanges(modes);
        var blocks = new Block[modeChanges];
        var blockCount = 0;
        var blockStart = 0;
        var previousMode = modes[0];
        for (var i = 1; i < modes.length; i += 1) {
            var currentMode = modes[i];
            if (currentMode == previousMode)
                continue;

            blocks[blockCount] = new Block(previousMode, i - blockStart);
            blockCount += 1;
            previousMode = currentMode;
            blockStart = i;
        }
        blocks[blockCount] = new Block(previousMode, modes.length - blockStart);

        return blocks;
    }

    private static int countModeChanges(DataSegmentMode[] modes)
    {
        var count = 1;
        var previousMode = modes[0];
        for (DataSegmentMode currentMode : modes) {
            if (currentMode != previousMode) {
                count += 1;
                previousMode = currentMode;
            }
        }
        return count;
    }

    private static int countModeChanges(Block[] blocks)
    {
        var count = 1;
        var previousMode = blocks[0].mode();
        for (var block : blocks) {
            if (block.mode() != previousMode) {
                count += 1;
                previousMode = block.mode();
            }
        }
        return count;
    }

    /**
     * Determines the mode encoding each byte in the fewest bits.
     * <p>
     * Numeric mode takes 3&frac13; bits per byte, alphanumeric mode 5&frac12;, Kanji mode 6&frac12;
     * and binary mode 8, which is the order the modes are tried in. Kanji mode is the odd one out:
     * it encodes a pair of bytes, so it is only considered where a whole pair is encodable, and it
     * then claims both bytes.
     * </p>
     *
     * @param data              the data to encode
     * @param considerKanjiMode {@code true} if Kanji mode may be used
     * @return the mode of each byte
     */
    private static DataSegmentMode[] bestModes(ByteSlice data, boolean considerKanjiMode) {
        var length = data.length();
        var modes = new DataSegmentMode[length];

        var index = 0;
        while (index < length) {
            var b = data.at(index);
            if (DataSegmentNumeric.isNumeric(b)) {
                modes[index] = DataSegmentMode.NUMERIC;
            } else if (DataSegmentAlphanumeric.isAlphanumeric(b)) {
                modes[index] = DataSegmentMode.ALPHANUMERIC;
            } else if (considerKanjiMode && index + 1 < length
                    && DataSegmentKanji.isShiftJisDoubleByte(b, data.at(index + 1))) {
                modes[index] = DataSegmentMode.KANJI;
                index += 1;
                modes[index] = DataSegmentMode.KANJI;
            } else {
                modes[index] = DataSegmentMode.BINARY;
            }

            index += 1;
        }

        return modes;
    }

    /**
     * A run of consecutive bytes to be encoded in a single mode.
     */
    private static class Block {
        private DataSegmentMode mode;
        private final int length;

        Block(DataSegmentMode mode, int length) {
            this.mode = mode;
            this.length = length;
        }

        /**
         * The encoding mode
         * @return the mode
         */
        DataSegmentMode mode() {
            return mode;
        }

        /**
         * Sets the encoding mode.
         * @param mode the mode
         */
        void setMode(DataSegmentMode mode) {
            this.mode = mode;
        }

        /**
         * The payload length.
         * @return the number of bytes
         */
        int length() {
            return length;
        }
    }

    // endregion

    // region Mode assignment

    /** The modes a block can be encoded in, in the order the costs below are indexed in. */
    private static final DataSegmentMode[] MODES = {
            DataSegmentMode.NUMERIC, DataSegmentMode.ALPHANUMERIC, DataSegmentMode.KANJI, DataSegmentMode.BINARY
    };

    /** The number of modes a block can be encoded in. */
    private static final int MODE_COUNT = MODES.length;

    /**
     * The bits per byte of each mode, in sixths of a bit so that every value is a whole number:
     * numeric 3&frac13;, alphanumeric 5&frac12;, Kanji 6&frac12; and binary 8.
     */
    private static final int[] BYTE_COSTS = { 20, 33, 39, 48 };

    /** A cost larger than any real one, standing for a mode that cannot encode a block. */
    private static final int INFINITY = Integer.MAX_VALUE / 2;

    /**
     * Assigns each block the mode of the segment encoding it, so that the bit stream is shortest.
     * <p>
     * For each block and each mode able to encode it, the shortest bit stream up to and including
     * the block is computed, either by continuing the segment of the previous block or by starting
     * a new segment, which adds a header. That is a shortest-path problem with one node per (block,
     * mode) pair, solved block by block. Which mode a step came from is recorded, so the assignment
     * is read back by walking the winning path backwards from the last block.
     * </p>
     * <p>
     * Costs are counted in sixths of a bit, since three of the four modes encode a byte in a
     * fractional number of bits. A segment's real length is the sum of its byte costs rounded up to
     * whole bits, so rounding up wherever a segment ends makes the cost exact.
     * </p>
     *
     * @param blocks  the blocks, each with the mode encoding it most compactly
     * @param version the QR code version (1&ndash;40)
     */
    private static void assignModes(Block[] blocks, int version) {
        // A block of length 0 costs exactly the segment header (mode and character count indicator).
        var headerCosts = new int[MODE_COUNT];
        for (var m = 0; m < MODE_COUNT; m += 1)
            headerCosts[m] = 6 * segmentHeaderLength(MODES[m], version);

        var blockCount = blocks.length;
        var previousCosts = new int[MODE_COUNT]; // the shortest bit stream up to the previous block, per mode
        var costs = new int[MODE_COUNT]; // the shortest bit stream up to the current block, per mode
        var previousModes = new byte[blockCount * MODE_COUNT]; // the mode of the previous block on that path

        for (var i = 0; i < blockCount; i += 1) {
            var swap = previousCosts;
            previousCosts = costs;
            costs = swap;

            var block = blocks[i];
            for (var m = 0; m < MODE_COUNT; m += 1) {
                if (!canEncode(MODES[m], block.mode())) {
                    costs[m] = INFINITY;
                    continue;
                }

                var dataCost = block.length() * BYTE_COSTS[m];
                if (i == 0) {
                    costs[m] = headerCosts[m] + dataCost;
                    continue;
                }

                // Continue the segment of the previous block, which costs no header. It is tried
                // first and only beaten strictly, so it also wins a tie.
                var best = previousCosts[m] + dataCost;
                var bestPrevious = m;

                // Or end a segment of another mode and start a new one.
                for (var p = 0; p < MODE_COUNT; p += 1) {
                    var previousCost = previousCosts[p];
                    if (p == m || previousCost >= INFINITY)
                        continue;

                    var cost = roundUpToBits(previousCost) + headerCosts[m] + dataCost;
                    if (cost < best) {
                        best = cost;
                        bestPrevious = p;
                    }
                }

                costs[m] = best;
                previousModes[i * MODE_COUNT + m] = (byte) bestPrevious;
            }
        }

        // The cheapest mode for the last block ends the winning path; walk it back to the first block.
        var mode = 0;
        for (var m = 1; m < MODE_COUNT; m += 1) {
            if (roundUpToBits(costs[m]) < roundUpToBits(costs[mode]))
                mode = m;
        }

        for (var i = blockCount - 1; i >= 0; i -= 1) {
            blocks[i].setMode(MODES[mode]);
            mode = previousModes[i * MODE_COUNT + mode];
        }
    }

    /**
     * Rounds a cost in sixths of a bit up to whole bits, still counted in sixths.
     *
     * @param cost the cost, in sixths of a bit
     * @return the rounded cost, in sixths of a bit
     */
    private static int roundUpToBits(int cost) {
        return (cost + 5) / 6 * 6;
    }

    /**
     * Indicates whether a segment of the specified mode can encode a block of the specified mode.
     * <p>
     * Binary mode encodes every block. Alphanumeric mode additionally encodes a numeric block, as
     * the digits are part of its character set. Kanji mode, whose unit is a pair of bytes, encodes
     * nothing but a Kanji block, which keeps every Kanji segment an even number of bytes long.
     * </p>
     *
     * @param segmentMode the mode of the segment
     * @param blockMode   the mode encoding the block most compactly
     * @return {@code true} if the segment can encode the block
     */
    private static boolean canEncode(DataSegmentMode segmentMode, DataSegmentMode blockMode) {
        return segmentMode == DataSegmentMode.BINARY
                || segmentMode == blockMode
                || (segmentMode == DataSegmentMode.ALPHANUMERIC && blockMode == DataSegmentMode.NUMERIC);
    }

    /**
     * Returns the length of a segment header with the specified parameters.
     *
     * @param mode   the encoding mode
     * @param version the QR code version (1&ndash;40)
     * @return the length, including the header, in bits
     */
    private static int segmentHeaderLength(DataSegmentMode mode, int version) {
        // Duplicated code for performance
        return switch (mode) {
            case BINARY -> 12 + (version <= 9 ? 0 : 8);
            case NUMERIC -> 14 + (version + 7) / 17 * 2;
            case ALPHANUMERIC -> 13 + (version + 7) / 17 * 2;
            case KANJI -> 12 + (version + 7) / 17 * 2;
            default -> {
                assert false;
                yield 0;
            }
        };
    }

    // endregion
}

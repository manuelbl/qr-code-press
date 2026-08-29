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
 * with the same mode are collected into blocks. Since every block costs a mode indicator and a
 * character count indicator, a block is not always worth having: a short numeric run between two
 * alphanumeric runs is cheaper as part of the alphanumeric segment than as a segment of its own.
 * The blocks are therefore merged as long as merging shortens the bit stream.
 * </p>
 * <p>
 * The result depends on the QR code version, as the width of the character count indicator does.
 * In edge cases, a different version yields a slightly different &mdash; and slightly longer
 * &mdash; segmentation. The difference is a few bits at most.
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

        // The passes run in this order: numeric runs are first absorbed into the alphanumeric runs
        // around them, and whatever remains is then absorbed into binary runs.
        merge(blocks, version, MergeRule.NUMERIC_INTO_ALPHANUMERIC);
        merge(blocks, version, MergeRule.ANY_INTO_BINARY);

        var segments = new ArrayList<DataSegment>(blocks.size());
        var offset = 0;
        for (var block : blocks) {
            segments.add(block.mode().newSegment(data.slice(offset, block.length())));
            offset += block.length();
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
    private static List<Block> buildBlocks(ByteSlice data, boolean considerKanjiMode) {
        var blocks = new ArrayList<Block>();
        if (data.length() == 0)
            return blocks;

        var modes = bestModes(data, considerKanjiMode);
        var blockStart = 0;
        for (var i = 1; i < modes.length; i += 1) {
            if (modes[i] != modes[blockStart]) {
                blocks.add(new Block(modes[blockStart], i - blockStart));
                blockStart = i;
            }
        }
        blocks.add(new Block(modes[blockStart], modes.length - blockStart));

        return blocks;
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
     *
     * @param mode   the encoding mode
     * @param length the number of bytes
     */
    private record Block(DataSegmentMode mode, int length) {

        /**
         * Returns the length of the segment this block would become.
         *
         * @param version the QR code version (1&ndash;40)
         * @return the length, including the header, in bits
         */
        int segmentLength(int version) {
            return mode.segmentLength(length, version);
        }
    }

    // endregion

    // region Merging

    /**
     * Merges neighboring blocks as long as the specified rule applies and merging shortens the
     * bit stream.
     * <p>
     * A merge changes the modes next to it, which can enable a further merge, so the blocks are
     * swept repeatedly until a sweep leaves them unchanged. Each sweep runs from the back so that
     * removing a block shifts as few of the remaining ones as possible.
     * </p>
     *
     * @param blocks  the blocks, modified in place
     * @param version the QR code version (1&ndash;40)
     * @param rule    the rule saying which blocks may be merged, and into which mode
     */
    private static void merge(List<Block> blocks, int version, MergeRule rule) {
        var previousCount = -1;
        while (blocks.size() > 1 && previousCount != blocks.size()) {
            previousCount = blocks.size();

            var index = blocks.size() - 1;
            while (index > 0) {
                var last = blocks.get(index).mode();
                var middle = blocks.get(index - 1).mode();
                var first = index >= 2 ? blocks.get(index - 2).mode() : null;

                // A block enclosed by two blocks of the same mode is a candidate even if merging
                // it with either neighbor alone would not pay off, as the merged block needs but
                // one header instead of three.
                if (first != null && rule.mergesThree(first, middle, last)) {
                    if (tryMerge(blocks, version, rule.mergedMode(), index - 2, 3))
                        index -= 1;
                } else if (rule.mergesTwo(middle, last)) {
                    tryMerge(blocks, version, rule.mergedMode(), index - 1, 2);
                }

                index -= 1;
            }
        }
    }

    /**
     * Merges the specified blocks if the result is no longer than the blocks separately.
     *
     * @param blocks     the blocks, modified in place
     * @param version    the QR code version (1&ndash;40)
     * @param mergedMode the mode of the merged block
     * @param start      the index of the first block to merge
     * @param count      the number of blocks to merge
     * @return {@code true} if the blocks were merged
     */
    private static boolean tryMerge(List<Block> blocks, int version, DataSegmentMode mergedMode,
            int start, int count) {
        var payloadLength = 0;
        var separateLength = 0;
        for (var i = start; i < start + count; i += 1) {
            payloadLength += blocks.get(i).length();
            separateLength += blocks.get(i).segmentLength(version);
        }

        var mergedBlock = new Block(mergedMode, payloadLength);
        if (mergedBlock.segmentLength(version) > separateLength)
            return false;

        blocks.set(start, mergedBlock);
        blocks.subList(start + 1, start + count).clear();
        return true;
    }

    /**
     * A rule saying which neighboring blocks are worth testing for a merge.
     * <p>
     * The rules only decide which combinations of modes are plausible; whether a plausible merge
     * actually happens is decided by comparing the bit lengths.
     * </p>
     */
    private enum MergeRule {

        /**
         * Absorbs a numeric run into the alphanumeric runs around it.
         * <p>
         * Numeric mode is the more compact of the two, so this pays off only for short numeric
         * runs. It runs first because it can leave a single alphanumeric block where there were
         * three blocks, which the second pass then judges as a whole.
         * </p>
         */
        NUMERIC_INTO_ALPHANUMERIC(DataSegmentMode.ALPHANUMERIC) {
            @Override
            boolean mergesThree(DataSegmentMode first, DataSegmentMode middle, DataSegmentMode last) {
                return first == DataSegmentMode.ALPHANUMERIC && middle == DataSegmentMode.NUMERIC
                        && last == DataSegmentMode.ALPHANUMERIC;
            }

            @Override
            boolean mergesTwo(DataSegmentMode first, DataSegmentMode second) {
                return (first == DataSegmentMode.ALPHANUMERIC && second == DataSegmentMode.NUMERIC)
                        || (first == DataSegmentMode.NUMERIC && second == DataSegmentMode.ALPHANUMERIC);
            }
        },

        /**
         * Absorbs a run of any mode into the binary runs around it.
         * <p>
         * Binary mode is the least compact one, so it never pays off to move data into it for its
         * own sake &mdash; only to save the headers of the blocks that disappear.
         * </p>
         */
        ANY_INTO_BINARY(DataSegmentMode.BINARY) {
            @Override
            boolean mergesThree(DataSegmentMode first, DataSegmentMode middle, DataSegmentMode last) {
                return middle != DataSegmentMode.BINARY && first == last;
            }

            @Override
            boolean mergesTwo(DataSegmentMode first, DataSegmentMode second) {
                return (first == DataSegmentMode.BINARY) != (second == DataSegmentMode.BINARY);
            }
        };

        private final DataSegmentMode mergedMode;

        MergeRule(DataSegmentMode mergedMode) {
            this.mergedMode = mergedMode;
        }

        /**
         * Returns the mode a merged block gets under this rule.
         *
         * @return the mode
         */
        DataSegmentMode mergedMode() {
            return mergedMode;
        }

        /**
         * Indicates whether three consecutive blocks with the specified modes may be merged.
         *
         * @param first  the mode of the first block
         * @param middle the mode of the block in between
         * @param last   the mode of the last block
         * @return {@code true} if they may be merged
         */
        abstract boolean mergesThree(DataSegmentMode first, DataSegmentMode middle, DataSegmentMode last);

        /**
         * Indicates whether two consecutive blocks with the specified modes may be merged.
         *
         * @param first  the mode of the first block
         * @param second the mode of the second block
         * @return {@code true} if they may be merged
         */
        abstract boolean mergesTwo(DataSegmentMode first, DataSegmentMode second);
    }

    // endregion
}

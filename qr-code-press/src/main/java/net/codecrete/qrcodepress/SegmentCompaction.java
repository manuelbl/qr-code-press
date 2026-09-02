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
        var blockCount = blocks.length;

        // The passes run in this order: numeric runs are first absorbed into the alphanumeric runs
        // around them, and whatever remains is then absorbed into binary runs.
        blockCount = mergeBlocks(blocks, blockCount, version, MergePolicy.NUMERIC_INTO_ALPHANUMERIC);
        blockCount = mergeBlocks(blocks, blockCount, version, MergePolicy.ANY_INTO_BINARY);

        var segments = new ArrayList<DataSegment>(blockCount);
        var offset = 0;
        for (var i = 0; i < blockCount; i++) {
            var block = blocks[i];
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
            // Duplicated code for performance
            return switch (mode) {
                case BINARY -> 12 + (version <= 9 ? 0 : 8) + length * 8;
                case NUMERIC -> 14 + (version + 7) / 17 * 2 + (length * 10 + 2) / 3;
                case ALPHANUMERIC -> 13 + (version + 7) / 17 * 2 + (length * 11 + 1) / 2;
                case KANJI -> 12 + (version + 7) / 17 * 2 + length * 13 / 2;
                default -> {
                    assert false;
                    yield 0;
                }
            };
        }
    }

    // endregion

    // region Merging

    /**
     * Merges adjacent blocks according to the specified policy, as long as merging shortens the bit stream.
     * <p>
     * The blocks are merged in place. The first {@code blockCount} entries of the array are the
     * blocks, and the returned count replaces it.
     * </p>
     *
     * @param blocks     the blocks
     * @param blockCount the number of blocks
     * @param version    the QR code version (1&ndash;40)
     * @param policy     the blocks to absorb, and the mode to merge them into
     * @return the number of blocks after merging
     */
    private static int mergeBlocks(Block[] blocks, int blockCount, int version, MergePolicy policy) {
        // A merge can bring two blocks next to each other that were not before, so the passes are
        // repeated until one of them merges nothing.
        var previousCount = -1;
        while (blockCount > 1 && blockCount != previousCount) {
            previousCount = blockCount;
            blockCount = mergePass(blocks, blockCount, version, policy);
        }

        return blockCount;
    }

    /**
     * Runs a single merging pass over the blocks, from left to right.
     * <p>
     * The surviving blocks are compacted to the front of the array. Merging never creates blocks,
     * so the target index trails the source index and the array can be its own target.
     * </p>
     *
     * @param blocks     the blocks
     * @param blockCount the number of blocks
     * @param version    the QR code version (1&ndash;40)
     * @param policy     the blocks to absorb, and the mode to merge them into
     * @return the number of blocks after the pass
     */
    private static int mergePass(Block[] blocks, int blockCount, int version, MergePolicy policy) {
        var processedBlocks = 1;
        var sourceIndex = 1;
        while (sourceIndex < blockCount) {
            // blocks[processedBlocks - 1] is the last block of the pass so far, and the one absorbing further blocks
            var absorbed = tryMerge(blocks, processedBlocks - 1, sourceIndex, blockCount, version, policy);
            if (absorbed > 0) {
                sourceIndex += absorbed;
            } else {
                blocks[processedBlocks] = blocks[sourceIndex];
                processedBlocks += 1;
                sourceIndex += 1;
            }
        }

        return processedBlocks;
    }

    /**
     * Tries to merge the block at {@code targetIndex} with the blocks starting at {@code sourceIndex}.
     * <p>
     * Three blocks are tried first, as a block the policy absorbs is often surrounded by two blocks
     * merging with neither of them alone pays off. If three blocks may be merged but merging them is
     * not shorter, two are not tried: the middle block would remain either way.
     * </p>
     *
     * @param blocks      the blocks
     * @param targetIndex the index of the absorbing block
     * @param sourceIndex the index of the first block to absorb
     * @param blockCount  the number of blocks
     * @param version     the QR code version (1&ndash;40)
     * @param policy      the blocks to absorb, and the mode to merge them into
     * @return the number of absorbed blocks (0, 1 or 2)
     */
    private static int tryMerge(Block[] blocks, int targetIndex, int sourceIndex, int blockCount, int version,
                                MergePolicy policy) {
        var mode0 = blocks[targetIndex].mode();
        var mode1 = blocks[sourceIndex].mode();

        if (sourceIndex + 1 < blockCount && policy.canMerge3(mode0, mode1, blocks[sourceIndex + 1].mode()))
            return mergeIfShorter(blocks, targetIndex, sourceIndex, 2, version, policy.mergedMode) ? 2 : 0;

        if (policy.canMerge2(mode0, mode1))
            return mergeIfShorter(blocks, targetIndex, sourceIndex, 1, version, policy.mergedMode) ? 1 : 0;

        return 0;
    }

    /**
     * Replaces the block at {@code targetIndex} with the merge of it and the {@code count} blocks
     * starting at {@code sourceIndex}, unless the merged segment is longer than the separate ones.
     *
     * @param blocks      the blocks
     * @param targetIndex the index of the absorbing block
     * @param sourceIndex the index of the first block to absorb
     * @param count       the number of blocks to absorb
     * @param version     the QR code version (1&ndash;40)
     * @param mergedMode  the mode of the merged block
     * @return {@code true} if the blocks have been merged
     */
    private static boolean mergeIfShorter(Block[] blocks, int targetIndex, int sourceIndex, int count, int version,
                                          DataSegmentMode mergedMode) {
        var target = blocks[targetIndex];
        var payloadLength = target.length();
        var separateLength = target.segmentLength(version);
        for (var i = 0; i < count; i += 1) {
            var block = blocks[sourceIndex + i];
            payloadLength += block.length();
            separateLength += block.segmentLength(version);
        }

        var mergedBlock = new Block(mergedMode, payloadLength);
        if (mergedBlock.segmentLength(version) > separateLength)
            return false;

        blocks[targetIndex] = mergedBlock;
        return true;
    }

    /**
     * The blocks a merging pass absorbs, and the mode it merges them into.
     * <p>
     * A merge is only ever considered where the merged mode can encode all of the blocks involved.
     * Whether it is shorter is decided separately, for the blocks at hand.
     * </p>
     */
    private enum MergePolicy {

        /** Absorbs numeric blocks into the alphanumeric blocks next to them. */
        NUMERIC_INTO_ALPHANUMERIC(DataSegmentMode.ALPHANUMERIC) {
            @Override
            boolean canMerge2(DataSegmentMode mode0, DataSegmentMode mode1) {
                return (mode0 == DataSegmentMode.ALPHANUMERIC && mode1 == DataSegmentMode.NUMERIC)
                        || (mode0 == DataSegmentMode.NUMERIC && mode1 == DataSegmentMode.ALPHANUMERIC);
            }

            @Override
            boolean canMerge3(DataSegmentMode mode0, DataSegmentMode mode1, DataSegmentMode mode2) {
                return mode0 == DataSegmentMode.ALPHANUMERIC && mode1 == DataSegmentMode.NUMERIC && mode2 == mode0;
            }
        },

        /**
         * Absorbs blocks into the binary blocks next to them, and a non-binary block between two
         * blocks of equal mode into those two. Binary mode encodes every block, so the second case
         * needs no restriction beyond the blocks around the absorbed one being alike.
         */
        ANY_INTO_BINARY(DataSegmentMode.BINARY) {
            @Override
            boolean canMerge2(DataSegmentMode mode0, DataSegmentMode mode1) {
                return (mode0 == DataSegmentMode.BINARY) != (mode1 == DataSegmentMode.BINARY);
            }

            @Override
            boolean canMerge3(DataSegmentMode mode0, DataSegmentMode mode1, DataSegmentMode mode2) {
                return mode1 != DataSegmentMode.BINARY && mode2 == mode0;
            }
        };

        /** The mode of a block merged by this policy. */
        final DataSegmentMode mergedMode;

        /**
         * Creates a new instance.
         *
         * @param mergedMode the mode of a merged block
         */
        MergePolicy(DataSegmentMode mergedMode) {
            this.mergedMode = mergedMode;
        }

        /**
         * Indicates if two consecutive blocks with the specified modes may be merged.
         *
         * @param mode0 the mode of the first block
         * @param mode1 the mode of the second block
         * @return {@code true} if they may be merged
         */
        abstract boolean canMerge2(DataSegmentMode mode0, DataSegmentMode mode1);

        /**
         * Indicates if three consecutive blocks with the specified modes may be merged.
         *
         * @param mode0 the mode of the first block
         * @param mode1 the mode of the second block
         * @param mode2 the mode of the third block
         * @return {@code true} if they may be merged
         */
        abstract boolean canMerge3(DataSegmentMode mode0, DataSegmentMode mode1, DataSegmentMode mode2);
    }

    // endregion
}

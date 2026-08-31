/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * Builds the finished module matrix from the interleaved codewords.
 * <p>
 * Draws the fixed patterns, fills the payload into the free modules,
 * then selects and applies the mask pattern that scores lowest.
 * </p>
 */
final class MatrixEncoder {

    /** The number of data mask patterns the specification defines. */
    static final int PATTERN_COUNT = 8;

    /** Passed as the forced mask pattern to let the encoder select one itself. */
    static final int NO_FORCED_MASK = -1;

    private MatrixEncoder() {
        // non-instantiable
    }

    /**
     * A finished QR code symbol: its modules and the mask pattern applied to them.
     *
     * @param modules the module matrix, with the fixed patterns, the payload, the format
     *                information and the mask applied
     * @param mask    the applied mask pattern (0&ndash;7)
     */
    record Encoded(BitMatrix modules, int mask) {
    }

    // region Encode

    /**
     * Encodes the specified codewords into a QR code symbol.
     *
     * @param codewords  the interleaved data and error correction codewords
     * @param version    the QR code version (1&ndash;40)
     * @param ecc        the error correction level (0&ndash;3)
     * @param forcedMask the mask pattern to apply (0&ndash;7), or {@link #NO_FORCED_MASK} to select
     *                   the one that scores lowest
     * @return the finished symbol
     */
    static Encoded encode(byte[] codewords, int version, int ecc, int forcedMask) {
        return encode(codewords, version, ecc, forcedMask, null);
    }

    /**
     * Encodes the specified codewords into a QR code symbol, reporting the penalty score of every
     * mask pattern.
     * <p>
     * This is slower than {@link #encode(byte[], int, int, int)}: collecting the scores means every
     * pattern has to be scored in full, even once it is clear that it cannot win.
     * </p>
     *
     * @param codewords  the interleaved data and error correction codewords
     * @param version    the QR code version (1&ndash;40)
     * @param ecc        the error correction level (0&ndash;3)
     * @param forcedMask the mask pattern to apply (0&ndash;7), or {@link #NO_FORCED_MASK} to select
     *                   the one that scores lowest
     * @param penalties  the array of {@value #PATTERN_COUNT} scores to fill in, indexed by pattern
     * @return the finished symbol
     */
    static Encoded encodeWithPenalties(byte[] codewords, int version, int ecc, int forcedMask,
            PenaltyScore[] penalties) {
        return encode(codewords, version, ecc, forcedMask, penalties);
    }

    private static Encoded encode(byte[] codewords, int version, int ecc, int forcedMask,
            PenaltyScore[] penalties) {
        var symbol = ScoringMatrix.ofSymbol(codewords, version);
        return applyBestPattern(symbol, version, ecc, forcedMask, penalties);
    }

    // endregion

    // region Payload

    /**
     * Fills the payload bits into the modules the fixed patterns leave free.
     * <p>
     * Which module a codeword bit lands on depends on the version alone, so the walk itself is
     * precomputed by {@link #payloadTargets(int)} and this is a flat pass over that table: one
     * codeword bit and one target per iteration, and no coordinate arithmetic. Nor is there a
     * branch on the bit &mdash; a light module shifts a zero into place and leaves its word
     * unchanged &mdash; because a codeword bit is as good as random and would mispredict half the
     * time.
     * </p>
     * <p>
     * The QR code has room for a few bits more than the codewords occupy; those remainder bits are
     * left light, which is why the pass stops at the codewords rather than at the end of the table.
     * </p>
     *
     * @param modules   the module matrix with the fixed patterns already drawn; it must have the
     *                  version's size, since the targets address its words directly
     * @param codewords the interleaved data and error correction codewords
     * @param version   the QR code version (1&ndash;40)
     */
    static void fillPayload(BitMatrix modules, byte[] codewords, int version) {
        var targets = payloadTargets(version);
        var bitCount = Math.min(codewords.length * 8, targets.length);

        for (var i = 0; i < bitCount; i += 1) {
            var bit = (codewords[i >> 3] >> (7 - (i & 0x07))) & 1;
            modules.orBit(targets[i], bit);
        }
    }

    private static final LazyCache<short[]> PAYLOAD_TARGET_CACHE = new LazyCache<>(
            QrCodeParameters.MAX_VERSION + 1, MatrixEncoder::computePayloadTargets);

    /**
     * Returns the modules the payload occupies, in the order the codeword bits fill them.
     * <p>
     * Each entry is a {@link BitMatrix#address(int, int) matrix address}, so a caller writes the
     * bits with {@link BitMatrix#orBit(short, int)}. The table is as long as the payload area,
     * one entry per module: a version whose codewords fill the area exactly but for the remainder
     * bits uses all but the last few.
     * </p>
     * <p>
     * The returned array is shared and cached. Callers must not mutate it.
     * </p>
     *
     * @param version the QR code version (1&ndash;40)
     * @return the shared table of addresses
     */
    static short[] payloadTargets(int version) {
        return PAYLOAD_TARGET_CACHE.get(version);
    }

    /**
     * Walks the payload zigzag of a version and records the module it visits at each step.
     * <p>
     * The codewords are laid out in a zigzag of two-column strides, starting in the bottom right
     * corner and skipping the reserved modules. The walk covers every column but the vertical
     * timing pattern, which is reserved over its full height, so it visits the payload area
     * exactly once and {@link BitMatrix#popCount()} is the table's length.
     * </p>
     *
     * @param version the QR code version (1&ndash;40)
     * @return the table of addresses
     */
    private static short[] computePayloadTargets(int version) {
        var payloadArea = FixedPatterns.payloadAreaMap(version);
        var size = payloadArea.size();
        var targets = new short[payloadArea.popCount()];
        var count = 0;

        // right to left, in strides of two columns
        for (var h = size - 1; h > 0; h -= 2) {
            if (h == 6)
                h -= 1; // skip the vertical timing pattern

            var upward = ((size - h - 1) & 2) == 0;

            for (var v = 0; v < size; v += 1) {
                var y = upward ? size - v - 1 : v;

                // alternate between the two columns of the stride
                for (var x = h; x > h - 2; x -= 1) {
                    if (payloadArea.get(x, y)) {
                        targets[count] = payloadArea.address(x, y);
                        count += 1;
                    }
                }
            }
        }

        return targets;
    }

    // endregion

    // region Mask patterns

    private static final LazyCache<MaskPair> MASK_CACHE = new LazyCache<>(
            PATTERN_COUNT * (QrCodeParameters.MAX_VERSION + 1),
            index -> createMaskPair(index % PATTERN_COUNT, index / PATTERN_COUNT));

    /**
     * Returns the mask pattern for the specified pattern index and version, restricted to the
     * payload area and paired with its transpose.
     * <p>
     * The returned pair is shared and cached. Callers must not mutate it.
     * </p>
     *
     * @param pattern the data mask pattern index (0&ndash;7)
     * @param version the QR code version (1&ndash;40)
     * @return the shared mask pair
     */
    static MaskPair maskPair(int pattern, int version) {
        return MASK_CACHE.get(PATTERN_COUNT * version + pattern);
    }

    private static MaskPair createMaskPair(int pattern, int version) {
        var rows = createPattern(pattern, version);
        rows.and(FixedPatterns.payloadAreaMap(version));
        var columns = rows.copy();
        columns.transpose();
        return new MaskPair(rows, columns);
    }

    /** A data mask pattern, as the specification states it: a predicate over the coordinates. */
    private interface PatternFunction {
        boolean isMasked(int x, int y);
    }

    private static final PatternFunction[] PATTERN_FUNCTIONS = {
            (x, y) -> (x + y) % 2 == 0,
            (x, y) -> y % 2 == 0,
            (x, y) -> x % 3 == 0,
            (x, y) -> (x + y) % 3 == 0,
            (x, y) -> (x / 3 + y / 2) % 2 == 0,
            (x, y) -> x * y % 2 + x * y % 3 == 0,
            (x, y) -> (x * y % 2 + x * y % 3) % 2 == 0,
            (x, y) -> ((x + y) % 2 + x * y % 3) % 2 == 0
    };

    /**
     * The vertical period of every mask pattern: each one repeats after at most 12 rows, and 12 is
     * a multiple of all their periods.
     */
    private static final int PATTERN_PERIOD = 12;

    /**
     * Creates a matrix filled with the specified mask pattern, covering the whole symbol.
     *
     * @param pattern the data mask pattern index (0&ndash;7)
     * @param version the QR code version (1&ndash;40)
     * @return a new matrix
     */
    private static BitMatrix createPattern(int pattern, int version) {
        var function = PATTERN_FUNCTIONS[pattern];
        var size = QrCodeParameters.size(version);
        var matrix = new BitMatrix(size);

        // Evaluate the predicate for one period only ...
        for (var y = 0; y < Math.min(PATTERN_PERIOD, size); y += 1) {
            for (var x = 0; x < size; x += 1) {
                if (function.isMasked(x, y))
                    matrix.set(x, y, true);
            }
        }

        // ... and replicate it down the matrix, row by row. The read lags the write by exactly one
        // period, so each round of copying builds on the one before.
        for (var y = PATTERN_PERIOD; y < size; y += 1)
            matrix.copyRow(y - PATTERN_PERIOD, y);

        return matrix;
    }

    /**
     * The order in which the mask patterns are evaluated: by how often they win, descending.
     * <p>
     * A pattern that wins often drives the lowest score down early, which is what lets
     * {@link Penalty#calculate(ScoringMatrix, int)} stop early on the patterns that follow. The
     * order affects speed only, never the outcome.
     * </p>
     */
    private static final int[] PATTERN_EVALUATION_ORDER = { 2, 3, 7, 4, 6, 5, 0, 1 };

    private static Encoded applyBestPattern(ScoringMatrix symbol, int version, int ecc, int forcedMask,
            PenaltyScore[] penalties) {
        var bestPattern = selectPattern(symbol, version, ecc, forcedMask, penalties);

        drawFormatInformation(symbol, ecc, bestPattern);
        // Finishing the scoring matrix leaves the mask and the format information in its row view,
        // which is the finished module matrix.
        return new Encoded(symbol.finish(maskPair(bestPattern, version)), bestPattern);
    }

    private static int selectPattern(ScoringMatrix scoringMatrix, int version, int ecc, int forcedMask,
            PenaltyScore[] penalties) {
        // Scoring the patterns would only be thrown away if the caller pinned one and does not
        // want the scores.
        if (forcedMask != NO_FORCED_MASK && penalties == null)
            return forcedMask;

        var bestPattern = -1;
        var lowestPenalty = Integer.MAX_VALUE;

        for (var pattern : PATTERN_EVALUATION_ORDER) {
            drawFormatInformation(scoringMatrix, ecc, pattern);
            var mask = maskPair(pattern, version);
            scoringMatrix.xor(mask);

            int penalty;
            if (penalties == null) {
                penalty = Penalty.calculate(scoringMatrix, lowestPenalty);
            } else {
                penalties[pattern] = Penalty.calculateFully(scoringMatrix);
                penalty = penalties[pattern].total();
            }

            scoringMatrix.xor(mask);

            if (penalty < lowestPenalty) {
                lowestPenalty = penalty;
                bestPattern = pattern;
            }
        }

        return forcedMask != NO_FORCED_MASK ? forcedMask : bestPattern;
    }

    // endregion

    // region Format information

    /**
     * Draws the format information &mdash; the error correction level and the mask pattern, with
     * their BCH error correction bits &mdash; into the area reserved for it.
     * <p>
     * The 15 bits appear twice, so that the format information survives damage to one corner of
     * the symbol.
     * </p>
     *
     * @param modules the module matrix
     * @param ecc     the error correction level (0&ndash;3)
     * @param pattern the data mask pattern index (0&ndash;7)
     */
    static void drawFormatInformation(ScoringMatrix modules, int ecc, int pattern) {
        var formatBits = QrCodeParameters.formatInformationBits(ecc, pattern);
        var size = modules.size();

        // the copy along the bottom left and top right edges
        for (var i = 0; i < 8; i += 1)
            setFormatBit(modules, size - 1 - i, 8, formatBits, i);
        for (var i = 8; i < 15; i += 1)
            setFormatBit(modules, 8, size - 15 + i, formatBits, i);

        // the copy around the top left finder pattern
        for (var i = 0; i < 6; i += 1)
            setFormatBit(modules, 8, i, formatBits, i);
        setFormatBit(modules, 8, 7, formatBits, 6);
        setFormatBit(modules, 8, 8, formatBits, 7);
        setFormatBit(modules, 7, 8, formatBits, 8);
        for (var i = 9; i < 15; i += 1)
            setFormatBit(modules, 14 - i, 8, formatBits, i);
    }

    private static void setFormatBit(ScoringMatrix modules, int x, int y, int bits, int bitIndex) {
        modules.setFormatBit(x, y, (bits & (1 << bitIndex)) != 0);
    }

    // endregion
}

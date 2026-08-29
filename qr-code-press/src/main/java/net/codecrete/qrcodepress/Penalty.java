/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * Scores a masked QR code, so that the encoder can pick the mask pattern that reads best.
 * <p>
 * See "7.8.3 Evaluation of data masking results" in the QR code specification
 * (ISO/IEC 18004:2024(en)) for the rules.
 * </p>
 * <p>
 * Each rule works on whole 64-bit words rather than on single modules, so a rule costs a handful of
 * instructions per word instead of one per module. The rules that scan columns run the row
 * algorithm over the transpose the {@link ScoringMatrix} carries.
 * </p>
 * <p>
 * Every rule that scans rows comes in three forms, one per {@link BitMatrix} row layout, each
 * scanning the one, two or three words of modules that layout holds with its loop over them
 * unrolled. See {@link BitMatrix} for which versions take which layout. All three compute the same
 * score, and a matrix's layout follows from its size, so which one runs never changes an outcome
 * &mdash; only how many words are scanned.
 * </p>
 * <p>
 * Every rule subtracts the contribution of the three finder patterns, which is the same for every
 * mask pattern. That keeps the numbers small and comparable, and it is what lets
 * {@link #calculate(ScoringMatrix, int)} stop early. A rule therefore scores non-negatively only
 * for a matrix that carries the finder patterns, which every {@link ScoringMatrix} does; scored
 * against anything else, an individual rule can return a negative number.
 * </p>
 */
final class Penalty {

    private Penalty() {
        // non-instantiable
    }

    /**
     * Computes the total penalty score, stopping early once the candidate cannot win.
     * <p>
     * Every rule contributes a non-negative amount to a symbol &mdash; which is all a
     * {@link ScoringMatrix} can be &mdash; so the running sum only ever grows. Once it reaches
     * {@code lowestPenaltySoFar}, this candidate cannot beat the current best and the remaining
     * rules are skipped.
     * </p>
     * <p>
     * The result is the exact score when it is below {@code lowestPenaltySoFar}; otherwise it is
     * the partial sum at the point of bailout, which lies between {@code lowestPenaltySoFar} and
     * the exact score. Either way, comparing it against {@code lowestPenaltySoFar} with strict
     * less-than yields the same decision as a full computation.
     * </p>
     *
     * @param matrix             the masked symbol and its transpose
     * @param lowestPenaltySoFar the score of the best candidate so far
     * @return the score, or a partial sum between {@code lowestPenaltySoFar} and the score
     */
    static int calculate(ScoringMatrix matrix, int lowestPenaltySoFar) {
        // Ordered by mean contribution, descending, so that the bailout fires as early as possible.
        var sum = twoByTwoBlocks(matrix.rows());
        if (sum >= lowestPenaltySoFar)
            return sum;

        sum += streaks(matrix.columns());
        if (sum >= lowestPenaltySoFar)
            return sum;

        sum += streaks(matrix.rows());
        if (sum >= lowestPenaltySoFar)
            return sum;

        sum += finderPatterns(matrix.rows());
        if (sum >= lowestPenaltySoFar)
            return sum;

        sum += finderPatterns(matrix.columns());
        if (sum >= lowestPenaltySoFar)
            return sum;

        return sum + colorBalance(matrix.rows());
    }

    /**
     * Computes the penalty score of every rule, without stopping early.
     *
     * @param matrix the masked symbol and its transpose
     * @return the score, broken down by rule
     */
    static PenaltyScore calculateFully(ScoringMatrix matrix) {
        return new PenaltyScore(
                twoByTwoBlocks(matrix.rows()),
                streaks(matrix.rows()),
                streaks(matrix.columns()),
                finderPatterns(matrix.rows()),
                finderPatterns(matrix.columns()),
                colorBalance(matrix.rows()));
    }

    // region Streaks

    /**
     * Scores streaks of five or more adjacent modules of the same color in a row.
     * <p>
     * A streak of length {@code L >= 5} scores {@code N1 + (L - 5)} points, with {@code N1 = 3}.
     * </p>
     *
     * @param modules the module matrix
     * @return the score
     */
    static int streaks(BitMatrix modules) {
        return switch (modules.usedWordsPerRow()) {
            case 1 -> streaksOneWord(modules);
            case 2 -> streaksTwoWords(modules);
            default -> streaksThreeWords(modules);
        };
    }

    /** Each finder pattern contributes two streaks of 5, two streaks of 7 and one streak of 8. */
    private static final int BASE_STREAKS = 3 * (2 * 3 + 2 * 5 + 6);

    private static int streaksOneWord(BitMatrix modules) {
        // With T[i] = bits[i] ^ bits[i+1] (set where two adjacent modules differ):
        //   tz[i]         = ~T[i] & ~T[i+1]     (three equal modules at positions i..i+2)
        //   fiveWindow[i] = tz[i] & tz[i+2]     (five equal modules at positions i..i+4)
        //   run5Start[i]  = fiveWindow[i] AND (i == 0 OR T[i-1] == 1)
        // A run of length L >= 5 contains (L - 4) five-windows and starts exactly once, and
        // (L - 2) = (L - 4) + 2 points, so the row scores
        //   popCount(fiveWindow) + 2 * popCount(run5Start).
        // The five-window mask is clipped to columns [0, size-5] so that the padding zeros past
        // the row's last column cannot open a window of their own.

        var raw = modules.raw();
        var size = modules.size();
        if (size < 5)
            return 0;

        var edgeMask = (1L << (size - 4)) - 1;
        var fiveWindowCount = 0;
        var run5StartCount = 0;

        for (var y = 0; y < size; y += 1) {
            var w = raw[y];

            var t = w ^ (w >>> 1);
            var tz = ~(t | (t >>> 1));
            var fw = tz & (tz >>> 2) & edgeMask;
            // a window starts a run if the module before it differs; bit 0 is forced, as the start
            // of the row always starts a run
            var rs = fw & ((t << 1) | 1L);

            fiveWindowCount += Long.bitCount(fw);
            run5StartCount += Long.bitCount(rs);
        }

        return fiveWindowCount + 2 * run5StartCount - BASE_STREAKS;
    }

    private static int streaksTwoWords(BitMatrix modules) {
        // The recurrence of streaksOneWord across two words; see streaksThreeWords for how the
        // carries and the last word work, which is the same here.

        var raw = modules.raw();
        var size = modules.size();
        if (size < 5)
            return 0;

        var edgeMask = buildEdgeMask(size - 4);
        var fiveWindowCount = 0;
        var run5StartCount = 0;

        for (var y = 0; y < size; y += 1) {
            var row = 2 * y;
            var w0 = raw[row];
            var w1 = raw[row + 1];

            var t0 = w0 ^ ((w0 >>> 1) | (w1 << 63));
            var t1 = w1 ^ (w1 >>> 1);

            var tz0 = ~(t0 | ((t0 >>> 1) | (t1 << 63)));
            var tz1 = ~(t1 | (t1 >>> 1));

            var fw0 = tz0 & ((tz0 >>> 2) | (tz1 << 62)) & edgeMask[0];
            var fw1 = tz1 & (tz1 >>> 2) & edgeMask[1];

            var rs0 = fw0 & ((t0 << 1) | 1L);
            var rs1 = fw1 & ((t1 << 1) | (t0 >>> 63));

            fiveWindowCount += Long.bitCount(fw0) + Long.bitCount(fw1);
            run5StartCount += Long.bitCount(rs0) + Long.bitCount(rs1);
        }

        return fiveWindowCount + 2 * run5StartCount - BASE_STREAKS;
    }

    private static int streaksThreeWords(BitMatrix modules) {
        // The recurrence of streaksOneWord, plumbed across the three words of modules a row holds:
        // every shift carries the bits it drops into the neighbouring word. The shifts out of the
        // last word bring in zeros, which the edge mask clears again: at the largest size of this
        // layout the mask keeps bits 0 to 59 of that word, and the shifts reach only bits 62 and 63.

        var raw = modules.raw();
        var size = modules.size();
        if (size < 5)
            return 0;

        var edgeMask = buildEdgeMask(size - 4);
        var fiveWindowCount = 0;
        var run5StartCount = 0;

        for (var y = 0; y < size; y += 1) {
            var row = BitMatrix.WORDS_PER_ROW * y;
            var w0 = raw[row];
            var w1 = raw[row + 1];
            var w2 = raw[row + 2];

            var t0 = w0 ^ ((w0 >>> 1) | (w1 << 63));
            var t1 = w1 ^ ((w1 >>> 1) | (w2 << 63));
            var t2 = w2 ^ (w2 >>> 1);

            // tz[i] = ~T[i] & ~T[i+1] (three equal modules at i..i+2)
            var tz0 = ~(t0 | ((t0 >>> 1) | (t1 << 63)));
            var tz1 = ~(t1 | ((t1 >>> 1) | (t2 << 63)));
            var tz2 = ~(t2 | (t2 >>> 1));

            // fw[i] = tz[i] & tz[i+2] (five equal modules at i..i+4)
            var fw0 = tz0 & ((tz0 >>> 2) | (tz1 << 62)) & edgeMask[0];
            var fw1 = tz1 & ((tz1 >>> 2) | (tz2 << 62)) & edgeMask[1];
            var fw2 = tz2 & (tz2 >>> 2) & edgeMask[2];

            // a window starts a run if the module before it differs; bit 0 of word 0 is forced,
            // as the start of the row always starts a run
            var rs0 = fw0 & ((t0 << 1) | 1L);
            var rs1 = fw1 & ((t1 << 1) | (t0 >>> 63));
            var rs2 = fw2 & ((t2 << 1) | (t1 >>> 63));

            fiveWindowCount += Long.bitCount(fw0) + Long.bitCount(fw1) + Long.bitCount(fw2);
            run5StartCount += Long.bitCount(rs0) + Long.bitCount(rs1) + Long.bitCount(rs2);
        }

        return fiveWindowCount + 2 * run5StartCount - BASE_STREAKS;
    }

    // endregion

    // region 2x2 blocks

    /**
     * Scores blocks of 2&times;2 modules of the same color, {@code N2 = 3} points each.
     * <p>
     * Overlapping blocks count separately, as the specification prescribes.
     * </p>
     *
     * @param modules the module matrix
     * @return the score
     */
    static int twoByTwoBlocks(BitMatrix modules) {
        return switch (modules.usedWordsPerRow()) {
            case 1 -> twoByTwoBlocksOneWord(modules);
            case 2 -> twoByTwoBlocksTwoWords(modules);
            default -> twoByTwoBlocksThreeWords(modules);
        };
    }

    /** Each finder pattern contributes four blocks, from the 3x3 dark modules at its center. */
    private static final int BASE_TWO_BY_TWO_BLOCKS = 4 * 3;

    private static int twoByTwoBlocksOneWord(BitMatrix modules) {
        // For a pair of adjacent rows (A, B), bit x of `monochrome` is set exactly when the 2x2
        // block whose top-left corner is (x, y) is of a single color:
        //   monochrome = ~((A ^ (A >> 1)) | (B ^ (B >> 1)) | (A ^ B))
        // Columns at x >= size-1 are cleared by the edge mask.

        var raw = modules.raw();
        var size = modules.size();
        if (size < 2)
            return 0;

        var edgeMask = (1L << (size - 1)) - 1;
        var count = 0;

        // each row is the lower row of one pair and the upper row of the next, so it is read once
        var b = raw[0];
        for (var y = 0; y < size - 1; y += 1) {
            var a = b;
            b = raw[y + 1];
            var monochrome = ~((a ^ (a >>> 1)) | (b ^ (b >>> 1)) | (a ^ b)) & edgeMask;
            count += Long.bitCount(monochrome);
        }

        return (count - BASE_TWO_BY_TWO_BLOCKS) * 3;
    }

    private static int twoByTwoBlocksTwoWords(BitMatrix modules) {
        // The identity of twoByTwoBlocksOneWord, applied to each of the two words of modules a row
        // holds, with every shift carrying the bits it drops into the neighbouring word.

        var raw = modules.raw();
        var size = modules.size();
        if (size < 2)
            return 0;

        var edgeMask = buildEdgeMask(size - 1);

        var count = 0;
        for (var y = 0; y < size - 1; y += 1) {
            var aRow = 2 * y;
            var bRow = aRow + 2;

            var a0 = raw[aRow];
            var a1 = raw[aRow + 1];
            var b0 = raw[bRow];
            var b1 = raw[bRow + 1];

            var mono0 = ~((a0 ^ ((a0 >>> 1) | (a1 << 63))) | (b0 ^ ((b0 >>> 1) | (b1 << 63)))
                    | (a0 ^ b0)) & edgeMask[0];
            var mono1 = ~((a1 ^ (a1 >>> 1)) | (b1 ^ (b1 >>> 1)) | (a1 ^ b1)) & edgeMask[1];

            count += Long.bitCount(mono0) + Long.bitCount(mono1);
        }

        return (count - BASE_TWO_BY_TWO_BLOCKS) * 3;
    }

    private static int twoByTwoBlocksThreeWords(BitMatrix modules) {
        // The identity of twoByTwoBlocksOneWord, applied to each of the three words of modules a
        // row holds, with every shift carrying the bits it drops into the neighbouring word.

        var raw = modules.raw();
        var size = modules.size();
        if (size < 2)
            return 0;

        var edgeMask = buildEdgeMask(size - 1);

        var count = 0;
        for (var y = 0; y < size - 1; y += 1) {
            var aRow = BitMatrix.WORDS_PER_ROW * y;
            var bRow = aRow + BitMatrix.WORDS_PER_ROW;

            var a0 = raw[aRow];
            var a1 = raw[aRow + 1];
            var a2 = raw[aRow + 2];
            var b0 = raw[bRow];
            var b1 = raw[bRow + 1];
            var b2 = raw[bRow + 2];

            var mono0 = ~((a0 ^ ((a0 >>> 1) | (a1 << 63))) | (b0 ^ ((b0 >>> 1) | (b1 << 63)))
                    | (a0 ^ b0)) & edgeMask[0];
            var mono1 = ~((a1 ^ ((a1 >>> 1) | (a2 << 63))) | (b1 ^ ((b1 >>> 1) | (b2 << 63)))
                    | (a1 ^ b1)) & edgeMask[1];
            var mono2 = ~((a2 ^ (a2 >>> 1)) | (b2 ^ (b2 >>> 1)) | (a2 ^ b2)) & edgeMask[2];

            count += Long.bitCount(mono0) + Long.bitCount(mono1) + Long.bitCount(mono2);
        }

        return (count - BASE_TWO_BY_TWO_BLOCKS) * 3;
    }

    // endregion

    // region Finder-like patterns

    /**
     * Scores patterns resembling a finder pattern, {@code N3 = 40} points each.
     * <p>
     * The pattern is the 1:1:3:1:1 ratio of the finder pattern, i.e. the module sequence
     * {@code 1011101}, with at least four light modules on one side and at least one on the other.
     * Modules beyond the edge of the symbol count as light.
     * </p>
     *
     * @param modules the module matrix
     * @return the score
     */
    static int finderPatterns(BitMatrix modules) {
        return switch (modules.usedWordsPerRow()) {
            case 1 -> finderPatternsOneWord(modules);
            case 2 -> finderPatternsTwoWords(modules);
            default -> finderPatternsThreeWords(modules);
        };
    }

    /** The three mandatory finder patterns match nine times in total. */
    private static final int BASE_FINDER_PATTERNS = 9;

    private static int finderPatternsOneWord(BitMatrix modules) {
        var raw = modules.raw();
        var size = modules.size();
        var count = 0;

        for (var y = 0; y < size; y += 1) {
            count += matchesInWord(0L, raw[y], 0L);
        }

        return (count - BASE_FINDER_PATTERNS) * 40;
    }

    private static int finderPatternsTwoWords(BitMatrix modules) {
        var raw = modules.raw();
        var size = modules.size();
        var count = 0;

        for (var y = 0; y < size; y += 1) {
            var row = 2 * y;
            var w0 = raw[row];
            var w1 = raw[row + 1];

            count += matchesInWord(0L, w0, w1) + matchesInWord(w0, w1, 0L);
        }

        return (count - BASE_FINDER_PATTERNS) * 40;
    }

    private static int finderPatternsThreeWords(BitMatrix modules) {
        var raw = modules.raw();
        var size = modules.size();
        var count = 0;

        for (var y = 0; y < size; y += 1) {
            var row = BitMatrix.WORDS_PER_ROW * y;
            var w0 = raw[row];
            var w1 = raw[row + 1];
            var w2 = raw[row + 2];

            // the word past the last one is the always-zero padding word, and light is what the
            // rule wants beyond the edge of the symbol anyway
            count += matchesInWord(0L, w0, w1)
                    + matchesInWord(w0, w1, w2)
                    + matchesInWord(w1, w2, 0L);
        }

        return (count - BASE_FINDER_PATTERNS) * 40;
    }

    /**
     * Counts the matches beginning in one word of a row, given the words on either side of it.
     * <p>
     * A whole word is matched at once rather than column by column. Bit {@code s} of
     * {@code pattern} is set where the module sequence {@code 1011101} begins at column {@code s}:
     * each shift lines up the module at a fixed offset from {@code s}, carrying in the bits it
     * needs from the neighbouring word. Where there is no neighbour the shift brings in zeros,
     * which is exactly what the rule asks for, as the modules beyond the edge of the symbol count
     * as light. The pattern's own dark modules keep {@code s} within {@code [0, size-7]}, so no
     * edge mask is needed either.
     * </p>
     * <p>
     * The row layouts differ only in how many times this is called and what they pass for the
     * neighbours, so all of them match by the same identity.
     * </p>
     *
     * @param previous the word before this one in the row, or 0 if this is the first
     * @param w        the word to match
     * @param next     the word after this one in the row, or 0 if this is the last
     * @return the number of matches beginning in {@code w}
     */
    private static int matchesInWord(long previous, long w, long next) {
        // bit s of rN is the module at column s+N, bit s of lN the module at column s-N
        var r1 = (w >>> 1) | (next << 63);
        var r2 = (w >>> 2) | (next << 62);
        var r3 = (w >>> 3) | (next << 61);
        var r4 = (w >>> 4) | (next << 60);
        var r5 = (w >>> 5) | (next << 59);
        var r6 = (w >>> 6) | (next << 58);
        var r7 = (w >>> 7) | (next << 57);
        var r8 = (w >>> 8) | (next << 56);
        var r9 = (w >>> 9) | (next << 55);
        var r10 = (w >>> 10) | (next << 54);
        var l1 = (w << 1) | (previous >>> 63);
        var l2 = (w << 2) | (previous >>> 62);
        var l3 = (w << 3) | (previous >>> 61);
        var l4 = (w << 4) | (previous >>> 60);

        var pattern = w & r2 & r3 & r4 & r6 & ~(r1 | r5);
        // both variants of the rule want one light module on either side of the pattern ...
        var enclosed = pattern & ~(l1 | r7);
        // ... and three more on one side or the other, which is where the four come from. A
        // pattern with room on both sides still counts once: ~(l & r) is ~l | ~r.
        return Long.bitCount(enclosed & ~((l2 | l3 | l4) & (r8 | r9 | r10)));
    }

    // endregion

    // region Color balance

    /**
     * Scores the deviation of the proportion of dark modules from 50%, {@code N4 = 10} points for
     * every full step of 5%.
     *
     * @param modules the module matrix
     * @return the score
     */
    static int colorBalance(BitMatrix modules) {
        var darkModules = modules.popCount();

        var size = modules.size();
        var total = size * size;
        // The deviation in percent is |darkModules / total - 1/2| * 100, and a step is 5% of it.
        // Scaled by 2 * total, that whole expression stays exact in integers: the numerator is
        // |2 * darkModules - total| * 10 and the denominator is total. The division rounds down,
        // so that a proportion between 45% and 55% scores nothing, as the specification
        // prescribes.
        return 10 * (Math.abs(2 * darkModules - total) * 10 / total);
    }

    // endregion

    /**
     * Builds a per-word mask keeping the lowest {@code validBits} bits of a wide row and clearing
     * the rest, so that the padding past the last column cannot produce a spurious match.
     *
     * @param validBits the number of leading bit positions to keep
     * @return one mask per word of modules of a row
     */
    private static long[] buildEdgeMask(int validBits) {
        var validWord = validBits >> 6;
        var validBit = validBits & 0x3f;
        var partialMask = (1L << validBit) - 1;

        var mask = new long[BitMatrix.MAX_USED_WORDS_PER_ROW];
        for (var w = 0; w < mask.length; w += 1) {
            if (w < validWord)
                mask[w] = -1L;
            else if (w == validWord)
                mask[w] = partialMask;
        }
        return mask;
    }
}

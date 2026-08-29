/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * The penalty score of a data mask pattern, broken down by rule.
 * <p>
 * QR codes use one of eight mask patterns to improve the readability of the symbol. The penalty
 * score measures how hard a QR code masked with a given pattern is to read; the encoder picks the
 * pattern with the lowest score.
 * </p>
 * <p>
 * The breakdown is collected for analysis only; the library itself does not need it. Collecting it
 * makes generating a QR code slower, because every pattern has to be scored in full even once it is
 * clear that it cannot win.
 * </p>
 * <p>
 * In deviation from the QR code specification, the score does not include the contribution of the
 * finder patterns: it is the same for every mask pattern, so it is subtracted out.
 * </p>
 *
 * @param twoByTwoBlocks           the score for blocks of 2&times;2 modules of the same color
 * @param horizontalStreaks        the score for long horizontal streaks of the same color
 * @param verticalStreaks          the score for long vertical streaks of the same color
 * @param horizontalFinderPatterns the score for horizontal patterns resembling a finder pattern
 *                                 (the 1:1:3:1:1 ratio)
 * @param verticalFinderPatterns   the score for vertical patterns resembling a finder pattern
 * @param colorBalance             the score for an imbalance between dark and light modules
 */
public record PenaltyScore(int twoByTwoBlocks, int horizontalStreaks, int verticalStreaks,
        int horizontalFinderPatterns, int verticalFinderPatterns, int colorBalance) {

    /**
     * Returns the total penalty score, i.e. the sum of the individual scores.
     *
     * @return the total score
     */
    public int total() {
        return twoByTwoBlocks + horizontalStreaks + verticalStreaks
                + horizontalFinderPatterns + verticalFinderPatterns + colorBalance;
    }
}

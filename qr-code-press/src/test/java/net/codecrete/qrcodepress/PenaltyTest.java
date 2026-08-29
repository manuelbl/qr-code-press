/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Penalty scoring tests.
 * <p>
 * Every rule subtracts the contribution of the three finder patterns, so a matrix carrying no
 * finder patterns at all scores the negated base penalty rather than zero. The base penalties are
 * named below, so that each expectation reads as "the score this matrix earns, minus the base".
 * </p>
 */
class PenaltyTest {

    /** What the three finder patterns contribute to the same-color streak rule. */
    private static final int BASE_STREAKS = 66;

    /** What the three finder patterns contribute to the 2&times;2 block rule. */
    private static final int BASE_TWO_BY_TWO_BLOCKS = 36;

    /** What the three finder patterns contribute to the finder-pattern rule. */
    private static final int BASE_FINDER_PATTERNS = 360;

    /** Sizes taking the wide row layout, with columns reaching into the second and third word. */
    private static final int[] WIDE_SIZES = { 65, 129, 177 };

    // region Streaks

    @ParameterizedTest(name = "size {0}, inverted {1}")
    @MethodSource("compactSizesInverted")
    @DisplayName("scores nothing for a streak of four")
    void streaksIgnoresShortStreaks(int size, boolean invert) {
        var modules = checkerboard(size);

        // a streak of 4, placed so the checkerboard around it does not extend it
        for (var x = 3; x < 7; x += 1)
            modules.set(x, 3, true);
        modules.set(7, 3, false);

        assertThat(Penalty.streaks(maybeInvert(modules, invert))).isEqualTo(-BASE_STREAKS);
    }

    @ParameterizedTest(name = "size {0}, inverted {1}")
    @MethodSource("compactSizesInverted")
    @DisplayName("scores 3 for a streak of five")
    void streaksScoresAStreakOfFive(int size, boolean invert) {
        var modules = checkerboard(size);

        for (var x = 3; x < 8; x += 1)
            modules.set(x, 3, true);

        assertThat(Penalty.streaks(maybeInvert(modules, invert))).isEqualTo(3 - BASE_STREAKS);
    }

    @ParameterizedTest(name = "size {0}, inverted {1}")
    @MethodSource("compactSizesInverted")
    @DisplayName("scores a streak ending at the last column")
    void streaksScoresAStreakAtTheRowEnd(int size, boolean invert) {
        var modules = checkerboard(size);

        for (var x = size - 5; x < size; x += 1)
            modules.set(x, 4, true);
        // keep the checkerboard from extending the streak, which it does at an even size
        modules.set(size - 6, 4, false);

        assertThat(Penalty.streaks(maybeInvert(modules, invert))).isEqualTo(3 - BASE_STREAKS);
    }

    @ParameterizedTest(name = "size {0}, inverted {1}")
    @MethodSource("compactSizesInverted")
    @DisplayName("counts a long streak once, not once per five-module window")
    void streaksCountsALongStreakOnce(int size, boolean invert) {
        var modules = checkerboard(size);

        // a streak of 9
        for (var x = 3; x < 12; x += 1)
            modules.set(x, 3, true);

        assertThat(Penalty.streaks(maybeInvert(modules, invert))).isEqualTo(7 - BASE_STREAKS);
    }

    @ParameterizedTest(name = "size {0}, starting at column {1}")
    @CsvSource({ "65, 60", "129, 60", "129, 124", "177, 60", "177, 124" })
    @DisplayName("scores a streak straddling a word boundary")
    void streaksScoresAcrossWordBoundaries(int size, int startColumn) {
        var modules = checkerboard(size);

        for (var x = startColumn; x < startColumn + 5; x += 1)
            modules.set(x, 3, true);
        // keep the checkerboard from extending the streak at either end
        modules.set(startColumn - 1, 3, false);
        if (startColumn + 5 < size)
            modules.set(startColumn + 5, 3, false);

        assertThat(Penalty.streaks(modules)).isEqualTo(3 - BASE_STREAKS);
    }

    @ParameterizedTest(name = "size {0}")
    @MethodSource("allSizes")
    @DisplayName("scores a streak starting at the first column")
    void streaksScoresAStreakAtTheRowStart(int size) {
        var modules = checkerboard(size);

        for (var x = 0; x < 5; x += 1)
            modules.set(x, 4, true);

        assertThat(Penalty.streaks(modules)).isEqualTo(3 - BASE_STREAKS);
    }

    @ParameterizedTest(name = "size {0}")
    @MethodSource("allSizes")
    @DisplayName("does not let the padding past the last column extend a streak")
    void streaksIgnoresThePadding(int size) {
        var modules = checkerboard(size);

        // a streak of four light modules at the end of the row; the zero padding that fills the rest
        // of the word must not turn it into a streak of five or more
        for (var x = size - 4; x < size; x += 1)
            modules.set(x, 2, false);
        // keep the checkerboard from extending the light streak, which it does at an even size
        modules.set(size - 5, 2, true);

        assertThat(Penalty.streaks(modules)).isEqualTo(-BASE_STREAKS);
    }

    // endregion

    // region 2x2 blocks

    @ParameterizedTest(name = "size {0}, inverted {1}")
    @MethodSource("allSizesInverted")
    @DisplayName("scores nothing on a checkerboard")
    void twoByTwoBlocksScoresNothingOnACheckerboard(int size, boolean invert) {
        var modules = maybeInvert(checkerboard(size), invert);

        assertThat(Penalty.twoByTwoBlocks(modules)).isEqualTo(-BASE_TWO_BY_TWO_BLOCKS);

        TestMatrices.naiveTranspose(modules);
        assertThat(Penalty.twoByTwoBlocks(modules)).isEqualTo(-BASE_TWO_BY_TWO_BLOCKS);
    }

    @ParameterizedTest(name = "size {0}, inverted {1}")
    @MethodSource("allSizesInverted")
    @DisplayName("scores 3 for each block of 2x2 modules of the same colour")
    void twoByTwoBlocksScoresEachBlock(int size, boolean invert) {
        var modules = checkerboard(size);
        modules.fillRect(4, 3, 2, 2);
        modules.fillRect(6, 8, 2, 2);

        maybeInvert(modules, invert);

        assertThat(Penalty.twoByTwoBlocks(modules)).isEqualTo(6 - BASE_TWO_BY_TWO_BLOCKS);

        TestMatrices.naiveTranspose(modules);
        assertThat(Penalty.twoByTwoBlocks(modules)).isEqualTo(6 - BASE_TWO_BY_TWO_BLOCKS);
    }

    @ParameterizedTest(name = "size {0}")
    @MethodSource("wideSizes")
    @DisplayName("scores a block straddling a word boundary")
    void twoByTwoBlocksScoresAcrossWordBoundaries(int size) {
        var modules = checkerboard(size);
        modules.fillRect(63, 5, 2, 2);

        assertThat(Penalty.twoByTwoBlocks(modules)).isEqualTo(3 - BASE_TWO_BY_TWO_BLOCKS);
    }

    @ParameterizedTest(name = "size {0}")
    @ValueSource(ints = { 17, 25, 65, 129, 177 })
    @DisplayName("scores a block in the bottom right corner")
    void twoByTwoBlocksScoresTheLastPosition(int size) {
        var modules = checkerboard(size);
        modules.fillRect(size - 2, size - 2, 2, 2);

        assertThat(Penalty.twoByTwoBlocks(modules)).isEqualTo(3 - BASE_TWO_BY_TWO_BLOCKS);
    }

    @ParameterizedTest(name = "size {0}, at column {1}")
    @CsvSource({ "21, 3", "65, 61", "69, 62", "73, 63", "77, 64", "81, 65" })
    @DisplayName("counts overlapping blocks separately")
    void twoByTwoBlocksCountsOverlappingBlocks(int size, int x) {
        var modules = checkerboard(size);

        // a 3x3 area holds four overlapping 2x2 blocks
        modules.fillRect(x, 4, 3, 3);

        assertThat(Penalty.twoByTwoBlocks(modules)).isEqualTo(12 - BASE_TWO_BY_TWO_BLOCKS);
    }

    // endregion

    // region Finder-like patterns

    @ParameterizedTest(name = "size {0}")
    @ValueSource(ints = { 19, 25, 37 })
    @DisplayName("scores nothing for a finder-like pattern lacking the light margin")
    void finderPatternsNeedTheLightMargin(int size) {
        var modules = checkerboard(size);
        drawFinderLikePattern(modules, 3, 3, 0, 3);
        drawFinderLikePattern(modules, 8, 6, 3, 0);

        assertThat(Penalty.finderPatterns(modules)).isEqualTo(-BASE_FINDER_PATTERNS);

        invert(modules);
        assertThat(Penalty.finderPatterns(modules)).isEqualTo(-BASE_FINDER_PATTERNS);
    }

    @ParameterizedTest(name = "margin on the left: {0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("scores 40 for a finder-like pattern with the light margin on either side")
    void finderPatternsScoreEitherOrientation(boolean marginOnTheLeft) {
        var modules = checkerboard(25);

        drawFinderLikePattern(modules, 4, 3, marginOnTheLeft ? 4 : 1, marginOnTheLeft ? 1 : 4);

        assertThat(Penalty.finderPatterns(modules)).isEqualTo(40 - BASE_FINDER_PATTERNS);

        // the pattern is not symmetric in color: inverting it makes it disappear
        invert(modules);
        assertThat(Penalty.finderPatterns(modules)).isEqualTo(-BASE_FINDER_PATTERNS);
    }

    @ParameterizedTest(name = "at column {0}, row {1}")
    @MethodSource("borderPositions")
    @DisplayName("counts the symbol edge as the light margin, on the left")
    void finderPatternsCountTheLeftEdgeAsMargin(int x, int y) {
        var modules = checkerboard(25);

        drawFinderLikePattern(modules, x, y, x, 1);

        assertThat(Penalty.finderPatterns(modules)).isEqualTo(40 - BASE_FINDER_PATTERNS);
    }

    @ParameterizedTest(name = "at column {0} from the right, row {1}")
    @MethodSource("borderPositions")
    @DisplayName("counts the symbol edge as the light margin, on the right")
    void finderPatternsCountTheRightEdgeAsMargin(int x, int y) {
        var modules = checkerboard(25);

        drawFinderLikePattern(modules, modules.size() - x - 7, y, 1, x);

        assertThat(Penalty.finderPatterns(modules)).isEqualTo(40 - BASE_FINDER_PATTERNS);
    }

    // endregion

    // region Color balance

    @ParameterizedTest(name = "size {0}, {1} dark")
    @CsvSource({
            "17, 0.5,   0",
            "21, 0.545, 0",
            "21, 0.455, 0",
            "21, 0.555, 10",
            "21, 0.445, 10",
            "37, 0.595, 10",
            "37, 0.405, 10",
            "37, 0.605, 20",
            "37, 0.395, 20"
    })
    @DisplayName("scores 10 for every full 5% the dark proportion deviates from a half")
    void colorBalanceScoresTheDeviation(int size, double darkProportion, int expected) {
        var modules = fill(size, darkProportion);

        assertThat(Penalty.colorBalance(modules)).isEqualTo(expected);

        invert(modules);
        assertThat(Penalty.colorBalance(modules)).as("inverted").isEqualTo(expected);

        TestMatrices.naiveTranspose(modules);
        assertThat(Penalty.colorBalance(modules)).as("transposed").isEqualTo(expected);
    }

    @ParameterizedTest(name = "size {0}")
    @MethodSource("symbolSizes")
    @DisplayName("scores every possible dark count as the specification states it, in percent")
    void colorBalanceMatchesThePercentageRule(int size) {
        var total = size * size;

        for (var darkModules = 0; darkModules <= total; darkModules += 1) {
            var percent = darkModules * 100.0 / total;
            var expected = 10 * (int) Math.floor(Math.abs(percent - 50.0) / 5.0);

            assertThat(Penalty.colorBalance(withDarkCount(size, darkModules)))
                    .as("%d of %d dark modules, %.3f%%", darkModules, total, percent)
                    .isEqualTo(expected);
        }
    }

    @ParameterizedTest(name = "size {0}")
    @MethodSource("symbolSizes")
    @DisplayName("scores a symbol exactly as its colour-inverse, for every dark count")
    void colorBalanceIsSymmetricUnderInversion(int size) {
        var total = size * size;

        for (var darkModules = 0; darkModules <= total / 2; darkModules += 1) {
            var score = Penalty.colorBalance(withDarkCount(size, darkModules));

            assertThat(Penalty.colorBalance(withDarkCount(size, total - darkModules)))
                    .as("%d of %d dark modules, inverted", darkModules, total)
                    .isEqualTo(score);
        }
    }

    // endregion

    // region Base penalty

    @ParameterizedTest(name = "size {0}")
    @MethodSource("symbolSizes")
    @DisplayName("the three finder patterns cost nothing, in either direction")
    void theFinderPatternsCostNothing(int size) {
        var modules = checkerboardWithFinders(size);

        assertThat(Penalty.twoByTwoBlocks(modules)).as("blocks").isZero();
        assertThat(Penalty.finderPatterns(modules)).as("finder patterns").isZero();
        assertThat(Penalty.colorBalance(modules)).as("colour balance").isZero();

        modules.transpose();

        assertThat(Penalty.twoByTwoBlocks(modules)).as("blocks, transposed").isZero();
        assertThat(Penalty.finderPatterns(modules)).as("finder patterns, transposed").isZero();
        assertThat(Penalty.colorBalance(modules)).as("colour balance, transposed").isZero();
    }

    @ParameterizedTest(name = "size {0}")
    @MethodSource("symbolSizes")
    @DisplayName("the streaks inside the three finder patterns cost nothing, in either direction")
    void theFinderPatternStreaksCostNothing(int size) {
        var modules = checkerboardWithFinders(size);

        // Break the light separators, so that they do not continue into the checkerboard as streaks
        // of their own; only the streaks belonging to the finder patterns are to be scored here.
        modules.set(7, 8, true);
        modules.set(8, 7, true);
        modules.set(7, size - 9, true);
        modules.set(8, size - 8, true);
        modules.set(size - 8, 8, true);
        modules.set(size - 9, 7, true);

        assertThat(Penalty.streaks(modules)).as("streaks").isZero();

        modules.transpose();

        assertThat(Penalty.streaks(modules)).as("streaks, transposed").isZero();
    }

    // endregion

    // region Early stop

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 2, 5, 11, 12, 40 })
    @DisplayName("honours the early-stop contract at every threshold")
    void earlyStopHonoursItsContract(int version) {
        var scoringMatrix = symbolOf(version);

        // A threshold of Integer.MAX_VALUE can never be reached, so this is the exact score.
        var truePenalty = Penalty.calculate(scoringMatrix, Integer.MAX_VALUE);

        for (var threshold = -10; threshold <= truePenalty + 50; threshold += 1) {
            var result = Penalty.calculate(scoringMatrix, threshold);

            if (threshold > truePenalty) {
                // The running sum never exceeds the exact score, so a threshold above it cannot be
                // reached and the score comes back in full.
                assertThat(result).as("threshold %d, exact score %d", threshold, truePenalty)
                        .isEqualTo(truePenalty);
            } else {
                // Otherwise the result is a partial sum that has reached the threshold, or the
                // exact score. Either lies between the two, so comparing the result with the
                // threshold decides the mask exactly as a full computation would.
                assertThat(result).as("threshold %d, exact score %d", threshold, truePenalty)
                        .isBetween(threshold, truePenalty);
            }
        }
    }

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 2, 5, 11, 12, 40 })
    @DisplayName("the full breakdown adds up to the score the early-stop path computes")
    void theBreakdownAddsUpToTheTotal(int version) {
        var scoringMatrix = symbolOf(version);

        var score = Penalty.calculateFully(scoringMatrix);

        assertThat(score.total()).isEqualTo(Penalty.calculate(scoringMatrix, Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("reports each rule under its own name")
    void theBreakdownNamesEachRule() {
        var scoringMatrix = symbolOf(3);

        var score = Penalty.calculateFully(scoringMatrix);

        assertThat(score.twoByTwoBlocks()).isEqualTo(Penalty.twoByTwoBlocks(scoringMatrix.rows()));
        assertThat(score.horizontalStreaks()).isEqualTo(Penalty.streaks(scoringMatrix.rows()));
        assertThat(score.verticalStreaks()).isEqualTo(Penalty.streaks(scoringMatrix.columns()));
        assertThat(score.horizontalFinderPatterns())
                .isEqualTo(Penalty.finderPatterns(scoringMatrix.rows()));
        assertThat(score.verticalFinderPatterns())
                .isEqualTo(Penalty.finderPatterns(scoringMatrix.columns()));
        assertThat(score.colorBalance()).isEqualTo(Penalty.colorBalance(scoringMatrix.rows()));
    }

    // endregion

    // region Agreement between the row layouts

    @ParameterizedTest(name = "size {0}")
    @ValueSource(ints = { 21, 29, 61, 63, 64, 65, 66, 124, 125, 127, 128, 129, 177 })
    @DisplayName("every rule scores what a module-by-module reading of it does, in either layout")
    void everyRuleAgreesWithTheNaiveScore(int size) {
        // A matrix picks its row layout by its size, so the sizes here straddle the boundary at 64
        // and the two implementations of each rule are held to one and the same oracle. The sizes
        // around 128 straddle a word boundary within a wide row, where the shifts carrying bits
        // between the words meet the edge mask: 125 is version 27, the largest of them.
        for (var modules : scoringSamples(size)) {
            assertThat(Penalty.streaks(modules))
                    .as("streaks, size %d", size).isEqualTo(naiveStreaks(modules));
            assertThat(Penalty.twoByTwoBlocks(modules))
                    .as("blocks, size %d", size).isEqualTo(naiveTwoByTwoBlocks(modules));
            assertThat(Penalty.finderPatterns(modules))
                    .as("finder patterns, size %d", size).isEqualTo(naiveFinderPatterns(modules));
        }
    }

    /**
     * The matrices the agreement test scores: two pseudo-random ones, one shaped like a symbol, and
     * one carrying finder-like patterns that reach the left and right edge of the symbol.
     */
    private static List<BitMatrix> scoringSamples(int size) {
        var atTheEdges = checkerboardWithFinders(size);
        drawFinderLikePattern(atTheEdges, 0, 9, 0, 4);
        drawFinderLikePattern(atTheEdges, size - 7, 10, 4, 0);

        return List.of(
                TestMatrices.pattern(size, 0xC0FFEE),
                TestMatrices.pattern(size, 0x5EED),
                checkerboardWithFinders(size),
                atTheEdges);
    }

    /** Scores streaks by walking each row module by module. */
    private static int naiveStreaks(BitMatrix modules) {
        var size = modules.size();
        var score = 0;

        for (var y = 0; y < size; y += 1) {
            var runLength = 1;
            for (var x = 1; x <= size; x += 1) {
                if (x < size && modules.get(x, y) == modules.get(x - 1, y)) {
                    runLength += 1;
                    continue;
                }
                if (runLength >= 5)
                    score += 3 + (runLength - 5);
                runLength = 1;
            }
        }

        return score - BASE_STREAKS;
    }

    /** Scores 2x2 blocks by testing each of the four modules of every block. */
    private static int naiveTwoByTwoBlocks(BitMatrix modules) {
        var size = modules.size();
        var count = 0;

        for (var y = 0; y + 1 < size; y += 1) {
            for (var x = 0; x + 1 < size; x += 1) {
                var color = modules.get(x, y);
                if (modules.get(x + 1, y) == color && modules.get(x, y + 1) == color
                        && modules.get(x + 1, y + 1) == color)
                    count += 1;
            }
        }

        return count * 3 - BASE_TWO_BY_TWO_BLOCKS;
    }

    /** Scores finder-like patterns by testing each of the seven modules at every position. */
    private static int naiveFinderPatterns(BitMatrix modules) {
        var size = modules.size();
        var count = 0;

        for (var y = 0; y < size; y += 1) {
            for (var x = 0; x + 7 <= size; x += 1) {
                if (!isFinderLikePattern(modules, x, y))
                    continue;

                // four light modules on one side and at least one on the other
                var lightOnTheLeft = isLight(modules, x - 4, x, y) && isLight(modules, x + 7, x + 8, y);
                var lightOnTheRight = isLight(modules, x - 1, x, y) && isLight(modules, x + 7, x + 11, y);
                if (lightOnTheLeft || lightOnTheRight)
                    count += 1;
            }
        }

        return count * 40 - BASE_FINDER_PATTERNS;
    }

    /** Whether the module sequence {@code 1011101} starts at {@code (x, y)}. */
    private static boolean isFinderLikePattern(BitMatrix modules, int x, int y) {
        return modules.get(x, y) && !modules.get(x + 1, y) && modules.get(x + 2, y)
                && modules.get(x + 3, y) && modules.get(x + 4, y) && !modules.get(x + 5, y)
                && modules.get(x + 6, y);
    }

    /** Whether every module of the row from {@code x} to {@code endX} is light, the symbol edge included. */
    private static boolean isLight(BitMatrix modules, int x, int endX, int y) {
        for (var i = x; i < endX; i += 1) {
            if (i >= 0 && i < modules.size() && modules.get(i, y))
                return false;
        }
        return true;
    }

    // endregion

    // region Arguments

    static Stream<Arguments> compactSizesInverted() {
        return sizesInverted(17, 25, 37, 61, 64);
    }

    static Stream<Arguments> allSizesInverted() {
        return sizesInverted(17, 25, 37, 61, 64, 65, 129, 177);
    }

    static Stream<Arguments> allSizes() {
        return IntStream.of(17, 25, 37, 61, 64, 65, 129, 177).mapToObj(Arguments::arguments);
    }

    static Stream<Arguments> wideSizes() {
        return IntStream.of(WIDE_SIZES).mapToObj(Arguments::arguments);
    }

    static Stream<Arguments> symbolSizes() {
        return IntStream.of(21, 29, 37, 61, 65, 129, 177).mapToObj(Arguments::arguments);
    }

    /** The five columns and two rows at which a finder-like pattern still reaches a symbol edge. */
    static Stream<Arguments> borderPositions() {
        return IntStream.of(7, 8).boxed()
                .flatMap(y -> IntStream.range(0, 5).mapToObj(x -> arguments(x, y)));
    }

    private static Stream<Arguments> sizesInverted(int... sizes) {
        return IntStream.of(sizes).boxed()
                .flatMap(size -> Stream.of(arguments(size, false), arguments(size, true)));
    }

    // endregion

    // region Matrix helpers

    /**
     * The symbol of a version, filled with pseudo-random codewords: the only thing that can be
     * scored, and the only thing every rule scores non-negatively.
     */
    private static ScoringMatrix symbolOf(int version) {
        return ScoringMatrix.ofSymbol(TestMatrices.codewordsFor(version), version);
    }

    /** Fills the matrix so that exactly the given number of its modules is dark. */
    private static BitMatrix withDarkCount(int size, int darkModules) {
        var modules = new BitMatrix(size);
        for (var i = 0; i < darkModules; i += 1)
            modules.set(i % size, i / size, true);
        return modules;
    }

    /** Fills the matrix so that the given proportion of its modules is dark, spread evenly. */
    private static BitMatrix fill(int size, double darkProportion) {
        var modules = new BitMatrix(size);
        var error = 0.0;
        for (var y = 0; y < size; y += 1) {
            for (var x = 0; x < size; x += 1) {
                error += darkProportion;
                if (error > 0.5) {
                    modules.set(x, y, true);
                    error -= 1;
                }
            }
        }
        return modules;
    }

    /** A checkerboard: the pattern that earns no penalty at all, so any addition stands out. */
    private static BitMatrix checkerboard(int size) {
        var modules = new BitMatrix(size);
        for (var y = 0; y < size; y += 1) {
            for (var x = y % 2; x < size; x += 2)
                modules.set(x, y, true);
        }
        return modules;
    }

    /** A checkerboard with the three finder patterns and their separators drawn over it. */
    private static BitMatrix checkerboardWithFinders(int size) {
        var modules = checkerboard(size);

        // the light separators
        modules.invert();
        modules.fillRect(0, 0, 8, 8);
        modules.fillRect(0, size - 8, 8, 8);
        modules.fillRect(size - 8, 0, 8, 8);
        modules.invert();

        // the dark 7x7 square
        modules.fillRect(0, 0, 7, 7);
        modules.fillRect(0, size - 7, 7, 7);
        modules.fillRect(size - 7, 0, 7, 7);

        // the light 5x5 ring
        modules.invert();
        modules.fillRect(1, 1, 5, 5);
        modules.fillRect(1, size - 6, 5, 5);
        modules.fillRect(size - 6, 1, 5, 5);
        modules.invert();

        // the dark 3x3 center
        modules.fillRect(2, 2, 3, 3);
        modules.fillRect(2, size - 5, 3, 3);
        modules.fillRect(size - 5, 2, 3, 3);

        return modules;
    }

    /**
     * Draws the {@code 1011101} module sequence at {@code (x, y)}, preceded by {@code leading} and
     * followed by {@code trailing} light modules.
     */
    private static void drawFinderLikePattern(BitMatrix modules, int x, int y, int leading, int trailing) {
        for (var i = 0; i < leading; i += 1)
            modules.set(x - leading + i, y, false);

        modules.set(x, y, true);
        modules.set(x + 1, y, false);
        modules.set(x + 2, y, true);
        modules.set(x + 3, y, true);
        modules.set(x + 4, y, true);
        modules.set(x + 5, y, false);
        modules.set(x + 6, y, true);

        for (var i = 0; i < trailing; i += 1)
            modules.set(x + 7 + i, y, false);
    }

    private static BitMatrix maybeInvert(BitMatrix modules, boolean invert) {
        if (invert)
            invert(modules);
        return modules;
    }

    /**
     * Inverts the matrix module by module. {@link BitMatrix#invert()} would do, but going through
     * the accessors keeps this oracle independent of the code under test.
     */
    private static void invert(BitMatrix modules) {
        var size = modules.size();
        for (var y = 0; y < size; y += 1) {
            for (var x = 0; x < size; x += 1)
                modules.set(x, y, !modules.get(x, y));
        }
    }

    // endregion
}

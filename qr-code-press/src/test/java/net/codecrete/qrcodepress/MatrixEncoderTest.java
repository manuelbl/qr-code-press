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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.codecrete.qrcodepress.MatrixEncoder.NO_FORCED_MASK;
import static net.codecrete.qrcodepress.MatrixEncoder.PATTERN_COUNT;
import static net.codecrete.qrcodepress.TestMatrices.codewordsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class MatrixEncoderTest {

    // region Mask patterns

    /** One row of the mask-pattern fixture per version and pattern: hash and population count. */
    static Stream<Arguments> maskCases() {
        return VerifiedData.readTsv("maskpatterns.tsv").stream()
                .map(row -> arguments(Integer.parseInt(row[0]), Integer.parseInt(row[1]),
                        row[2], Integer.parseInt(row[3])));
    }

    @ParameterizedTest(name = "version {0}, pattern {1}")
    @MethodSource("maskCases")
    @DisplayName("mask pair matches verified data")
    void maskPairMatches(int version, int pattern, String sha256, int popCount) {
        var mask = MatrixEncoder.maskPair(pattern, version).rows();

        assertThat(TestMatrices.sha256(mask)).isEqualTo(sha256);
        assertThat(mask.popCount()).as("masked modules").isEqualTo(popCount);
    }

    @ParameterizedTest(name = "version {0}, pattern {1}")
    @MethodSource("sampleMasks")
    @DisplayName("mask modules match verified data")
    void maskPairMatchesModuleByModule(int version, int pattern) {
        var mask = MatrixEncoder.maskPair(pattern, version).rows();

        var dump = VerifiedData.read(String.format("maskpatterns/v%02d-m%d.txt", version, pattern));
        assertThat(TestMatrices.rowsOf(mask)).isEqualTo(dump.lines().toList());
    }

    @ParameterizedTest(name = "version {0}, pattern {1}")
    @MethodSource("sampleMasks")
    @DisplayName("pairs every mask with its own transpose")
    void maskPairCarriesTheTranspose(int version, int pattern) {
        var maskPair = MatrixEncoder.maskPair(pattern, version);

        var expected = maskPair.rows().copy();
        TestMatrices.naiveTranspose(expected);

        TestMatrices.assertMatricesEqual(expected, maskPair.columns());
    }

    @ParameterizedTest(name = "version {0}, pattern {1}")
    @MethodSource("sampleMasks")
    @DisplayName("masks nothing outside the payload area")
    void maskCoversThePayloadAreaOnly(int version, int pattern) {
        var outside = MatrixEncoder.maskPair(pattern, version).rows().copy();

        var reserved = FixedPatterns.payloadAreaMap(version).copy();
        reserved.invert();
        outside.and(reserved);

        assertThat(outside.popCount()).isZero();
    }

    @Test
    @DisplayName("hands out the same shared mask pair every time")
    void maskPairIsCached() {
        assertThat(MatrixEncoder.maskPair(5, 12)).isSameAs(MatrixEncoder.maskPair(5, 12));
    }

    // endregion

    // region Payload

    @ParameterizedTest(name = "version {0}")
    @MethodSource("net.codecrete.qrcodepress.VerifiedData#allVersions")
    @DisplayName("fills every payload module the codewords reach, and nothing else")
    void fillPayloadCoversThePayloadArea(int version) {
        var codewords = new byte[QrCodeParameters.codewordCapacity(version)];
        Arrays.fill(codewords, (byte) 0xff);
        var modules = new BitMatrix(QrCodeParameters.size(version));

        MatrixEncoder.fillPayload(modules, codewords, version);

        // Every codeword bit lands on its own payload module. The remainder modules past the last
        // codeword stay light, which is why this is not simply the payload area's module count.
        assertThat(modules.popCount()).as("modules set").isEqualTo(8 * codewords.length);

        var outside = modules.copy();
        var reserved = FixedPatterns.payloadAreaMap(version).copy();
        reserved.invert();
        outside.and(reserved);
        assertThat(outside.popCount()).as("modules set outside the payload area").isZero();
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("net.codecrete.qrcodepress.VerifiedData#allVersions")
    @DisplayName("addresses every payload module exactly once")
    void payloadTargetsCoverThePayloadAreaOnce(int version) {
        var targets = MatrixEncoder.payloadTargets(version);
        var payloadArea = FixedPatterns.payloadAreaMap(version);

        assertThat(targets).as("table length").hasSize(payloadArea.popCount())
                .doesNotHaveDuplicates();

        // Writing every address back has to reproduce the payload area.
        var covered = new BitMatrix(QrCodeParameters.size(version));
        for (var target : targets)
            covered.orBit(target, 1);
        TestMatrices.assertMatricesEqual(payloadArea, covered);
    }

    @Test
    @DisplayName("hands out the same shared payload target table every time")
    void payloadTargetsAreCached() {
        assertThat(MatrixEncoder.payloadTargets(12)).isSameAs(MatrixEncoder.payloadTargets(12));
    }

    @Test
    @DisplayName("lays the payload out from the bottom right corner, upwards, two columns at a time")
    void fillPayloadStartsInTheBottomRightCorner() {
        // The first codeword's bits go into the bottom right 2x4 block, most significant bit
        // first, alternating between the right and the left column of the stride.
        var modules = new BitMatrix(QrCodeParameters.size(1));

        MatrixEncoder.fillPayload(modules, new byte[] { (byte) 0b1010_0110 }, 1);

        assertThat(TestMatrices.rowsOf(modules).subList(17, 21)).containsExactly(
                "....................#",  // bit 6 at (20, 17), bit 7 clear at (19, 17)
                "...................#.",  // bit 4 clear at (20, 18), bit 5 at (19, 18)
                "....................#",  // bit 2 at (20, 19), bit 3 clear at (19, 19)
                "....................#"); // bit 0 at (20, 20), bit 1 clear at (19, 20)
    }

    // endregion

    // region Format information

    @ParameterizedTest(name = "ECC level {0}, pattern {1}")
    @MethodSource("eccLevelsAndPatterns")
    @DisplayName("draws the 15 format bits twice, and nowhere but in the reserved area")
    void drawsFormatInformationTwice(int ecc, int pattern) {
        var scoringMatrix = ScoringMatrix.ofSymbol(codewordsFor(6), 6);
        // The format area is reserved and left light by the fixed patterns, so the modules the
        // drawing changes are exactly the dark format modules.
        var before = scoringMatrix.rows().copy();

        MatrixEncoder.drawFormatInformation(scoringMatrix, ecc, pattern);

        var changed = scoringMatrix.rows().copy();
        changed.xor(before);
        var formatBits = QrCodeParameters.formatInformationBits(ecc, pattern);
        assertThat(changed.popCount()).as("dark format modules")
                .isEqualTo(2 * Integer.bitCount(formatBits));

        var strays = changed.copy();
        strays.and(FixedPatterns.payloadAreaMap(6));
        assertThat(strays.popCount()).as("format modules in the payload area").isZero();
    }

    @Test
    @DisplayName("keeps the transpose in sync while drawing the format information")
    void formatInformationKeepsTheTransposeInSync() {
        var scoringMatrix = ScoringMatrix.ofSymbol(codewordsFor(2), 2);

        MatrixEncoder.drawFormatInformation(scoringMatrix, 2, 5);

        var expected = scoringMatrix.rows().copy();
        TestMatrices.naiveTranspose(expected);
        TestMatrices.assertMatricesEqual(expected, scoringMatrix.columns());
    }

    // endregion

    // region Mask selection

    @ParameterizedTest(name = "pattern {0}")
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7 })
    @DisplayName("applies the mask pattern the caller pins")
    void honoursTheForcedMask(int pattern) {
        var codewords = codewordsFor(4);

        var encoded = MatrixEncoder.encode(codewords, 4, 1, pattern);

        assertThat(encoded.mask()).isEqualTo(pattern);
    }

    @ParameterizedTest(name = "pattern {0}")
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7 })
    @DisplayName("produces the same symbol whether or not the penalty scores are collected")
    void forcedMaskProducesTheSameSymbolWithAndWithoutDiagnostics(int pattern) {
        var codewords = codewordsFor(4);

        var plain = MatrixEncoder.encode(codewords, 4, 1, pattern);
        var scored = MatrixEncoder.encodeWithPenalties(codewords, 4, 1, pattern,
                new PenaltyScore[PATTERN_COUNT]);

        assertThat(scored.mask()).isEqualTo(plain.mask());
        TestMatrices.assertMatricesEqual(plain.modules(), scored.modules());
    }

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 1, 5, 15, 30, 40 })
    @DisplayName("selects the same mask with the early stop as without it")
    void earlyStopSelectsTheSameMask(int version) {
        var codewords = codewordsFor(version);

        var fast = MatrixEncoder.encode(codewords, version, 1, NO_FORCED_MASK);
        var scored = MatrixEncoder.encodeWithPenalties(codewords, version, 1, NO_FORCED_MASK,
                new PenaltyScore[PATTERN_COUNT]);

        assertThat(fast.mask()).isEqualTo(scored.mask());
        TestMatrices.assertMatricesEqual(fast.modules(), scored.modules());
    }

    @Test
    @DisplayName("reports a penalty score for every mask pattern, and picks the lowest")
    void reportsThePenaltyOfEveryPattern() {
        var penalties = new PenaltyScore[PATTERN_COUNT];

        var encoded = MatrixEncoder.encodeWithPenalties(codewordsFor(7), 7, 1, NO_FORCED_MASK, penalties);

        assertThat(penalties).doesNotContainNull();
        assertThat(penalties[encoded.mask()].total())
                .isEqualTo(Arrays.stream(penalties).mapToInt(PenaltyScore::total).min().orElseThrow());
    }

    // endregion

    static Stream<Arguments> sampleMasks() {
        return Arrays.stream(VerifiedData.SAMPLE_VERSIONS).boxed()
                .flatMap(version -> IntStream.range(0, PATTERN_COUNT)
                        .mapToObj(pattern -> arguments(version, pattern)));
    }

    static Stream<Arguments> eccLevelsAndPatterns() {
        return IntStream.range(0, 4).boxed()
                .flatMap(ecc -> IntStream.range(0, PATTERN_COUNT).mapToObj(pattern -> arguments(ecc, pattern)));
    }
}

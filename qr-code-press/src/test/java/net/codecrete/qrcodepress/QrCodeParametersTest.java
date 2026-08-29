/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.codecrete.qrcodepress.QrCodeParameters.MAX_VERSION;
import static net.codecrete.qrcodepress.QrCodeParameters.MIN_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Pins the transcribed ISO/IEC 18004 tables.
 * <p>
 * The capacity and alignment pattern tables are cross-checked against ZXing, an independent QR code
 * implementation. The format and version information tables are lookup tables in both libraries, so
 * they are instead cross-checked against the BCH polynomial arithmetic they encode.
 * </p>
 */
class QrCodeParametersTest {

    /** ZXing's error correction levels, in this library's index order (0–3 = L/M/Q/H). */
    private static final ErrorCorrectionLevel[] ZXING_ECC_LEVELS = {
            ErrorCorrectionLevel.L, ErrorCorrectionLevel.M, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.H
    };

    static IntStream versions() {
        return IntStream.rangeClosed(MIN_VERSION, MAX_VERSION);
    }

    static Stream<Arguments> versionsAndEccLevels() {
        return versions().boxed()
                .flatMap(version -> IntStream.range(0, ZXING_ECC_LEVELS.length)
                        .mapToObj(ecc -> arguments(version, ecc)));
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("versions")
    @DisplayName("size matches ZXing")
    void sizeMatchesZxing(int version) {
        var expected = Version.getVersionForNumber(version).getDimensionForVersion();

        assertThat(QrCodeParameters.size(version)).isEqualTo(expected);
    }

    @Test
    @DisplayName("size spans 21 to 177 modules")
    void sizeSpansStandardRange() {
        assertThat(QrCodeParameters.size(MIN_VERSION)).isEqualTo(21);
        assertThat(QrCodeParameters.size(MAX_VERSION)).isEqualTo(177);
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("versions")
    @DisplayName("total codeword capacity matches ZXing")
    void codewordCapacityMatchesZxing(int version) {
        var expected = Version.getVersionForNumber(version).getTotalCodewords();

        assertThat(QrCodeParameters.codewordCapacity(version)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "version {0}, ECC level {1}")
    @MethodSource("versionsAndEccLevels")
    @DisplayName("data codeword capacity matches ZXing")
    void dataCodewordCapacityMatchesZxing(int version, int ecc) {
        var expected = Arrays.stream(zxingBlocks(version, ecc).getECBlocks())
                .mapToInt(block -> block.getCount() * block.getDataCodewords())
                .sum();

        assertThat(QrCodeParameters.dataCodewordCapacity(version, ecc)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "version {0}, ECC level {1}")
    @MethodSource("versionsAndEccLevels")
    @DisplayName("block count matches ZXing")
    void blockCountMatchesZxing(int version, int ecc) {
        var expected = zxingBlocks(version, ecc).getNumBlocks();

        assertThat(QrCodeParameters.blockCount(version, ecc)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "version {0}, ECC level {1}")
    @MethodSource("versionsAndEccLevels")
    @DisplayName("error correction codewords split evenly across the blocks")
    void errorCorrectionCodewordsSplitEvenly(int version, int ecc) {
        var blocks = QrCodeParameters.blockCount(version, ecc);
        var eccCodewords = QrCodeParameters.codewordCapacity(version)
                - QrCodeParameters.dataCodewordCapacity(version, ecc);

        assertThat(eccCodewords % blocks).isZero();
        assertThat(eccCodewords / blocks).isEqualTo(zxingBlocks(version, ecc).getECCodewordsPerBlock());
    }

    @ParameterizedTest(name = "version {0}, ECC level {1}")
    @MethodSource("versionsAndEccLevels")
    @DisplayName("data codewords split into ZXing's block lengths")
    void dataCodewordsSplitIntoZxingBlockLengths(int version, int ecc) {
        var blocks = QrCodeParameters.blockCount(version, ecc);
        var dataCodewords = QrCodeParameters.dataCodewordCapacity(version, ecc);

        // the short blocks come first, the remainder is spread one codeword each over the long blocks
        var shortBlockLength = dataCodewords / blocks;
        var longBlocks = dataCodewords % blocks;
        var actual = new int[blocks];
        Arrays.fill(actual, 0, blocks - longBlocks, shortBlockLength);
        Arrays.fill(actual, blocks - longBlocks, blocks, shortBlockLength + 1);

        assertThat(actual).containsExactly(zxingBlockLengths(version, ecc));
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("versions")
    @DisplayName("alignment pattern positions match ZXing")
    void alignmentPatternPositionsMatchZxing(int version) {
        var expected = Version.getVersionForNumber(version).getAlignmentPatternCenters();

        assertThat(QrCodeParameters.alignmentPatternPositions(version)).containsExactly(expected);
    }

    @Test
    @DisplayName("version 1 has no alignment patterns")
    void version1HasNoAlignmentPatterns() {
        assertThat(QrCodeParameters.alignmentPatternPositions(1)).isEmpty();
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("versions")
    @DisplayName("alignment pattern positions ascend from 6 to the far finder pattern")
    void alignmentPatternPositionsAscendWithinTheSymbol(int version) {
        var positions = QrCodeParameters.alignmentPatternPositions(version);
        if (positions.length == 0) {
            return;
        }

        assertThat(positions).isSorted();
        assertThat(positions[0]).isEqualTo(6);
        assertThat(positions[positions.length - 1]).isEqualTo(QrCodeParameters.size(version) - 7);
    }

    static IntStream eccLevels() {
        return IntStream.range(0, ZXING_ECC_LEVELS.length);
    }

    @ParameterizedTest(name = "ECC level {0}, mask {1}")
    @MethodSource("eccLevelsAndMasks")
    @DisplayName("format information matches the BCH(15, 5) code over ZXing's level indicator")
    void formatInformationBitsMatchBchCode(int ecc, int pattern) {
        // The 5 data bits are the error correction level indicator followed by the mask pattern,
        // so taking the indicator from ZXing checks the level order of the table as well as its
        // code words.
        var data = (ZXING_ECC_LEVELS[ecc].getBits() << 3) | pattern;
        var expected = (data << 10 | bchRemainder(data, 10, 0x537)) ^ 0x5412;

        assertThat(QrCodeParameters.formatInformationBits(ecc, pattern)).isEqualTo(expected);
    }

    static Stream<Arguments> eccLevelsAndMasks() {
        return eccLevels().boxed()
                .flatMap(ecc -> IntStream.range(0, 8).mapToObj(pattern -> arguments(ecc, pattern)));
    }

    @Test
    @DisplayName("the 32 format information values are pairwise distinct")
    void formatInformationBitsAreDistinct() {
        var values = eccLevelsAndMasks()
                .mapToInt(arguments -> QrCodeParameters.formatInformationBits(
                        (Integer) arguments.get()[0], (Integer) arguments.get()[1]))
                .toArray();

        assertThat(values).hasSize(32).doesNotHaveDuplicates();
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("versionsWithVersionInformation")
    @DisplayName("version information matches the BCH(18, 6) code")
    void versionInformationBitsMatchBchCode(int version) {
        var expected = version << 12 | bchRemainder(version, 12, 0x1f25);

        assertThat(QrCodeParameters.versionInformationBits(version)).isEqualTo(expected);
    }

    static IntStream versionsWithVersionInformation() {
        return IntStream.rangeClosed(7, MAX_VERSION);
    }

    private static Version.ECBlocks zxingBlocks(int version, int ecc) {
        return Version.getVersionForNumber(version).getECBlocksForLevel(ZXING_ECC_LEVELS[ecc]);
    }

    private static int[] zxingBlockLengths(int version, int ecc) {
        return Arrays.stream(zxingBlocks(version, ecc).getECBlocks())
                .flatMapToInt(block -> IntStream.generate(block::getDataCodewords).limit(block.getCount()))
                .toArray();
    }

    /**
     * Computes the remainder of the polynomial division used by the BCH codes of the specification.
     *
     * @param data      the data bits
     * @param bits      the number of remainder bits
     * @param generator the generator polynomial
     * @return the remainder
     */
    private static int bchRemainder(int data, int bits, int generator) {
        var remainder = data;
        for (var i = 0; i < bits; i += 1) {
            remainder = (remainder << 1) ^ ((remainder >>> (bits - 1)) * generator);
        }
        return remainder;
    }
}

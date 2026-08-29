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
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class FixedPatternsTest {

    // region Verified data

    /** One row of the fixed-pattern fixture per version: hashes and population counts. */
    static Stream<Arguments> fixedPatternCases() {
        return VerifiedData.readTsv("fixedpatterns.tsv").stream()
                .map(row -> arguments(Integer.parseInt(row[0]), Integer.parseInt(row[1]),
                        row[2], row[3], row[4],
                        Integer.parseInt(row[5]), Integer.parseInt(row[6]), Integer.parseInt(row[7])));
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("fixedPatternCases")
    @DisplayName("fixed patterns match the verified data")
    void matchesVerifiedData(int version, int size, String drawnSha, String reservedSha, String payloadAreaSha,
                             int drawnPopCount, int reservedPopCount, int payloadAreaPopCount) {
        var patterns = FixedPatterns.build(version);
        var payloadArea = FixedPatterns.payloadAreaMap(version);

        assertThat(patterns.drawn().size()).as("size").isEqualTo(size);
        assertThat(TestMatrices.sha256(patterns.drawn())).as("drawn").isEqualTo(drawnSha);
        assertThat(TestMatrices.sha256(patterns.reserved())).as("reserved").isEqualTo(reservedSha);
        assertThat(TestMatrices.sha256(payloadArea)).as("payload area").isEqualTo(payloadAreaSha);

        assertThat(patterns.drawn().popCount()).as("drawn modules").isEqualTo(drawnPopCount);
        assertThat(patterns.reserved().popCount()).as("reserved modules").isEqualTo(reservedPopCount);
        assertThat(payloadArea.popCount()).as("payload modules").isEqualTo(payloadAreaPopCount);
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("sampleVersions")
    @DisplayName("placed modules match verified data")
    void matchesModuleByModule(int version) {
        var patterns = FixedPatterns.build(version);

        assertThat(TestMatrices.rowsOf(patterns.drawn())).as("drawn")
                .isEqualTo(dump(version, "drawn"));
        assertThat(TestMatrices.rowsOf(patterns.reserved())).as("reserved")
                .isEqualTo(dump(version, "reserved"));
        assertThat(TestMatrices.rowsOf(FixedPatterns.payloadAreaMap(version))).as("payload area")
                .isEqualTo(dump(version, "payloadarea"));
    }

    // endregion

    // region Invariants

    @ParameterizedTest(name = "version {0}")
    @MethodSource("net.codecrete.qrcodepress.VerifiedData#allVersions")
    @DisplayName("draws no module outside the reserved area")
    void drawsOnlyInsideTheReservedArea(int version) {
        var patterns = FixedPatterns.build(version);

        // drawn AND NOT reserved must be empty
        var reserved = patterns.reserved();
        reserved.invert();
        var strays = patterns.drawn();
        strays.and(reserved);

        assertThat(strays.popCount()).isZero();
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("net.codecrete.qrcodepress.VerifiedData#allVersions")
    @DisplayName("the payload area is exactly the complement of the reserved area")
    void payloadAreaComplementsTheReservedArea(int version) {
        var reserved = FixedPatterns.build(version).reserved();

        reserved.invert();

        TestMatrices.assertMatricesEqual(reserved, FixedPatterns.payloadAreaMap(version));
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("net.codecrete.qrcodepress.VerifiedData#allVersions")
    @DisplayName("the payload area holds the codewords plus at most 7 remainder bits")
    void payloadAreaFitsTheCodewords(int version) {
        var payloadBits = FixedPatterns.payloadAreaMap(version).popCount();

        var codewordBits = 8 * QrCodeParameters.codewordCapacity(version);
        assertThat(payloadBits).isBetween(codewordBits, codewordBits + 7);
    }

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 1, 7, 40 })
    @DisplayName("hands out a fresh matrix that the caller may mutate")
    void createMatrixReturnsAFreshCopy(int version) {
        var first = FixedPatterns.createMatrix(version);
        var expected = TestMatrices.rowsOf(first);

        first.invert();

        assertThat(TestMatrices.rowsOf(FixedPatterns.createMatrix(version))).isEqualTo(expected);
    }

    @Test
    @DisplayName("hands out the same shared payload area map every time")
    void payloadAreaMapIsCached() {
        assertThat(FixedPatterns.payloadAreaMap(12)).isSameAs(FixedPatterns.payloadAreaMap(12));
    }

    // endregion

    // region Geometry spot checks

    @Test
    @DisplayName("reserves the format information area even where its modules are light")
    void reservesTheFormatInformationArea() {
        var patterns = FixedPatterns.build(1);

        // The format information is drawn later, so none of its modules is dark yet. Module (8, 2)
        // sits in the vertical strip beside the top left finder pattern.
        assertThat(patterns.reserved().get(8, 2)).as("reserved").isTrue();
        assertThat(patterns.drawn().get(8, 2)).as("drawn").isFalse();
    }

    @ParameterizedTest(name = "version {0}")
    @MethodSource("net.codecrete.qrcodepress.VerifiedData#allVersions")
    @DisplayName("draws the asymmetric dark module")
    void drawsTheDarkModule(int version) {
        var drawn = FixedPatterns.build(version).drawn();

        assertThat(drawn.get(8, QrCodeParameters.size(version) - 8)).isTrue();
    }

    @Test
    @DisplayName("draws the alignment patterns as a dark ring around a dark centre")
    void drawsAlignmentPatterns() {
        // Version 7 places one alignment pattern at (22, 22), clear of every other fixed pattern.
        var drawn = FixedPatterns.build(7).drawn();

        var pattern = Stream.of(20, 21, 22, 23, 24)
                .map(y -> IntStream.rangeClosed(20, 24)
                        .mapToObj(x -> drawn.get(x, y) ? "#" : ".")
                        .reduce("", String::concat))
                .toList();

        assertThat(pattern).containsExactly("#####", "#...#", "#.#.#", "#...#", "#####");
    }

    @Test
    @DisplayName("carries version information from version 7 on, and none below")
    void carriesVersionInformationFromVersionSeven() {
        assertThat(FixedPatterns.build(6).reserved().get(0, QrCodeParameters.size(6) - 11)).isFalse();
        assertThat(FixedPatterns.build(7).reserved().get(0, QrCodeParameters.size(7) - 11)).isTrue();
    }

    @Test
    @DisplayName("places no alignment pattern in version 1")
    void placesNoAlignmentPatternInVersionOne() {
        // Version 1 is nothing but the three finders, the timing patterns and the dark module, so
        // the reserved area is what those add up to.
        var reserved = FixedPatterns.build(1).reserved();

        assertThat(reserved.get(10, 10)).isFalse();
    }

    // endregion

    static Stream<Arguments> sampleVersions() {
        return Arrays.stream(VerifiedData.SAMPLE_VERSIONS).mapToObj(Arguments::arguments);
    }

    private static List<String> dump(int version, String view) {
        return VerifiedData.read(String.format("fixedpatterns/v%02d-%s.txt", version, view)).lines().toList();
    }
}

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
import java.util.stream.Stream;

import static net.codecrete.qrcodepress.QrCodeParameters.MAX_VERSION;
import static net.codecrete.qrcodepress.QrCodeParameters.MIN_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class VersionPlannerTest {

    // region Verified data

    static Stream<Arguments> qrCodeCases() {
        return VerifiedData.qrCodeCases().stream().map(c -> arguments(c.index(), c));
    }

    @ParameterizedTest(name = "case {0}")
    @MethodSource("qrCodeCases")
    @DisplayName("version and error correction level match the verified data")
    void matchesVersionAndEcc(int caseIndex, VerifiedData.QrCodeCase testCase) {
        var segments = VerifiedData.segments(testCase.textIndex());

        var plan = VersionPlanner.plan(segments, testCase.requestedEcc(),
                testCase.minVersion(), testCase.maxVersion(), testCase.boostEcc());

        assertThat(plan).isEqualTo(
                new VersionPlanner.Plan(testCase.effectiveVersion(), testCase.effectiveEcc()));
    }

    // endregion

    // region Version selection

    @ParameterizedTest(name = "ECC level {0}")
    @ValueSource(ints = { 0, 1, 2, 3 })
    @DisplayName("chooses the smallest version the segments fit into, for every payload length")
    void choosesSmallestFittingVersion(int ecc) {
        // Every payload length is swept, so the versions where the character count indicator
        // widens are crossed with the data ending on either side of the boundary.
        for (var length = 0; length <= maxPayloadLength(ecc); length += 1) {
            var segments = binarySegments(length);

            var plan = VersionPlanner.plan(segments, ecc, MIN_VERSION, MAX_VERSION, false);

            assertThat(plan.version()).as("%d bytes at ECC level %d", length, ecc)
                    .isEqualTo(smallestFittingVersion(segments, ecc));
        }
    }

    /**
     * Finds the smallest fitting version by measuring the segments at every version, which is what
     * the planner avoids by recomputing only where the character count indicators widen.
     */
    private static int smallestFittingVersion(List<DataSegment> segments, int ecc) {
        for (var version = MIN_VERSION; version <= MAX_VERSION; version += 1) {
            if (fits(segments, version, ecc))
                return version;
        }
        throw new AssertionError("the payload was expected to fit");
    }

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 1, 9, 10, 26, 27, 40 })
    @DisplayName("uses the pinned version even where a smaller one would do")
    void usesThePinnedVersion(int version) {
        var plan = VersionPlanner.plan(binarySegments(1), 0, version, version, false);

        assertThat(plan.version()).isEqualTo(version);
    }

    @Test
    @DisplayName("starts the search at the smallest acceptable version")
    void startsAtTheMinimumVersion() {
        var segments = binarySegments(1);

        assertThat(VersionPlanner.plan(segments, 0, 15, 40, false).version()).isEqualTo(15);
        assertThat(VersionPlanner.plan(segments, 0, 1, 40, false).version()).isEqualTo(1);
    }

    // endregion

    // region Error correction level

    @ParameterizedTest(name = "ECC level {0}")
    @ValueSource(ints = { 0, 1, 2, 3 })
    @DisplayName("boosts the error correction level as far as the chosen version allows")
    void boostsEccWithinTheChosenVersion(int ecc) {
        for (var length = 0; length <= maxPayloadLength(ecc); length += 1) {
            var segments = binarySegments(length);
            var unboosted = VersionPlanner.plan(segments, ecc, MIN_VERSION, MAX_VERSION, false);

            var plan = VersionPlanner.plan(segments, ecc, MIN_VERSION, MAX_VERSION, true);

            var where = String.format("%d bytes at ECC level %d", length, ecc);
            assertThat(plan.version()).as("%s: boosting never grows the version", where)
                    .isEqualTo(unboosted.version());
            assertThat(plan.ecc()).as("%s: boosting never lowers the level", where)
                    .isBetween(ecc, 3);
            assertThat(fits(segments, plan.version(), plan.ecc()))
                    .as("%s: the boosted level still fits", where).isTrue();
            if (plan.ecc() < 3)
                assertThat(fits(segments, plan.version(), plan.ecc() + 1))
                        .as("%s: the next level does not fit", where).isFalse();
        }
    }

    @ParameterizedTest(name = "ECC level {0}")
    @ValueSource(ints = { 0, 1, 2, 3 })
    @DisplayName("keeps the requested error correction level when not boosting")
    void keepsTheRequestedEcc(int ecc) {
        var plan = VersionPlanner.plan(binarySegments(1), ecc, MIN_VERSION, MAX_VERSION, false);

        assertThat(plan.ecc()).isEqualTo(ecc);
    }

    @Test
    @DisplayName("boosts an empty payload to the highest error correction level")
    void boostsEmptyPayloadToTheHighestEcc() {
        var plan = VersionPlanner.plan(List.of(), 0, MIN_VERSION, MAX_VERSION, true);

        assertThat(plan).isEqualTo(new VersionPlanner.Plan(1, 3));
    }

    // endregion

    // region Data too long

    @ParameterizedTest(name = "version {0}, ECC level {1}")
    @CsvSource({
            "1, 0, 'version 1 and error correction level L'",
            "5, 2, 'version 5 and error correction level Q'",
            "39, 3, 'version 39 and error correction level H'"
    })
    @DisplayName("names the version the range was narrowed to when the data does not fit")
    void reportsTheNarrowedVersion(int maxVersion, int ecc, String expected) {
        var segments = binarySegments(QrCodeParameters.dataCodewordCapacity(maxVersion, ecc));

        assertThatExceptionOfType(DataTooLongException.class)
                .isThrownBy(() -> VersionPlanner.plan(segments, ecc, MIN_VERSION, maxVersion, false))
                .withMessageContaining(expected);
    }

    @ParameterizedTest(name = "ECC level {0}")
    @CsvSource({ "0, L", "1, M", "2, Q", "3, H" })
    @DisplayName("names only the error correction level when not even version 40 suffices")
    void reportsTheEccLevelAlone(int ecc, String level) {
        var segments = binarySegments(maxPayloadLength(ecc) + 1);

        assertThatExceptionOfType(DataTooLongException.class)
                .isThrownBy(() -> VersionPlanner.plan(segments, ecc, MIN_VERSION, MAX_VERSION, false))
                .withMessage("Data is too long to fit into a QR code with error correction level "
                        + level + ".")
                .withMessageNotContaining("version");
    }

    @Test
    @DisplayName("does not fall back to a lower error correction level")
    void doesNotLowerTheEccToMakeTheDataFit() {
        // The payload fits into version 1 at ECC level L, but the caller asked for H.
        var segments = binarySegments(QrCodeParameters.dataCodewordCapacity(1, 3));

        assertThatExceptionOfType(DataTooLongException.class)
                .isThrownBy(() -> VersionPlanner.plan(segments, 3, 1, 1, true));
    }

    // endregion

    /** Indicates whether the segments fit into a QR code of the version and error correction level. */
    private static boolean fits(List<DataSegment> segments, int version, int ecc) {
        return DataSegment.totalLength(segments, version)
                <= 8 * QrCodeParameters.dataCodewordCapacity(version, ecc);
    }

    /** The longest binary payload a version 40 QR code holds at the error correction level. */
    private static int maxPayloadLength(int ecc) {
        // The header is the 4-bit mode indicator plus the 16-bit character count indicator.
        return (8 * QrCodeParameters.dataCodewordCapacity(MAX_VERSION, ecc) - 20) / 8;
    }

    private static List<DataSegment> binarySegments(int payloadLength) {
        return payloadLength == 0
                ? List.of()
                : List.of(DataSegment.of(DataSegmentMode.BINARY, new byte[payloadLength]));
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end-to-end characterization test: every one of the 112 exported cases is run through the
 * public API and compared, module by module, with the verified test data.
 * <p>
 * This is the test that proves bit-identical output. Where the per-stage fixtures pin a stage in
 * isolation, this one pins the composition of all of them: version planning, codewords, error
 * correction, the fixed patterns, the payload zigzag, mask selection and the format information.
 * </p>
 * <p>
 * Many cases are driven from their segments so it's easier to test cases that would not
 * result from compaction; {@link #matchesVerifiedDataFromText} additionally drives the whole text path.
 * </p>
 */
class VerifiedDataTest {

    static List<VerifiedData.QrCodeCase> cases() {
        return VerifiedData.qrCodeCases();
    }

    /**
     * Builds the QR code of a case the way the exporter did.
     *
     * @param testCase   the case
     * @param forcedMask the mask pattern to pin, or -1 to let the encoder select one
     * @return the QR code
     */
    private static QrCode encode(VerifiedData.QrCodeCase testCase, int forcedMask) {
        var builder = QrCode.builder()
                .segments(VerifiedData.segments(testCase.textIndex()))
                .errorCorrection(Ecc.of(testCase.requestedEcc()))
                .versionRange(testCase.minVersion(), testCase.maxVersion())
                .boostErrorCorrection(testCase.boostEcc());
        if (forcedMask >= 0)
            builder.forceMask(forcedMask);
        return builder.build();
    }

    @ParameterizedTest(name = "case {0}")
    @MethodSource("cases")
    @DisplayName("QR code matches verified data")
    void qrCodeVerifiedData(VerifiedData.QrCodeCase testCase) {
        var qrCode = encode(testCase, -1);

        assertThat(qrCode.getVersion()).as("version").isEqualTo(testCase.effectiveVersion());
        assertThat(qrCode.getErrorCorrectionLevel()).as("error correction level")
                .isEqualTo(Ecc.of(testCase.effectiveEcc()));
        assertThat(qrCode.getMask()).as("mask pattern").isEqualTo(testCase.effectiveMask());
        assertThat(TestMatrices.rowsOf(qrCode)).as("modules")
                .isEqualTo(VerifiedData.moduleRows(testCase.index()));
    }

    @ParameterizedTest(name = "case {0}")
    @MethodSource("cases")
    @DisplayName("QR code matches verified data, build from the text alone")
    void matchesVerifiedDataFromText(VerifiedData.QrCodeCase testCase) {
        // The text path builds its segments for the largest acceptable version, whereas the
        // exporter built them for the default version (20). That is the same
        // segmentation for every one of these texts — where it were not, the character count
        // indicators would differ in width and this test would say so.
        var qrCode = QrCode.builder()
                .text(VerifiedData.text(testCase.textIndex()))
                .eci(VerifiedData.eci(testCase.textIndex()))
                .errorCorrection(Ecc.of(testCase.requestedEcc()))
                .versionRange(testCase.minVersion(), testCase.maxVersion())
                .boostErrorCorrection(testCase.boostEcc())
                .build();

        assertThat(qrCode.getVersion()).as("version").isEqualTo(testCase.effectiveVersion());
        assertThat(qrCode.getErrorCorrectionLevel()).as("error correction level")
                .isEqualTo(Ecc.of(testCase.effectiveEcc()));
        assertThat(qrCode.getMask()).as("mask pattern").isEqualTo(testCase.effectiveMask());
        assertThat(TestMatrices.rowsOf(qrCode)).as("modules")
                .isEqualTo(VerifiedData.moduleRows(testCase.index()));
    }

    @ParameterizedTest(name = "case {0}")
    @MethodSource("cases")
    @DisplayName("produces the same QR code when the mask is pinned")
    void pinningTheMaskChangesNothing(VerifiedData.QrCodeCase testCase) {
        // Skipping mask selection must land on exactly the same symbol as running it, which is the
        // shortcut the encoder takes when a mask is pinned and no penalty scores are wanted.
        var qrCode = encode(testCase, testCase.effectiveMask());

        assertThat(TestMatrices.rowsOf(qrCode)).isEqualTo(VerifiedData.moduleRows(testCase.index()));
    }

    @ParameterizedTest(name = "case {0}")
    @MethodSource("cases")
    @DisplayName("produces the same QR code when the penalty scores are collected")
    void collectingDiagnosticsChangesNothing(VerifiedData.QrCodeCase testCase) {
        // Collecting the scores turns off the early stop in the penalty calculation, so this pins
        // that the bound only ever discards patterns that were going to lose anyway.
        var info = QrCode.builder()
                .segments(VerifiedData.segments(testCase.textIndex()))
                .errorCorrection(Ecc.of(testCase.requestedEcc()))
                .versionRange(testCase.minVersion(), testCase.maxVersion())
                .boostErrorCorrection(testCase.boostEcc())
                .buildWithDiagnostics();

        assertThat(info.qrCode().getMask()).as("mask pattern").isEqualTo(testCase.effectiveMask());
        assertThat(TestMatrices.rowsOf(info.qrCode())).as("modules")
                .isEqualTo(VerifiedData.moduleRows(testCase.index()));
        assertThat(info.segments()).as("segments")
                .isEqualTo(VerifiedData.segments(testCase.textIndex()));
        assertThat(info.penalties()).as("penalties").hasSize(8).doesNotContainNull();

        var lowest = info.penalties().stream().mapToInt(PenaltyScore::total).min().orElseThrow();
        assertThat(info.penalties().get(info.qrCode().getMask()).total())
                .as("penalty of the applied mask pattern").isEqualTo(lowest);
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Decodes the generated QR codes with ZXing, an independent implementation.
 * <p>
 * Having a decoder that shares no code with this library agreeing the result is correct
 * is an additional valuable test. Requiring that no error correction was needed makes the
 * assertion strict: every single module has to be right, not just enough of them to recover the
 * payload.
 * </p>
 */
class ZxingRoundTripTest {

    static List<VerifiedData.QrCodeCase> cases() {
        return VerifiedData.qrCodeCases();
    }

    @ParameterizedTest(name = "case {0}")
    @MethodSource("cases")
    @DisplayName("decodes to the payload text without correcting a single error")
    void decodesWithoutErrors(VerifiedData.QrCodeCase testCase) throws ChecksumException, FormatException {
        var segments = VerifiedData.segments(testCase.textIndex());
        var qrCode = QrCode.builder()
                .segments(segments)
                .errorCorrection(Ecc.of(testCase.requestedEcc()))
                .versionRange(testCase.minVersion(), testCase.maxVersion())
                .boostErrorCorrection(testCase.boostEcc())
                .build();

        var result = ZxingSupport.decode(ZxingSupport.toBitMatrix(qrCode));

        assertThat(result.getText()).as("decoded text").isEqualTo(VerifiedData.text(testCase.textIndex()));
        assertThat(result.getErrorsCorrected()).as("errors corrected").isZero();

        // The raw bytes are the data codewords ZXing read back out of the symbol, so this pins the
        // padding and the interleaving as well as the payload.
        assertThat(result.getRawBytes()).as("data codewords").isEqualTo(
                Codewords.buildData(segments, qrCode.getVersion(),
                        qrCode.getErrorCorrectionLevel().ordinal()));
    }

    @Test
    @DisplayName("reports a corrected error when a module is flipped")
    void flippedModuleIsCorrected() throws ChecksumException, FormatException {
        // The negative control for `decodesWithoutErrors`: if flipping a module left the error
        // count at zero, the assertion above would be worthless.
        var qrCode = QrCode.builder().text("Hello, world!").errorCorrection(Ecc.QUARTILE).build();
        var bits = ZxingSupport.toBitMatrix(qrCode);
        bits.flip(qrCode.getSize() - 1, qrCode.getSize() - 1); // a data module, clear of the finder patterns

        var result = ZxingSupport.decode(bits);

        assertThat(result.getText()).isEqualTo("Hello, world!");
        assertThat(result.getErrorsCorrected()).isPositive();
    }

    @Test
    @DisplayName("fails to decode when the error correction data is overwhelmed")
    void heavilyDamagedCodeDoesNotDecode() {
        // The other end of the control: damage beyond what the error correction level covers has
        // to be detected rather than silently decoded into something else.
        var qrCode = QrCode.builder().text("Hello, world!").errorCorrection(Ecc.LOW).build();
        var bits = ZxingSupport.toBitMatrix(qrCode);
        for (var y = qrCode.getSize() - 10; y < qrCode.getSize(); y += 1) {
            for (var x = qrCode.getSize() - 10; x < qrCode.getSize(); x += 1)
                bits.flip(x, y);
        }

        assertThatExceptionOfType(ChecksumException.class).isThrownBy(() -> ZxingSupport.decode(bits));
    }
}

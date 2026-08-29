/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * The public entry points and accessors of {@link QrCode}.
 * <p>
 * That the encoded symbols are correct is the verified suite's job; this test covers the surface the
 * caller sees: the factory methods, the accessors, and the errors for arguments that make no sense.
 * </p>
 */
class QrCodeTest {

    @Nested
    @DisplayName("factory methods")
    class Factories {

        @Test
        @DisplayName("encodeText encodes the text in the smallest QR code")
        void encodeText() {
            var qrCode = QrCode.encodeText("Hello, world!", Ecc.LOW);

            assertThat(qrCode.getVersion()).isEqualTo(1);
            assertThat(qrCode.getSize()).isEqualTo(21);
            // version 1 has room to spare for these 13 characters, which boosting spends on ECC
            assertThat(qrCode.getErrorCorrectionLevel()).isEqualTo(Ecc.MEDIUM);
        }

        @ParameterizedTest
        @EnumSource(Ecc.class)
        @DisplayName("encodeText never falls below the requested error correction level")
        void encodeTextRespectsTheMinimumLevel(Ecc errorCorrection) {
            var qrCode = QrCode.encodeText("The quick brown fox jumps over the lazy dog", errorCorrection);

            assertThat(qrCode.getErrorCorrectionLevel()).isGreaterThanOrEqualTo(errorCorrection);
        }

        @Test
        @DisplayName("encodeBinary announces binary data with an ECI segment")
        void encodeBinary() {
            var data = new byte[] { 0x00, (byte) 0xff, 0x42 };

            var info = QrCode.builder().binary(data).errorCorrection(Ecc.LOW).buildWithDiagnostics();

            assertThat(info.segments()).first()
                    .isInstanceOfSatisfying(DataSegmentEci.class,
                            segment -> assertThat(segment.designator()).isEqualTo(Eci.BINARY_DATA));
            assertThat(QrCode.encodeBinary(data, Ecc.LOW).getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("encodeSegments encodes hand-built segments")
        void encodeSegments() {
            var segments = List.of(
                    DataSegment.of(DataSegmentMode.NUMERIC, "0123456789".getBytes(StandardCharsets.US_ASCII)));

            var qrCode = QrCode.encodeSegments(segments, Ecc.HIGH);

            assertThat(qrCode.getVersion()).isEqualTo(1);
            assertThat(qrCode.getErrorCorrectionLevel()).isEqualTo(Ecc.HIGH);
        }

        @Test
        @DisplayName("rejects null arguments")
        void rejectsNull() {
            assertThatNullPointerException().isThrownBy(() -> QrCode.encodeText(null, Ecc.LOW));
            assertThatNullPointerException().isThrownBy(() -> QrCode.encodeText("A", null));
            assertThatNullPointerException().isThrownBy(() -> QrCode.encodeBinary(null, Ecc.LOW));
            assertThatNullPointerException().isThrownBy(() -> QrCode.encodeSegments(null, Ecc.LOW));
        }

        @Test
        @DisplayName("reports data that does not fit into any QR code")
        void reportsDataTooLong() {
            var data = new byte[3000]; // beyond the 2953 bytes of version 40 at the lowest level

            assertThatExceptionOfType(DataTooLongException.class)
                    .isThrownBy(() -> QrCode.encodeBinary(data, Ecc.LOW))
                    .withMessageContaining("error correction level L");
        }

        @Test
        @DisplayName("encodes an empty payload")
        void encodesEmptyText() {
            var qrCode = QrCode.encodeText("", Ecc.LOW);

            assertThat(qrCode.getVersion()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("accessors")
    class Accessors {

        private final QrCode qrCode = QrCode.builder()
                .text("Hello, world!").errorCorrection(Ecc.MEDIUM).version(3).forceMask(5).build();

        @Test
        @DisplayName("report the parameters the QR code was built with")
        void reportParameters() {
            assertThat(qrCode.getVersion()).isEqualTo(3);
            assertThat(qrCode.getSize()).isEqualTo(3 * 4 + 17);
            assertThat(qrCode.getMask()).isEqualTo(5);
            assertThat(qrCode.getErrorCorrectionLevel()).isEqualTo(Ecc.HIGH); // boosted from MEDIUM
        }

        @Test
        @DisplayName("getModule reports the top left finder pattern")
        void getModuleReportsTheFinderPattern() {
            // The finder pattern is fixed, so it is the one part of any QR code that is known
            // without decoding it: a 7x7 ring of dark modules around a 3x3 dark core.
            assertThat(qrCode.getModule(0, 0)).isTrue();
            assertThat(qrCode.getModule(1, 1)).isFalse();
            assertThat(qrCode.getModule(3, 3)).isTrue();
            assertThat(qrCode.getModule(7, 7)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(ints = { -1, 29 })
        @DisplayName("getModule reports coordinates outside the QR code as light")
        void getModuleIsLightOutside(int coordinate) {
            assertThat(qrCode.getModule(coordinate, 0)).isFalse();
            assertThat(qrCode.getModule(0, coordinate)).isFalse();
        }

        @Test
        @DisplayName("toString names the parameters")
        void toStringNamesTheParameters() {
            assertThat(qrCode)
                    .hasToString("QrCode[version=3, errorCorrectionLevel=HIGH, mask=5]");
        }
    }

    @Test
    @DisplayName("the version constants match the specification")
    void versionConstants() {
        assertThat(QrCode.MIN_VERSION).isEqualTo(1);
        assertThat(QrCode.MAX_VERSION).isEqualTo(40);
    }
}

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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static net.codecrete.qrcodepress.DataSegmentMode.ALPHANUMERIC;
import static net.codecrete.qrcodepress.DataSegmentMode.BINARY;
import static net.codecrete.qrcodepress.DataSegmentMode.ECI;
import static net.codecrete.qrcodepress.DataSegmentMode.KANJI;
import static net.codecrete.qrcodepress.DataSegmentMode.NUMERIC;
import static net.codecrete.qrcodepress.DataSegmentMode.STRUCTURED_APPEND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class DataSegmentModeTest {

    @ParameterizedTest
    @CsvSource({
            "NUMERIC, 1",
            "ALPHANUMERIC, 2",
            "STRUCTURED_APPEND, 3",
            "BINARY, 4",
            "ECI, 7",
            "KANJI, 8"
    })
    @DisplayName("the mode indicators are the ones of the specification")
    void modeIndicators(DataSegmentMode mode, int expected) {
        assertThat(mode.modeIndicator()).isEqualTo(expected);
        assertThat(mode.modeIndicator()).isBetween(0, 15);
    }

    @ParameterizedTest
    @CsvSource({
            "NUMERIC, true",
            "ALPHANUMERIC, true",
            "KANJI, true",
            "BINARY, true",
            "ECI, false",
            "STRUCTURED_APPEND, false"
    })
    @DisplayName("only the data modes carry data")
    void dataModes(DataSegmentMode mode, boolean expected) {
        assertThat(mode.isDataMode()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            // character count indicator widths per version group (1-9, 10-26, 27-40)
            "NUMERIC, 1, 10", "NUMERIC, 9, 10", "NUMERIC, 10, 12", "NUMERIC, 26, 12",
            "NUMERIC, 27, 14", "NUMERIC, 40, 14",
            "ALPHANUMERIC, 1, 9", "ALPHANUMERIC, 9, 9", "ALPHANUMERIC, 10, 11", "ALPHANUMERIC, 26, 11",
            "ALPHANUMERIC, 27, 13", "ALPHANUMERIC, 40, 13",
            "KANJI, 1, 8", "KANJI, 9, 8", "KANJI, 10, 10", "KANJI, 26, 10",
            "KANJI, 27, 12", "KANJI, 40, 12",
            "BINARY, 1, 8", "BINARY, 9, 8", "BINARY, 10, 16", "BINARY, 26, 16",
            "BINARY, 27, 16", "BINARY, 40, 16"
    })
    @DisplayName("the character count indicator widths are the ones of the specification")
    void countIndicatorLengths(DataSegmentMode mode, int version, int expected) {
        assertThat(mode.countIndicatorLength(version)).isEqualTo(expected);
        assertThat(mode.headerLength(version)).isEqualTo(4 + expected);
    }

    @ParameterizedTest
    @EnumSource(value = DataSegmentMode.class, names = { "ECI", "STRUCTURED_APPEND" })
    @DisplayName("the modes without data have no character count indicator")
    void noCountIndicatorWithoutData(DataSegmentMode mode) {
        assertThat(mode.countIndicatorLength(1)).isZero();
        assertThat(mode.countIndicatorLength(40)).isZero();
        assertThat(mode.headerLength(1)).isEqualTo(4);
        assertThat(mode.headerLength(40)).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({
            "NUMERIC, 1, 4", "NUMERIC, 2, 7", "NUMERIC, 3, 10", "NUMERIC, 4, 14",
            "ALPHANUMERIC, 1, 6", "ALPHANUMERIC, 2, 11", "ALPHANUMERIC, 3, 17", "ALPHANUMERIC, 4, 22",
            "KANJI, 2, 13", "KANJI, 4, 26", "KANJI, 6, 39",
            "BINARY, 1, 8", "BINARY, 2, 16", "BINARY, 3, 24"
    })
    @DisplayName("the encoded length follows the bits per character of the mode")
    void encodedBitLengths(DataSegmentMode mode, int dataLength, int expected) {
        assertThat(mode.encodedBitLength(dataLength)).isEqualTo(expected);
        assertThat(mode.segmentLength(dataLength, 12)).isEqualTo(mode.headerLength(12) + expected);
    }

    @ParameterizedTest
    @CsvSource({ "NUMERIC, 9", "ALPHANUMERIC, 5", "KANJI, 4", "BINARY, 3" })
    @DisplayName("counts the bytes fitting into an encoded length")
    void byteCounts(DataSegmentMode mode, int expected) {
        assertThat(mode.byteCount(31)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = DataSegmentMode.class, names = { "NUMERIC", "ALPHANUMERIC", "KANJI", "BINARY" })
    @DisplayName("the byte count is the inverse of the encoded length")
    void byteCountIsInverseOfEncodedBitLength(DataSegmentMode mode) {
        // Kanji encodes pairs of bytes, so its byte count moves in steps of two.
        var step = mode == KANJI ? 2 : 1;

        for (var bitLength = 0; bitLength < 100; bitLength += 1) {
            var byteCount = mode.byteCount(bitLength);

            assertThat(mode.encodedBitLength(byteCount))
                    .as("%d bits hold %d bytes", bitLength, byteCount)
                    .isLessThanOrEqualTo(bitLength);
            assertThat(mode.encodedBitLength(byteCount + step))
                    .as("%d bits do not hold %d bytes", bitLength, byteCount + step)
                    .isGreaterThan(bitLength);
        }
    }

    @ParameterizedTest
    @EnumSource(value = DataSegmentMode.class, names = { "ECI", "STRUCTURED_APPEND" })
    @DisplayName("the modes without data have no data operations")
    void dataOperationsRejectNonDataModes(DataSegmentMode mode) {
        assertThatIllegalArgumentException().isThrownBy(() -> mode.encodedBitLength(4));
        assertThatIllegalArgumentException().isThrownBy(() -> mode.byteCount(32));
        assertThatIllegalArgumentException().isThrownBy(() -> mode.segmentLength(4, 12));
        assertThatIllegalArgumentException().isThrownBy(() -> mode.newSegment(ByteSlice.EMPTY));
        assertThatIllegalArgumentException().isThrownBy(() -> mode.checkEncodable(ByteSlice.EMPTY));
    }

    @Test
    @DisplayName("each data mode checks its own data")
    void checkEncodable() {
        var digits = ByteSlice.of(new byte[] { '4', '2' });
        var letters = ByteSlice.of(new byte[] { 'A', 'b' });

        assertThatNoException().isThrownBy(() -> NUMERIC.checkEncodable(digits));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NUMERIC.checkEncodable(letters))
                .withMessage("Byte 0x41 at index 0 cannot be encoded in NUMERIC mode");

        assertThatNoException().isThrownBy(() -> ALPHANUMERIC.checkEncodable(digits));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ALPHANUMERIC.checkEncodable(letters))
                .withMessage("Byte 0x62 at index 1 cannot be encoded in ALPHANUMERIC mode");

        // binary mode accepts anything, including data no other mode would take
        assertThatNoException().isThrownBy(() -> BINARY.checkEncodable(letters));
        assertThatNoException()
                .isThrownBy(() -> BINARY.checkEncodable(ByteSlice.of(new byte[] { 0, (byte) 0xff })));

        assertThatNoException()
                .isThrownBy(() -> KANJI.checkEncodable(SegmentTestSupport.sliceOfShiftJis("希")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> KANJI.checkEncodable(letters))
                .withMessageContaining("cannot be encoded in KANJI mode");
    }

    @Test
    @DisplayName("creates a segment of the mode without copying the data")
    void createsSegment() {
        var slice = ByteSlice.of(new byte[] { '4', '2' });

        assertThat(NUMERIC.newSegment(slice)).isInstanceOf(DataSegmentNumeric.class);
        assertThat(ALPHANUMERIC.newSegment(slice)).isInstanceOf(DataSegmentAlphanumeric.class);
        assertThat(BINARY.newSegment(slice)).isInstanceOf(DataSegmentBinary.class);
        assertThat(KANJI.newSegment(SegmentTestSupport.sliceOfShiftJis("希")))
                .isInstanceOf(DataSegmentKanji.class);

        assertThat(NUMERIC.newSegment(slice).data()).isSameAs(slice);
    }

    @Test
    @DisplayName("all modes are covered by the tests above")
    void allModesCovered() {
        assertThat(DataSegmentMode.values())
                .containsExactly(NUMERIC, ALPHANUMERIC, KANJI, BINARY, ECI, STRUCTURED_APPEND);
    }
}

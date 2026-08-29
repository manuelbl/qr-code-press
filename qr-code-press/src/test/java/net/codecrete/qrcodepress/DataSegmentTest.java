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

import java.util.List;
import java.util.stream.Stream;

import static net.codecrete.qrcodepress.DataSegmentMode.ALPHANUMERIC;
import static net.codecrete.qrcodepress.DataSegmentMode.KANJI;
import static net.codecrete.qrcodepress.DataSegmentMode.NUMERIC;
import static net.codecrete.qrcodepress.SegmentTestSupport.shiftJis;
import static net.codecrete.qrcodepress.SegmentTestSupport.sliceOfShiftJis;
import static net.codecrete.qrcodepress.SegmentTestSupport.sliceOfUtf8;
import static net.codecrete.qrcodepress.SegmentTestSupport.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DataSegmentTest {

    // region Factory methods

    @ParameterizedTest
    @CsvSource({ "NUMERIC, 12345678", "ALPHANUMERIC, ABCD", "BINARY, abdefghijk" })
    @DisplayName("keeps the data length of the encoded data")
    void dataLength(DataSegmentMode mode, String text) {
        var data = utf8(text);

        var segment = DataSegment.of(mode, data);

        assertThat(segment.getMode()).isEqualTo(mode);
        assertThat(segment.getDataLength()).isEqualTo(data.length);
    }

    @Test
    @DisplayName("keeps the data length of Kanji data")
    void dataLengthKanji() {
        var segment = DataSegment.of(KANJI, SegmentTestSupport.shiftJis("氏サケニイ組品ヱ"));

        assertThat(segment.getDataLength()).isEqualTo(16);
    }

    @Test
    @DisplayName("copies the data, so the caller may modify its array")
    void copiesData() {
        var data = utf8("12345");

        var segment = DataSegment.of(NUMERIC, data);
        data[0] = '9';

        assertThat(SegmentTestSupport.codewords(segment)).containsExactly(0b0001_1110, 0b1101_0110, 0b1000_0000);
    }

    @Test
    @DisplayName("encodes a range of the data")
    void encodesRange() {
        var data = utf8("xx123xx");

        var segment = DataSegment.of(NUMERIC, data, 2, 3);

        assertThat(segment.getDataLength()).isEqualTo(3);
        assertThat(SegmentTestSupport.codewords(segment)).containsExactly(0b0001_1110, 0b1100_0000);
    }

    @Test
    @DisplayName("rejects a range outside the data")
    void rejectsRangeOutsideData() {
        var data = utf8("12345");

        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> DataSegment.of(NUMERIC, data, 3, 4));
    }

    @Test
    @DisplayName("rejects null arguments")
    void rejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> DataSegment.of(NUMERIC, null));
        assertThatNullPointerException().isThrownBy(() -> DataSegment.of(null, utf8("123")));
        assertThatNullPointerException().isThrownBy(() -> DataSegment.ofEci(null));
    }

    @ParameterizedTest
    @CsvSource({ "ECI", "STRUCTURED_APPEND" })
    @DisplayName("rejects the modes that carry no data")
    void rejectsModesWithoutData(DataSegmentMode mode) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DataSegment.of(mode, utf8("123")))
                .withMessageContaining("carries no data");
    }

    @ParameterizedTest
    @CsvSource({ "NUMERIC, 12X45", "ALPHANUMERIC, ABCdEF", "NUMERIC, ' 123'" })
    @DisplayName("rejects data that the mode cannot encode")
    void rejectsUnencodableData(DataSegmentMode mode, String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DataSegment.of(mode, utf8(text)))
                .withMessageContaining("cannot be encoded in " + mode + " mode");
    }

    @Test
    @DisplayName("rejects Kanji data that is not a sequence of Shift-JIS double-byte codes")
    void rejectsUnencodableKanjiData() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DataSegment.of(KANJI, utf8("abcd")))
                .withMessageContaining("cannot be encoded in KANJI mode");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DataSegment.of(KANJI, SegmentTestSupport.shiftJis("希"), 0, 1))
                .withMessageContaining("must be even");
    }

    @Test
    @DisplayName("rejects a Kanji pair beyond the encodable range before anything is encoded")
    void rejectsKanjiPairBeyondTheEncodableRange() {
        // 0xEBC0 lies past the end of the upper range (0xE040-0xEBBF) while looking like a valid
        // lead/trail combination. The check has to catch it here, not when the bits are written.
        var data = new byte[] { (byte) 0xeb, (byte) 0xc0 };

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DataSegment.of(KANJI, data))
                .withMessage("Byte pair 0xeb 0xc0 at index 0 cannot be encoded in KANJI mode");
    }

    @Test
    @DisplayName("creates an ECI segment")
    void createsEciSegment() {
        var segment = DataSegment.ofEci(Eci.LATIN_9);

        assertThat(segment).isInstanceOf(DataSegmentEci.class);
        assertThat(segment.getMode()).isEqualTo(DataSegmentMode.ECI);
        assertThat(segment.getDataLength()).isZero();
        assertThat(((DataSegmentEci) segment).designator()).isEqualTo(Eci.LATIN_9);
    }

    @Test
    @DisplayName("creates a Structured Append segment")
    void createsStructuredAppendSegment() {
        var segment = DataSegment.ofStructuredAppend(1, 8, 0x3f);

        assertThat(segment).isInstanceOf(DataSegmentStructuredAppend.class);
        assertThat(segment.getMode()).isEqualTo(DataSegmentMode.STRUCTURED_APPEND);
        assertThat(segment.getDataLength()).isZero();

        var structuredAppend = (DataSegmentStructuredAppend) segment;
        assertThat(structuredAppend.position()).isEqualTo(1);
        assertThat(structuredAppend.total()).isEqualTo(8);
        assertThat(structuredAppend.parity()).isEqualTo(0x3f);
    }

    // endregion

    // region Length calculation

    @ParameterizedTest
    @CsvSource({
            "NUMERIC, 1, 9, 18", "NUMERIC, 2, 9, 21", "NUMERIC, 3, 9, 24", "NUMERIC, 4, 9, 28",
            "NUMERIC, 4, 10, 30", "NUMERIC, 4, 26, 30", "NUMERIC, 4, 27, 32", "NUMERIC, 4, 40, 32",
            "ALPHANUMERIC, 1, 9, 19", "ALPHANUMERIC, 2, 9, 24", "ALPHANUMERIC, 3, 9, 30",
            "ALPHANUMERIC, 3, 10, 32", "ALPHANUMERIC, 3, 26, 32", "ALPHANUMERIC, 3, 27, 34",
            "KANJI, 2, 9, 25", "KANJI, 4, 9, 38", "KANJI, 4, 10, 40", "KANJI, 4, 26, 40",
            "KANJI, 4, 27, 42",
            "BINARY, 1, 9, 20", "BINARY, 2, 9, 28", "BINARY, 3, 9, 36",
            "BINARY, 3, 10, 44", "BINARY, 3, 26, 44", "BINARY, 3, 27, 44"
    })
    @DisplayName("the segment length is the header plus the encoded data")
    void segmentLength(DataSegmentMode mode, int dataLength, int version, int expected) {
        assertThat(mode.segmentLength(dataLength, version)).isEqualTo(expected);
    }

    @Test
    @DisplayName("the total length of a segment matches the length calculated from the mode")
    void totalLength() {
        var segment = DataSegment.of(ALPHANUMERIC, utf8("ABC012"));

        for (var version : new int[] { 1, 9, 10, 26, 27, 40 })
            assertThat(segment.totalLength(version)).isEqualTo(ALPHANUMERIC.segmentLength(6, version));
    }

    @Test
    @DisplayName("the total length of a list of segments is the sum of the segments")
    void totalLengthOfList() {
        var segments = List.of(
                DataSegment.ofEci(Eci.UTF_8),
                DataSegment.of(ALPHANUMERIC, utf8("ABC012")));

        for (var version : new int[] { 8, 16, 28 })
            assertThat(DataSegment.totalLength(segments, version))
                    .isEqualTo(segments.get(0).totalLength(version) + segments.get(1).totalLength(version));
    }

    // endregion

    // region Bit stream

    static Stream<Arguments> bitStreamCases() {
        var binary = new DataSegmentBinary(sliceOfUtf8("abc"));
        var eciAndBinary = List.of(new DataSegmentEci(Eci.UTF_8), new DataSegmentBinary(sliceOfUtf8("abc")));
        var numeric = new DataSegmentNumeric(sliceOfUtf8("137"));
        var alphanumeric = new DataSegmentAlphanumeric(sliceOfUtf8("CDEF"));
        var eciAndKanji = List.of(new DataSegmentEci(Eci.SHIFT_JIS), new DataSegmentKanji(sliceOfShiftJis("け希")));

        return Stream.of(
                Arguments.of(List.of(binary), 8, 4 + 8 + 3 * 8 + 4),
                Arguments.of(List.of(binary), 15, 4 + 16 + 3 * 8 + 4),
                Arguments.of(List.of(binary), 30, 4 + 16 + 3 * 8 + 4),
                Arguments.of(eciAndBinary, 8, 4 + 8 + 4 + 8 + 3 * 8 + 4),
                Arguments.of(eciAndBinary, 15, 4 + 8 + 4 + 16 + 3 * 8 + 4),
                Arguments.of(eciAndBinary, 30, 4 + 8 + 4 + 16 + 3 * 8 + 4),
                Arguments.of(List.of(numeric), 8, 4 + 10 + 10 + 4),
                Arguments.of(List.of(numeric), 15, 4 + 12 + 10 + 4),
                Arguments.of(List.of(numeric), 30, 4 + 14 + 10 + 4),
                Arguments.of(List.of(alphanumeric), 8, 4 + 9 + 22 + 4),
                Arguments.of(List.of(alphanumeric), 15, 4 + 11 + 22 + 4),
                Arguments.of(List.of(alphanumeric), 30, 4 + 13 + 22 + 4),
                Arguments.of(eciAndKanji, 8, 4 + 8 + 4 + 8 + 26 + 4),
                Arguments.of(eciAndKanji, 15, 4 + 8 + 4 + 10 + 26 + 4),
                Arguments.of(eciAndKanji, 30, 4 + 8 + 4 + 12 + 26 + 4));
    }

    @ParameterizedTest
    @MethodSource("bitStreamCases")
    @DisplayName("writes the header, the data and the terminator of every segment")
    void createBitStream(List<DataSegment> segments, int version, int expectedBitLength) {
        var bitStream = DataSegment.createBitStream(segments, version, (expectedBitLength + 100) / 8);

        assertThat(bitStream.length()).isEqualTo(expectedBitLength);
        assertThat(bitStream.extractBits(bitStream.length() - 4, 4)).isZero();
    }

    @Test
    @DisplayName("writes the mode indicator and the character count indicator")
    void createBitStreamWritesHeader() {
        var segments = List.of(DataSegment.of(NUMERIC, utf8("137")));

        var bitStream = DataSegment.createBitStream(segments, 8, 8);

        assertThat(bitStream.extractBits(0, 4)).isEqualTo(NUMERIC.modeIndicator());
        assertThat(bitStream.extractBits(4, 10)).isEqualTo(3);
        assertThat(bitStream.extractBits(14, 10)).isEqualTo(137);
    }

    @Test
    @DisplayName("counts Kanji characters, not bytes, in the character count indicator")
    void createBitStreamCountsKanjiCharacters() {
        // Each Kanji character occupies two bytes and 13 bits. Writing the byte count here makes a
        // decoder read twice as many characters as the segment holds and reject the QR code.
        var segments = List.of(DataSegment.of(KANJI, shiftJis("け希")));

        var bitStream = DataSegment.createBitStream(segments, 1, 8);

        assertThat(bitStream.extractBits(0, 4)).isEqualTo(KANJI.modeIndicator());
        assertThat(bitStream.extractBits(4, 8)).as("character count").isEqualTo(2);
    }

    @Test
    @DisplayName("truncates the terminator if the capacity is nearly exhausted")
    void createBitStreamTruncatesTerminator() {
        var segments = List.of(DataSegment.of(NUMERIC, utf8("12")));

        // 4 bits of mode indicator, 10 bits of count indicator and 7 bits of data leave 3 bits
        var bitStream = DataSegment.createBitStream(segments, 1, 3);

        assertThat(bitStream.length()).isEqualTo(24);
        assertThat(bitStream.extractBits(21, 3)).isZero();
    }

    @Test
    @DisplayName("omits the terminator if the capacity is exhausted")
    void createBitStreamOmitsTerminator() {
        var segments = List.of(DataSegment.of(NUMERIC, utf8("137")));

        // 4 bits of mode indicator, 10 bits of count indicator and 10 bits of data fill the capacity
        var bitStream = DataSegment.createBitStream(segments, 1, 3);

        assertThat(bitStream.length()).isEqualTo(24);
        assertThat(bitStream.extractBits(14, 10)).isEqualTo(137);
    }

    // endregion

    @Test
    @DisplayName("names the mode and the data length")
    void stringRepresentation() {
        assertThat(DataSegment.of(NUMERIC, utf8("1")))
                .hasToString("DataSegment[mode=NUMERIC, dataLength=1]");
        assertThat(DataSegment.ofEci(Eci.LATIN_1))
                .hasToString("DataSegment[mode=ECI, dataLength=0]");
    }
}

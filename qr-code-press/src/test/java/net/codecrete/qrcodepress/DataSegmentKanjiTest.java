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

import java.util.stream.Stream;

import static net.codecrete.qrcodepress.SegmentTestSupport.SHIFT_JIS;
import static net.codecrete.qrcodepress.SegmentTestSupport.codewords;
import static net.codecrete.qrcodepress.SegmentTestSupport.encode;
import static net.codecrete.qrcodepress.SegmentTestSupport.sliceOfShiftJis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class DataSegmentKanjiTest {

    @ParameterizedTest
    @CsvSource({
            "0x8147, true", "0x8167, true", "0x81BD, true", "0x81FC, true", "0x824F, true",
            "0x8286, true", "0x82E2, true", "0x838A, true", "0x8457, true", "0x84BE, true",
            "0x889F, true", "0x88EB, true", "0x8E90, true", "0x8FFC, true", "0x9040, true",
            "0x924F, true", "0x965D, true", "0x98CA, true", "0x9BD4, true", "0x9FFC, true",
            "0xE040, true", "0xE094, true", "0xE0E7, true", "0xE1D8, true", "0xE2D3, true",
            "0xE4FC, true", "0xEAA4, true", "0xEBBF, true",
            "0x004E, false", "0x00A8, false", "0x813F, false", "0x81FD, false", "0x8047, false",
            "0xA043, false", "0xDF88, false", "0xEC71, false",
            // the upper range ends at 0xEBBF, so these lie beyond it despite a valid lead byte
            "0xEBC0, false", "0xEBFC, false"
    })
    @DisplayName("recognizes the double-byte Shift-JIS codes")
    void isShiftJisDoubleByte(int shiftJisCode, boolean expected) {
        var b1 = (byte) (shiftJisCode >> 8);
        var b2 = (byte) shiftJisCode;

        assertThat(DataSegmentKanji.isShiftJisDoubleByte(b1, b2)).isEqualTo(expected);

        if (expected)
            assertThat(DataSegmentKanji.encodeShiftJisCode(shiftJisCode)).isBetween(0, (1 << 13) - 1);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0x4e, 0xa8, 0x813f, 0x8047, 0x9ffd, 0xa043, 0xdf88, 0xec71 })
    @DisplayName("rejects codes outside the double-byte Shift-JIS ranges")
    void encodeInvalidShiftJisCode(int shiftJisCode) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DataSegmentKanji.encodeShiftJisCode(shiftJisCode))
                .withMessageContaining("only two-byte codes");
    }

    static Stream<Arguments> encodingCases() {
        return Stream.of(
                Arguments.of("ヒ", new int[] { 0b0000_1101, 0b1000_1000 }),
                Arguments.of("鵈", new int[] { 0b1111_0101, 0b1110_0000 }),
                Arguments.of("苜悉", new int[] { 0b1101_0100, 0b1110_0010, 0b1000_1110, 0b1100_0000 }));
    }

    @ParameterizedTest
    @MethodSource("encodingCases")
    @DisplayName("encodes each double-byte character into thirteen bits")
    void encodeKanji(String text, int[] expected) {
        var segment = new DataSegmentKanji(sliceOfShiftJis(text));

        assertThat(codewords(segment)).containsExactly(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "新購ラつ教", "なれ南恐文ぽういょ対捕ルタ年尾アケ和正言", "、（５ぺ殻適斑", "滌漾鼕堯" })
    @DisplayName("decodes back to the text that was encoded")
    void encodeDecodeKanji(String text) {
        var segment = new DataSegmentKanji(sliceOfShiftJis(text));

        var bitStream = encode(segment);

        assertThat(bitStream.length()).isEqualTo(segment.encodedLength());
        assertThat(new String(decodeKanji(bitStream), SHIFT_JIS)).isEqualTo(text);
    }

    @Test
    @DisplayName("rejects data with an odd number of bytes")
    void rejectsOddDataLength() {
        var slice = sliceOfShiftJis("希").slice(0, 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DataSegmentKanji(slice))
                .withMessageContaining("must be even");
    }

    @Test
    @DisplayName("rejects the first pair that is not a double-byte Shift-JIS code")
    void checkEncodable() {
        assertThatNoException()
                .isThrownBy(() -> DataSegmentKanji.checkEncodable(sliceOfShiftJis("希望")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DataSegmentKanji.checkEncodable(ByteSlice.of("ab".getBytes(SHIFT_JIS))))
                .withMessageContaining("at index 0");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DataSegmentKanji.checkEncodable(ByteSlice.of("希ab".getBytes(SHIFT_JIS))))
                .withMessageContaining("at index 2");

        // a trailing byte without a partner is not reported
        assertThatNoException()
                .isThrownBy(() -> DataSegmentKanji.checkEncodable(sliceOfShiftJis("希").slice(0, 1)));
    }

    @Test
    @DisplayName("names both bytes of the pair it rejects")
    void reportsTheWholeBytePair() {
        // 0xEB is a valid lead byte and 0xC0 a valid trail byte, but the pair is beyond 0xEBBF,
        // so neither byte is invalid on its own and the message must name both.
        var slice = ByteSlice.of(new byte[] { (byte) 0x88, (byte) 0x9f, (byte) 0xeb, (byte) 0xc0 });

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DataSegmentKanji.checkEncodable(slice))
                .withMessage("Byte pair 0xeb 0xc0 at index 2 cannot be encoded in KANJI mode");
    }

    @Test
    @DisplayName("accepts no pair that encodeShiftJisCode would reject")
    void everyAcceptedPairIsEncodable() {
        // The predicate is deliberately narrower than the encoder's range check: it also rejects
        // lead and trail bytes outside the Shift-JIS structure, which the raw 16-bit range test
        // would let through. What must never happen is the other direction -- a pair the predicate
        // accepts and the encoder then throws on, which is how segment compaction would pick Kanji
        // mode for data it cannot encode.
        for (var code = 0; code <= 0xffff; code += 1) {
            if (!DataSegmentKanji.isShiftJisDoubleByte((byte) (code >> 8), (byte) code))
                continue;

            var encoded = DataSegmentKanji.encodeShiftJisCode(code);
            assertThat(encoded)
                    .withFailMessage("0x%04x encodes to %d, outside thirteen bits", code, encoded)
                    .isBetween(0, (1 << 13) - 1);
        }
    }

    private static byte[] decodeKanji(BitStream bitStream) {
        var bitLength = bitStream.length();
        var numBytes = bitLength / 13 * 2;
        var result = new byte[numBytes];
        var index = 0;

        for (var offset = 0; offset + 12 < bitLength; offset += 13) {
            var shiftJisCode = decodeShiftJisCode(bitStream.extractBits(offset, 13));
            result[index] = (byte) (shiftJisCode >> 8);
            result[index + 1] = (byte) shiftJisCode;
            index += 2;
        }

        return result;
    }

    private static int decodeShiftJisCode(int encoded) {
        var code = (encoded / 0xc0) << 8 | (encoded % 0xc0);
        return code >> 8 < 0x1f ? code + 0x8140 : code + 0xc140;
    }
}

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

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static net.codecrete.qrcodepress.SegmentTestSupport.codewords;
import static net.codecrete.qrcodepress.SegmentTestSupport.encode;
import static net.codecrete.qrcodepress.SegmentTestSupport.sliceOfUtf8;
import static org.assertj.core.api.Assertions.assertThat;

class DataSegmentNumericTest {

    @Test
    @DisplayName("only the decimal digits are numeric")
    void isNumeric() {
        for (var b = 0; b < 256; b += 1)
            assertThat(DataSegmentNumeric.isNumeric((byte) b))
                    .as("byte 0x%02x", b)
                    .isEqualTo(b >= '0' && b <= '9');
    }

    static Stream<Arguments> encodingCases() {
        return Stream.of(
                Arguments.of("1", new int[] { 0b0001_0000 }),
                Arguments.of("35", new int[] { 0b0100_0110 }),
                Arguments.of("999", new int[] { 0b1111_1001, 0b1100_0000 }),
                Arguments.of("987", new int[] { 0b1111_0110, 0b1100_0000 }),
                Arguments.of("9871", new int[] { 0b1111_0110, 0b1100_0100 }),
                Arguments.of("4032", new int[] { 0b0110_0100, 0b1100_1000 }),
                Arguments.of("40327", new int[] { 0b0110_0100, 0b1100_1101, 0b1000_0000 }));
    }

    @ParameterizedTest
    @MethodSource("encodingCases")
    @DisplayName("encodes three digits into ten bits")
    void encodeNumeric(String text, int[] expected) {
        var segment = new DataSegmentNumeric(sliceOfUtf8(text));

        assertThat(codewords(segment)).containsExactly(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "7", "23", "51", "293", "700", "1730", "2293", "70906", "3082201", "00000000" })
    @DisplayName("decodes back to the digits that were encoded")
    void encodeDecodeNumeric(String text) {
        var segment = new DataSegmentNumeric(sliceOfUtf8(text));

        var bitStream = encode(segment);

        assertThat(bitStream.length()).isEqualTo(segment.encodedLength());
        assertThat(new String(decodeNumeric(bitStream), StandardCharsets.US_ASCII)).isEqualTo(text);
    }

    private static byte[] decodeNumeric(BitStream bitStream) {
        var bitLength = bitStream.length();
        var numDigits = bitLength / 10 * 3 + (bitLength % 10 + 1) / 4;
        var result = new byte[numDigits];
        var index = 0;

        for (var offset = 0; offset < bitLength; offset += 10) {
            // groups of three digits
            var group = bitStream.extractBits(offset, Math.min(10, bitLength - offset));
            var digitsInGroup = Math.min(numDigits - index, 3);
            for (var i = 0; i < digitsInGroup; i += 1) {
                result[index + digitsInGroup - i - 1] = (byte) (group % 10 + '0');
                group /= 10;
            }
            index += digitsInGroup;
        }

        return result;
    }
}

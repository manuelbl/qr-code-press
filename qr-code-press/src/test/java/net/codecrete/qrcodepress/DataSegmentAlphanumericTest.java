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

import static net.codecrete.qrcodepress.DataSegmentAlphanumeric.NOT_ALPHANUMERIC;
import static net.codecrete.qrcodepress.SegmentTestSupport.codewords;
import static net.codecrete.qrcodepress.SegmentTestSupport.encode;
import static net.codecrete.qrcodepress.SegmentTestSupport.sliceOfUtf8;
import static org.assertj.core.api.Assertions.assertThat;

class DataSegmentAlphanumericTest {

    @Test
    @DisplayName("numbers the 45 characters of the alphanumeric character set")
    void alphanumericEncoding() {
        for (var ch = '0'; ch <= '9'; ch += 1)
            assertCharacter(ch, ch - '0');
        for (var ch = 'A'; ch <= 'Z'; ch += 1)
            assertCharacter(ch, ch - 'A' + 10);

        assertCharacter(' ', 36);
        assertCharacter('$', 37);
        assertCharacter('%', 38);
        assertCharacter('*', 39);
        assertCharacter('+', 40);
        assertCharacter('-', 41);
        assertCharacter('.', 42);
        assertCharacter('/', 43);
        assertCharacter(':', 44);

        var count = 0;
        for (var b = 0; b < 256; b += 1) {
            if (DataSegmentAlphanumeric.isAlphanumeric((byte) b)) {
                count += 1;
                assertThat(DataSegmentAlphanumeric.encodeByte((byte) b)).isNotEqualTo(NOT_ALPHANUMERIC);
            } else {
                assertThat(DataSegmentAlphanumeric.encodeByte((byte) b))
                        .as("byte 0x%02x", b)
                        .isEqualTo(NOT_ALPHANUMERIC);
            }
        }

        assertThat(count).isEqualTo(45);
    }

    private static void assertCharacter(char ch, int expected) {
        assertThat(DataSegmentAlphanumeric.isAlphanumeric((byte) ch)).as("'%c' is alphanumeric", ch).isTrue();
        assertThat(DataSegmentAlphanumeric.encodeByte((byte) ch)).as("'%c'", ch).isEqualTo(expected);
    }

    static Stream<Arguments> encodingCases() {
        return Stream.of(
                Arguments.of("A", new int[] { 0b0010_1000 }),
                Arguments.of("AZ", new int[] { 0b0011_1100, 0b1010_0000 }),
                Arguments.of("AZ:", new int[] { 0b0011_1100, 0b1011_0110, 0b0000_0000 }),
                Arguments.of("AZ%3", new int[] { 0b0011_1100, 0b1011_1010, 0b1100_0100 }));
    }

    @ParameterizedTest
    @MethodSource("encodingCases")
    @DisplayName("encodes two characters into eleven bits")
    void encodeAlphanumeric(String text, int[] expected) {
        var segment = new DataSegmentAlphanumeric(sliceOfUtf8(text));

        assertThat(codewords(segment)).containsExactly(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A", "7", "B3", "TH", "QR3", "7BV", "QTN3", "PO1FF", "WV8XH3", "3082201", "MQDBXPPL", "$%*+-./:"
    })
    @DisplayName("decodes back to the characters that were encoded")
    void encodeDecodeAlphanumeric(String text) {
        var segment = new DataSegmentAlphanumeric(sliceOfUtf8(text));

        var bitStream = encode(segment);

        assertThat(bitStream.length()).isEqualTo(segment.encodedLength());
        assertThat(new String(decodeAlphanumeric(bitStream), StandardCharsets.US_ASCII)).isEqualTo(text);
    }

    private static byte[] decodeAlphanumeric(BitStream bitStream) {
        var bitLength = bitStream.length();
        var numChars = bitLength / 11 * 2 + bitLength % 11 / 6;
        var result = new byte[numChars];
        var index = 0;

        for (var offset = 0; offset + 10 < bitLength; offset += 11) {
            // groups of two characters
            var group = bitStream.extractBits(offset, 11);
            result[index] = decodeByte(group / 45);
            result[index + 1] = decodeByte(group % 45);
            index += 2;
        }

        if (numChars % 2 == 1)
            result[numChars - 1] = decodeByte(bitStream.extractBits(bitLength - 6, 6));

        return result;
    }

    private static byte decodeByte(int value) {
        for (var b = 0x20; b <= 0x5a; b += 1) {
            if (DataSegmentAlphanumeric.encodeByte((byte) b) == value)
                return (byte) b;
        }
        throw new IllegalArgumentException("Not an alphanumeric character number: " + value);
    }
}

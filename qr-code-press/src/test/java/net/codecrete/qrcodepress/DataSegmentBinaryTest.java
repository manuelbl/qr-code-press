/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
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

class DataSegmentBinaryTest {

    static Stream<Arguments> encodingCases() {
        return Stream.of(
                Arguments.of("a", new int[] { 0x61 }),
                Arguments.of("k/", new int[] { 0x6b, 0x2f }),
                Arguments.of("ĠźǄȻ", new int[] { 0xc4, 0xa0, 0xc5, 0xba, 0xc7, 0x84, 0xc8, 0xbb }),
                Arguments.of("Ξϴ", new int[] { 0xce, 0x9e, 0xcf, 0xb4 }),
                Arguments.of("أطعمة", new int[] { 0xd8, 0xa3, 0xd8, 0xb7, 0xd8, 0xb9, 0xd9, 0x85, 0xd8, 0xa9 }));
    }

    @ParameterizedTest
    @MethodSource("encodingCases")
    @DisplayName("writes the bytes as they are")
    void encodeByte(String text, int[] expected) {
        var segment = new DataSegmentBinary(sliceOfUtf8(text));

        assertThat(codewords(segment)).containsExactly(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a", "7", "c3", "k/", "0@f", "7¾ô", "ĠźǄȻ", "Ξϴ", "أطعمة", "𝄢𝇇",
            "夢に体当たり、砕け散って9カ月経った今", "😀💂💆‍♀️⛑👆🏻"
    })
    @DisplayName("decodes back to the bytes that were encoded")
    void encodeDecodeByte(String text) {
        var segment = new DataSegmentBinary(sliceOfUtf8(text));

        var bitStream = encode(segment);

        assertThat(bitStream.length()).isEqualTo(segment.encodedLength());
        assertThat(new String(decodeByte(bitStream), StandardCharsets.UTF_8)).isEqualTo(text);
    }

    private static byte[] decodeByte(BitStream bitStream) {
        var numBytes = bitStream.length() / 8;
        var result = new byte[numBytes];
        for (var i = 0; i < numBytes; i += 1)
            result[i] = (byte) bitStream.extractBits(i * 8, 8);
        return result;
    }
}

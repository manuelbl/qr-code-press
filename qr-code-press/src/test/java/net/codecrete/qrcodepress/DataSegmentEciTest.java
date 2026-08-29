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

import java.util.stream.Stream;

import static net.codecrete.qrcodepress.SegmentTestSupport.codewords;
import static net.codecrete.qrcodepress.SegmentTestSupport.encode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DataSegmentEciTest {

    static Stream<Arguments> encodingCases() {
        return Stream.of(
                Arguments.of(0, new int[] { 0b0000_0000 }),
                Arguments.of(127, new int[] { 0b0111_1111 }),
                Arguments.of(128, new int[] { 0b1000_0000, 0b1000_0000 }),
                Arguments.of(16383, new int[] { 0b1011_1111, 0b1111_1111 }),
                Arguments.of(16384, new int[] { 0b1100_0000, 0b0100_0000, 0b0000_0000 }),
                Arguments.of(999999, new int[] { 0b1100_1111, 0b0100_0010, 0b0011_1111 }));
    }

    @ParameterizedTest
    @MethodSource("encodingCases")
    @DisplayName("encodes the designator in one, two or three bytes")
    void encodeEci(int value, int[] expected) {
        var segment = new DataSegmentEci(Eci.of(value));

        assertThat(codewords(segment)).containsExactly(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 127, 128, 16383, 16384, 999999 })
    @DisplayName("decodes back to the designator that was encoded")
    void encodeDecodeEci(int value) {
        var segment = new DataSegmentEci(Eci.of(value));

        var bitStream = encode(segment);

        assertThat(bitStream.length()).isEqualTo(segment.encodedLength());
        assertThat(decodeEci(bitStream)).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 127, 128, 16383, 16384, 999999 })
    @DisplayName("the encoded length is a whole number of bytes")
    void encodedLength(int value) {
        var segment = new DataSegmentEci(Eci.of(value));

        assertThat(segment.encodedLength()).isIn(8, 16, 24);
        assertThat(segment.totalLength(1)).isEqualTo(4 + segment.encodedLength());
    }

    @Test
    @DisplayName("rejects the designators that are instructions to the encoder")
    void rejectsNonDesignators() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DataSegmentEci(Eci.NONE))
                .withMessageContaining("Eci.NONE");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DataSegmentEci(Eci.AUTOMATIC))
                .withMessageContaining("Eci.AUTOMATIC");
    }

    private static int decodeEci(BitStream bitStream) {
        var leadingBits = bitStream.extractBits(0, 2);
        if (leadingBits <= 1)
            return bitStream.extractBits(1, 7);
        if (leadingBits == 2)
            return bitStream.extractBits(2, 14);
        return bitStream.extractBits(4, 20);
    }
}

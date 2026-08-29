/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BitStreamTest {

    @Test
    @DisplayName("extracts the values that were appended")
    void appendAndExtractBits() {
        var stream = new BitStream(10);
        stream.appendBits(18, 6);
        stream.appendBits(30, 5);
        stream.appendBits(15, 5);
        stream.appendBits(3, 2);
        stream.appendBits((int) 2196229387L, 32);

        assertThat(stream.length()).isEqualTo(6 + 5 + 5 + 2 + 32);

        assertThat(stream.extractBits(0, 6)).isEqualTo(18);
        assertThat(stream.extractBits(6, 5)).isEqualTo(30);
        assertThat(stream.extractBits(11, 5)).isEqualTo(15);
        assertThat(stream.extractBits(16, 2)).isEqualTo(3);
        assertThat(Integer.toUnsignedLong(stream.extractBits(18, 32))).isEqualTo(2196229387L);
    }

    @Test
    @DisplayName("keeps values apart across many unaligned appends")
    void appendAndExtractManyValues() {
        var stream = new BitStream(200);
        for (var i = 0; i < 57; i += 1) {
            stream.appendBits(358234 + i, 19);
        }

        for (var i = 0; i < 57; i += 1) {
            assertThat(stream.extractBits(i * 19, 19)).isEqualTo(358234 + i);
        }
    }

    @Test
    @DisplayName("appends a value that does not fit into the given number of bits")
    void appendRejectsTooLargeValue() {
        var stream = new BitStream(10);

        assertThatIllegalArgumentException().isThrownBy(() -> stream.appendBits(128, 4));
    }

    @Test
    @DisplayName("rejects an out-of-range number of bits when appending")
    void appendRejectsInvalidLength() {
        var stream = new BitStream(10);

        assertThatIllegalArgumentException().isThrownBy(() -> stream.appendBits(128, 33));
        assertThatIllegalArgumentException().isThrownBy(() -> stream.appendBits(1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> stream.appendBits(1, -1));
    }

    @Test
    @DisplayName("rejects appending beyond the capacity")
    void appendRejectsExceedingCapacity() {
        var stream = new BitStream(1);

        assertThatIllegalArgumentException().isThrownBy(() -> stream.appendBits(128, 9));
    }

    @Test
    @DisplayName("fills the capacity exactly")
    void appendFillsCapacityExactly() {
        var stream = new BitStream(2);

        stream.appendBits(0xabcd, 16);

        assertThat(stream.length()).isEqualTo(16);
        assertThat(stream.codewords()).containsExactly(0xab, 0xcd);
    }

    @Test
    @DisplayName("rejects an out-of-range number of bits when extracting")
    void extractRejectsInvalidLength() {
        var stream = new BitStream(20);
        stream.appendBits(123456789, 32);
        stream.appendBits(123456789, 32);

        assertThatIllegalArgumentException().isThrownBy(() -> stream.extractBits(0, 33));
        assertThatIllegalArgumentException().isThrownBy(() -> stream.extractBits(0, 0));
    }

    @Test
    @DisplayName("rejects extracting beyond the appended bits")
    void extractRejectsOutOfRangeIndex() {
        var stream = new BitStream(10);
        stream.appendBits(23456, 15);

        assertThatIllegalArgumentException().isThrownBy(() -> stream.extractBits(10, 6));
        assertThatIllegalArgumentException().isThrownBy(() -> stream.extractBits(-1, 4));
    }

    @Test
    @DisplayName("packs the bits into big-endian codewords")
    void codewords() {
        var stream = new BitStream(10);
        stream.appendBits(0x41, 8);
        stream.appendBits(0x7, 4);
        stream.appendBits(0xa, 4);
        stream.appendBits(0x20, 8);
        stream.appendBits(0x38, 8);

        assertThat(stream.codewords()).containsExactly(0x41, 0x7a, 0x20, 0x38);
    }

    @Test
    @DisplayName("pads the last codeword with zeros")
    void codewordsPadLastCodeword() {
        var stream = new BitStream(10);
        stream.appendBits(0x39, 8);
        stream.appendBits(0x21, 6);

        assertThat(stream.codewords()).containsExactly(0x39, 0x84);
    }

    @Test
    @DisplayName("an empty stream has no codewords")
    void emptyStreamHasNoCodewords() {
        var stream = new BitStream(10);

        assertThat(stream.length()).isZero();
        assertThat(stream.codewords()).isEmpty();
    }

}

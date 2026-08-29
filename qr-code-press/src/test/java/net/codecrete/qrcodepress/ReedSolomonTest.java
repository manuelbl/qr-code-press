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

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ReedSolomonTest {

    /** The error correction capacities QR codes actually use, per ISO/IEC 18004. */
    private static final int[] QR_CAPACITIES = { 7, 10, 13, 15, 16, 17, 18, 20, 22, 24, 26, 28, 30 };

    // region Cross-check against ZXing

    static Stream<Arguments> capacitiesAndLengths() {
        // The extremes are included alongside the QR code capacities: capacity 1 leaves the
        // division loop with a single coefficient, and 255 is the largest field-wide code.
        var capacities = IntStream.concat(IntStream.of(1, 2, 255), Arrays.stream(QR_CAPACITIES));
        return capacities.boxed().flatMap(capacity ->
                Stream.of(1, 2, 9, 47, 123).map(length -> Arguments.of(capacity, length)));
    }

    @ParameterizedTest(name = "{0} ECC codewords for {1} data codewords")
    @MethodSource("capacitiesAndLengths")
    @DisplayName("computes the error correction codewords ZXing computes")
    void matchesZxing(int capacity, int dataLength) {
        var data = pseudoRandomBytes(dataLength, capacity * 31L + dataLength);

        var actual = errorCorrection(capacity, ByteSlice.of(data));

        assertThat(actual).isEqualTo(ZxingSupport.errorCorrection(data, capacity));
    }

    @Test
    @DisplayName("computes the error correction codewords ZXing computes for an empty block")
    void matchesZxingForEmptyData() {
        // Not reachable through the encoder, but the polynomial division has to leave the
        // remainder at zero rather than run off the end of it.
        var actual = errorCorrection(10, ByteSlice.EMPTY);

        assertThat(actual).containsOnly((byte) 0);
    }

    // endregion

    // region Instances

    @ParameterizedTest
    @ValueSource(ints = { 1, 7, 30, 255 })
    @DisplayName("reuses the instance of a capacity, so the generator polynomial is computed once")
    void cachesInstances(int capacity) {
        assertThat(ReedSolomon.forCapacity(capacity)).isSameAs(ReedSolomon.forCapacity(capacity));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0, 256, Integer.MAX_VALUE })
    @DisplayName("rejects a capacity outside the range of the field")
    void rejectsCapacityOutOfRange(int capacity) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReedSolomon.forCapacity(capacity))
                .withMessageContaining("between 1 and 255");
    }

    // endregion

    // region Error correction properties

    @ParameterizedTest
    @ValueSource(ints = { 7, 18, 30 })
    @DisplayName("writes its codewords at the offset and stride it is given, and nowhere else")
    void writesAtTheOffsetAndStride(int capacity) {
        var data = pseudoRandomBytes(40, capacity);
        var expected = errorCorrection(capacity, ByteSlice.of(data));
        var offset = 3;
        var stride = 5;

        // One byte more than the last codeword needs, so that a write past the end shows up.
        var target = new byte[offset + (capacity - 1) * stride + 1];
        ReedSolomon.forCapacity(capacity).computeErrorCorrection(ByteSlice.of(data), target, offset, stride);

        for (var i = 0; i < target.length; i += 1) {
            var index = i - offset;
            var isCodeword = index >= 0 && index % stride == 0 && index / stride < capacity;
            assertThat(target[i]).isEqualTo(isCodeword ? expected[index / stride] : 0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 7, 18, 30 })
    @DisplayName("reads only the slice it is given")
    void readsOnlyItsSlice(int capacity) {
        var data = pseudoRandomBytes(20, capacity);
        var padded = new byte[40];
        System.arraycopy(data, 0, padded, 10, data.length);

        var actual = errorCorrection(capacity, ByteSlice.of(padded).slice(10, data.length));

        assertThat(actual).isEqualTo(errorCorrection(capacity, ByteSlice.of(data)));
    }

    @Test
    @DisplayName("leaves no remainder when dividing the data followed by its error correction")
    void codewordsAreDivisibleByTheGeneratorPolynomial() {
        // The error correction codewords are the remainder of the division, so appending them
        // makes the message a multiple of the generator polynomial. This is what lets a decoder
        // detect errors, and it holds for no other suffix.
        var capacity = 18;
        var data = pseudoRandomBytes(50, 4711);

        var message = new byte[data.length + capacity];
        System.arraycopy(data, 0, message, 0, data.length);
        ReedSolomon.forCapacity(capacity)
                .computeErrorCorrection(ByteSlice.of(data), message, data.length, 1);

        assertThat(errorCorrection(capacity, ByteSlice.of(message))).containsOnly((byte) 0);
    }

    // endregion

    /** Returns the error correction codewords of a block as an array of their own. */
    private static byte[] errorCorrection(int capacity, ByteSlice data) {
        var codewords = new byte[capacity];
        ReedSolomon.forCapacity(capacity).computeErrorCorrection(data, codewords, 0, 1);
        return codewords;
    }

    /** Returns reproducible pseudo-random bytes, so a failure can be reproduced from its name. */
    private static byte[] pseudoRandomBytes(int length, long seed) {
        var data = new byte[length];
        new Random(seed).nextBytes(data);
        return data;
    }
}

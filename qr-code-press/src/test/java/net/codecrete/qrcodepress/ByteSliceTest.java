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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ByteSliceTest {

    @Test
    @DisplayName("covers the entire array")
    void ofCoversEntireArray() {
        var slice = ByteSlice.of(new byte[] { 3, 1, 4, 1, 5 });

        assertThat(slice.offset()).isZero();
        assertThat(slice.length()).isEqualTo(5);
        assertThat(slice.at(0)).isEqualTo((byte) 3);
        assertThat(slice.at(4)).isEqualTo((byte) 5);
    }

    @Test
    @DisplayName("shares the array it is created from")
    void ofSharesArray() {
        var array = new byte[] { 3, 1, 4 };
        var slice = ByteSlice.of(array);

        array[1] = 9;

        assertThat(slice.at(1)).isEqualTo((byte) 9);
    }

    @Test
    @DisplayName("copies the range it is created from")
    void copyOfCopiesRange() {
        var array = new byte[] { 3, 1, 4, 1, 5 };
        var slice = ByteSlice.copyOf(array, 1, 3);

        array[1] = 9;

        assertThat(slice.length()).isEqualTo(3);
        assertThat(slice.at(0)).isEqualTo((byte) 1);
        assertThat(slice.at(2)).isEqualTo((byte) 1);
    }

    @Test
    @DisplayName("rejects a range outside the array")
    void copyOfRejectsInvalidRange() {
        var array = new byte[5];

        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> ByteSlice.copyOf(array, 3, 3));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> ByteSlice.copyOf(array, -1, 2));
    }

    @Test
    @DisplayName("slices relative to the slice, not to the array")
    void sliceIsRelative() {
        var slice = ByteSlice.of(new byte[] { 3, 1, 4, 1, 5, 9 }).slice(2, 3).slice(1, 2);

        assertThat(slice.offset()).isEqualTo(3);
        assertThat(slice.length()).isEqualTo(2);
        assertThat(slice.at(0)).isEqualTo((byte) 1);
        assertThat(slice.at(1)).isEqualTo((byte) 5);
    }

    @Test
    @DisplayName("copies out only the bytes it covers")
    void toArrayCopiesSliceOnly() {
        var array = new byte[] { 3, 1, 4, 1, 5, 9 };
        var slice = ByteSlice.of(array).slice(2, 3);

        var bytes = slice.toArray();
        array[2] = 7;

        assertThat(bytes).containsExactly(4, 1, 5);
    }

    @Test
    @DisplayName("the empty slice has no bytes")
    void emptySliceHasNoBytes() {
        assertThat(ByteSlice.EMPTY.length()).isZero();
    }

    @Test
    @DisplayName("rejects a null array")
    void rejectsNullArray() {
        assertThatNullPointerException().isThrownBy(() -> new ByteSlice(null, 0, 0));
    }
}

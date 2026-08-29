/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.Arrays;
import java.util.Objects;

/**
 * A view of a range of a byte array.
 * <p>
 * Data segments refer to their payload with a slice instead of an array of their own, so that
 * splitting a payload into several segments does not copy it. The array is never copied and never
 * modified; all slices of a payload share the array the library made when the payload crossed the
 * public API boundary.
 * </p>
 *
 * @param array  the underlying array
 * @param offset the index of the first byte of the slice
 * @param length the number of bytes in the slice
 */
record ByteSlice(byte[] array, int offset, int length) {

    /** The empty slice, used by the segments that carry no payload. */
    static final ByteSlice EMPTY = new ByteSlice(new byte[0], 0, 0);

    ByteSlice {
        Objects.requireNonNull(array, "array");
    }

    /**
     * Returns a slice covering the entire specified array.
     * <p>
     * The array is not copied. It must be owned by the library, or the caller could later change
     * the payload of a segment.
     * </p>
     *
     * @param array the array
     * @return the slice
     */
    static ByteSlice of(byte[] array) {
        return new ByteSlice(array, 0, array.length);
    }

    /**
     * Returns a slice covering a copy of the specified range of the specified array.
     *
     * @param array  the array
     * @param offset the index of the first byte to copy
     * @param length the number of bytes to copy
     * @return the slice
     * @throws IndexOutOfBoundsException if the range is out of bounds
     */
    static ByteSlice copyOf(byte[] array, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, array.length);
        return of(Arrays.copyOfRange(array, offset, offset + length));
    }

    /**
     * Returns the byte at the specified index within this slice.
     *
     * @param index the index
     * @return the byte
     */
    byte at(int index) {
        return array[offset + index];
    }

    /**
     * Returns a slice of this slice.
     *
     * @param start  the index (within this slice) of the first byte of the new slice
     * @param length the number of bytes in the new slice
     * @return the slice
     */
    ByteSlice slice(int start, int length) {
        return new ByteSlice(array, offset + start, length);
    }

    /**
     * Returns the bytes of this slice as an array of their own.
     * <p>
     * The bytes are copied, so the result is independent of the underlying array.
     * </p>
     *
     * @return the bytes
     */
    byte[] toArray() {
        return Arrays.copyOfRange(array, offset, offset + length);
    }
}

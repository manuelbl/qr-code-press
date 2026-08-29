/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.Arrays;

/**
 * A stream of bits for encoding the QR code payload.
 * <p>
 * Bit streams use a big endian order for both bytes and bits.
 * </p>
 * <p>
 * Instances of this class must be allocated with sufficient capacity. They cannot grow.
 * </p>
 * <p>
 * Values are treated as unsigned 32-bit integers, i.e. a negative {@code int} stands for the value
 * with the same bit pattern in the range 2<sup>31</sup> to 2<sup>32</sup>&nbsp;&minus;&nbsp;1.
 * </p>
 */
final class BitStream {

    private final byte[] codewords;
    private int length;

    /**
     * Creates a new instance with the specified capacity.
     *
     * @param capacity the capacity, in bytes
     */
    BitStream(int capacity) {
        codewords = new byte[capacity];
    }

    /**
     * Returns the length of the bit stream, in bits.
     *
     * @return the length
     */
    int length() {
        return length;
    }

    /**
     * Appends the specified value (with the specified number of bits).
     * <p>
     * The passed value must be in the range 0 &le; value &lt; 2<sup>numBits</sup>.
     * </p>
     *
     * @param value   the value to append
     * @param numBits the number of bits to append
     * @throws IllegalArgumentException if the value or the number of bits is out of range, or if
     *                                  the capacity would be exceeded
     */
    void appendBits(int value, int numBits) {
        if (numBits <= 0 || numBits > 32) {
            throw new IllegalArgumentException("numBits must be between 1 and 32");
        }

        if (numBits < 32 && value >>> numBits != 0) {
            throw new IllegalArgumentException("value must be in the range 0 <= value < 2^numBits");
        }

        var newLength = length + numBits;
        if (newLength > codewords.length * 8) {
            throw new IllegalArgumentException("appending the specified number of bits exceeds the capacity");
        }

        var valueMask = 1 << (numBits - 1);
        for (var i = length; i < newLength; i += 1) {
            if ((value & valueMask) != 0) {
                codewords[i >> 3] |= (byte) (1 << (7 - (i & 7)));
            }
            valueMask >>>= 1;
        }

        length = newLength;
    }

    /**
     * Extracts the specified number of bits at the specified index.
     *
     * @param index   the index of the first bit to extract
     * @param numBits the number of bits to extract
     * @return the extracted bits, as an unsigned integer
     * @throws IllegalArgumentException if the index or the number of bits is out of range
     */
    int extractBits(int index, int numBits) {
        if (numBits <= 0 || numBits > 32) {
            throw new IllegalArgumentException("numBits must be between 1 and 32");
        }

        if (index < 0 || index + numBits > length) {
            throw new IllegalArgumentException("index out of range");
        }

        var result = 0;
        var valueMask = 1 << (numBits - 1);
        for (var i = index; i < index + numBits; i += 1) {
            var codewordMask = (byte) (1 << (7 - (i & 7)));
            if ((codewords[i >> 3] & codewordMask) != 0) {
                result |= valueMask;
            }
            valueMask >>>= 1;
        }

        return result;
    }

    /**
     * Returns the bit stream as 8-bit codewords.
     * <p>
     * If the bit stream is not a multiple of 8 bits, the last codeword is padded with zeros.
     * </p>
     *
     * @return the codewords
     */
    byte[] codewords() {
        return Arrays.copyOf(codewords, (length + 7) >> 3);
    }

    /**
     * Returns the array backing this bit stream, giving up ownership of it.
     * <p>
     * The array spans the full capacity, and the bytes past the length of the bit stream are zero.
     * It is not copied, so the caller may modify it &mdash; which is what appending the padding to
     * the data codewords does. This bit stream must not be used afterward.
     * </p>
     *
     * @return the codewords
     */
    byte[] takeCodewords() {
        return codewords;
    }

}

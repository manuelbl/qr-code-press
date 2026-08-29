/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * A data segment using numeric mode.
 * <p>
 * Groups of three digits are encoded as a 10-bit number. A trailing group of two digits takes
 * 7 bits, a trailing single digit 4 bits.
 * </p>
 */
final class DataSegmentNumeric extends DataSegment {

    DataSegmentNumeric(ByteSlice data) {
        super(DataSegmentMode.NUMERIC, data, encodedBitLength(data.length()));
    }

    /**
     * Indicates whether the specified byte can be encoded in numeric mode.
     *
     * @param b the byte
     * @return {@code true} if it can be encoded
     */
    static boolean isNumeric(byte b) {
        return b >= '0' && b <= '9';
    }

    /**
     * Returns the encoded length of the specified number of digits.
     *
     * @param dataLength the number of digits
     * @return the encoded length, in bits
     */
    static int encodedBitLength(int dataLength) {
        return (dataLength * 10 + 2) / 3;
    }

    /**
     * Returns the number of digits fitting into the specified encoded length.
     *
     * @param bitLength the encoded length, in bits
     * @return the number of digits
     */
    static int byteCount(int bitLength) {
        return bitLength * 3 / 10;
    }

    @Override
    void writeToBitStream(BitStream bitStream) {
        var data = data();
        var index = 0;
        while (index < data.length()) {
            // three digits are encoded into 10 bits
            var groupLength = Math.min(data.length() - index, 3);
            var value = 0;
            for (var i = index; i < index + groupLength; i += 1)
                value = value * 10 + data.at(i) - '0';
            bitStream.appendBits(value, groupLength * 3 + 1);
            index += groupLength;
        }
    }
}

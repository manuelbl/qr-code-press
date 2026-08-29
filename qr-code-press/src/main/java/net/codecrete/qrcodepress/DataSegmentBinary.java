/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * A data segment using binary mode.
 * <p>
 * The bytes are written as they are. Any data can be encoded this way, at 8 bits per byte.
 * </p>
 */
final class DataSegmentBinary extends DataSegment {

    DataSegmentBinary(ByteSlice data) {
        super(DataSegmentMode.BINARY, data, encodedBitLength(data.length()));
    }

    /**
     * Returns the encoded length of the specified number of bytes.
     *
     * @param dataLength the number of bytes
     * @return the encoded length, in bits
     */
    static int encodedBitLength(int dataLength) {
        return dataLength * 8;
    }

    /**
     * Returns the number of bytes fitting into the specified encoded length.
     *
     * @param bitLength the encoded length, in bits
     * @return the number of bytes
     */
    static int byteCount(int bitLength) {
        return bitLength / 8;
    }

    @Override
    void writeToBitStream(BitStream bitStream) {
        var data = data();
        for (var i = 0; i < data.length(); i += 1)
            bitStream.appendBits(data.at(i) & 0xff, 8);
    }
}

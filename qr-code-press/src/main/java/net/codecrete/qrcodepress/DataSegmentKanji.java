/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * A data segment using Kanji mode.
 * <p>
 * Each double-byte Shift-JIS character is encoded as a 13-bit number, saving three bits over
 * binary mode. The data length is therefore always even.
 * </p>
 */
final class DataSegmentKanji extends DataSegment {

    DataSegmentKanji(ByteSlice data) {
        super(DataSegmentMode.KANJI, data, encodedBitLength(data.length()));
        if (data.length() % 2 != 0)
            throw new IllegalArgumentException(
                    "Kanji mode encodes pairs of bytes; the data length must be even, got " + data.length());
    }

    /**
     * Indicates whether the specified pair of bytes can be encoded in Kanji mode.
     * <p>
     * Kanji mode can be used for Shift-JIS double-byte codes, and equally for any other byte pair
     * within the range of those codes.
     * </p>
     *
     * @param b1 the first byte
     * @param b2 the second byte
     * @return {@code true} if the pair can be encoded
     */
    static boolean isShiftJisDoubleByte(byte b1, byte b2) {
        var v1 = b1 & 0xff;
        if (v1 < 0x81 || (v1 > 0x9f && v1 < 0xe0) || v1 > 0xeb)
            return false;

        var v2 = b2 & 0xff;
        if (v2 < 0x40 || v2 > 0xfc)
            return false;

        // The upper range of the encodable codes ends at 0xEBBF, not at 0xEBFC. Without this the
        // predicate would admit pairs that encodeShiftJisCode(int) then rejects, and the segment
        // compaction would pick Kanji mode for data it cannot encode.
        return v1 != 0xeb || v2 <= 0xbf;
    }

    /**
     * Checks that every pair of bytes of the specified data can be encoded in Kanji mode.
     * <p>
     * A trailing byte without a partner is not reported; the data length is checked separately.
     * </p>
     *
     * @param data the data
     * @throws IllegalArgumentException if a pair of bytes cannot be encoded
     */
    static void checkEncodable(ByteSlice data) {
        for (var i = 0; i + 1 < data.length(); i += 2) {
            if (!isShiftJisDoubleByte(data.at(i), data.at(i + 1)))
                throw new IllegalArgumentException(String.format(
                        "Byte pair 0x%02x 0x%02x at index %d cannot be encoded in %s mode",
                        data.at(i), data.at(i + 1), i, DataSegmentMode.KANJI));
        }
    }

    /**
     * Returns the encoded length of the specified number of bytes.
     *
     * @param dataLength the number of bytes (an even number)
     * @return the encoded length, in bits
     */
    static int encodedBitLength(int dataLength) {
        return dataLength * 13 / 2;
    }

    /**
     * Returns the number of bytes fitting into the specified encoded length.
     *
     * @param bitLength the encoded length, in bits
     * @return the number of bytes (an even number)
     */
    static int byteCount(int bitLength) {
        return bitLength / 13 * 2;
    }

    /**
     * Encodes a double-byte Shift-JIS code as a 13-bit number.
     *
     * @param shiftJisCode the Shift-JIS code
     * @return the encoded number
     * @throws IllegalArgumentException if the code is not a double-byte Shift-JIS code
     */
    static int encodeShiftJisCode(int shiftJisCode) {
        int code;
        if (shiftJisCode >= 0x8140 && shiftJisCode <= 0x9ffc) {
            code = shiftJisCode - 0x8140;
        } else if (shiftJisCode >= 0xe040 && shiftJisCode <= 0xebbf) {
            code = shiftJisCode - 0xc140;
        } else {
            throw new IllegalArgumentException(String.format(
                    "Invalid Shift-JIS code 0x%04x: only two-byte codes can be encoded in Kanji mode",
                    shiftJisCode));
        }
        return (code >> 8) * 0xc0 + (code & 0xff);
    }

    /**
     * {@inheritDoc}
     * <p>
     * A Kanji character occupies two bytes, and the indicator counts characters, so it is half the
     * data length.
     * </p>
     */
    @Override
    int characterCount() {
        return data().length() / 2;
    }

    @Override
    void writeToBitStream(BitStream bitStream) {
        var data = data();
        for (var i = 0; i < data.length(); i += 2) {
            // each double-byte character is encoded into 13 bits
            var shiftJisCode = (data.at(i) & 0xff) * 256 + (data.at(i + 1) & 0xff);
            bitStream.appendBits(encodeShiftJisCode(shiftJisCode), 13);
        }
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * A data segment using alphanumeric mode.
 * <p>
 * The 45 characters of the alphanumeric character set are numbered from 0 to 44. A pair of
 * characters is encoded as an 11-bit number, a trailing single character as a 6-bit number.
 * </p>
 */
final class DataSegmentAlphanumeric extends DataSegment {

    /** The value returned for bytes outside the alphanumeric character set. */
    static final int NOT_ALPHANUMERIC = 0xff;

    DataSegmentAlphanumeric(ByteSlice data) {
        super(DataSegmentMode.ALPHANUMERIC, data, encodedBitLength(data.length()));
    }

    /**
     * Indicates whether the specified byte can be encoded in alphanumeric mode.
     *
     * @param b the byte
     * @return {@code true} if it can be encoded
     */
    static boolean isAlphanumeric(byte b) {
        return (b >= 'A' && b <= 'Z')
                || (b >= '-' && b <= ':') // includes the digits
                || b == ' '
                || b == '$'
                || b == '%'
                || b == '*'
                || b == '+';
    }

    /**
     * Returns the encoded length of the specified number of characters.
     *
     * @param dataLength the number of characters
     * @return the encoded length, in bits
     */
    static int encodedBitLength(int dataLength) {
        return (dataLength * 11 + 1) / 2;
    }

    /**
     * Returns the number of characters fitting into the specified encoded length.
     *
     * @param bitLength the encoded length, in bits
     * @return the number of characters
     */
    static int byteCount(int bitLength) {
        return bitLength * 2 / 11;
    }

    /**
     * Returns the number of the specified character within the alphanumeric character set.
     *
     * @param b the byte
     * @return the number (0&ndash;44), or {@link #NOT_ALPHANUMERIC} if the byte is not part of the
     *         character set
     */
    static int encodeByte(byte b) {
        if (b < 0x20 || b > 0x5a)
            return NOT_ALPHANUMERIC;
        return ALPHANUMERIC_ENCODING[b - 0x20];
    }

    @Override
    void writeToBitStream(BitStream bitStream) {
        var data = data();
        var length = data.length();
        for (var i = 0; i + 1 < length; i += 2) {
            // two characters are encoded into 11 bits
            bitStream.appendBits(encodeByte(data.at(i)) * 45 + encodeByte(data.at(i + 1)), 11);
        }

        if (length % 2 == 1)
            bitStream.appendBits(encodeByte(data.at(length - 1)), 6);
    }

    // The number of each character within the alphanumeric character set, indexed by the byte
    // value minus 0x20. See table 5 "Encoding/decoding table for Alphanumeric mode" in the QR code
    // specification (ISO/IEC 18004:2015(E)).
    private static final int[] ALPHANUMERIC_ENCODING = {
            0x24,  // 20h SPACE
            0xff,  // 21h !
            0xff,  // 22h "
            0xff,  // 23h #
            0x25,  // 24h $
            0x26,  // 25h %
            0xff,  // 26h &
            0xff,  // 27h '
            0xff,  // 28h (
            0xff,  // 29h )
            0x27,  // 2Ah *
            0x28,  // 2Bh +
            0xff,  // 2Ch ,
            0x29,  // 2Dh -
            0x2a,  // 2Eh .
            0x2b,  // 2Fh /
            0x00,  // 30h 0
            0x01,  // 31h 1
            0x02,  // 32h 2
            0x03,  // 33h 3
            0x04,  // 34h 4
            0x05,  // 35h 5
            0x06,  // 36h 6
            0x07,  // 37h 7
            0x08,  // 38h 8
            0x09,  // 39h 9
            0x2c,  // 3Ah :
            0xff,  // 3Bh ;
            0xff,  // 3Ch <
            0xff,  // 3Dh =
            0xff,  // 3Eh >
            0xff,  // 3Fh ?
            0xff,  // 40h @
            0x0a,  // 41h A
            0x0b,  // 42h B
            0x0c,  // 43h C
            0x0d,  // 44h D
            0x0e,  // 45h E
            0x0f,  // 46h F
            0x10,  // 47h G
            0x11,  // 48h H
            0x12,  // 49h I
            0x13,  // 4Ah J
            0x14,  // 4Bh K
            0x15,  // 4Ch L
            0x16,  // 4Dh M
            0x17,  // 4Eh N
            0x18,  // 4Fh O
            0x19,  // 50h P
            0x1a,  // 51h Q
            0x1b,  // 52h R
            0x1c,  // 53h S
            0x1d,  // 54h T
            0x1e,  // 55h U
            0x1f,  // 56h V
            0x20,  // 57h W
            0x21,  // 58h X
            0x22,  // 59h Y
            0x23   // 5Ah Z
    };
}

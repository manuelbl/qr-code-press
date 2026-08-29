/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * The mode of a data segment.
 * <p>
 * Data segments either carry payload, encoded in one of the four data modes (numeric,
 * alphanumeric, Kanji, binary), or they have a special function such as announcing a character
 * encoding. The mode says which of the two it is, and how the payload is encoded.
 * </p>
 */
public enum DataSegmentMode {

    /**
     * Numeric mode.
     * <p>
     * Payload consisting of the decimal digits 0 to 9. Three digits are encoded in 10 bits.
     * </p>
     */
    NUMERIC(1, new int[] { 10, 12, 14 }) {
        @Override
        int encodedBitLength(int dataLength) {
            return DataSegmentNumeric.encodedBitLength(dataLength);
        }

        @Override
        int byteCount(int bitLength) {
            return DataSegmentNumeric.byteCount(bitLength);
        }

        @Override
        DataSegment newSegment(ByteSlice data) {
            return new DataSegmentNumeric(data);
        }

        @Override
        void checkEncodable(ByteSlice data) {
            checkEachByte(this, data, DataSegmentNumeric::isNumeric);
        }
    },

    /**
     * Alphanumeric mode.
     * <p>
     * Payload consisting of the digits 0 to 9, the uppercase letters A to Z, space, dollar,
     * percent, asterisk, plus, hyphen, period, slash and colon. Two characters are encoded in
     * 11 bits.
     * </p>
     */
    ALPHANUMERIC(2, new int[] { 9, 11, 13 }) {
        @Override
        int encodedBitLength(int dataLength) {
            return DataSegmentAlphanumeric.encodedBitLength(dataLength);
        }

        @Override
        int byteCount(int bitLength) {
            return DataSegmentAlphanumeric.byteCount(bitLength);
        }

        @Override
        DataSegment newSegment(ByteSlice data) {
            return new DataSegmentAlphanumeric(data);
        }

        @Override
        void checkEncodable(ByteSlice data) {
            checkEachByte(this, data, DataSegmentAlphanumeric::isAlphanumeric);
        }
    },

    /**
     * Kanji mode.
     * <p>
     * Payload consisting of double-byte Shift-JIS characters, or of any byte pairs within the
     * range of the Shift-JIS double-byte codes. Each pair of bytes is encoded in 13 bits.
     * </p>
     */
    KANJI(8, new int[] { 8, 10, 12 }) {
        @Override
        int encodedBitLength(int dataLength) {
            return DataSegmentKanji.encodedBitLength(dataLength);
        }

        @Override
        int byteCount(int bitLength) {
            return DataSegmentKanji.byteCount(bitLength);
        }

        @Override
        DataSegment newSegment(ByteSlice data) {
            return new DataSegmentKanji(data);
        }

        @Override
        void checkEncodable(ByteSlice data) {
            DataSegmentKanji.checkEncodable(data);
        }
    },

    /**
     * Binary mode.
     * <p>
     * Payload consisting of arbitrary bytes, encoded as they are.
     * </p>
     */
    BINARY(4, new int[] { 8, 16, 16 }) {
        @Override
        int encodedBitLength(int dataLength) {
            return DataSegmentBinary.encodedBitLength(dataLength);
        }

        @Override
        int byteCount(int bitLength) {
            return DataSegmentBinary.byteCount(bitLength);
        }

        @Override
        DataSegment newSegment(ByteSlice data) {
            return new DataSegmentBinary(data);
        }

        @Override
        void checkEncodable(ByteSlice data) {
            // any byte can be encoded in binary mode
        }
    },

    /**
     * ECI mode.
     * <p>
     * Segment carrying no payload but the character encoding of the segments that follow it.
     * </p>
     *
     * @see Eci
     */
    ECI(7, null),

    /**
     * Structured Append mode.
     * <p>
     * Segment carrying no payload but the position of the QR code within a sequence of QR codes
     * the data is split across.
     * </p>
     */
    STRUCTURED_APPEND(3, null);

    private final int modeIndicator;

    // The width (in bits) of the character count indicator for each of the version groups
    // 1-9, 10-26 and 27-40, or null if the mode has no character count indicator.
    private final int[] countIndicatorWidths;

    DataSegmentMode(int modeIndicator, int[] countIndicatorWidths) {
        this.modeIndicator = modeIndicator;
        this.countIndicatorWidths = countIndicatorWidths;
    }

    /**
     * Returns the 4-bit mode indicator introducing a segment of this mode.
     *
     * @return the mode indicator
     */
    int modeIndicator() {
        return modeIndicator;
    }

    /**
     * Indicates whether segments of this mode carry payload.
     * <p>
     * The data modes are exactly the modes with a character count indicator.
     * </p>
     *
     * @return {@code true} if this is a data mode
     */
    boolean isDataMode() {
        return countIndicatorWidths != null;
    }

    /**
     * Returns the width of the character count indicator for the specified version.
     *
     * @param version the QR code version (1&ndash;40)
     * @return the width, in bits, or 0 if this mode has no character count indicator
     */
    int countIndicatorLength(int version) {
        // The version groups 1-9, 10-26 and 27-40 map to the indexes 0, 1 and 2.
        return countIndicatorWidths != null ? countIndicatorWidths[(version + 7) / 17] : 0;
    }

    /**
     * Returns the length of the segment header (mode indicator and character count indicator).
     *
     * @param version the QR code version (1&ndash;40)
     * @return the header length, in bits
     */
    int headerLength(int version) {
        return 4 + countIndicatorLength(version);
    }

    /**
     * Returns the length of the encoded payload, without the header.
     * <p>
     * Each data mode overrides this with the formula of its segment type; the implementation here
     * covers the modes that carry no payload.
     * </p>
     *
     * @param dataLength the payload length, in bytes
     * @return the encoded length, in bits
     * @throws IllegalArgumentException if this is not a data mode
     */
    int encodedBitLength(int dataLength) {
        throw notADataMode();
    }

    /**
     * Returns the number of payload bytes fitting into the specified encoded length.
     *
     * @param bitLength the encoded length (without the header), in bits
     * @return the number of bytes
     * @throws IllegalArgumentException if this is not a data mode
     */
    int byteCount(int bitLength) {
        throw notADataMode();
    }

    /**
     * Returns the total length of a segment of this mode, including the header.
     *
     * @param dataLength the payload length, in bytes
     * @param version    the QR code version (1&ndash;40)
     * @return the segment length, in bits
     * @throws IllegalArgumentException if this is not a data mode
     */
    int segmentLength(int dataLength, int version) {
        return headerLength(version) + encodedBitLength(dataLength);
    }

    /**
     * Creates a data segment of this mode for the specified payload.
     * <p>
     * The payload is not copied, and its content is not checked &mdash; callers coming from
     * outside the library pass it through {@link #checkEncodable(ByteSlice)} first. Constraints on
     * the payload <em>length</em> are still enforced by the segment itself.
     * </p>
     *
     * @param data the payload
     * @return the data segment
     * @throws IllegalArgumentException if this is not a data mode, or if the payload has a length
     *                                  the mode cannot encode
     */
    DataSegment newSegment(ByteSlice data) {
        throw notADataMode();
    }

    /**
     * Checks that the specified data can be encoded in this mode.
     * <p>
     * This runs when data enters the library, not when the library splits data it has already
     * checked into segments of its own.
     * </p>
     *
     * @param data the payload
     * @throws IllegalArgumentException if this is not a data mode, or if the data cannot be
     *                                  encoded in this mode
     */
    void checkEncodable(ByteSlice data) {
        throw notADataMode();
    }

    /**
     * Checks each byte of the specified data individually against the specified predicate.
     * <p>
     * Kanji mode is not checked this way; its unit is a pair of bytes, not a single byte.
     * </p>
     *
     * @param mode        the mode being checked, for the error message
     * @param data        the payload
     * @param isEncodable the predicate a byte must satisfy
     * @throws IllegalArgumentException if a byte does not satisfy the predicate
     */
    private static void checkEachByte(DataSegmentMode mode, ByteSlice data, BytePredicate isEncodable) {
        for (var i = 0; i < data.length(); i += 1) {
            if (!isEncodable.test(data.at(i)))
                throw new IllegalArgumentException(String.format(
                        "Byte 0x%02x at index %d cannot be encoded in %s mode", data.at(i), i, mode));
        }
    }

    private IllegalArgumentException notADataMode() {
        return new IllegalArgumentException("Segment mode " + this + " carries no data");
    }

    /** A predicate on a single byte. */
    @FunctionalInterface
    private interface BytePredicate {
        boolean test(byte b);
    }
}

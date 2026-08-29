/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * A segment stating the position of this QR code within a Structured Append sequence.
 * <p>
 * The header is 16 bits: the position and the number of QR codes as 4-bit numbers (both one less
 * than their value), followed by the parity byte of the entire data. It is the first segment of
 * each QR code of the sequence.
 * </p>
 */
final class DataSegmentStructuredAppend extends DataSegment {

    /** The number of QR codes a Structured Append sequence can consist of. */
    static final int MAX_SEQUENCE_LENGTH = 16;

    private static final int ENCODED_BIT_LENGTH = 16;

    private final int position;
    private final int total;
    private final int parity;

    DataSegmentStructuredAppend(int position, int total, int parity) {
        super(DataSegmentMode.STRUCTURED_APPEND, ByteSlice.EMPTY, ENCODED_BIT_LENGTH);

        if (total < 1 || total > MAX_SEQUENCE_LENGTH)
            throw new IllegalArgumentException(
                    "total must be between 1 and " + MAX_SEQUENCE_LENGTH + ", got " + total);
        if (position < 1 || position > MAX_SEQUENCE_LENGTH)
            throw new IllegalArgumentException(
                    "position must be between 1 and " + MAX_SEQUENCE_LENGTH + ", got " + position);
        if (position > total)
            throw new IllegalArgumentException(
                    "position must not exceed total, got position " + position + " and total " + total);
        if (parity < 0 || parity > 255)
            throw new IllegalArgumentException("parity must be between 0 and 255, got " + parity);

        this.position = position;
        this.total = total;
        this.parity = parity;
    }

    /**
     * Returns the position of this QR code within the sequence (1&ndash;16).
     *
     * @return the position
     */
    int position() {
        return position;
    }

    /**
     * Returns the number of QR codes in the sequence (1&ndash;16).
     *
     * @return the number of QR codes
     */
    int total() {
        return total;
    }

    /**
     * Returns the parity of the entire data (0&ndash;255).
     *
     * @return the parity
     */
    int parity() {
        return parity;
    }

    @Override
    void writeToBitStream(BitStream bitStream) {
        bitStream.appendBits(position - 1, 4);
        bitStream.appendBits(total - 1, 4);
        bitStream.appendBits(parity, 8);
    }
}

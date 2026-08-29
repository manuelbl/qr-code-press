/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.Objects;

/**
 * A segment announcing the character encoding (ECI) of the segments that follow it.
 * <p>
 * The designator is encoded in one, two or three bytes, depending on its value. The number of
 * leading '1' bits tells the decoder how many bytes to read.
 * </p>
 */
final class DataSegmentEci extends DataSegment {

    private final Eci designator;

    DataSegmentEci(Eci designator) {
        super(DataSegmentMode.ECI, ByteSlice.EMPTY, encodedBitLength(designatorValue(designator)));
        this.designator = designator;
    }

    private static int designatorValue(Eci designator) {
        Objects.requireNonNull(designator, "designator");
        var value = designator.getValue();
        if (value < 0)
            throw new IllegalArgumentException("Eci." + (value == Eci.NONE_VALUE ? "NONE" : "AUTOMATIC")
                    + " is an instruction to the encoder, not an ECI designator, and cannot be encoded");
        return value;
    }

    private static int encodedBitLength(int value) {
        if (value <= 127)
            return 8;
        if (value <= 16383)
            return 16;
        return 24;
    }

    /**
     * Returns the ECI designator announced by this segment.
     *
     * @return the designator
     */
    Eci designator() {
        return designator;
    }

    @Override
    void writeToBitStream(BitStream bitStream) {
        var value = designator.getValue();
        if (value <= 127) {
            bitStream.appendBits(value, 8);
        } else if (value <= 16383) {
            bitStream.appendBits(0b10_000000_00000000 | value, 16);
        } else {
            bitStream.appendBits(0b110_00000_00000000_00000000 | value, 24);
        }
    }
}

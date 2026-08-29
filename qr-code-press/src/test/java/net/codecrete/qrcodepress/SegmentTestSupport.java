/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Helpers shared by the data segment tests.
 */
final class SegmentTestSupport {

    static final Charset SHIFT_JIS = Eci.SHIFT_JIS.getCharset();

    private SegmentTestSupport() {
        // non-instantiable
    }

    /**
     * Writes the encoded data of the segment (without the header) to a bit stream.
     *
     * @param segment the segment
     * @return the bit stream
     */
    static BitStream encode(DataSegment segment) {
        var bitStream = new BitStream(segment.encodedLength() / 8 + 4);
        segment.writeToBitStream(bitStream);
        return bitStream;
    }

    /**
     * Returns the encoded data of the segment (without the header), padded to whole bytes.
     *
     * @param segment the segment
     * @return the codewords
     */
    static byte[] codewords(DataSegment segment) {
        return encode(segment).codewords();
    }

    /**
     * Returns the unencoded data of the segment.
     *
     * @param segment the segment
     * @return the data
     */
    static byte[] dataOf(DataSegment segment) {
        return segment.data().toArray();
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] shiftJis(String text) {
        return text.getBytes(SHIFT_JIS);
    }

    static ByteSlice sliceOfUtf8(String text) {
        return ByteSlice.of(utf8(text));
    }

    static ByteSlice sliceOfShiftJis(String text) {
        return ByteSlice.of(shiftJis(text));
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A chunk of the payload carried by a QR code.
 * <p>
 * The payload of a QR code is a sequence of data segments. Each segment holds a run of bytes
 * encoded in one of the four data modes (numeric, alphanumeric, Kanji, binary), or it announces a
 * character encoding (ECI) or the position within a Structured Append sequence.
 * </p>
 * <p>
 * Splitting the payload into segments is what makes a QR code small: a run of digits takes 3.33
 * bits per character in numeric mode instead of 8 in binary mode. The encoder does the splitting
 * itself; segments are built by hand only when the payload is to be encoded in a specific way.
 * </p>
 * <p>
 * Segments retain their data in the unencoded form until the bit stream of the QR code is created.
 * Instances are immutable and safe to share between threads; the payload is copied when it is
 * handed to this class.
 * </p>
 */
public abstract sealed class DataSegment
        permits DataSegmentNumeric, DataSegmentAlphanumeric, DataSegmentKanji, DataSegmentBinary,
        DataSegmentEci, DataSegmentStructuredAppend {

    private final DataSegmentMode mode;
    private final ByteSlice data;
    private final int encodedLength;

    /**
     * Creates a new instance.
     *
     * @param mode          the segment mode
     * @param data          the payload (not copied), or {@link ByteSlice#EMPTY} if the mode carries
     *                      no payload
     * @param encodedLength the length of the encoded payload, without the header, in bits
     */
    DataSegment(DataSegmentMode mode, ByteSlice data, int encodedLength) {
        this.mode = mode;
        this.data = data;
        this.encodedLength = encodedLength;
    }

    // region Factory methods

    /**
     * Creates a data segment encoding the specified data in the specified mode.
     * <p>
     * The data is copied, so it may be modified after this call.
     * </p>
     *
     * @param mode the segment mode; one of the data modes {@link DataSegmentMode#NUMERIC},
     *             {@link DataSegmentMode#ALPHANUMERIC}, {@link DataSegmentMode#KANJI} and
     *             {@link DataSegmentMode#BINARY}
     * @param data the data to encode
     * @return the data segment
     * @throws IllegalArgumentException if the mode carries no data, or if the data cannot be
     *                                  encoded in the specified mode
     */
    public static DataSegment of(DataSegmentMode mode, byte[] data) {
        Objects.requireNonNull(data, "data");
        return of(mode, data, 0, data.length);
    }

    /**
     * Creates a data segment encoding the specified range of the specified data in the specified
     * mode.
     * <p>
     * The data is copied, so it may be modified after this call.
     * </p>
     *
     * @param mode   the segment mode; one of the data modes {@link DataSegmentMode#NUMERIC},
     *               {@link DataSegmentMode#ALPHANUMERIC}, {@link DataSegmentMode#KANJI} and
     *               {@link DataSegmentMode#BINARY}
     * @param data   the array containing the data to encode
     * @param offset the index of the first byte to encode
     * @param length the number of bytes to encode
     * @return the data segment
     * @throws IllegalArgumentException  if the mode carries no data, or if the data cannot be
     *                                   encoded in the specified mode
     * @throws IndexOutOfBoundsException if the range is out of bounds
     */
    public static DataSegment of(DataSegmentMode mode, byte[] data, int offset, int length) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(data, "data");
        if (!mode.isDataMode())
            throw new IllegalArgumentException("Segment mode " + mode + " carries no data; use "
                    + (mode == DataSegmentMode.ECI ? "ofEci(...)" : "ofStructuredAppend(...)") + " instead");

        var slice = ByteSlice.copyOf(data, offset, length);
        mode.checkEncodable(slice);
        return mode.newSegment(slice);
    }

    /**
     * Creates an ECI segment announcing the specified character encoding.
     * <p>
     * The segment applies to all segments that follow it.
     * </p>
     *
     * @param designator the ECI designator
     * @return the data segment
     * @throws IllegalArgumentException if the designator is {@link Eci#NONE} or
     *                                  {@link Eci#AUTOMATIC}, which are instructions to the encoder
     *                                  rather than designators
     */
    public static DataSegment ofEci(Eci designator) {
        return new DataSegmentEci(designator);
    }

    /**
     * Creates a Structured Append segment.
     * <p>
     * The segment states the position of this QR code within a sequence of QR codes the data is
     * split across. It must be the first segment of the QR code.
     * </p>
     *
     * @param position the position of the QR code within the sequence (1&ndash;16)
     * @param total    the number of QR codes in the sequence (1&ndash;16)
     * @param parity   the parity of the entire data, as a value from 0 to 255
     * @return the data segment
     * @throws IllegalArgumentException if 1 &le; position &le; total &le; 16 is violated, or if the
     *                                  parity is out of range
     */
    public static DataSegment ofStructuredAppend(int position, int total, int parity) {
        return new DataSegmentStructuredAppend(position, total, parity);
    }

    // endregion

    // region Segmentation

    /**
     * Builds the segments encoding the specified text with the shortest bit stream.
     * <p>
     * The character encoding is chosen as follows:
     * </p>
     * <ul>
     * <li>{@link Eci#AUTOMATIC}: ISO-8859-1 without an ECI segment if the text survives it
     * unchanged, UTF-8 with an ECI segment otherwise. {@code charset} is ignored.</li>
     * <li>{@link Eci#NONE}: {@code charset}, which is mandatory in this case, and no ECI
     * segment.</li>
     * <li>any other designator: {@code charset}, or the character set of the designator if
     * {@code charset} is {@code null}, and an ECI segment announcing the designator.</li>
     * </ul>
     *
     * @param text          the text to encode
     * @param eci           the ECI designator
     * @param charset       the character set to encode the text with, or {@code null} to derive it
     *                      from the designator
     * @param version       the QR code version (1&ndash;40) the segments are optimized for
     * @param kanjiStrategy the Kanji mode strategy
     * @return the segments
     * @throws IllegalArgumentException if the designator is {@link Eci#NONE} and no character set
     *                                  is specified
     * @throws EciException             if the character set of the designator is unavailable
     */
    static List<DataSegment> fromText(String text, Eci eci, Charset charset, int version,
            KanjiStrategy kanjiStrategy) {
        var encoded = encodeText(text, eci, charset);
        return fromBinary(ByteSlice.of(encoded.data()), encoded.eci(), version,
                kanjiStrategy.appliesTo(encoded.eci()));
    }

    /**
     * A text encoded as the bytes a QR code carries, and how it is to be announced.
     *
     * @param data    the encoded text
     * @param eci     the ECI designator announcing the character encoding, or {@link Eci#NONE} if
     *                the encoding is the default one and needs no announcement
     * @param charset the character set the text was encoded with
     */
    record EncodedText(byte[] data, Eci eci, Charset charset) {
    }

    /**
     * Encodes the specified text as the bytes a QR code carries.
     * <p>
     * This resolves the character encoding exactly as {@link #fromText} documents it, and is the
     * single place where {@link Eci#AUTOMATIC} and {@link Eci#NONE} are given their meaning. It is
     * separate from segmentation because a sequence of QR codes encodes the whole text once and
     * segments the parts.
     * </p>
     *
     * @param text    the text to encode
     * @param eci     the ECI designator
     * @param charset the character set to encode the text with, or {@code null} to derive it from
     *                the designator
     * @return the encoded text, with the designator and character set actually used
     * @throws IllegalArgumentException if the designator is {@link Eci#NONE} and no character set
     *                                  is specified
     * @throws EciException             if the character set of the designator is unavailable
     */
    static EncodedText encodeText(String text, Eci eci, Charset charset) {
        byte[] data = null;

        if (Eci.AUTOMATIC.equals(eci)) {
            // Latin-1 is what a QR code means by default, so text that fits it needs no ECI
            // segment at all — 12 bits saved, and readable by scanners that ignore ECI.
            data = encodeAsLatin1(text);
            if (data != null) {
                eci = Eci.NONE;
                charset = StandardCharsets.ISO_8859_1;
            } else {
                eci = Eci.UTF_8;
                charset = StandardCharsets.UTF_8;
            }
        } else if (Eci.NONE.equals(eci)) {
            if (charset == null)
                throw new IllegalArgumentException(
                        "A character set is required if the ECI designator is Eci.NONE.");
        } else if (charset == null) {
            charset = eci.getCharset();
        }

        if (data == null)
            data = text.getBytes(charset);

        return new EncodedText(data, eci, charset);
    }

    /**
     * Builds the segments encoding the specified data with the shortest bit stream.
     * <p>
     * The data is not copied. The resulting segments refer to it, so it must be owned by the
     * library.
     * </p>
     *
     * @param data              the data to encode
     * @param eci               the ECI designator, or {@link Eci#NONE} to add no ECI segment
     * @param version           the QR code version (1&ndash;40) the segments are optimized for
     * @param considerKanjiMode {@code true} if Kanji mode may be used
     * @return the segments
     */
    static List<DataSegment> fromBinary(ByteSlice data, Eci eci, int version, boolean considerKanjiMode) {
        var dataSegments = SegmentCompaction.buildSegments(data, version, considerKanjiMode);
        if (Eci.NONE.equals(eci))
            return dataSegments;

        var segments = new ArrayList<DataSegment>(dataSegments.size() + 1);
        segments.add(new DataSegmentEci(eci));
        segments.addAll(dataSegments);
        return segments;
    }

    /**
     * Encodes the specified text as ISO-8859-1.
     *
     * @param text the text
     * @return the encoded text, or {@code null} if it contains characters ISO-8859-1 cannot
     *         represent
     */
    static byte[] encodeAsLatin1(String text) {
        var encoder = StandardCharsets.ISO_8859_1.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            var encoded = encoder.encode(CharBuffer.wrap(text));
            var data = new byte[encoded.remaining()];
            encoded.get(data);
            return data;
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    // endregion

    // region Properties

    /**
     * Returns the mode of this segment.
     *
     * @return the mode
     */
    public DataSegmentMode getMode() {
        return mode;
    }

    /**
     * Returns the length of the unencoded data of this segment.
     * <p>
     * Segments that carry no data have the length 0.
     * </p>
     *
     * @return the length, in bytes
     */
    public int getDataLength() {
        return data.length();
    }

    /**
     * Returns the payload of this segment.
     *
     * @return the payload
     */
    ByteSlice data() {
        return data;
    }

    /**
     * Returns the value of the character count indicator of this segment.
     * <p>
     * The indicator counts the characters the segment encodes, which is the number of payload
     * bytes for every mode but Kanji, where each character occupies two bytes.
     * </p>
     *
     * @return the character count
     */
    int characterCount() {
        return data.length();
    }

    /**
     * Returns the length of the encoded data of this segment, without the header.
     *
     * @return the length, in bits
     */
    int encodedLength() {
        return encodedLength;
    }

    /**
     * Returns the total length of this segment, including the mode indicator and the character
     * count indicator.
     *
     * @param version the QR code version (1&ndash;40)
     * @return the length, in bits
     */
    int totalLength(int version) {
        return mode.headerLength(version) + encodedLength;
    }

    /**
     * Returns the total length of the specified segments.
     *
     * @param segments the segments
     * @param version  the QR code version (1&ndash;40)
     * @return the length, in bits
     */
    static int totalLength(List<DataSegment> segments, int version) {
        var length = 0;
        for (var segment : segments)
            length += segment.totalLength(version);
        return length;
    }

    // endregion

    // region Bit stream

    /**
     * Creates the bit stream for the specified segments.
     * <p>
     * The bit stream ends with the terminator. If the capacity is insufficient for the terminator,
     * it is truncated or omitted entirely, as the QR code specification provides for.
     * </p>
     *
     * @param segments the segments to encode
     * @param version  the QR code version (1&ndash;40)
     * @param capacity the capacity of the bit stream, in bytes
     * @return the bit stream
     */
    static BitStream createBitStream(List<DataSegment> segments, int version, int capacity) {
        var bitStream = new BitStream(capacity);
        for (var segment : segments) {
            var mode = segment.mode;
            bitStream.appendBits(mode.modeIndicator(), 4);
            if (mode.isDataMode())
                bitStream.appendBits(segment.characterCount(), mode.countIndicatorLength(version));
            segment.writeToBitStream(bitStream);
        }

        var terminatorLength = Math.min(4, capacity * 8 - bitStream.length());
        if (terminatorLength > 0)
            bitStream.appendBits(0, terminatorLength);

        return bitStream;
    }

    /**
     * Writes the encoded data of this segment to the specified bit stream.
     * <p>
     * The mode indicator and the character count indicator are not written.
     * </p>
     *
     * @param bitStream the bit stream
     */
    abstract void writeToBitStream(BitStream bitStream);

    // endregion

    @Override
    public String toString() {
        return "DataSegment[mode=" + mode + ", dataLength=" + getDataLength() + "]";
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a payload across a sequence of QR codes linked by Structured Append.
 * <p>
 * Each QR code of the sequence starts with a Structured Append segment stating its position, the
 * length of the sequence and the parity of the entire payload, so that a scanner can reassemble the
 * parts in the right order and notice that they belong together. Up to
 * {@value DataSegmentStructuredAppend#MAX_SEQUENCE_LENGTH} QR codes can be linked this way.
 * </p>
 * <p>
 * The payload is spread as evenly as possible over the least number of QR codes; see
 * {@link #build}.
 * </p>
 */
final class StructuredAppend {

    /**
     * The version the segments are compacted at, once, so the result can be reused.
     * <p>
     * The compaction depends only marginally on the version &mdash; it merely affects the width of
     * the character count indicators. Version 20 lies in the middle indicator band (10&ndash;26),
     * so it is exact for that band and a close approximation for the others.
     * </p>
     */
    private static final int COMPACTION_VERSION = 20;

    private StructuredAppend() {
        // non-instantiable
    }

    /**
     * The segments of the QR codes of a sequence, and the version they share.
     *
     * @param codes   the segments of each QR code, in sequence order
     * @param version the QR code version (1&ndash;40) all of them use
     */
    record Sequence(List<List<DataSegment>> codes, int version) {
    }

    /**
     * Distributes the specified data evenly over a sequence of QR codes.
     * <p>
     * The sequence uses the least possible number of QR codes, which is what the largest acceptable
     * version yields, then the smallest shared version that still fits that number of QR codes, and
     * within it spreads the data so that the fullest QR code is as empty as possible. All QR codes
     * of the sequence use the same version.
     * </p>
     *
     * @param encoded    the encoded text to distribute
     * @param minVersion the smallest acceptable version (1&ndash;40)
     * @param maxVersion the largest acceptable version (1&ndash;40)
     * @param ecc        the error correction level (0&ndash;3)
     * @return the sequence
     * @throws DataTooLongException if the data does not fit into
     *                              {@value DataSegmentStructuredAppend#MAX_SEQUENCE_LENGTH} QR
     *                              codes of the largest acceptable version
     */
    static Sequence build(DataSegment.EncodedText encoded, int minVersion, int maxVersion, int ecc) {
        // The compaction is the expensive part of a split and depends only marginally on the
        // version, so it runs once here and the resulting segments are reused by every fit check
        // below. They are only read, never modified.
        var segments = SegmentCompaction.buildSegments(ByteSlice.of(encoded.data()), COMPACTION_VERSION, false);

        // UTF-8 is the one multi-byte encoding a sequence accepts, and the only one whose
        // character boundaries the split has to respect; every other accepted charset is
        // single-byte, where any cut is a boundary.
        var isUtf8 = StandardCharsets.UTF_8.equals(encoded.charset());

        // Step 1: the least number of QR codes, which the largest version yields.
        var maxCapacity = capacity(maxVersion, ecc, encoded.eci());
        var numCodes = split(segments, maxVersion, maxCapacity, isUtf8).size();
        if (numCodes > DataSegmentStructuredAppend.MAX_SEQUENCE_LENGTH
                || !fitsInCodes(segments, maxVersion, maxCapacity, isUtf8, numCodes))
            throw new DataTooLongException(tooLongMessage(maxVersion, ecc));

        // Step 2: the smallest shared version that still fits that number of QR codes.
        var version = maxVersion;
        for (var v = minVersion; v < maxVersion; v += 1) {
            if (fitsInCodes(segments, v, capacity(v, ecc, encoded.eci()), isUtf8, numCodes)) {
                version = v;
                break;
            }
        }

        // Step 3: the smallest per-code capacity that still fits that number of QR codes. Filling
        // every QR code only that far is what spreads the data evenly.
        var low = 1;
        var high = capacity(version, ecc, encoded.eci());
        while (low < high) {
            var mid = low + (high - low) / 2;
            if (fitsInCodes(segments, version, mid, isUtf8, numCodes)) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }

        var codes = split(segments, version, low, isUtf8);
        addHeaders(codes, encoded);
        return new Sequence(codes, version);
    }

    /**
     * Returns the capacity of a single QR code of a sequence, for payload data.
     * <p>
     * This is the data capacity of the version and error correction level, less the Structured
     * Append header and the ECI header every QR code of the sequence carries.
     * </p>
     *
     * @param version the QR code version (1&ndash;40)
     * @param ecc     the error correction level (0&ndash;3)
     * @param eci     the ECI designator every QR code announces, or {@link Eci#NONE} for none
     * @return the capacity, in bits
     */
    private static int capacity(int version, int ecc, Eci eci) {
        // Neither header depends on the version, so the version passed here does not matter.
        var headerLength = new DataSegmentStructuredAppend(1, 1, 0).totalLength(version);
        if (!Eci.NONE.equals(eci))
            headerLength += new DataSegmentEci(eci).totalLength(version);

        return 8 * QrCodeParameters.dataCodewordCapacity(version, ecc) - headerLength;
    }

    /**
     * Indicates whether the specified segments fit into the specified number of QR codes, filling
     * each of them at most to the specified capacity.
     * <p>
     * Counting the QR codes of the split is not sufficient on its own: where the capacity is too
     * small for a single character, the split has to overfill a QR code to make progress at all.
     * </p>
     *
     * @param segments the segments to distribute
     * @param version  the QR code version (1&ndash;40)
     * @param capacity the per-code capacity, in bits
     * @param isUtf8   {@code true} if the data is UTF-8 encoded text
     * @param maxCodes the number of QR codes to fit into
     * @return {@code true} if the segments fit
     */
    private static boolean fitsInCodes(List<DataSegment> segments, int version, int capacity, boolean isUtf8,
            int maxCodes) {
        var codes = split(segments, version, capacity, isUtf8);
        if (codes.size() > maxCodes)
            return false;

        for (var code : codes) {
            if (DataSegment.totalLength(code, version) > capacity)
                return false;
        }
        return true;
    }

    /**
     * Distributes the specified segments over QR codes, filling each of them to the specified
     * capacity and splitting segments where needed.
     * <p>
     * The segments are only read, never modified, so the same list may be split again and again
     * with different versions and capacities.
     * </p>
     *
     * @param segments the segments to distribute
     * @param version  the QR code version (1&ndash;40)
     * @param capacity the per-code capacity, in bits
     * @param isUtf8   {@code true} if the data is UTF-8 encoded text, which is split at character
     *                 boundaries only
     * @return the segments of each QR code, in sequence order
     */
    private static List<List<DataSegment>> split(List<DataSegment> segments, int version, int capacity,
            boolean isUtf8) {
        var codes = new ArrayList<List<DataSegment>>();
        var current = new ArrayList<DataSegment>();
        var bitLength = 0;

        for (var wholeSegment : segments) {
            var segment = wholeSegment;
            while (true) {
                var segmentLength = segment.totalLength(version);
                if (bitLength + segmentLength <= capacity) {
                    current.add(segment);
                    bitLength += segmentLength;
                    break;
                }

                // The segment does not fit into the current QR code: split off what does.
                var mode = segment.getMode();
                var numBytes = mode.byteCount(capacity - bitLength - mode.headerLength(version));

                if (isUtf8) {
                    // Move the cut back to a character boundary. The specification does not require
                    // this, but a scanner that decodes each QR code on its own needs it.
                    var data = segment.data();
                    while (numBytes > 0 && (data.at(numBytes) & 0xc0) == 0x80)
                        numBytes -= 1;
                }

                if (numBytes <= 0 && !current.isEmpty()) {
                    // Nothing more fits into a QR code that already holds data: close it and retry
                    // the untouched segment in a fresh one.
                    codes.add(current);
                    current = new ArrayList<>();
                    bitLength = 0;
                    continue;
                }

                // Guarantee progress even where the capacity is too small for a single character,
                // a degenerate case the callers reject through their per-code capacity check.
                if (numBytes <= 0)
                    numBytes = 1;

                current.add(mode.newSegment(segment.data().slice(0, numBytes)));
                codes.add(current);
                current = new ArrayList<>();
                bitLength = 0;

                // The remainder may well fill further QR codes.
                segment = mode.newSegment(segment.data().slice(numBytes, segment.getDataLength() - numBytes));
            }
        }

        codes.add(current);
        return codes;
    }

    /**
     * Prefixes each QR code of the specified sequence with its Structured Append segment and, if
     * one is needed, the ECI segment.
     *
     * @param codes   the segments of each QR code, in sequence order
     * @param encoded the entire encoded text of the sequence, whose parity every QR code carries
     */
    private static void addHeaders(List<List<DataSegment>> codes, DataSegment.EncodedText encoded) {
        var parity = parity(encoded.data());
        var eci = encoded.eci();
        var eciSegment = !Eci.NONE.equals(eci) ? new DataSegmentEci(eci) : null;

        for (var i = 0; i < codes.size(); i += 1) {
            var segments = codes.get(i);
            segments.add(0, new DataSegmentStructuredAppend(i + 1, codes.size(), parity));
            if (eciSegment != null)
                segments.add(1, eciSegment);
        }
    }

    /**
     * Returns the parity of the specified data, which is the exclusive-or of all its bytes.
     *
     * @param data the data
     * @return the parity (0&ndash;255)
     */
    private static int parity(byte[] data) {
        var parity = 0;
        for (var b : data)
            parity ^= b & 0xff;
        return parity;
    }

    /**
     * Returns the message for data exceeding the capacity of a full sequence.
     *
     * @param version the QR code version (1&ndash;40) of the QR codes of the sequence
     * @param ecc     the error correction level (0&ndash;3)
     * @return the message
     */
    private static String tooLongMessage(int version, int ecc) {
        return "Data is too long to fit into " + DataSegmentStructuredAppend.MAX_SEQUENCE_LENGTH
                + " QR codes with version " + version + " and error correction level "
                + VersionPlanner.eccLetter(ecc) + ".";
    }
}

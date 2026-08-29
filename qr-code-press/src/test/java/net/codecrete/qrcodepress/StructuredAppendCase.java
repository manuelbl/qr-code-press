/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static net.codecrete.qrcodepress.SegmentTestSupport.dataOf;

/**
 * One case of the Structured Append fixture: the input half of a row of {@code
 * verified/structuredappend.tsv}.
 * <p>
 * Unlike every other fixture, the payloads here are not the texts in {@code input/texts/}: a
 * sequence needs kilobytes. A case therefore states its text as a generator, a length and a seed
 * rather than carrying it. A text stated that way cannot drift by a typo.
 * </p>
 * <p>
 * The record is shared by all three ends of the fixture: {@link VerifiedDataExport} reads its cases from
 * {@code input/sequences.tsv} and writes a row from each, and {@link StructuredAppendTest} parses a
 * row of the fixture back into one. Both column layouts are therefore defined here, once.
 * </p>
 *
 * @param generator  the text generator, {@code alphanumeric} or {@code mixed}
 * @param length     the number of characters to generate
 * @param seed       the seed of the generator
 * @param eci        the ECI token, {@code none}, {@code latin1} or {@code utf8}
 * @param minVersion the smallest acceptable version (1&ndash;40)
 * @param maxVersion the largest acceptable version (1&ndash;40)
 * @param ecc        the error correction level
 */
record StructuredAppendCase(String generator, int length, int seed, String eci,
                            int minVersion, int maxVersion, Ecc ecc) {

    /**
     * The length of an input row whose length the export searches for rather than reads.
     *
     * @see VerifiedDataExport
     */
    static final String SEARCHED_LENGTH = "search";

    /**
     * Parses a row of {@code input/sequences.tsv}, which states no case index and may leave the
     * length to the boundary search.
     *
     * @param row the row, split into its columns
     * @return the case, with a length of zero where the export searches for one
     */
    static StructuredAppendCase fromInputRow(String[] row) {
        var length = SEARCHED_LENGTH.equals(row[1]) ? 0 : Integer.parseInt(row[1]);
        return new StructuredAppendCase(row[0], length, Integer.parseInt(row[2]), row[3],
                Integer.parseInt(row[4]), Integer.parseInt(row[5]),
                Ecc.valueOf(row[6].toUpperCase(Locale.ROOT)));
    }

    /**
     * Parses the input columns of a fixture row, which are preceded by the case index.
     *
     * @param row the row, split into its columns
     * @return the case
     */
    static StructuredAppendCase fromRow(String[] row) {
        return new StructuredAppendCase(row[1], Integer.parseInt(row[2]), Integer.parseInt(row[3]),
                row[4], Integer.parseInt(row[5]), Integer.parseInt(row[6]),
                Ecc.valueOf(row[7].toUpperCase(Locale.ROOT)));
    }

    /**
     * Returns the same case with a different text length, as the boundary search needs it.
     *
     * @param newLength the number of characters to generate
     * @return the case
     */
    StructuredAppendCase withLength(int newLength) {
        return new StructuredAppendCase(generator, newLength, seed, eci, minVersion, maxVersion, ecc);
    }

    /**
     * Returns the text of the case.
     *
     * @return the text
     */
    String text() {
        return switch (generator) {
            case "alphanumeric" -> RandomText.alphanumeric(length, seed);
            case "mixed" -> RandomText.mixed(length, seed);
            default -> throw new IllegalArgumentException("unknown generator: " + generator);
        };
    }

    /**
     * Returns the payload of the case: its text, encoded as its ECI token says.
     * <p>
     * {@code none} and {@code latin1} produce the same bytes and differ only in whether every QR
     * code of the sequence announces the encoding, which costs capacity the split has to work
     * within.
     * </p>
     *
     * @return the encoded text
     */
    DataSegment.EncodedText encoded() {
        var text = text();
        return switch (eci) {
            case "none" -> new DataSegment.EncodedText(text.getBytes(StandardCharsets.ISO_8859_1),
                    Eci.NONE, StandardCharsets.ISO_8859_1);
            case "latin1" -> new DataSegment.EncodedText(text.getBytes(StandardCharsets.ISO_8859_1),
                    Eci.LATIN_1, StandardCharsets.ISO_8859_1);
            case "utf8" -> new DataSegment.EncodedText(text.getBytes(StandardCharsets.UTF_8),
                    Eci.UTF_8, StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException("unknown ECI token: " + eci);
        };
    }

    /**
     * Returns the payload the specified QR code of a sequence carries, headers excluded.
     *
     * @param code the segments of the QR code
     * @return the payload bytes
     */
    static byte[] payloadOf(List<DataSegment> code) {
        var payload = new ByteArrayOutputStream();
        for (var segment : code) {
            if (segment.getMode().isDataMode())
                payload.writeBytes(dataOf(segment));
        }
        return payload.toByteArray();
    }

    /**
     * Returns the number of payload bytes each QR code of a sequence carries, headers excluded.
     * <p>
     * The Structured Append and ECI segments hold no data bytes, but they are filtered by mode
     * rather than by length, so a header that ever gained one would not silently be counted as
     * payload.
     * </p>
     *
     * @param codes the segments of each QR code, in sequence order
     * @return the payload lengths, in sequence order
     */
    static int[] payloadLengths(List<List<DataSegment>> codes) {
        return codes.stream().mapToInt(code -> payloadOf(code).length).toArray();
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Access to the verified test data.
 * <p>
 * Two resource directories are involved:
 * </p>
 * <ul>
 *     <li>{@code src/test/resources/input/} holds the hand-written inputs — the payload texts, the
 * case parameters and the sequence cases,
 *     <li>{@code src/test/resources/verified/} holds what
 * {@link VerifiedDataExport} derives from them, and is never edited by hand.
 * </ul>
 * <p>
 * The manifests of both are tab-separated, with comment lines naming the columns.
 * </p>
 */
final class VerifiedData {

    private static final HexFormat HEX = HexFormat.of();

    /**
     * The versions the exporter dumped in full, chosen for their structural differences.
     * <p>
     * It is the exporter's choice, so it is stated there and only read here.
     * </p>
     */
    static final int[] SAMPLE_VERSIONS = VerifiedDataExport.SAMPLE_VERSIONS;

    private VerifiedData() {
        // non-instantiable
    }

    /**
     * Provides every QR code version, for tests asserting a property across the whole range.
     *
     * @return one argument per version
     */
    static Stream<Arguments> allVersions() {
        return IntStream.rangeClosed(QrCodeParameters.MIN_VERSION, QrCodeParameters.MAX_VERSION)
                .mapToObj(Arguments::arguments);
    }

    /**
     * Reads a verified data file as text.
     *
     * @param name the file name, relative to the verified data directory
     * @return the content
     */
    static String read(String name) {
        return readResource("/verified/" + name);
    }

    /**
     * Reads a hand-written input file as text.
     *
     * @param name the file name, relative to the input directory
     * @return the content
     */
    static String readInput(String name) {
        return readResource("/input/" + name);
    }

    private static String readResource(String resource) {
        try (InputStream stream = VerifiedData.class.getResourceAsStream(resource)) {
            assertThat(stream).as("test resource %s", resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Reads a tab-separated data file, without its comment lines.
     *
     * @param name the file name, relative to the verified data directory
     * @return the rows, each split into its columns
     */
    static List<String[]> readTsv(String name) {
        return rowsOf(read(name));
    }

    /**
     * Reads a tab-separated input file, without its comment lines.
     *
     * @param name the file name, relative to the input directory
     * @return the rows, each split into its columns
     */
    @SuppressWarnings("SameParameterValue")
    static List<String[]> readInputTsv(String name) {
        return rowsOf(readInput(name));
    }

    private static List<String[]> rowsOf(String content) {
        return content.lines()
                .filter(line -> !line.startsWith("#") && !line.isEmpty())
                .map(line -> line.split("\t", -1))
                .toList();
    }

    /**
     * Decodes a hexadecimal string from the verified data.
     *
     * @param hex the hexadecimal string
     * @return the bytes
     */
    static byte[] bytes(String hex) {
        return HEX.parseHex(hex);
    }

    /**
     * Encodes bytes the way the verified data does, so that actual and expected values compare as
     * strings.
     *
     * @param bytes the bytes
     * @return the hexadecimal string
     */
    static String hex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }

    // region QR Code Cases

    /**
     * One of the exported QR code encoding cases.
     *
     * @param index            the case index, which also names the file of expected modules,
     *                         {@code verified/qrcodes/<case>.txt}
     * @param textIndex        the index of the payload text
     * @param requestedEcc     the requested error correction level (0&ndash;3)
     * @param minVersion       the smallest acceptable version
     * @param maxVersion       the largest acceptable version
     * @param boostEcc         whether the error correction level was allowed to be raised
     * @param effectiveEcc     the error correction level of the resulting QR code (0&ndash;3)
     * @param effectiveVersion the version of the resulting QR code
     * @param effectiveMask    the mask of the resulting QR code (0&ndash;7)
     */
    record QrCodeCase(int index, int textIndex, int requestedEcc, int minVersion, int maxVersion,
                      boolean boostEcc, int effectiveEcc, int effectiveVersion, int effectiveMask) {
    }

    /**
     * Reads the manifest of exported encoding cases.
     *
     * @return the cases, in the order of their index
     */
    static List<QrCodeCase> qrCodeCases() {
        return readTsv("qrcodes.tsv").stream()
                .map(row -> new QrCodeCase(Integer.parseInt(row[0]), Integer.parseInt(row[1]), Ecc.valueOf(row[2]).ordinal(),
                        Integer.parseInt(row[3]), Integer.parseInt(row[4]), Boolean.parseBoolean(row[5]),
                        Ecc.valueOf(row[6]).ordinal(), Integer.parseInt(row[7]), Integer.parseInt(row[8])))
                .toList();
    }

    /**
     * Reads the expected module rows of an encoding case, in the rendering
     * {@link TestMatrices#rowsOf(BitMatrix)} produces.
     *
     * @param caseIndex the case index
     * @return the rows, {@code #} for a dark module and {@code .} for a light one
     */
    static List<String> moduleRows(int caseIndex) {
        return read(String.format("qrcodes/%04d.txt", caseIndex)).lines().toList();
    }

    // endregion

    // region Payload texts

    /**
     * The version the exporter built the segments for.
     * <p>
     * It is the exporter's choice, so it is stated there and only read here.
     * </p>
     */
    private static final int SEGMENT_VERSION = VerifiedDataExport.SEGMENT_VERSION;

    private static final Map<Integer, List<DataSegment>> SEGMENTS = new ConcurrentHashMap<>();

    /**
     * Reads one of the payload texts.
     *
     * @param textIndex the index of the text
     * @return the text
     */
    static String text(int textIndex) {
        var text = readInput(readInputTsv("texts.tsv").get(textIndex)[1]);

        // The exported manifest carries the character count the exporter read, so a text mangled by
        // a line-ending conversion or a stray byte-order mark fails here rather than as an
        // inscrutable codeword mismatch.
        var charCount = Integer.parseInt(readTsv("texts.tsv").get(textIndex)[1]);
        assertThat(text).as("payload text %d", textIndex).hasSize(charCount);
        return text;
    }

    /**
     * Returns the ECI designator the specified payload text is encoded with, as
     * {@code input/texts.tsv} states it.
     * <p>
     * {@link Eci#AUTOMATIC} is what the library does by default: ISO-8859-1 without an
     * ECI segment where that is lossless, UTF-8 with one otherwise.
     * </p>
     *
     * @param textIndex the index of the text
     * @return the designator
     */
    static Eci eci(int textIndex) {
        var designator = readInputTsv("texts.tsv").get(textIndex)[2];
        return "auto".equals(designator) ? Eci.AUTOMATIC : Eci.of(Integer.parseInt(designator));
    }

    /**
     * Builds the segments the exporter encoded the specified payload text as.
     * <p>
     * The exporter calls {@link DataSegment#fromText(String, Eci, Charset, int, KanjiStrategy)}
     * with the default version {@link VerifiedDataExport#SEGMENT_VERSION}.
     * </p>
     *
     * @param textIndex the index of the text
     * @return the segments
     */
    static List<DataSegment> segments(int textIndex) {
        return SEGMENTS.computeIfAbsent(textIndex, index -> List.copyOf(DataSegment.fromText(
                text(index), eci(index), null, SEGMENT_VERSION, KanjiStrategy.AUTOMATIC)));
    }

    // endregion
}

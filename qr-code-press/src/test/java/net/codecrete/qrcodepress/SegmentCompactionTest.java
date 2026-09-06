/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.codecrete.qrcodepress.SegmentTestSupport.dataOf;
import static net.codecrete.qrcodepress.SegmentTestSupport.shiftJis;
import static net.codecrete.qrcodepress.SegmentTestSupport.utf8;
import static org.assertj.core.api.Assertions.assertThat;

class SegmentCompactionTest {

    // region Verified data

    /**
     * One case per (text, version) of the compaction fixture, with the segments of the verified
     * test data. The fixture runs on the bytes of the charset each payload text's ECI
     * declares &mdash; Shift-JIS for texts 12 and 14, UTF-8 for the rest &mdash; with Kanji mode
     * enabled, so that it exercises compaction alone and not the text-encoding selection. The
     * input bytes come from the fixture itself, so this test encodes nothing.
     */
    static Stream<Arguments> compactionCases() {
        // The fixture holds one row per segment, keyed by text and version.
        record Key(int textIndex, int version) { }
        record Case(byte[] input, List<String> expected) { }

        var cases = new LinkedHashMap<Key, Case>();

        for (var row : VerifiedData.readTsv("compaction.tsv")) {
            var key = new Key(Integer.parseInt(row[0]), Integer.parseInt(row[1]));
            cases.computeIfAbsent(key, k -> new Case(VerifiedData.bytes(row[2]), new ArrayList<>()))
                    .expected().add(row[4].toUpperCase(Locale.ROOT) + ":" + row[6]);
        }

        return cases.entrySet().stream().map(entry -> Arguments.of(
                entry.getKey().textIndex(), entry.getKey().version(),
                entry.getValue().input(), entry.getValue().expected()));
    }

    @ParameterizedTest(name = "text {0}, version {1}")
    @MethodSource("compactionCases")
    @DisplayName("payload split matches verified data")
    void matchesSegmentation(int textIndex, int version, byte[] data, List<String> expected) {
        var segments = SegmentCompaction.buildSegments(ByteSlice.of(data), version, true);

        assertThat(segments.stream().map(SegmentCompactionTest::describe)).containsExactlyElementsOf(expected);
    }

    /**
     * Ten cases of text with their expected bit length.
     * <p>
     * Seven of them are generated texts, stated as the length and seed they are
     * generated from; see {@link RandomText}.
     * </p>
     */
    static Stream<Arguments> bitLengthCases() {
        return Stream.of(
                Arguments.of("\"ABC\"", "ABC", 10, Eci.UTF_8, 32),
                Arguments.of("\"カリ12ゼーシ\"", "カリ12ゼーシ", 10, Eci.SHIFT_JIS, 116),
                Arguments.of("\"カリ123456ゼーシ\"", "カリ123456ゼーシ", 10, Eci.SHIFT_JIS, 129),
                Arguments.of("mixed(40, 9117)", RandomText.mixed(40, 9117), 13, Eci.UTF_8, 578),
                Arguments.of("mixed(400, 9117)", RandomText.mixed(400, 9117), 7, Eci.UTF_8, 4695),
                Arguments.of("mixed(4000, 9117)", RandomText.mixed(4000, 9117), 20, Eci.UTF_8, 45301),
                Arguments.of("mixed(7813, 9117)", RandomText.mixed(7813, 9117), 40, Eci.UTF_8, 88703),
                Arguments.of("alphanumeric(3123, 9117)", RandomText.alphanumeric(3123, 9117), 17,
                        Eci.UTF_8, 17192),
                Arguments.of("mixed(2117, 8172)", RandomText.mixed(2117, 8172), 33, Eci.UTF_8, 22704),
                Arguments.of("alphanumeric(2632, 3200)", RandomText.alphanumeric(2632, 3200), 35,
                        Eci.LATIN_1, 14493));
    }

    @ParameterizedTest(name = "{0}, version {2}")
    @MethodSource("bitLengthCases")
    @DisplayName("matches the verified bit length")
    void matchesBitLength(String label, String text, int version, Eci eci, int expectedLength) {
        var considerKanjiMode = eci.equals(Eci.SHIFT_JIS);
        var data = text.getBytes(eci.getCharset());

        var segments = SegmentCompaction.buildSegments(ByteSlice.of(data), version, considerKanjiMode);

        assertThat(DataSegment.totalLength(segments, version)).isEqualTo(expectedLength);
    }

    // endregion

    // region Blocks

    /**
     * The only coverage of the empty payload.
     *
     * @param version the version to compact for
     */
    @ParameterizedTest
    @ValueSource(ints = { 1, 10, 27, 40 })
    @DisplayName("produces no segments for empty data")
    void emptyData(int version) {
        assertThat(SegmentCompaction.buildSegments(ByteSlice.EMPTY, version, true)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "01234567890123456789, NUMERIC(20)",
            "ABCDEFGHIJ $%*+-./:, ALPHANUMERIC(19)",
            "abcdefghij, BINARY(10)"
    })
    @DisplayName("encodes data of a single mode in a single segment")
    void singleMode(String text, String expected) {
        assertThat(compactAndOutline(text, 10)).containsExactly(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 10, 27, 40 })
    @DisplayName("assigns each byte the mode encoding it in the fewest bits")
    void modePerByte(int version) {
        // The runs are long enough that none of them is worth merging into its neighbors
        assertThat(compactAndOutline("~~0123456789012345~~ABCDEFGHIJKLMNOP~~", version)).containsExactly(
                "BINARY(2)", "NUMERIC(16)", "BINARY(2)", "ALPHANUMERIC(16)", "BINARY(2)");
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 10, 27, 40 })
    @DisplayName("the segments partition the data, in order and without gaps")
    void segmentsPartitionTheData(int version) {
        var data = shiftJis("2342342340ABC234234jkl~~0123456789012345亜唖娃阿哀愛");

        var segments = SegmentCompaction.buildSegments(ByteSlice.of(data), version, true);

        var joined = new ByteArrayOutputStream();
        segments.forEach(segment -> joined.writeBytes(dataOf(segment)));
        assertThat(joined.toByteArray()).isEqualTo(data);
    }

    // endregion

    // region Optimal segmentation

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // A numeric run between alphanumeric runs. Numeric mode is the more compact of the
            // two, so absorbing the run only pays off while it is short.
            "AB111111111111CD  | 1  | ALPHANUMERIC(16)",
            "AB1111111111111CD | 1  | ALPHANUMERIC(2) NUMERIC(13) ALPHANUMERIC(2)",
            "AB1111111111111CD | 10 | ALPHANUMERIC(17)",
            "AB1111111111111CD | 27 | ALPHANUMERIC(17)",

            // A numeric run between binary runs. Binary mode is far less compact, so the run has
            // to be shorter still for the surrounding segment to be worth extending over it.
            "ab11111cd         | 1  | BINARY(9)",
            "ab111111cd        | 1  | BINARY(2) NUMERIC(6) BINARY(2)",
            "ab111111cd        | 10 | BINARY(10)",
            "ab11111111cd      | 10 | BINARY(2) NUMERIC(8) BINARY(2)",
            "ab11111111cd      | 27 | BINARY(12)",

            // An alphanumeric run between binary runs.
            "abAAAAAAAAAAcd    | 1  | BINARY(14)",
            "abAAAAAAAAAAAcd   | 1  | BINARY(2) ALPHANUMERIC(11) BINARY(2)",
            "abAAAAAAAAAAAcd   | 10 | BINARY(15)"
    })
    @DisplayName("extends a segment over a run between two of its own mode while that is no longer")
    void absorbsRunBetweenTwoRuns(String text, int version, String expected) {
        assertThat(String.join(" ", compactAndOutline(text.trim(), version))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "A111111   | 1  | ALPHANUMERIC(7)",
            "A11111111 | 1  | ALPHANUMERIC(1) NUMERIC(8)",
            "A11111111 | 10 | ALPHANUMERIC(1) NUMERIC(8)",
            "A11111111 | 27 | ALPHANUMERIC(9)",
            "111111A   | 1  | ALPHANUMERIC(7)",
            "11111111A | 1  | NUMERIC(8) ALPHANUMERIC(1)",

            // Seven digits next to a letter are the tie: 57 bits either way. A tie is settled in
            // favour of the mode the run itself prefers, so the digits keep their own segment.
            "A1111111  | 1  | ALPHANUMERIC(1) NUMERIC(7)",
            "1111111A  | 1  | NUMERIC(7) ALPHANUMERIC(1)"
    })
    @DisplayName("extends a segment over the run next to it while that is no longer")
    void absorbsAdjacentRun(String text, int version, String expected) {
        assertThat(String.join(" ", compactAndOutline(text.trim(), version))).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 10, 27, 40 })
    @DisplayName("weighs whole segmentations, not neighbouring runs in isolation")
    void weighsWholeSegmentations(int version) {
        // No two of these three runs are worth joining on their own — each pair costs more merged
        // than apart. All three together are cheaper than any of the alternatives, which only a
        // search over whole segmentations finds.
        var data = shiftJis("~1亜");

        var segments = SegmentCompaction.buildSegments(ByteSlice.of(data), version, true);

        assertThat(segments.stream().map(SegmentCompactionTest::outline)).containsExactly("BINARY(4)");
    }

    /**
     * Short random texts of runs of all four modes, one case per (text, version).
     * <p>
     * The runs are one to four characters long, which is the length at which the choice between
     * a segment of its own and the segment next to it is close, and every arrangement of the four
     * modes turns up somewhere among the seeds. The texts are short enough for the exhaustive
     * oracle below.
     * </p>
     */
    static Stream<Arguments> randomRunCases() {
        return IntStream.range(0, 50).boxed()
                .flatMap(seed -> Stream.of(1, 10, 27)
                        .map(version -> Arguments.of(seed, version, randomRuns(16, seed))));
    }

    @ParameterizedTest(name = "seed {0}, version {1}")
    @MethodSource("randomRunCases")
    @DisplayName("produces the shortest bit stream of any possible segmentation")
    void matchesExhaustiveOptimum(int seed, int version, String text) {
        var data = ByteSlice.of(shiftJis(text));

        var segments = SegmentCompaction.buildSegments(data, version, true);

        assertThat(DataSegment.totalLength(segments, version))
                .isEqualTo(shortestSegmentation(data, 0, version, new int[data.length() + 1]));
    }

    /**
     * Generates a text of short runs of digits, uppercase letters, lowercase letters and Kanji,
     * which encode in numeric, alphanumeric, binary and Kanji mode respectively.
     *
     * @param length the number of characters
     * @param seed   the seed of the generator
     * @return the text
     */
    private static String randomRuns(int length, int seed) {
        var random = new Random(seed);
        var text = new StringBuilder(length);
        while (text.length() < length) {
            var alphabet = random.nextInt(4);
            var runLength = Math.min(length - text.length(), 1 + random.nextInt(4));
            for (var i = 0; i < runLength; i += 1)
                text.append(switch (alphabet) {
                    case 0 -> (char) ('0' + random.nextInt(10));
                    case 1 -> (char) ('A' + random.nextInt(26));
                    case 2 -> (char) ('a' + random.nextInt(26));
                    default -> (char) (0x3042 + random.nextInt(80)); // hiragana
                });
        }
        return text.toString();
    }

    /**
     * Returns the length of the shortest bit stream encoding the data from {@code start} on, found
     * by trying every segment that can start there.
     * <p>
     * This is the exhaustive oracle for {@link #matchesExhaustiveOptimum}. It searches the
     * segmentations of the <em>bytes</em>, so it shares neither the blocks nor the cost model of
     * the compaction it checks, and it measures a segment by building it, so it does not share the
     * length formulas duplicated inside the compaction either.
     * </p>
     *
     * @param data    the data to encode
     * @param start   the index of the first byte still to be encoded
     * @param version the QR code version (1&ndash;40)
     * @param known   the lengths already found, by start index, or 0 where none is yet
     * @return the length, in bits
     */
    private static int shortestSegmentation(ByteSlice data, int start, int version, int[] known) {
        if (start == data.length())
            return 0;
        if (known[start] != 0)
            return known[start];

        var shortest = Integer.MAX_VALUE;
        for (var end = start + 1; end <= data.length(); end += 1) {
            for (var mode : DataSegmentMode.values()) {
                var segment = segmentOrNull(mode, data.slice(start, end - start));
                if (segment == null)
                    continue;

                shortest = Math.min(shortest,
                        segment.totalLength(version) + shortestSegmentation(data, end, version, known));
            }
        }

        known[start] = shortest;
        return shortest;
    }

    /** Builds a segment of the specified mode, or returns {@code null} if the mode cannot encode the data. */
    private static DataSegment segmentOrNull(DataSegmentMode mode, ByteSlice data) {
        try {
            mode.checkEncodable(data);
            return mode.newSegment(data);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // endregion

    // region Kanji mode

    @Test
    @DisplayName("uses Kanji mode for Shift-JIS double-byte characters")
    void usesKanjiMode() {
        var data = ByteSlice.of(shiftJis("昨夜のコンサートは最高でした。"));

        var segments = SegmentCompaction.buildSegments(data, 10, true);

        assertThat(segments).singleElement()
                .extracting(DataSegment::getMode).isEqualTo(DataSegmentMode.KANJI);
    }

    @Test
    @DisplayName("never uses Kanji mode when it is not to be considered")
    void skipsKanjiMode() {
        var data = ByteSlice.of(shiftJis("昨夜のコンサートは最高でした。"));

        var segments = SegmentCompaction.buildSegments(data, 10, false);

        assertThat(segments).singleElement()
                .extracting(DataSegment::getMode).isEqualTo(DataSegmentMode.BINARY);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 10, 27 })
    @DisplayName("mixes Kanji mode with the other modes")
    void mixesKanjiMode(int version) {
        var data = shiftJis("0123456789012345ABCDEFGHIJKLMNOP亜唖娃阿哀愛~~~~");

        var segments = SegmentCompaction.buildSegments(ByteSlice.of(data), version, true);

        assertThat(segments.stream().map(SegmentCompactionTest::outline))
                .containsExactly("NUMERIC(16)", "ALPHANUMERIC(16)", "KANJI(12)", "BINARY(4)");
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 10, 27, 40 })
    @DisplayName("keeps Kanji segments an even number of bytes long")
    void kanjiSegmentsHaveEvenLength(int version) {
        // The trailing "あ" leaves an odd byte after the digit run, which must not end up in a
        // Kanji segment.
        var data = ByteSlice.of(shiftJis("けれども123456ですあ"));

        var segments = SegmentCompaction.buildSegments(data, version, true);

        assertThat(segments)
                .filteredOn(segment -> segment.getMode() == DataSegmentMode.KANJI)
                .isNotEmpty()
                .allSatisfy(segment -> assertThat(segment.getDataLength() % 2).isZero());
    }

    // endregion

    /** Compacts UTF-8 text without Kanji mode and outlines the resulting segments. */
    private static List<String> compactAndOutline(String text, int version) {
        return SegmentCompaction.buildSegments(ByteSlice.of(utf8(text)), version, false).stream()
                .map(SegmentCompactionTest::outline)
                .toList();
    }

    /** Renders a segment as its mode and data length, e.g. {@code NUMERIC(10)}. */
    private static String outline(DataSegment segment) {
        return segment.getMode() + "(" + segment.getDataLength() + ")";
    }

    /** Renders a segment the way the verified data does, as {@code MODE:hexdata}. */
    private static String describe(DataSegment segment) {
        return segment.getMode() + ":" + VerifiedData.hex(dataOf(segment));
    }
}

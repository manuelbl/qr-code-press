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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.codecrete.qrcodepress.StructuredAppendCase.payloadOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests the distribution of a payload across a sequence of QR codes.
 * <p>
 * What is under test here is the split alone: the segments each QR code of the sequence receives,
 * not the symbols built from them. {@link QrCodeSequenceTest} covers those, and decodes them.
 * </p>
 */
class StructuredAppendTest {

    private static final int LOW = Ecc.LOW.ordinal();
    private static final int MEDIUM = Ecc.MEDIUM.ordinal();
    private static final int HIGH = Ecc.HIGH.ordinal();

    // region Split

    @Test
    @DisplayName("splits Latin-1 text into as few QR codes as the version holds")
    void splitsLatin1Text() {
        var text = RandomText.alphanumeric(3000, 1001);

        var sequence = StructuredAppend.build(latin1(text), 29, 29, MEDIUM);

        assertThat(sequence.codes()).hasSize(2);
        assertThat(sequence.version()).isEqualTo(29);
        assertThat(textOf(sequence, StandardCharsets.ISO_8859_1)).isEqualTo(text);
    }

    @Test
    @DisplayName("splits UTF-8 text into as few QR codes as the version holds")
    void splitsUtf8Text() {
        var text = RandomText.mixed(3003, 2003);

        var sequence = StructuredAppend.build(utf8(text), 31, 31, MEDIUM);

        assertThat(sequence.codes()).hasSize(3);
        assertThat(textOf(sequence, StandardCharsets.UTF_8)).isEqualTo(text);
    }

    @Test
    @DisplayName("fills a sequence of the full sixteen QR codes")
    void fillsAllSixteenCodes() {
        var text = RandomText.mixed(33000, 9117);

        var sequence = StructuredAppend.build(utf8(text), 40, 40, LOW);

        assertThat(sequence.codes()).hasSize(16);
        assertThat(textOf(sequence, StandardCharsets.UTF_8)).isEqualTo(text);
        assertCodesFit(sequence, LOW);
    }

    @Test
    @DisplayName("rejects data that does not fit into sixteen QR codes")
    void rejectsDataTooLongForSixteenCodes() {
        var text = RandomText.mixed(10017, 7543);
        var utfText = utf8(text);

        assertThatExceptionOfType(DataTooLongException.class)
                .isThrownBy(() -> StructuredAppend.build(utfText, 19, 19, HIGH))
                .withMessage("Data is too long to fit into 16 QR codes with version 19"
                        + " and error correction level H.");
    }

    @ParameterizedTest
    @CsvSource({ "5, 200", "20, 2000", "40, 8000" })
    @DisplayName("splits UTF-8 text at character boundaries only")
    void splitsAtCharacterBoundaries(int version, int length) throws CharacterCodingException {
        // Every QR code of the sequence has to decode on its own, so no multi-byte character may
        // straddle two of them. The text is full of four-byte emoji, which a split that ignores
        // character boundaries would cut apart.
        var text = RandomText.mixed(length, 4711);

        var sequence = StructuredAppend.build(utf8(text), version, version, MEDIUM);

        // Decoding each QR code on its own, and strictly, is what proves the cuts: a character
        // split across two QR codes leaves a malformed byte sequence at both ends of the cut.
        var strictUtf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        var decoded = new StringBuilder();
        for (var code : sequence.codes())
            decoded.append(strictUtf8.reset().decode(ByteBuffer.wrap(payloadOf(code))));

        assertThat(sequence.codes()).hasSizeGreaterThan(1);
        assertThat(decoded).hasToString(text);
    }

    @Test
    @DisplayName("keeps every QR code within the capacity of its version")
    void codesStayWithinCapacity() {
        var text = RandomText.alphanumeric(6000, 88);

        var sequence = StructuredAppend.build(latin1(text), 20, 20, MEDIUM);

        assertThat(sequence.codes()).hasSizeGreaterThan(1);
        assertCodesFit(sequence, MEDIUM);
    }

    @ParameterizedTest
    @CsvSource({ "1", "2", "3" })
    @DisplayName("produces a single QR code for a payload of a few characters")
    void veryShortData(int length) {
        var sequence = StructuredAppend.build(latin1("A".repeat(length)), 10, 10, MEDIUM);

        assertThat(sequence.codes()).hasSize(1);
        assertThat(textOf(sequence, StandardCharsets.ISO_8859_1)).isEqualTo("A".repeat(length));
    }

    @Test
    @DisplayName("produces a single QR code for empty data")
    void emptyData() {
        var sequence = StructuredAppend.build(latin1(""), 5, 5, MEDIUM);

        assertThat(sequence.codes()).singleElement().satisfies(code ->
                assertThat(code).singleElement()
                        .extracting(DataSegment::getMode).isEqualTo(DataSegmentMode.STRUCTURED_APPEND));
    }

    @Test
    @DisplayName("spreads the payload evenly over the QR codes of the sequence")
    void spreadsThePayloadEvenly() {
        var text = RandomText.alphanumeric(3000, 4711);

        var sequence = StructuredAppend.build(latin1(text), 29, 29, MEDIUM);

        assertThat(sequence.version()).isEqualTo(29);
        assertThat(sequence.codes()).hasSize(2);
        assertThat(textOf(sequence, StandardCharsets.ISO_8859_1)).isEqualTo(text);

        // Every QR code carries all but the same amount, rather than the first ones being filled
        // to capacity and the last one left nearly empty.
        var payloads = sequence.codes().stream().mapToInt(code -> payloadOf(code).length).toArray();
        var fullest = payloads[0];
        var emptiest = payloads[0];
        for (var payload : payloads) {
            fullest = Math.max(fullest, payload);
            emptiest = Math.min(emptiest, payload);
        }

        assertThat(emptiest).as("emptiest QR code").isGreaterThan(fullest * 9 / 10);
    }

    @Test
    @DisplayName("reduces the shared version as far as the number of QR codes allows")
    void reducesTheVersion() {
        var text = RandomText.alphanumeric(3000, 4711);

        var sequence = StructuredAppend.build(latin1(text), 10, 29, MEDIUM);

        // Two QR codes are needed at version 29, and a smaller version still holds them.
        assertThat(sequence.codes()).hasSize(2);
        assertThat(sequence.version()).isLessThan(29);
        assertThat(textOf(sequence, StandardCharsets.ISO_8859_1)).isEqualTo(text);
        assertCodesFit(sequence, MEDIUM);
    }

    @ParameterizedTest
    @CsvSource({ "10, 31", "20, 40", "1, 40" })
    @DisplayName("splits UTF-8 text evenly and at character boundaries")
    void splitsUtf8TextOverAVersionRange(int minVersion, int maxVersion) {
        var text = RandomText.mixed(3003, 2003);

        var sequence = StructuredAppend.build(utf8(text), minVersion, maxVersion, MEDIUM);

        assertThat(textOf(sequence, StandardCharsets.UTF_8)).isEqualTo(text);
        assertCodesFit(sequence, MEDIUM);
    }

    // endregion

    // region Verified data

    static Stream<Arguments> structuredAppendCases() {
        return VerifiedData.readTsv("structuredappend.tsv").stream()
                .map(row -> Arguments.of(label(row), row));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("structuredAppendCases")
    @DisplayName("payload split match the verified data")
    void matchesSplits(String label, String[] row) {
        var testCase = StructuredAppendCase.fromRow(row);
        var encoded = testCase.encoded();
        var minVersion = testCase.minVersion();
        var maxVersion = testCase.maxVersion();
        var ecc = testCase.ecc().ordinal();

        if ("toolong".equals(row[8])) {
            assertThatExceptionOfType(DataTooLongException.class)
                    .isThrownBy(() -> StructuredAppend.build(encoded, minVersion, maxVersion, ecc));
            return;
        }

        var expectedLengths = Arrays.stream(row[11].split(",")).mapToInt(Integer::parseInt).toArray();
        // The text is regenerated here rather than exported, so it is worth one assertion that the
        // two generators still agree before anything is concluded from what the split did to it.
        // A drifted draw would otherwise surface as a wrong split.
        assertThat(IntStream.of(expectedLengths).sum()).as("payload bytes of the verified text")
                .isEqualTo(encoded.data().length);

        var sequence = StructuredAppend.build(encoded, minVersion, maxVersion, ecc);

        assertThat(sequence.version()).as("shared version").isEqualTo(Integer.parseInt(row[9]));
        assertThat(sequence.codes()).as("QR codes").hasSize(Integer.parseInt(row[10]));
        assertThat(StructuredAppendCase.payloadLengths(sequence.codes()))
                .as("payload bytes per QR code").containsExactly(expectedLengths);
    }

    /** Returns the display label of a fixture row. */
    private static String label(String[] row) {
        return "case " + row[0] + ": " + row[1] + "(" + row[2] + ", " + row[3] + ") " + row[4]
                + " v" + row[5] + "-" + row[6] + " " + row[7];
    }

    // endregion

    // region Headers

    @Test
    @DisplayName("heads every QR code with its position in the sequence")
    void headersStateThePosition() {
        var text = RandomText.alphanumeric(3000, 1001);

        var codes = StructuredAppend.build(latin1(text), 29, 29, MEDIUM).codes();

        for (var i = 0; i < codes.size(); i += 1) {
            var position = i + 1;
            assertThat(codes.get(i).get(0)).isInstanceOfSatisfying(DataSegmentStructuredAppend.class, header -> {
                assertThat(header.position()).isEqualTo(position);
                assertThat(header.total()).isEqualTo(codes.size());
            });
        }
    }

    @Test
    @DisplayName("heads every QR code with the parity of the entire data")
    void headersStateTheParity() {
        var text = RandomText.alphanumeric(3000, 1001);
        var parity = 0;
        for (var b : text.getBytes(StandardCharsets.ISO_8859_1))
            parity ^= b & 0xff;
        var expectedParity = parity;

        var codes = StructuredAppend.build(latin1(text), 29, 29, MEDIUM).codes();

        assertThat(expectedParity).as("parity of the test data").isNotZero();
        assertThat(codes).isNotEmpty().allSatisfy(code ->
                assertThat(((DataSegmentStructuredAppend) code.get(0)).parity()).isEqualTo(expectedParity));
    }

    @Test
    @DisplayName("announces the character encoding in every QR code")
    void headersAnnounceTheEncoding() {
        var text = RandomText.mixed(3003, 2003);

        var codes = StructuredAppend.build(utf8(text), 31, 31, MEDIUM).codes();

        assertThat(codes).isNotEmpty().allSatisfy(code -> {
            assertThat(code.get(0).getMode()).isEqualTo(DataSegmentMode.STRUCTURED_APPEND);
            assertThat(code.get(1)).isInstanceOfSatisfying(DataSegmentEci.class,
                    eci -> assertThat(eci.designator()).isEqualTo(Eci.UTF_8));
        });
    }

    @Test
    @DisplayName("adds no ECI segment where the encoding needs no announcement")
    void headersOmitTheEncodingWhereItIsTheDefault() {
        var text = RandomText.alphanumeric(3000, 2003);

        var codes = StructuredAppend.build(latin1(text), 29, 29, MEDIUM).codes();

        assertThat(codes).isNotEmpty().allSatisfy(code -> {
            assertThat(code.get(0).getMode()).isEqualTo(DataSegmentMode.STRUCTURED_APPEND);
            assertThat(code).doesNotHaveAnyElementsOfTypes(DataSegmentEci.class);
        });
    }

    // endregion

    /** The text encoded the way a sequence of Latin-1 QR codes carries it. */
    private static DataSegment.EncodedText latin1(String text) {
        return new DataSegment.EncodedText(text.getBytes(StandardCharsets.ISO_8859_1), Eci.NONE,
                StandardCharsets.ISO_8859_1);
    }

    /** The text encoded the way a sequence of UTF-8 QR codes carries it. */
    private static DataSegment.EncodedText utf8(String text) {
        return new DataSegment.EncodedText(text.getBytes(StandardCharsets.UTF_8), Eci.UTF_8,
                StandardCharsets.UTF_8);
    }

    /** Asserts that every QR code of the sequence fits into its version. */
    private static void assertCodesFit(StructuredAppend.Sequence sequence, int ecc) {
        var capacity = 8 * QrCodeParameters.dataCodewordCapacity(sequence.version(), ecc);
        assertThat(sequence.codes()).allSatisfy(code ->
                assertThat(DataSegment.totalLength(code, sequence.version()))
                        .as("bits of a QR code of version " + sequence.version())
                        .isLessThanOrEqualTo(capacity));
    }

    /** Returns the text the entire sequence carries. */
    private static String textOf(StructuredAppend.Sequence sequence, Charset charset) {
        var payload = new ByteArrayOutputStream();
        for (var code : sequence.codes())
            payload.writeBytes(payloadOf(code));
        return payload.toString(charset);
    }
}

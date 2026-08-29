/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * The options of {@link QrCodeBuilder} and how they change the resulting QR code.
 */
class QrCodeBuilderTest {

    /** A Japanese text ("Last night's concert was the best."), the payload Kanji mode is for. */
    private static final String JAPANESE_TEXT = "昨夜のコンサートは最高でした。";

    /** Renders the segments as {@code MODE:length} pairs, so that a mismatch is readable. */
    private static List<String> describe(List<DataSegment> segments) {
        return segments.stream().map(s -> s.getMode() + ":" + s.getDataLength()).toList();
    }

    private static List<DataSegment> segmentsOf(QrCodeBuilder builder) {
        return builder.buildWithDiagnostics().segments();
    }

    @Nested
    @DisplayName("payload")
    class Payload {

        @Test
        @DisplayName("copies the binary data, so the caller may reuse the array")
        void copiesBinaryData() {
            var data = "12345678".getBytes(StandardCharsets.US_ASCII);
            var builder = QrCode.builder().binary(data).errorCorrection(Ecc.LOW);
            var expected = TestMatrices.rowsOf(builder.build());

            data[0] = 'X';

            assertThat(TestMatrices.rowsOf(builder.build())).isEqualTo(expected);
        }

        @Test
        @DisplayName("copies the segment list, so the caller may reuse it")
        void copiesSegmentList() {
            var segments = new ArrayList<DataSegment>();
            segments.add(DataSegment.of(DataSegmentMode.NUMERIC, "12345678".getBytes(StandardCharsets.US_ASCII)));
            var builder = QrCode.builder().segments(segments).errorCorrection(Ecc.LOW);
            var expected = TestMatrices.rowsOf(builder.build());

            segments.clear();

            assertThat(TestMatrices.rowsOf(builder.build())).isEqualTo(expected);
        }

        @Test
        @DisplayName("the last payload set replaces the ones before")
        void lastPayloadWins() {
            var qrCode = QrCode.builder()
                    .binary(new byte[100])
                    .text("Hello, world!")
                    .errorCorrection(Ecc.LOW)
                    .buildWithDiagnostics();

            assertThat(describe(qrCode.segments())).containsExactly("BINARY:13");
        }

        @Test
        @DisplayName("rejects a build without a payload")
        void rejectsMissingPayload() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> QrCode.builder().errorCorrection(Ecc.LOW).build())
                    .withMessageContaining("No payload");
        }

        @Test
        @DisplayName("defaults to error correction level M")
        void defaultsToMediumErrorCorrection() {
            var qrCode = QrCode.builder().text("A").boostErrorCorrection(false).build();

            assertThat(qrCode.getErrorCorrectionLevel()).isEqualTo(Ecc.MEDIUM);
        }

        @Test
        @DisplayName("rejects null arguments")
        void rejectsNull() {
            var builder = QrCode.builder();
            assertThatNullPointerException().isThrownBy(() -> builder.text(null));
            assertThatNullPointerException().isThrownBy(() -> builder.binary(null));
            assertThatNullPointerException().isThrownBy(() -> builder.segments(null));
            assertThatNullPointerException().isThrownBy(() -> builder.errorCorrection(null));
            assertThatNullPointerException().isThrownBy(() -> builder.eci(null));
            assertThatNullPointerException().isThrownBy(() -> builder.kanjiStrategy(null));
        }

        @Test
        @DisplayName("builds several QR codes from one builder")
        void buildsRepeatedly() {
            var builder = QrCode.builder().text("Hello, world!").errorCorrection(Ecc.LOW);

            assertThat(TestMatrices.rowsOf(builder.build()))
                    .isEqualTo(TestMatrices.rowsOf(builder.build()));
        }
    }

    @Nested
    @DisplayName("version range")
    class VersionRange {

        @Test
        @DisplayName("uses the smallest version in the range that fits")
        void usesTheSmallestFittingVersion() {
            var builder = QrCode.builder().text("Hello, world!").errorCorrection(Ecc.LOW);

            assertThat(builder.versionRange(1, 40).build().getVersion()).isEqualTo(1);
            assertThat(builder.versionRange(7, 40).build().getVersion()).isEqualTo(7);
            assertThat(builder.version(12).build().getVersion()).isEqualTo(12);
        }

        @Test
        @DisplayName("reports a payload too long for the range")
        void reportsPayloadTooLongForTheRange() {
            var builder = QrCode.builder().text("Hello, world!")
                    .errorCorrection(Ecc.HIGH).version(1);
            assertThatExceptionOfType(DataTooLongException.class)
                    .isThrownBy(builder::build)
                    .withMessageContaining("version 1");
        }

        @ParameterizedTest
        @CsvSource({ "0, 40", "1, 41", "-1, 5", "10, 5" })
        @DisplayName("rejects a range outside 1 to 40, or the wrong way round")
        void rejectsAnInvalidRange(int minVersion, int maxVersion) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QrCode.builder().versionRange(minVersion, maxVersion));
        }
    }

    @Nested
    @DisplayName("error correction")
    class ErrorCorrection {

        @Test
        @DisplayName("raises the level as far as the chosen version allows")
        void boostsByDefault() {
            var qrCode = QrCode.builder().text("Hello, world!").errorCorrection(Ecc.LOW).build();

            assertThat(qrCode.getErrorCorrectionLevel()).isEqualTo(Ecc.MEDIUM);
        }

        @Test
        @DisplayName("keeps the requested level when boosting is turned off")
        void keepsTheRequestedLevel() {
            var qrCode = QrCode.builder().text("Hello, world!").errorCorrection(Ecc.LOW)
                    .boostErrorCorrection(false).build();

            assertThat(qrCode.getErrorCorrectionLevel()).isEqualTo(Ecc.LOW);
        }
    }

    @Nested
    @DisplayName("character encoding")
    class CharacterEncoding {

        @Test
        @DisplayName("encodes text that fits ISO-8859-1 without an ECI segment")
        void latin1NeedsNoEciSegment() {
            var segments = segmentsOf(QrCode.builder().text("Grüezi").errorCorrection(Ecc.LOW));

            assertThat(describe(segments)).containsExactly("BINARY:6");
        }

        @Test
        @DisplayName("encodes other text as UTF-8, announced by an ECI segment")
        void otherTextIsUtf8() {
            var segments = segmentsOf(QrCode.builder().text("😀").errorCorrection(Ecc.LOW));

            assertThat(segments).first().isInstanceOfSatisfying(DataSegmentEci.class,
                    segment -> assertThat(segment.designator()).isEqualTo(Eci.UTF_8));
            assertThat(describe(segments)).containsExactly("ECI:0", "BINARY:4");
        }

        @Test
        @DisplayName("encodes text with the character set of the ECI designator")
        void usesTheCharacterSetOfTheDesignator() {
            var segments = segmentsOf(
                    QrCode.builder().text(JAPANESE_TEXT).eci(Eci.SHIFT_JIS).errorCorrection(Ecc.LOW));

            assertThat(describe(segments)).containsExactly("ECI:0", "KANJI:30");
        }

        @Test
        @DisplayName("encodes text with an explicitly specified character set")
        void usesAnExplicitCharacterSet() {
            var segments = segmentsOf(QrCode.builder().text(JAPANESE_TEXT)
                    .eci(Eci.NONE, StandardCharsets.UTF_8).errorCorrection(Ecc.LOW));

            assertThat(describe(segments)).containsExactly("BINARY:45");
        }

        @Test
        @DisplayName("requires a character set if no ECI designator is added")
        void requiresACharacterSetWithoutAnEci() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QrCode.builder().text("A").eci(Eci.NONE).errorCorrection(Ecc.LOW).build())
                    .withMessageContaining("character set");
        }

        @Test
        @DisplayName("rejects a character set alongside Eci.AUTOMATIC")
        void rejectsACharacterSetWithAutomaticEci() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QrCode.builder().eci(Eci.AUTOMATIC, StandardCharsets.UTF_8))
                    .withMessageContaining("Eci.AUTOMATIC");
        }

        @Test
        @DisplayName("drops an explicit character set when the designator is set again")
        void eciDropsAnExplicitCharacterSet() {
            var segments = segmentsOf(QrCode.builder().text(JAPANESE_TEXT)
                    .eci(Eci.NONE, StandardCharsets.UTF_8).eci(Eci.SHIFT_JIS).errorCorrection(Ecc.LOW));

            assertThat(describe(segments)).containsExactly("ECI:0", "KANJI:30");
        }

        @Test
        @DisplayName("omits the ECI segment of binary data on request")
        void omitsTheBinaryEciSegment() {
            var segments = segmentsOf(QrCode.builder()
                    .binary("12345678".getBytes(StandardCharsets.US_ASCII))
                    .eci(Eci.NONE).errorCorrection(Ecc.LOW));

            assertThat(describe(segments)).containsExactly("NUMERIC:8");
        }
    }

    @Nested
    @DisplayName("Kanji strategy")
    class Kanji {

        @Test
        @DisplayName("uses Kanji mode for Shift-JIS text by default")
        void automaticUsesKanjiForShiftJis() {
            var segments = segmentsOf(
                    QrCode.builder().text(JAPANESE_TEXT).eci(Eci.SHIFT_JIS).errorCorrection(Ecc.LOW));

            assertThat(describe(segments)).containsExactly("ECI:0", "KANJI:30");
        }

        @Test
        @DisplayName("leaves Kanji mode alone for text in another encoding")
        void automaticLeavesKanjiAloneOtherwise() {
            var segments = segmentsOf(QrCode.builder().text(JAPANESE_TEXT).errorCorrection(Ecc.LOW));

            assertThat(describe(segments)).containsExactly("ECI:0", "BINARY:45");
        }

        @Test
        @DisplayName("never uses Kanji mode when disabled")
        void disabledNeverUsesKanji() {
            var segments = segmentsOf(QrCode.builder().text(JAPANESE_TEXT).eci(Eci.SHIFT_JIS)
                    .kanjiStrategy(KanjiStrategy.DISABLED).errorCorrection(Ecc.LOW));

            assertThat(describe(segments)).containsExactly("ECI:0", "BINARY:30");
        }

        @Test
        @DisplayName("uses Kanji mode for any suitable data when enabled")
        void enabledUsesKanjiRegardlessOfEncoding() {
            // Binary data, announced as binary — but its byte pairs fall into the Shift-JIS
            // double-byte range, so Kanji mode can encode them, and does when it is enabled.
            var data = new byte[20];
            for (var i = 0; i < data.length; i += 2) {
                data[i] = (byte) 0x88;
                data[i + 1] = (byte) 0x9f;
            }

            assertThat(describe(segmentsOf(QrCode.builder().binary(data)
                    .kanjiStrategy(KanjiStrategy.ENABLED).errorCorrection(Ecc.LOW))))
                    .containsExactly("ECI:0", "KANJI:20");
            assertThat(describe(segmentsOf(QrCode.builder().binary(data).errorCorrection(Ecc.LOW))))
                    .containsExactly("ECI:0", "BINARY:20");
        }
    }

    @Nested
    @DisplayName("diagnostics")
    class Diagnostics {

        @Test
        @DisplayName("returns the same QR code as build()")
        void matchesBuild() {
            var builder = QrCode.builder().text("Hello, world!").errorCorrection(Ecc.LOW);

            var info = builder.buildWithDiagnostics();

            assertThat(TestMatrices.rowsOf(info.qrCode())).isEqualTo(TestMatrices.rowsOf(builder.build()));
        }

        @Test
        @DisplayName("scores all eight mask patterns")
        void scoresAllPatterns() {
            var info = QrCode.builder().text("Hello, world!").errorCorrection(Ecc.LOW)
                    .buildWithDiagnostics();

            assertThat(info.penalties()).hasSize(8).doesNotContainNull();
            assertThat(info.penalties().get(info.qrCode().getMask()).total())
                    .isEqualTo(info.penalties().stream().mapToInt(PenaltyScore::total).min().orElseThrow());
        }

        @Test
        @DisplayName("scores all eight mask patterns even when one is pinned")
        void scoresAllPatternsWithAPinnedMask() {
            var info = QrCode.builder().text("Hello, world!").errorCorrection(Ecc.LOW)
                    .forceMask(0).buildWithDiagnostics();

            assertThat(info.qrCode().getMask()).isZero();
            assertThat(info.penalties()).hasSize(8).doesNotContainNull();
        }
    }

    @Nested
    @DisplayName("forced mask")
    class ForcedMask {

        @ParameterizedTest
        @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7 })
        @DisplayName("applies the pinned mask pattern")
        void appliesThePinnedPattern(int mask) {
            var qrCode = QrCode.builder().text("Hello, world!").errorCorrection(Ecc.LOW)
                    .forceMask(mask).build();

            assertThat(qrCode.getMask()).isEqualTo(mask);
        }

        @ParameterizedTest
        @ValueSource(ints = { -1, 8 })
        @DisplayName("rejects a mask pattern outside 0 to 7")
        void rejectsAnInvalidPattern(int mask) {
            assertThatIllegalArgumentException().isThrownBy(() -> QrCode.builder().forceMask(mask));
        }
    }
}

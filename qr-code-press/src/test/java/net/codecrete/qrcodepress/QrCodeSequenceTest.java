/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tests the sequences of QR codes built for a text too long for a single one.
 * <p>
 * The QR codes are decoded with ZXing, so what is asserted is not merely that the library split the
 * text the way it intended to, but that an independent decoder reassembles the original text from
 * the symbols &mdash; including the Structured Append headers that link them.
 * </p>
 */
class QrCodeSequenceTest {

    // region Sequences

    @ParameterizedTest
    @CsvSource({ "10, 29", "20, 20" })
    @DisplayName("decodes to the text it was built from, code by code")
    void decodesToTheOriginalText(int minVersion, int maxVersion) throws ChecksumException, FormatException {
        var text = RandomText.alphanumeric(3000, 4711);

        var qrCodes = sequenceBuilder(text, minVersion, maxVersion).build();

        assertThat(qrCodes).hasSizeGreaterThan(1);
        var decoded = new StringBuilder();
        for (var i = 0; i < qrCodes.size(); i += 1) {
            var result = ZxingSupport.decode(ZxingSupport.toBitMatrix(qrCodes.get(i)));

            assertThat(result.getErrorsCorrected()).as("errors corrected").isZero();
            assertThat(result.hasStructuredAppend()).as("structured append").isTrue();
            // The sequence number holds the position and the length of the sequence, each one less
            // than its value, in four bits apiece.
            assertThat(result.getStructuredAppendSequenceNumber()).as("position in the sequence")
                    .isEqualTo((i << 4) | (qrCodes.size() - 1));
            assertThat(result.getStructuredAppendParity()).as("parity").isEqualTo(parityOf(text));
            decoded.append(result.getText());
        }

        assertThat(decoded).hasToString(text);
    }

    @Test
    @DisplayName("decodes UTF-8 text without splitting a character across two codes")
    void decodesUtf8Text() throws ChecksumException, FormatException {
        var text = RandomText.mixed(2000, 4711);

        var qrCodes = sequenceBuilder(text, 10, 29).build();

        assertThat(qrCodes).hasSizeGreaterThan(1);
        var decoded = new StringBuilder();
        for (var qrCode : qrCodes)
            decoded.append(ZxingSupport.decode(ZxingSupport.toBitMatrix(qrCode)).getText());

        assertThat(decoded).hasToString(text);
    }

    @Test
    @DisplayName("gives every QR code of a sequence the same version")
    void codesShareTheVersion() {
        var qrCodes = sequenceBuilder(RandomText.alphanumeric(3000, 4711), 10, 29).build();

        assertThat(qrCodes).hasSizeGreaterThan(1)
                .extracting(QrCode::getVersion).containsOnly(qrCodes.get(0).getVersion());
    }

    @Test
    @DisplayName("reduces the version as far as the number of QR codes allows")
    void usesTheSmallestVersionThatHoldsTheSequence() {
        var text = RandomText.alphanumeric(3000, 4711);

        var overTheRange = sequenceBuilder(text, 10, 29).build();
        // The largest version of the range decides how many QR codes are needed, so pinning it is
        // the same sequence without the version reduction.
        var atTheLargestVersion = sequenceBuilder(text, 29, 29).build();

        assertThat(overTheRange).hasSameSizeAs(atTheLargestVersion);
        assertThat(overTheRange.get(0).getVersion()).isLessThan(atTheLargestVersion.get(0).getVersion());
    }

    @Test
    @DisplayName("raises the error correction level of every QR code that has room to spare")
    void boostsTheErrorCorrectionLevel() {
        // Long enough to need two QR codes at version 29, short enough that half of it leaves room
        // for a higher error correction level. A text that fills both QR codes boosts neither.
        var text = RandomText.alphanumeric(2400, 4711);

        var boosted = sequenceBuilder(text, 29, 29).build();
        var asRequested = sequenceBuilder(text, 29, 29).boostErrorCorrection(false).build();

        // Every QR code of the sequence carries the same share of the text, so they all have the
        // same capacity to spare and all boost together.
        assertThat(boosted).hasSize(2).extracting(QrCode::getErrorCorrectionLevel)
                .allMatch(level -> level.ordinal() > Ecc.MEDIUM.ordinal());
        assertThat(asRequested).extracting(QrCode::getErrorCorrectionLevel).containsOnly(Ecc.MEDIUM);
    }

    // endregion

    // region Character encoding

    @Test
    @DisplayName("announces the character encoding in every QR code of the sequence")
    void announcesTheEncodingInEveryCode() throws ChecksumException, FormatException {
        var text = RandomText.cyrillic(1500, 4711);

        var qrCodes = sequenceBuilder(text, 10, 29).eci(Eci.LATIN_CYRILLIC).build();

        // Every QR code is decoded on its own, so a text that comes back intact is one whose ECI
        // segment sits on every QR code rather than on the first alone.
        assertThat(qrCodes).hasSizeGreaterThan(1);
        var decoded = new StringBuilder();
        for (var qrCode : qrCodes)
            decoded.append(ZxingSupport.decode(ZxingSupport.toBitMatrix(qrCode)).getText());

        assertThat(decoded).hasToString(text);
    }

    @Test
    @DisplayName("needs fewer QR codes for a single-byte encoding than for UTF-8")
    void singleByteEncodingNeedsFewerCodes() {
        var text = RandomText.cyrillic(1500, 4711);

        var cyrillic = sequenceBuilder(text, 10, 10).eci(Eci.LATIN_CYRILLIC).build();
        var utf8 = sequenceBuilder(text, 10, 10).build();

        assertThat(cyrillic).hasSizeLessThan(utf8.size());
    }

    @Test
    @DisplayName("encodes with an explicit character set and announces nothing for Eci.NONE")
    void encodesWithAnExplicitCharsetWithoutAnnouncingIt() throws ChecksumException, FormatException {
        var charset = Charset.forName("ISO-8859-2");
        var text = "Zażółć gęślą jaźń, ".repeat(120);

        var qrCodes = sequenceBuilder(text, 10, 10).eci(Eci.NONE, charset).build();

        // The hint is what supplies the character set the QR codes do not announce; an ECI segment
        // would override it, so decoding to the original text proves there is none.
        assertThat(qrCodes).hasSizeGreaterThan(1);
        var decoded = new StringBuilder();
        for (var qrCode : qrCodes)
            decoded.append(ZxingSupport.decode(ZxingSupport.toBitMatrix(qrCode), charset).getText());

        assertThat(decoded).hasToString(text);
    }

    @Test
    @DisplayName("rejects a multi-byte character set named by its designator")
    void rejectsAMultiByteDesignator() {
        var builder = sequenceBuilder(RandomText.alphanumeric(3000, 4711), 10, 29).eci(Eci.SHIFT_JIS);

        assertThatIllegalArgumentException().isThrownBy(builder::build)
                .withMessageContaining("only single-byte character sets and UTF-8");
    }

    @ParameterizedTest
    @CsvSource({ "Big5", "UTF-16" })
    @DisplayName("rejects a multi-byte character set")
    void rejectsAMultiByteCharset(String charsetName) {
        var builder = sequenceBuilder(RandomText.alphanumeric(3000, 4711), 10, 29)
                .eci(Eci.NONE, Charset.forName(charsetName));

        assertThatIllegalArgumentException().isThrownBy(builder::build)
                .withMessageContaining(charsetName);
    }

    @Test
    @DisplayName("rejects a designator that names no character set")
    void rejectsADesignatorWithoutACharacterSet() {
        var builder = sequenceBuilder("Hello", 1, 40).eci(Eci.BINARY_DATA);

        assertThatExceptionOfType(EciException.class).isThrownBy(builder::build);
    }

    @Test
    @DisplayName("rejects a character set alongside Eci.AUTOMATIC")
    void rejectsACharsetWithAutomatic() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QrCodeSequence.builder().eci(Eci.AUTOMATIC, StandardCharsets.UTF_8))
                .withMessageContaining("Eci.AUTOMATIC");
    }

    @Test
    @DisplayName("forgets a character set when a designator is set on its own")
    void aDesignatorAloneClearsTheCharacterSet() {
        var builder = QrCodeSequence.builder()
                .text("Hello")
                .eci(Eci.NONE, StandardCharsets.ISO_8859_1)
                .eci(Eci.NONE);

        assertThatIllegalArgumentException().isThrownBy(builder::build)
                .withMessageContaining("Eci.NONE");
    }

    // endregion

    // region Single QR code

    @Test
    @DisplayName("builds a standalone QR code for a text that fits into one")
    void shortTextYieldsASingleCode() throws ChecksumException, FormatException {
        var text = "Hello, world!";

        var qrCodes = sequenceBuilder(text, 1, 40).build();

        assertThat(qrCodes).hasSize(1);
        var result = ZxingSupport.decode(ZxingSupport.toBitMatrix(qrCodes.get(0)));
        assertThat(result.getText()).isEqualTo(text);
        assertThat(result.hasStructuredAppend()).as("structured append").isFalse();
        assertThat(qrCodes.get(0).getVersion()).as("version").isEqualTo(1);
    }

    @Test
    @DisplayName("collapses a sequence of two QR codes into one where the text fits into one")
    void borderlineTextYieldsASingleCode() {
        // Just short enough for a single version 10 QR code, but not once every QR code of a
        // sequence carries a Structured Append header as well.
        var text = RandomText.alphanumeric(310, 27);

        var qrCodes = sequenceBuilder(text, 10, 10).build();

        assertThat(qrCodes).as("QR codes of the sequence").hasSize(1);
        assertThat(qrCodes.get(0).getVersion()).isEqualTo(10);
    }

    @Test
    @DisplayName("builds a QR code of the smallest version of the range for a short text")
    void singleCodeUsesTheSmallestVersion() {
        var qrCodes = sequenceBuilder("Hello, world!", 5, 29).build();

        assertThat(qrCodes).hasSize(1);
        assertThat(qrCodes.get(0).getVersion()).isEqualTo(5);
    }

    // endregion

    // region Errors

    @Test
    @DisplayName("rejects a text too long for sixteen QR codes")
    void rejectsTextTooLongForASequence() {
        var text = RandomText.alphanumeric(20000, 3);

        var builder = sequenceBuilder(text, 19, 19);
        assertThatExceptionOfType(DataTooLongException.class)
                .isThrownBy(builder::build)
                .withMessage("Data is too long to fit into 16 QR codes with version 19"
                        + " and error correction level M.");
        builder = sequenceBuilder(text, 1, 19);
        assertThatExceptionOfType(DataTooLongException.class)
                .isThrownBy(builder::build);
    }

    @Test
    @DisplayName("rejects a missing payload")
    void rejectsIncompleteBuilder() {
        assertThatIllegalStateException()
                .isThrownBy(() -> QrCodeSequence.builder().errorCorrection(Ecc.MEDIUM).build())
                .withMessageContaining("text(...)");
    }

    @Test
    @DisplayName("defaults to error correction level M")
    void defaultsToMediumErrorCorrection() {
        var qrCodes = QrCodeSequence.builder().text("Hello").boostErrorCorrection(false).build();

        assertThat(qrCodes).singleElement()
                .extracting(QrCode::getErrorCorrectionLevel).isEqualTo(Ecc.MEDIUM);
    }

    @SuppressWarnings("WriteOnlyObject")
    @Test
    @DisplayName("rejects null arguments")
    void rejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> QrCodeSequence.builder().text(null));
        assertThatNullPointerException().isThrownBy(() -> QrCodeSequence.builder().errorCorrection(null));
        assertThatNullPointerException().isThrownBy(() -> QrCodeSequence.builder().eci(null));
        assertThatNullPointerException()
                .isThrownBy(() -> QrCodeSequence.builder().eci(null, StandardCharsets.UTF_8));
        assertThatNullPointerException().isThrownBy(() -> QrCodeSequence.builder().eci(Eci.UTF_8, null));
    }

    @ParameterizedTest
    @CsvSource({ "0, 40", "1, 41", "-1, 10", "20, 10" })
    @DisplayName("rejects an invalid version range")
    void rejectsInvalidVersionRange(int minVersion, int maxVersion) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QrCodeSequence.builder().versionRange(minVersion, maxVersion));
    }

    @Test
    @DisplayName("takes a single version as a range of one")
    void singleVersion() {
        var qrCodes = QrCodeSequence.builder()
                .text(RandomText.alphanumeric(3000, 4711))
                .errorCorrection(Ecc.MEDIUM)
                .version(25)
                .build();

        assertThat(qrCodes).hasSizeGreaterThan(1)
                .extracting(QrCode::getVersion).containsOnly(25);
    }

    // endregion

    private static QrCodeSequenceBuilder sequenceBuilder(String text, int minVersion, int maxVersion) {
        return QrCodeSequence.builder()
                .text(text)
                .errorCorrection(Ecc.MEDIUM)
                .versionRange(minVersion, maxVersion);
    }

    /** Returns the parity a Structured Append sequence of the text carries. */
    private static int parityOf(String text) {
        var parity = 0;
        for (var b : text.getBytes(StandardCharsets.ISO_8859_1))
            parity ^= b & 0xff;
        return parity;
    }
}

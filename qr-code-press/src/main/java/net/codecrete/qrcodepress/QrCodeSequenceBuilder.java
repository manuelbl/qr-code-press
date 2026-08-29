/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a sequence of QR codes carrying one text between them.
 * <p>
 * An instance is created with {@link QrCodeSequence#builder()}. The
 * {@linkplain #text(String) text} must be set; everything else, including the
 * {@linkplain #errorCorrection(Ecc) error correction level}, has a default.
 * </p>
 * <pre>{@code
 * List<QrCode> qrCodes = QrCodeSequence.builder()
 *         .text(longText)
 *         .errorCorrection(Ecc.QUARTILE)
 *         .versionRange(10, 29)
 *         .build();
 * }</pre>
 * <p>
 * If the text needs more than one QR code, each of them starts with a Structured Append segment
 * that links them.
 * </p>
 * <p>
 * The text is split evenly over the fewest QR codes that the maximum version of
 * {@link #versionRange(int, int)} allows. They all use the same version: the smallest one in the
 * range that still holds the text in that number of QR codes.
 * </p>
 * <p>
 * By default, the text is encoded in ISO-8859-1 if it can be encoded as such, and in UTF-8 with an
 * ECI segment otherwise; {@link #eci(Eci)} selects a character encoding instead. Only single-byte
 * character sets and UTF-8 are accepted. Shift-JIS is rejected along with every other multi-byte
 * encoding, and Kanji mode is never used in a sequence.
 * </p>
 * <p>
 * A builder may be reused and can build several sequences, but a single instance is not safe to use
 * from several threads at once.
 * </p>
 */
public final class QrCodeSequenceBuilder {

    private String text;
    private final EncodingOptions options = new EncodingOptions();

    /**
     * Creates a new instance.
     */
    QrCodeSequenceBuilder() {
        // instantiated through QrCodeSequence.builder()
    }

    // region Payload

    /**
     * Sets the text to encode.
     * <p>
     * The text is split into the QR codes of the sequence, and within each QR code into segments of
     * the modes that encode it most compactly.
     * </p>
     *
     * @param text the text
     * @return this builder
     */
    public QrCodeSequenceBuilder text(String text) {
        this.text = Objects.requireNonNull(text, "text");
        return this;
    }

    // endregion

    // region Options

    /**
     * Sets the error correction level.
     * <p>
     * Unless boosting is turned off with {@link #boostErrorCorrection(boolean)}, this is the
     * minimum level: the encoder raises it for each QR code as far as the version allows, which
     * costs nothing in size. The QR codes of a sequence may therefore end up with different error
     * correction levels, as they carry different amounts of data.
     * </p>
     * <p>
     * The default is {@link Ecc#MEDIUM}.
     * </p>
     *
     * @param errorCorrection the error correction level
     * @return this builder
     */
    public QrCodeSequenceBuilder errorCorrection(Ecc errorCorrection) {
        options.errorCorrection(errorCorrection);
        return this;
    }

    /**
     * Sets whether the error correction level may be raised.
     * <p>
     * Boosting spends spare capacity on a higher error correction level instead of on padding.
     * It is enabled by default.
     * </p>
     *
     * @param boost {@code true} to raise the error correction level as far as the version allows,
     *              {@code false} to use exactly the level that was set
     * @return this builder
     */
    public QrCodeSequenceBuilder boostErrorCorrection(boolean boost) {
        options.boostErrorCorrection(boost);
        return this;
    }

    /**
     * Sets the range of versions (sizes) to choose from.
     * <p>
     * All QR codes of a sequence use the same version, so that they are of the same size.
     * {@code maxVersion} decides how many QR codes the sequence needs: it is the version at
     * which the fewest are needed. The version actually used is the smallest one at or above
     * {@code minVersion} that still holds the text in that number of QR codes.
     * </p>
     * <p>
     * The default range is the full one, from {@value QrCode#MIN_VERSION} to
     * {@value QrCode#MAX_VERSION}. A sequence that collapses into a single QR code always uses the
     * smallest version of the range that holds the text.
     * </p>
     *
     * @param minVersion the smallest acceptable version
     *                   ({@value QrCode#MIN_VERSION}&ndash;{@value QrCode#MAX_VERSION})
     * @param maxVersion the largest acceptable version, at least {@code minVersion}
     * @return this builder
     * @throws IllegalArgumentException if
     *                                  {@value QrCode#MIN_VERSION} &le; {@code minVersion} &le;
     *                                  {@code maxVersion} &le; {@value QrCode#MAX_VERSION} is
     *                                  violated
     */
    public QrCodeSequenceBuilder versionRange(int minVersion, int maxVersion) {
        options.versionRange(minVersion, maxVersion);
        return this;
    }

    /**
     * Sets the version (size) to use.
     * <p>
     * This is {@link #versionRange(int, int)} with a range of a single version.
     * </p>
     *
     * @param version the version ({@value QrCode#MIN_VERSION}&ndash;{@value QrCode#MAX_VERSION})
     * @return this builder
     * @throws IllegalArgumentException if the version is out of range
     */
    public QrCodeSequenceBuilder version(int version) {
        return versionRange(version, version);
    }

    /**
     * Sets the ECI designator announcing the character encoding of the text.
     * <p>
     * The designator selects the character encoding and is announced by an ECI segment at the start
     * of every QR code of the sequence. Its character set is resolved when the sequence is built;
     * {@link #eci(Eci, Charset)} takes one instead of resolving it.
     * </p>
     * <p>
     * The default is {@link Eci#AUTOMATIC}, which means ISO-8859-1 without an ECI segment for a
     * text that is encodable in ISO-8859-1, and UTF-8 with an ECI segment otherwise.
     * {@link Eci#NONE} adds no ECI segment and needs an explicit character set, so it is usable
     * through {@link #eci(Eci, Charset)} only.
     * </p>
     * <p>
     * Only designators of single-byte character sets and {@link Eci#UTF_8} are accepted; a
     * multi-byte one such as {@link Eci#SHIFT_JIS} makes {@link #build()} throw.
     * </p>
     *
     * @param eci the ECI designator
     * @return this builder
     */
    public QrCodeSequenceBuilder eci(Eci eci) {
        options.eci(eci);
        return this;
    }

    /**
     * Sets the ECI designator announcing the character encoding of the text, along with the
     * character set the text is encoded with.
     * <p>
     * The character set is used as it is rather than resolved from the designator, which is what a
     * runtime without the {@code jdk.charsets} module needs. The two are not checked against each
     * other: the designator is what the QR codes announce, the character set is what the text is
     * encoded with.
     * </p>
     * <p>
     * Only single-byte character sets and UTF-8 are accepted; anything else makes {@link #build()}
     * throw. Kanji mode is not used in a sequence at all.
     * </p>
     *
     * @param eci     the ECI designator, or {@link Eci#NONE} to add no ECI segment
     * @param charset the character set to encode the text with
     * @return this builder
     * @throws IllegalArgumentException if the designator is {@link Eci#AUTOMATIC}, which selects a
     *                                  character set itself
     */
    public QrCodeSequenceBuilder eci(Eci eci, Charset charset) {
        options.eci(eci, charset);
        return this;
    }

    // endregion

    // region Terminal method

    /**
     * Builds the sequence of QR codes.
     * <p>
     * If the text fits into a single QR code, the result is that one QR code, without any
     * Structured Append data.
     * </p>
     *
     * @return the QR codes, in sequence order
     * @throws IllegalStateException    if no text has been set
     * @throws IllegalArgumentException if the character encoding is neither single-byte nor UTF-8,
     *                                  or if the ECI designator is {@link Eci#NONE} and no
     *                                  character set was given
     * @throws EciException             if the character set of the ECI designator is unavailable
     * @throws DataTooLongException     if the text does not fit into 16 QR codes of the version
     *                                  range at the error correction level
     */
    public List<QrCode> build() {
        if (text == null)
            throw new IllegalStateException("No text specified; call text(...).");

        // An explicit character set is screened before it encodes anything, so an unsuitable one
        // is reported by name rather than through whatever its encoder happens to do.
        if (options.charset() != null)
            checkCharset(options.charset());

        var encoded = DataSegment.encodeText(text, options.eci(), options.charset());
        checkCharset(encoded.charset());
        var sequence = StructuredAppend.build(encoded, options.minVersion(), options.maxVersion(), options.ecc());

        // A sequence of two QR codes may well collapse into one: the Structured Append and ECI
        // headers of both, and the padding of the last one, are what a single QR code saves.
        if (sequence.codes().size() <= 2) {
            var singleCode = buildSingleCode();
            if (singleCode != null)
                return List.of(singleCode);
        }

        var qrCodes = new ArrayList<QrCode>(sequence.codes().size());
        for (var segments : sequence.codes())
            qrCodes.add(buildQrCode(segments, sequence.version()));
        return List.copyOf(qrCodes);
    }

    /**
     * Checks that the specified character set can be split across the QR codes of a sequence.
     * <p>
     * A QR code of a sequence has to decode on its own, so the text may only be cut where a
     * character ends. Any cut does that in a single-byte character set, and UTF-8 marks its
     * continuation bytes, which is what the split looks for. Every other multi-byte encoding
     * &mdash; Shift-JIS, Big5, GB 18030, UTF-16 &mdash; would be cut mid-character.
     * </p>
     *
     * @param charset the character set
     * @throws IllegalArgumentException if the character set is neither single-byte nor UTF-8
     */
    private static void checkCharset(Charset charset) {
        if (StandardCharsets.UTF_8.equals(charset))
            return;
        if (!charset.canEncode() || charset.newEncoder().maxBytesPerChar() != 1.0f)
            throw new IllegalArgumentException("A sequence of QR codes cannot be encoded in "
                    + charset.name() + ": only single-byte character sets and UTF-8 can be split"
                    + " across QR codes at character boundaries.");
    }

    /**
     * Builds a single QR code carrying the entire text, without Structured Append data.
     *
     * @return the QR code, or {@code null} if the text does not fit into a single QR code of the
     *         version range
     */
    private QrCode buildSingleCode() {
        try {
            return new QrCodeBuilder(options.copy()).text(text).build();
        } catch (DataTooLongException e) {
            return null;
        }
    }

    /**
     * Builds one QR code of the sequence.
     *
     * @param segments the segments of the QR code, starting with its Structured Append segment
     * @param version  the version (1&ndash;40) all QR codes of the sequence use
     * @return the QR code
     */
    private QrCode buildQrCode(List<DataSegment> segments, int version) {
        return new QrCodeBuilder(options.copy())
                .segments(segments)
                .version(version)
                .build();
    }

    // endregion
}

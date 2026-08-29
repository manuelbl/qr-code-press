/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

/**
 * Builds a QR code from a payload and the options controlling how it is encoded.
 * <p>
 * An instance is created with {@link QrCode#builder()}. Exactly one payload must be set, with
 * {@link #text(String)}, {@link #binary(byte[])} or {@link #segments(List)}. Everything else,
 * including the {@linkplain #errorCorrection(Ecc) error correction level}, has a default.
 * </p>
 * <pre>{@code
 * QrCode qrCode = QrCode.builder()
 *         .text("昨夜のコンサートは最高でした。")
 *         .errorCorrection(Ecc.QUARTILE)
 *         .versionRange(5, 20)
 *         .eci(Eci.SHIFT_JIS)
 *         .build();
 * }</pre>
 * <p>
 * {@link #build()} returns the QR code; {@link #buildWithDiagnostics()} returns it along with
 * details about how it was encoded. A builder may be reused and can build several QR codes, but a
 * single instance is not safe to use from several threads at once.
 * </p>
 */
public final class QrCodeBuilder {

    private String text;
    private ByteSlice binaryData;
    private List<DataSegment> segments;

    private final EncodingOptions options;
    private KanjiStrategy kanjiStrategy = KanjiStrategy.AUTOMATIC;
    private int forcedMask = MatrixEncoder.NO_FORCED_MASK;

    /**
     * Creates a new instance.
     */
    QrCodeBuilder() {
        this(new EncodingOptions());
    }

    /**
     * Creates a new instance with the specified options.
     * <p>
     * The options are taken as they are, so the caller passes a {@linkplain EncodingOptions#copy()
     * copy} unless it is giving them up.
     * </p>
     *
     * @param options the options controlling how the payload is encoded
     */
    QrCodeBuilder(EncodingOptions options) {
        // instantiated through QrCode.builder() or by QrCodeSequenceBuilder
        this.options = options;
    }

    // region Payload

    /**
     * Sets the text to encode.
     * <p>
     * The text is split into segments of the modes that encode it most compactly. The character
     * encoding follows from {@link #eci(Eci)} and {@link #eci(Eci, Charset)}, which by default
     * select ISO-8859-1 if the text is encodable as such, and UTF-8 with an ECI segment otherwise.
     * </p>
     * <p>
     * This replaces any payload set before.
     * </p>
     *
     * @param text the text
     * @return this builder
     */
    public QrCodeBuilder text(String text) {
        Objects.requireNonNull(text, "text");
        clearPayload();
        this.text = text;
        return this;
    }

    /**
     * Sets the binary data to encode.
     * <p>
     * Unless {@link #eci(Eci)} says otherwise, the segments start with an ECI segment indicating
     * binary data ({@link Eci#BINARY_DATA}).
     * </p>
     * <p>
     * The data is copied, so it may be modified after this call. This replaces any payload set
     * before.
     * </p>
     *
     * @param data the data
     * @return this builder
     */
    public QrCodeBuilder binary(byte[] data) {
        Objects.requireNonNull(data, "data");
        clearPayload();
        binaryData = ByteSlice.copyOf(data, 0, data.length);
        return this;
    }

    /**
     * Sets the data segments to encode.
     * <p>
     * The segments are used as they are, so the options describing how a payload is turned into
     * segments do not apply: {@link #eci(Eci)}, {@link #eci(Eci, Charset)} and
     * {@link #kanjiStrategy(KanjiStrategy)}.
     * </p>
     * <p>
     * The list is copied, so it may be modified after this call. This replaces any payload set
     * before.
     * </p>
     *
     * @param segments the segments
     * @return this builder
     */
    public QrCodeBuilder segments(List<DataSegment> segments) {
        Objects.requireNonNull(segments, "segments");
        clearPayload();
        this.segments = List.copyOf(segments);
        return this;
    }

    private void clearPayload() {
        text = null;
        binaryData = null;
        segments = null;
    }

    // endregion

    // region Options

    /**
     * Sets the error correction level.
     * <p>
     * Unless boosting is turned off with {@link #boostErrorCorrection(boolean)}, this is the
     * minimum level: the encoder raises it as far as the chosen version allows, which costs
     * nothing in size.
     * </p>
     * <p>
     * The default is {@link Ecc#MEDIUM}.
     * </p>
     *
     * @param errorCorrection the error correction level
     * @return this builder
     */
    public QrCodeBuilder errorCorrection(Ecc errorCorrection) {
        options.errorCorrection(errorCorrection);
        return this;
    }

    /**
     * Sets whether the error correction level may be raised.
     * <p>
     * The versions come in discrete steps, so the chosen one usually has capacity to spare.
     * Boosting spends it on a higher error correction level instead of on padding. It is enabled
     * by default.
     * </p>
     *
     * @param boost {@code true} to raise the error correction level as far as the chosen version
     *              allows, {@code false} to use exactly the level that was set
     * @return this builder
     */
    public QrCodeBuilder boostErrorCorrection(boolean boost) {
        options.boostErrorCorrection(boost);
        return this;
    }

    /**
     * Sets the range of versions (sizes) to choose from.
     * <p>
     * The smallest version in the range that holds the payload is used. The default range is the
     * full one, from {@value QrCode#MIN_VERSION} to {@value QrCode#MAX_VERSION}.
     * </p>
     * <p>
     * A minimum version larger than needed produces a QR code with more padding; a maximum version
     * smaller than needed makes {@link #build()} throw {@link DataTooLongException}.
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
    public QrCodeBuilder versionRange(int minVersion, int maxVersion) {
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
    public QrCodeBuilder version(int version) {
        return versionRange(version, version);
    }

    /**
     * Sets the ECI designator announcing the character encoding of the payload.
     * <p>
     * The designator selects the character encoding of a {@linkplain #text(String) text} payload
     * and is announced by an ECI segment ahead of the data. Its character set is resolved when the
     * QR code is built; {@link #eci(Eci, Charset)} takes one instead of resolving it.
     * </p>
     * <p>
     * The default is {@link Eci#AUTOMATIC}, which means ISO-8859-1 without an ECI segment for a
     * text that is encodable in ISO-8859-1 and UTF-8 with an ECI segment otherwise. For a
     * {@linkplain #binary(byte[]) binary} payload it means {@link Eci#BINARY_DATA}.
     * {@link Eci#NONE} adds no ECI segment; a text payload then needs an explicit character set
     * and so has to use {@link #eci(Eci, Charset)}.
     * </p>
     *
     * @param eci the ECI designator
     * @return this builder
     */
    public QrCodeBuilder eci(Eci eci) {
        options.eci(eci);
        return this;
    }

    /**
     * Sets the ECI designator announcing the character encoding of the payload, along with the
     * character set a {@linkplain #text(String) text} payload is encoded with.
     * <p>
     * The character set is used as it is rather than resolved from the designator, which is what a
     * runtime without the {@code jdk.charsets} module needs. The two are not checked against each
     * other: the designator is what the QR code announces, the character set is what the text is
     * encoded with. It has no effect on a {@linkplain #binary(byte[]) binary} payload, which is
     * already bytes.
     * </p>
     *
     * @param eci     the ECI designator, or {@link Eci#NONE} to add no ECI segment
     * @param charset the character set to encode a text payload with
     * @return this builder
     * @throws IllegalArgumentException if the designator is {@link Eci#AUTOMATIC}, which selects a
     *                                  character set itself
     */
    public QrCodeBuilder eci(Eci eci, Charset charset) {
        options.eci(eci, charset);
        return this;
    }

    /**
     * Sets whether Kanji mode is considered when the payload is split into segments.
     * <p>
     * The default is {@link KanjiStrategy#AUTOMATIC}: Kanji mode is used only for Shift-JIS data,
     * as many scanners assume that a Kanji segment holds Shift-JIS text.
     * </p>
     *
     * @param kanjiStrategy the strategy
     * @return this builder
     */
    public QrCodeBuilder kanjiStrategy(KanjiStrategy kanjiStrategy) {
        this.kanjiStrategy = Objects.requireNonNull(kanjiStrategy, "kanjiStrategy");
        return this;
    }

    /**
     * Pins the data mask pattern, overriding the automatic selection.
     * <p>
     * The encoder normally scores all eight patterns and applies the one that scores lowest, which
     * is what the specification asks for. Pinning one produces a valid but possibly harder to read
     * QR code; it is meant for analysis and for reproducing a specific symbol.
     * </p>
     *
     * @param mask the mask pattern index (0&ndash;7)
     * @return this builder
     * @throws IllegalArgumentException if the mask pattern index is out of range
     */
    public QrCodeBuilder forceMask(int mask) {
        if (mask < 0 || mask >= MatrixEncoder.PATTERN_COUNT)
            throw new IllegalArgumentException("mask must be between 0 and "
                    + (MatrixEncoder.PATTERN_COUNT - 1) + ", got " + mask);

        forcedMask = mask;
        return this;
    }

    // endregion

    // region Terminal methods

    /**
     * Builds the QR code.
     *
     * @return the QR code
     * @throws IllegalStateException if no payload has been set
     * @throws DataTooLongException  if the payload does not fit into a QR code of the version range
     *                               at the error correction level
     */
    public QrCode build() {
        return encode(resolveSegments(), null);
    }

    /**
     * Builds the QR code and collects details about how it was encoded.
     * <p>
     * This is slower than {@link #build()}: the penalty score of every mask pattern is calculated
     * in full, even once it is clear that a pattern cannot win.
     * </p>
     *
     * @return the QR code and the encoding details
     * @throws IllegalStateException if no payload has been set
     * @throws DataTooLongException  if the payload does not fit into a QR code of the version range
     *                               at the error correction level
     */
    public EncodingInfo buildWithDiagnostics() {
        var dataSegments = resolveSegments();
        var penalties = new PenaltyScore[MatrixEncoder.PATTERN_COUNT];
        var qrCode = encode(dataSegments, penalties);
        return new EncodingInfo(qrCode, List.of(penalties), List.copyOf(dataSegments));
    }

    private QrCode encode(List<DataSegment> dataSegments, PenaltyScore[] penalties) {
        var plan = VersionPlanner.plan(dataSegments, options.ecc(), options.minVersion(), options.maxVersion(),
                options.boostErrorCorrection());
        var codewords = Codewords.addErrorCorrection(
                Codewords.buildData(dataSegments, plan.version(), plan.ecc()), plan.version(), plan.ecc());

        var encoded = penalties == null
                ? MatrixEncoder.encode(codewords, plan.version(), plan.ecc(), forcedMask)
                : MatrixEncoder.encodeWithPenalties(codewords, plan.version(), plan.ecc(), forcedMask, penalties);

        return new QrCode(encoded.modules(), Ecc.of(plan.ecc()), encoded.mask());
    }

    /**
     * Builds the segments of the payload.
     * <p>
     * They are optimized for the largest acceptable version: the width of the character count
     * indicator depends on the version, and with it, in edge cases, the optimal segmentation. As
     * the version is only settled once the segments are known, the range end has to serve.
     * </p>
     *
     * @return the segments
     */
    private List<DataSegment> resolveSegments() {
        if (text != null)
            return DataSegment.fromText(text, options.eci(), options.charset(), options.maxVersion(), kanjiStrategy);

        if (binaryData != null) {
            var designator = Eci.AUTOMATIC.equals(options.eci()) ? Eci.BINARY_DATA : options.eci();
            return DataSegment.fromBinary(binaryData, designator, options.maxVersion(),
                    kanjiStrategy.appliesTo(designator));
        }

        if (segments != null)
            return segments;

        throw new IllegalStateException("No payload specified; call text(...), binary(...) or segments(...).");
    }

    // endregion
}

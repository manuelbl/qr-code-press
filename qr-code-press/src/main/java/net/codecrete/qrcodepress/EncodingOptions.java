/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * The options controlling how a payload is encoded, and the rules they have to obey.
 * <p>
 * {@link QrCodeBuilder} and {@link QrCodeSequenceBuilder} both offer these options and both accept
 * exactly the same values for them, so the rules live here rather than in either builder: what a
 * version range is, and what an ECI designator paired with a character set means. The builders
 * keep their own fluent methods, their own javadoc and their own return types, and validate
 * through this class.
 * </p>
 * <p>
 * The options a builder does not share &mdash; the Kanji strategy and the forced mask, which mean
 * nothing for a sequence &mdash; stay on the builder that offers them.
 * </p>
 * <p>
 * Instances are mutable and not thread-safe, like the builders holding them. {@link #copy()}
 * hands the same options to a second builder without the two sharing state.
 * </p>
 */
final class EncodingOptions {

    private Ecc errorCorrection = Ecc.MEDIUM;
    private boolean boostErrorCorrection = true;
    private int minVersion = QrCode.MIN_VERSION;
    private int maxVersion = QrCode.MAX_VERSION;
    private Eci eci = Eci.AUTOMATIC;
    private Charset charset;

    /**
     * Creates a new instance with the default options.
     */
    EncodingOptions() {
        // defaults are the field initializers
    }

    private EncodingOptions(EncodingOptions other) {
        errorCorrection = other.errorCorrection;
        boostErrorCorrection = other.boostErrorCorrection;
        minVersion = other.minVersion;
        maxVersion = other.maxVersion;
        eci = other.eci;
        charset = other.charset;
    }

    /**
     * Returns a copy of these options.
     * <p>
     * The copy shares no state with this instance, so a builder may hand its options to another
     * builder and go on changing its own.
     * </p>
     *
     * @return the copy
     */
    EncodingOptions copy() {
        return new EncodingOptions(this);
    }

    // region Error correction

    /**
     * Sets the error correction level.
     *
     * @param errorCorrection the error correction level
     * @throws NullPointerException if the error correction level is {@code null}
     */
    void errorCorrection(Ecc errorCorrection) {
        this.errorCorrection = Objects.requireNonNull(errorCorrection, "errorCorrection");
    }

    /**
     * Returns the error correction level.
     *
     * @return the error correction level
     */
    Ecc errorCorrection() {
        return errorCorrection;
    }

    /**
     * Returns the error correction level as the index the encoding pipeline uses (0&ndash;3).
     *
     * @return the error correction level index
     */
    int ecc() {
        return errorCorrection.ordinal();
    }

    /**
     * Sets whether the error correction level may be raised.
     *
     * @param boost {@code true} to raise the level as far as the version allows
     */
    void boostErrorCorrection(boolean boost) {
        boostErrorCorrection = boost;
    }

    /**
     * Returns whether the error correction level may be raised.
     *
     * @return {@code true} if the level may be raised
     */
    boolean boostErrorCorrection() {
        return boostErrorCorrection;
    }

    // endregion

    // region Version range

    /**
     * Sets the range of versions (sizes) to choose from.
     *
     * @param minVersion the smallest acceptable version
     * @param maxVersion the largest acceptable version, at least {@code minVersion}
     * @throws IllegalArgumentException if
     *                                  {@value QrCode#MIN_VERSION} &le; {@code minVersion} &le;
     *                                  {@code maxVersion} &le; {@value QrCode#MAX_VERSION} is
     *                                  violated
     */
    void versionRange(int minVersion, int maxVersion) {
        checkVersion(minVersion, "minVersion");
        checkVersion(maxVersion, "maxVersion");
        if (minVersion > maxVersion)
            throw new IllegalArgumentException(
                    "minVersion must not be greater than maxVersion, got " + minVersion + " and " + maxVersion);

        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
    }

    private static void checkVersion(int version, String name) {
        if (version < QrCode.MIN_VERSION || version > QrCode.MAX_VERSION)
            throw new IllegalArgumentException(name + " must be between " + QrCode.MIN_VERSION + " and "
                    + QrCode.MAX_VERSION + ", got " + version);
    }

    /**
     * Returns the smallest acceptable version.
     *
     * @return the version ({@value QrCode#MIN_VERSION}&ndash;{@value QrCode#MAX_VERSION})
     */
    int minVersion() {
        return minVersion;
    }

    /**
     * Returns the largest acceptable version.
     *
     * @return the version ({@value QrCode#MIN_VERSION}&ndash;{@value QrCode#MAX_VERSION})
     */
    int maxVersion() {
        return maxVersion;
    }

    // endregion

    // region Character encoding

    /**
     * Sets the ECI designator, whose character set is resolved when the QR code is built.
     *
     * @param eci the ECI designator
     * @throws NullPointerException if the designator is {@code null}
     */
    void eci(Eci eci) {
        this.eci = Objects.requireNonNull(eci, "eci");
        charset = null;
    }

    /**
     * Sets the ECI designator along with the character set a text payload is encoded with.
     * <p>
     * The two are not checked against each other: the designator is what the QR code announces,
     * the character set is what the text is encoded with.
     * </p>
     *
     * @param eci     the ECI designator, or {@link Eci#NONE} to add no ECI segment
     * @param charset the character set
     * @throws NullPointerException     if the designator or the character set is {@code null}
     * @throws IllegalArgumentException if the designator is {@link Eci#AUTOMATIC}, which selects a
     *                                  character set itself
     */
    void eci(Eci eci, Charset charset) {
        Objects.requireNonNull(eci, "eci");
        Objects.requireNonNull(charset, "charset");
        if (Eci.AUTOMATIC.equals(eci))
            throw new IllegalArgumentException(
                    "Eci.AUTOMATIC selects the character set itself; use eci(Eci) or name a designator.");

        this.eci = eci;
        this.charset = charset;
    }

    /**
     * Returns the ECI designator.
     *
     * @return the ECI designator
     */
    Eci eci() {
        return eci;
    }

    /**
     * Returns the character set a text payload is encoded with, or {@code null} to resolve it from
     * the {@linkplain #eci() ECI designator}.
     *
     * @return the character set, or {@code null}
     */
    Charset charset() {
        return charset;
    }

    // endregion
}

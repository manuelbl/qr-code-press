/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * A sequence of QR codes carrying one text between them.
 * <p>
 * Text too long for a single QR code can be split across up to 16 QR codes, linked by the
 * <i>Structured Append</i> feature of the QR code specification. Each QR code states its position
 * in the sequence, so a scanner that supports the feature reassembles the text however the QR codes
 * are presented to it.
 * </p>
 * <p>
 * The sequence is built with {@link #builder()}:
 * </p>
 * <pre>{@code
 * List<QrCode> qrCodes = QrCodeSequence.builder()
 *         .text(longText)
 *         .errorCorrection(Ecc.MEDIUM)
 *         .versionRange(10, 29)
 *         .build();
 * }</pre>
 * <p>
 * The text is spread as evenly as possible over the fewest QR codes the
 * {@linkplain QrCodeSequenceBuilder#versionRange(int, int) version range} allows, so the QR codes
 * are small and carry similar amounts of data.
 * </p>
 * <p>
 * Not every scanner supports Structured Append, so a sequence is worth using only where the text
 * genuinely does not fit into a single QR code. The builder therefore returns a single standalone
 * QR code, without the Structured Append data, whenever the text fits into one.
 * </p>
 *
 * @see QrCode
 */
public final class QrCodeSequence {

    private QrCodeSequence() {
        // non-instantiable
    }

    /**
     * Creates a builder for a sequence of QR codes.
     * <p>
     * The builder is not thread-safe; the QR codes built with it are.
     * </p>
     *
     * @return the builder
     */
    public static QrCodeSequenceBuilder builder() {
        return new QrCodeSequenceBuilder();
    }
}

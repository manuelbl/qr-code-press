/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.io.Serial;

/**
 * Thrown when the supplied data does not fit into the QR code.
 * <p>
 * Ways to handle this exception include:
 * </p>
 * <ul>
 *   <li>Lower the error correction level.</li>
 *   <li>Raise the maximum version, if the version range was narrowed (otherwise the encoder has
 *       already tried up to the maximum version of 40).</li>
 *   <li>Reduce the amount of text or binary data.</li>
 *   <li>Split the text or binary data across multiple QR codes
 *        (see {@link QrCodeSequence}).</li>
 * </ul>
 */
public class DataTooLongException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance with the specified message.
     *
     * @param message the message describing the error
     */
    public DataTooLongException(String message) {
        super(message);
    }
}

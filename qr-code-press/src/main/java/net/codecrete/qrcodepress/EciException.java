/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.io.Serial;

/**
 * Thrown when an ECI designator cannot be used as requested.
 * <p>
 * This is the case if the designator is not associated with a character set at all, or if the
 * associated character set is not available in this Java runtime. See {@link Eci#getCharset()}.
 * </p>
 */
public class EciException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance with the specified message.
     *
     * @param message the message describing the error
     */
    public EciException(String message) {
        super(message);
    }
}

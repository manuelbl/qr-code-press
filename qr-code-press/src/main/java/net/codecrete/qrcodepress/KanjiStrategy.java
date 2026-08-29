/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * Controls whether Kanji mode is considered when building data segments.
 * <p>
 * Kanji mode compactly encodes text in Shift-JIS, an encoding for Japanese text. It can equally be
 * applied to any other data containing suitable byte pairs. Many QR code scanners, however,
 * incorrectly assume that data in Kanji mode must be Shift-JIS text.
 * </p>
 * <p>
 * For the best compatibility with such scanners, use Kanji mode only for data that is Shift-JIS
 * text, which is what {@link #AUTOMATIC} does.
 * </p>
 */
public enum KanjiStrategy {

    /**
     * Use Kanji mode if the text is encoded in Shift-JIS (and if it makes the code more compact).
     * <p>
     * This is the default.
     * </p>
     */
    AUTOMATIC,

    /**
     * Use Kanji mode whenever it makes the code more compact, irrespective of the encoding.
     */
    ENABLED,

    /**
     * Never use Kanji mode.
     */
    DISABLED;

    /**
     * Indicates whether Kanji mode is to be considered for data announced by the specified ECI
     * designator.
     *
     * @param eci the ECI designator the data carries
     * @return {@code true} if Kanji mode may be used
     */
    boolean appliesTo(Eci eci) {
        return switch (this) {
            case ENABLED -> true;
            case DISABLED -> false;
            case AUTOMATIC -> Eci.SHIFT_JIS.equals(eci);
        };
    }
}

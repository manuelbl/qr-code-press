/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * The error correction level of a QR code.
 * <p>
 * QR codes carry redundant data so that they can still be read when part of the symbol is dirty,
 * reflecting or covered. The higher the level, the more damage a scanner can recover from, and
 * the less payload fits into a QR code of a given size.
 * </p>
 */
public enum Ecc {

    /** Low error correction level. About 7% of the codewords may be erroneous. */
    LOW,

    /** Medium error correction level. About 15% of the codewords may be erroneous. */
    MEDIUM,

    /** Quartile error correction level. About 25% of the codewords may be erroneous. */
    QUARTILE,

    /** High error correction level. About 30% of the codewords may be erroneous. */
    HIGH;

    /** The levels in ascending order, so that an index can be mapped without cloning the array. */
    private static final Ecc[] LEVELS = values();

    /**
     * Returns the error correction level with the specified index.
     *
     * @param index the index (0&ndash;3), as the encoding pipeline uses it
     * @return the error correction level
     */
    static Ecc of(int index) {
        return LEVELS[index];
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.List;

/**
 * Chooses the version (size) and the error correction level of a QR code.
 * <p>
 * The smallest version that holds the data wins. At that version there is usually capacity left
 * over, as the versions come in discrete steps; boosting spends it on a higher error correction
 * level, which costs nothing in size and makes the QR code more robust.
 * </p>
 */
final class VersionPlanner {

    /** The error correction levels, in ascending order, as they are named to users. */
    private static final String ECC_LETTERS = "LMQH";

    /** The highest error correction level. */
    private static final int MAX_ECC = 3;

    private VersionPlanner() {
        // non-instantiable
    }

    /**
     * The version and error correction level a QR code is to be built with.
     *
     * @param version the QR code version (1&ndash;40)
     * @param ecc     the error correction level (0&ndash;3)
     */
    record Plan(int version, int ecc) {
    }

    /**
     * Plans the version and error correction level for the specified segments.
     *
     * @param segments   the segments to encode
     * @param ecc        the error correction level (0&ndash;3); the minimum one if boosting
     * @param minVersion the smallest acceptable version (1&ndash;40)
     * @param maxVersion the largest acceptable version (1&ndash;40)
     * @param boostEcc   {@code true} to raise the error correction level as far as the chosen
     *                   version allows
     * @return the plan
     * @throws DataTooLongException if the data does not fit into a QR code of the specified range
     */
    static Plan plan(List<DataSegment> segments, int ecc, int minVersion, int maxVersion, boolean boostEcc) {
        var bitLength = 0;
        var version = minVersion;

        for (; version <= maxVersion; version += 1) {
            // The character count indicators widen at versions 10 and 27, and nowhere else, so the
            // bit length only has to be recomputed when the search crosses one of those versions.
            if (version == minVersion || version == 10 || version == 27)
                bitLength = DataSegment.totalLength(segments, version);

            if (fits(bitLength, version, ecc))
                break;
        }

        if (version > maxVersion)
            throw new DataTooLongException(tooLongMessage(maxVersion, ecc));

        if (boostEcc) {
            // The bit length depends on the version, not on the error correction level, so the
            // one the search settled on still applies.
            while (ecc < MAX_ECC && fits(bitLength, version, ecc + 1))
                ecc += 1;
        }

        return new Plan(version, ecc);
    }

    /**
     * Indicates whether the specified number of data bits fits into a QR code of the specified
     * version and error correction level.
     *
     * @param bitLength the number of data bits
     * @param version   the QR code version (1&ndash;40)
     * @param ecc       the error correction level (0&ndash;3)
     * @return {@code true} if the data fits
     */
    private static boolean fits(int bitLength, int version, int ecc) {
        return bitLength <= 8 * QrCodeParameters.dataCodewordCapacity(version, ecc);
    }

    /**
     * Returns the letter naming the specified error correction level to users.
     *
     * @param ecc the error correction level (0&ndash;3)
     * @return the letter
     */
    static char eccLetter(int ecc) {
        return ECC_LETTERS.charAt(ecc);
    }

    /**
     * Returns the message for data exceeding the capacity of the version range.
     *
     * @param maxVersion the largest acceptable version (1&ndash;40)
     * @param ecc        the error correction level (0&ndash;3)
     * @return the message
     */
    private static String tooLongMessage(int maxVersion, int ecc) {
        // Naming the version is only a hint worth giving if the caller narrowed the range; at
        // version 40 the data is simply too long for a QR code at this error correction level.
        var level = eccLetter(ecc);
        return maxVersion < QrCodeParameters.MAX_VERSION
                ? "Data is too long to fit into a QR code with version " + maxVersion
                        + " and error correction level " + level + "."
                : "Data is too long to fit into a QR code with error correction level " + level + ".";
    }
}

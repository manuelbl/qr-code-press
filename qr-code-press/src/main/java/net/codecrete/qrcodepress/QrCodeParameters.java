/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * The ISO/IEC 18004 parameter tables, keyed by version (1&ndash;40) and error correction level
 * (0&ndash;3 = L/M/Q/H).
 * <p>
 * This is the foundation the rest of the pipeline depends on (version planning, codeword layout and
 * matrix encoding), as well as the fixed patterns and Structured Append. The comments cite the table
 * numbers in the specification.
 * </p>
 * <p>
 * The tables are indexed directly, without range checks: arguments are validated where they enter
 * the library, and the lookups sit on the hot path.
 * </p>
 */
final class QrCodeParameters {

    /** The lowest QR code version. */
    static final int MIN_VERSION = 1;

    /** The highest QR code version. */
    static final int MAX_VERSION = 40;

    private QrCodeParameters() {
        // non-instantiable
    }

    // region Size

    /**
     * Returns the side length (in modules) of a QR code of the given version.
     *
     * @param version the version (1&ndash;40)
     * @return the number of modules per side
     */
    static int size(int version) {
        return 17 + version * 4;
    }

    /**
     * Returns the version of a QR code with the given side length (in modules).
     *
     * @param size the number of modules per side
     * @return the version (1&ndash;40)
     */
    static int version(int size) {
        return (size - 17) / 4;
    }

    // endregion

    // region Capacity

    /**
     * Returns the number of data codewords of a QR code of the given version and error correction
     * level.
     *
     * @param version the version (1&ndash;40)
     * @param ecc     the error correction level (0&ndash;3 = L/M/Q/H)
     * @return the number of codewords
     */
    static int dataCodewordCapacity(int version, int ecc) {
        return DATA_CODEWORD_CAPACITY[ecc][version - 1];
    }

    // See table 7 "Number of symbol characters and input data capacity for QR Code"
    // in QR code specification (ISO/IEC 18004:2015(E))
    private static final int[][] DATA_CODEWORD_CAPACITY = {
            // ECC L
            {
                    19, 34, 55, 80, 108, 136, 156, 194, 232, 274,
                    324, 370, 428, 461, 523, 589, 647, 721, 795, 861,
                    932, 1006, 1094, 1174, 1276, 1370, 1468, 1531, 1631, 1735,
                    1843, 1955, 2071, 2191, 2306, 2434, 2566, 2702, 2812, 2956
            },
            // ECC M
            {
                    16, 28, 44, 64, 86, 108, 124, 154, 182, 216,
                    254, 290, 334, 365, 415, 453, 507, 563, 627, 669,
                    714, 782, 860, 914, 1000, 1062, 1128, 1193, 1267, 1373,
                    1455, 1541, 1631, 1725, 1812, 1914, 1992, 2102, 2216, 2334
            },
            // ECC Q
            {
                    13, 22, 34, 48, 62, 76, 88, 110, 132, 154,
                    180, 206, 244, 261, 295, 325, 367, 397, 445, 485,
                    512, 568, 614, 664, 718, 754, 808, 871, 911, 985,
                    1033, 1115, 1171, 1231, 1286, 1354, 1426, 1502, 1582, 1666
            },
            // ECC H
            {
                    9, 16, 26, 36, 46, 60, 66, 86, 100, 122,
                    140, 158, 180, 197, 223, 253, 283, 313, 341, 385,
                    406, 442, 464, 514, 538, 596, 628, 661, 701, 745,
                    793, 845, 901, 961, 986, 1054, 1096, 1142, 1222, 1276
            }
    };

    /**
     * Returns the number of error correction blocks of a QR code of the given version and error
     * correction level.
     *
     * @param version the version (1&ndash;40)
     * @param ecc     the error correction level (0&ndash;3 = L/M/Q/H)
     * @return the number of blocks
     */
    static int blockCount(int version, int ecc) {
        return BLOCK_COUNT[ecc][version - 1];
    }

    // See table 9 "Error correction characteristics for QR Code"
    // in QR code specification (ISO/IEC 18004:2015(E))
    private static final int[][] BLOCK_COUNT = {
            // ECC L
            {
                    1, 1, 1, 1, 1, 2, 2, 2, 2, 4,
                    4, 4, 4, 4, 6, 6, 6, 6, 7, 8,
                    8, 9, 9, 10, 12, 12, 12, 13, 14, 15,
                    16, 17, 18, 19, 19, 20, 21, 22, 24, 25
            },
            // ECC M
            {
                    1, 1, 1, 2, 2, 4, 4, 4, 5, 5,
                    5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
                    17, 17, 18, 20, 21, 23, 25, 26, 28, 29,
                    31, 33, 35, 37, 38, 40, 43, 45, 47, 49
            },
            // ECC Q
            {
                    1, 1, 2, 2, 4, 4, 6, 6, 8, 8,
                    8, 10, 12, 16, 12, 17, 16, 18, 21, 20,
                    23, 23, 25, 27, 29, 34, 34, 35, 38, 40,
                    43, 45, 48, 51, 53, 56, 59, 62, 65, 68
            },
            // ECC H
            {
                    1, 1, 2, 4, 4, 4, 5, 6, 8, 8,
                    11, 11, 16, 16, 18, 16, 19, 21, 25, 25,
                    25, 34, 30, 32, 35, 37, 40, 42, 45, 48,
                    51, 54, 57, 60, 63, 66, 70, 74, 77, 81
            }
    };

    /**
     * Returns the total number of codewords (data and error correction) of a QR code of the given
     * version.
     *
     * @param version the version (1&ndash;40)
     * @return the number of codewords
     */
    static int codewordCapacity(int version) {
        return CODEWORD_CAPACITY[version - 1];
    }

    // See table 9 "Error correction characteristics for QR Code"
    // in QR code specification (ISO/IEC 18004:2015(E))
    private static final int[] CODEWORD_CAPACITY = {
            26, 44, 70, 100, 134, 172, 196, 242, 292, 346,
            404, 466, 532, 581, 655, 733, 815, 901, 991, 1085,
            1156, 1258, 1364, 1474, 1588, 1706, 1828, 1921, 2051, 2185,
            2323, 2465, 2611, 2761, 2876, 3034, 3196, 3362, 3532, 3706
    };

    // endregion

    // region Alignment patterns

    /**
     * Returns the row/column coordinates of the alignment pattern centers of a QR code of the given
     * version.
     * <p>
     * The alignment patterns are located at all combinations of the returned coordinates, except
     * for the three combinations overlapping the finder patterns. Version 1 has no alignment
     * patterns and returns an empty array.
     * </p>
     * <p>
     * The returned array is shared and must not be modified.
     * </p>
     *
     * @param version the version (1&ndash;40)
     * @return the coordinates, in ascending order
     */
    static int[] alignmentPatternPositions(int version) {
        return ALIGNMENT_PATTERN_POSITIONS[version - 1];
    }

    // See table E.1 "Row/column coordinates of center module of alignment patterns"
    // in QR code specification (ISO/IEC 18004:2015(E))
    private static final int[][] ALIGNMENT_PATTERN_POSITIONS = {
            {},
            { 6, 18 },
            { 6, 22 },
            { 6, 26 },
            { 6, 30 },
            { 6, 34 },
            { 6, 22, 38 },
            { 6, 24, 42 },
            { 6, 26, 46 },
            { 6, 28, 50 },
            { 6, 30, 54 },
            { 6, 32, 58 },
            { 6, 34, 62 },
            { 6, 26, 46, 66 },
            { 6, 26, 48, 70 },
            { 6, 26, 50, 74 },
            { 6, 30, 54, 78 },
            { 6, 30, 56, 82 },
            { 6, 30, 58, 86 },
            { 6, 34, 62, 90 },
            { 6, 28, 50, 72, 94 },
            { 6, 26, 50, 74, 98 },
            { 6, 30, 54, 78, 102 },
            { 6, 28, 54, 80, 106 },
            { 6, 32, 58, 84, 110 },
            { 6, 30, 58, 86, 114 },
            { 6, 34, 62, 90, 118 },
            { 6, 26, 50, 74, 98, 122 },
            { 6, 30, 54, 78, 102, 126 },
            { 6, 26, 52, 78, 104, 130 },
            { 6, 30, 56, 82, 108, 134 },
            { 6, 34, 60, 86, 112, 138 },
            { 6, 30, 58, 86, 114, 142 },
            { 6, 34, 62, 90, 118, 146 },
            { 6, 30, 54, 78, 102, 126, 150 },
            { 6, 24, 50, 76, 102, 128, 154 },
            { 6, 28, 54, 80, 106, 132, 158 },
            { 6, 32, 58, 84, 110, 136, 162 },
            { 6, 26, 54, 82, 110, 138, 166 },
            { 6, 30, 58, 86, 114, 142, 170 }
    };

    // endregion

    // region Format and version information

    /**
     * Returns the 15-bit format information for the given error correction level and mask pattern.
     * <p>
     * The value is the BCH(15, 5) code word of the 5 format data bits &mdash; the 2-bit error
     * correction level indicator of table 12 (L 01, M 00, Q 11, H 10) followed by the 3-bit mask
     * pattern &mdash; already XORed with the mask 0x5412 required by the specification.
     * </p>
     *
     * @param ecc     the error correction level (0&ndash;3 = L/M/Q/H)
     * @param pattern the mask pattern (0&ndash;7)
     * @return the format information bits
     */
    static int formatInformationBits(int ecc, int pattern) {
        return FORMAT_INFORMATION_BITS[(ecc << 3) + pattern];
    }

    private static final int[] FORMAT_INFORMATION_BITS = {
            // ECC Low
            0x77c4, 0x72f3, 0x7daa, 0x789d, 0x662f, 0x6318, 0x6c41, 0x6976,
            // ECC Medium
            0x5412, 0x5125, 0x5e7c, 0x5b4b, 0x45f9, 0x40ce, 0x4f97, 0x4aa0,
            // ECC Quartile
            0x355f, 0x3068, 0x3f31, 0x3a06, 0x24b4, 0x2183, 0x2eda, 0x2bed,
            // ECC High
            0x1689, 0x13be, 0x1ce7, 0x19d0, 0x0762, 0x0255, 0x0d0c, 0x083b
    };

    /**
     * Returns the 18-bit version information of the given version.
     * <p>
     * The value is the BCH(18, 6) code word of the 6 version bits. Versions below 7 carry no
     * version information.
     * </p>
     *
     * @param version the version (7&ndash;40)
     * @return the version information bits
     */
    static int versionInformationBits(int version) {
        return VERSION_INFORMATION_BITS[version - 7];
    }

    private static final int[] VERSION_INFORMATION_BITS = {
            // Version 7 to 40
            0x07c94, 0x085bc, 0x09a99, 0x0a4d3,
            0x0bbf6, 0x0c762, 0x0d847, 0x0e60d, 0x0f928, 0x10b78, 0x1145d, 0x12a17, 0x13532, 0x149a6,
            0x15683, 0x168c9, 0x177ec, 0x18ec4, 0x191e1, 0x1afab, 0x1b08e, 0x1cc1a, 0x1d33f, 0x1ed75,
            0x1f250, 0x209d5, 0x216f0, 0x228ba, 0x2379f, 0x24b0b, 0x2542e, 0x26a64, 0x27541, 0x28c69
    };

    // endregion
}

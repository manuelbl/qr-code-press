/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * Single source of truth for the fixed-pattern geometry of a QR code version.
 * <p>
 * The fixed patterns are the modules placed identically for a given version regardless of payload:
 * finder patterns, separators, timing patterns, alignment patterns and version information, plus the
 * reserved area for the format information.
 * </p>
 * <p>
 * {@link #build(int)} produces two views of the same geometry in a single walk:
 * </p>
 * <ul>
 * <li>{@code drawn} &mdash; the actual dark and light modules of the fixed patterns.</li>
 * <li>{@code reserved} &mdash; the union of all fixed-pattern footprints, i.e. every module the
 * payload must not use. A footprint is reserved even where its modules are light (separators, the
 * light rings of a finder, format and version {@code 0} bits), so the reserved view cannot be
 * derived from the drawn one.</li>
 * </ul>
 * <p>
 * The format information is reserve-only here; its bits depend on the error correction level and on
 * the chosen mask pattern and are drawn later, by
 * {@link MatrixEncoder#drawFormatInformation(ScoringMatrix, int, int)}.
 * </p>
 */
final class FixedPatterns {

    private FixedPatterns() {
        // non-instantiable
    }

    /**
     * The two views of the fixed-pattern geometry of a version.
     *
     * @param drawn    the dark and light modules of the fixed patterns
     * @param reserved the modules the payload must not use
     */
    record Patterns(BitMatrix drawn, BitMatrix reserved) {
    }

    // region Cache

    /**
     * The cached geometry of a version: the drawn modules, and the reserved mask already inverted
     * into the payload-area map (the modules the payload zigzag may fill). Both come out of a
     * single {@link #build(int)} walk, so they are cached together.
     */
    private record Cached(BitMatrix drawn, BitMatrix payloadAreaMap) {
    }

    private static final LazyCache<Cached> CACHE =
            new LazyCache<>(QrCodeParameters.MAX_VERSION + 1, FixedPatterns::compute);

    private static Cached compute(int version) {
        var patterns = build(version);
        var payloadAreaMap = patterns.reserved();
        payloadAreaMap.invert();
        return new Cached(patterns.drawn(), payloadAreaMap);
    }

    // endregion

    // region Accessors

    /**
     * Creates a module matrix for the specified version with the fixed patterns already drawn.
     *
     * @param version the QR code version (1&ndash;40)
     * @return a new, mutable matrix
     */
    static BitMatrix createMatrix(int version) {
        return CACHE.get(version).drawn().copy();
    }

    /**
     * Returns the payload-area map of the specified version: a matrix with a bit set wherever
     * payload data goes, i.e. the complement of the reserved modules.
     * <p>
     * The returned matrix is shared and cached. Callers must not mutate it.
     * </p>
     *
     * @param version the QR code version (1&ndash;40)
     * @return the shared payload-area map
     */
    static BitMatrix payloadAreaMap(int version) {
        return CACHE.get(version).payloadAreaMap();
    }

    // endregion

    // region Single walk

    /**
     * Builds the fixed-pattern geometry of the specified version in a single walk.
     * <p>
     * Each region stamps its dark modules into {@code drawn} and reserves its footprint into
     * {@code reserved} in the same place, so the two views cannot drift apart. The invariant
     * {@code drawn AND NOT reserved == 0} holds by construction.
     * </p>
     * <p>
     * The draw order is load-bearing: the alignment patterns are drawn after the timing patterns so
     * that they overwrite the timing line where the two overlap.
     * </p>
     *
     * @param version the QR code version (1&ndash;40)
     * @return the drawn modules and the reserved-module mask
     */
    static Patterns build(int version) {
        var size = QrCodeParameters.size(version);
        var drawn = new BitMatrix(size);
        var reserved = new BitMatrix(size);

        // the single asymmetric dark module
        drawn.set(8, size - 8, true);
        reserved.set(8, size - 8, true);

        // format information (reserve-only; its bits depend on the ECC level and the mask)
        reserveFormatInformation(reserved);

        // version information (versions 7 and up)
        drawVersionInformation(drawn, version);
        reserveVersionInformation(reserved, version);

        // finder patterns and their separators (footprint 8x8, pattern 7x7)
        drawFinderPattern(drawn, 0, 0);
        drawFinderPattern(drawn, size - 7, 0);
        drawFinderPattern(drawn, 0, size - 7);
        reserved.fillRect(0, 0, 8, 8);
        reserved.fillRect(size - 8, 0, 8, 8);
        reserved.fillRect(0, size - 8, 8, 8);

        // timing patterns
        drawTimingPattern(drawn);
        reserved.fillRect(8, 6, size - 16, 1);
        reserved.fillRect(6, 8, 1, size - 16);

        // alignment patterns, drawn last so they overwrite the timing line on overlap
        drawAndReserveAlignmentPatterns(drawn, reserved, version);

        return new Patterns(drawn, reserved);
    }

    // endregion

    // region Finder patterns

    private static void drawFinderPattern(BitMatrix modules, int x, int y) {
        for (var i = 0; i < 7; i += 1) {
            modules.set(x + i, y, true);
            modules.set(x + i, y + 6, true);
        }

        for (var i = 1; i < 6; i += 1) {
            modules.set(x, y + i, true);
            modules.set(x + 1, y + i, false);
            modules.set(x + 5, y + i, false);
            modules.set(x + 6, y + i, true);
        }

        for (var i = 2; i < 5; i += 1) {
            modules.set(x + i, y + 1, false);
            modules.set(x + i, y + 5, false);
        }

        for (var i = 2; i < 5; i += 1) {
            modules.set(x + i, y + 2, true);
            modules.set(x + i, y + 3, true);
            modules.set(x + i, y + 4, true);
        }
    }

    // endregion

    // region Alignment patterns

    private static void drawAndReserveAlignmentPatterns(BitMatrix drawn, BitMatrix reserved, int version) {
        if (version == 1)
            return; // version 1 has no alignment patterns

        var positions = QrCodeParameters.alignmentPatternPositions(version);
        var count = positions.length;

        for (var x = 0; x < count; x += 1) {
            for (var y = 0; y < count; y += 1) {
                // the three corners occupied by the finder patterns carry no alignment pattern
                if ((x == 0 && y == 0) || (x == count - 1 && y == 0) || (x == 0 && y == count - 1))
                    continue;

                reserved.fillRect(positions[x] - 2, positions[y] - 2, 5, 5);
                drawAlignmentPattern(drawn, positions[x], positions[y]);
            }
        }
    }

    private static void drawAlignmentPattern(BitMatrix modules, int x, int y) {
        for (var i = -2; i <= 2; i += 1) {
            modules.set(x + i, y - 2, true);
            modules.set(x + i, y + 2, true);
        }

        for (var i = -1; i <= 1; i += 1) {
            modules.set(x - 2, y + i, true);
            modules.set(x + 2, y + i, true);
        }

        modules.set(x, y, true);
    }

    // endregion

    // region Timing patterns

    private static void drawTimingPattern(BitMatrix modules) {
        var size = modules.size();
        for (var x = 8; x < size - 8; x += 1) {
            var isDark = ((x + 1) & 1) != 0;
            modules.set(x, 6, isDark);
            modules.set(6, x, isDark);
        }
    }

    // endregion

    // region Version information

    private static void reserveVersionInformation(BitMatrix reserved, int version) {
        if (version < 7)
            return; // versions 1 to 6 carry no version information

        var size = reserved.size();
        reserved.fillRect(0, size - 11, 6, 3); // bottom left corner
        reserved.fillRect(size - 11, 0, 3, 6); // top right corner
    }

    private static void drawVersionInformation(BitMatrix modules, int version) {
        if (version < 7)
            return; // versions 1 to 6 carry no version information

        var size = modules.size();
        var bits = QrCodeParameters.versionInformationBits(version);

        for (var bit = 0; bit < 18; bit += 1) {
            var isDark = (bits & (1 << bit)) != 0;
            var x = bit / 3;
            var y = bit % 3;

            modules.set(x, size - 11 + y, isDark); // bottom left corner
            modules.set(size - 11 + y, x, isDark); // top right corner
        }
    }

    // endregion

    // region Format information

    private static void reserveFormatInformation(BitMatrix reserved) {
        var size = reserved.size();
        reserved.fillRect(8, 0, 1, 9);
        reserved.fillRect(0, 8, 8, 1);
        reserved.fillRect(size - 8, 8, 8, 1);
        reserved.fillRect(8, size - 7, 1, 7);
    }

    // endregion
}

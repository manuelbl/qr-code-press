/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helpers for tests dealing with {@link BitMatrix}.
 */
final class TestMatrices {

    private TestMatrices() {
    }

    /**
     * Builds a deterministic pseudo-random pattern, using a linear congruential generator.
     */
    static BitMatrix pattern(int size, int seed) {
        var matrix = new BitMatrix(size);
        var state = seed;
        for (var y = 0; y < size; y += 1) {
            for (var x = 0; x < size; x += 1) {
                state = state * 1664525 + 1013904223;
                if ((state & 1) != 0) {
                    matrix.set(x, y, true);
                }
            }
        }
        return matrix;
    }

    /**
     * Returns reproducible pseudo-random codewords filling a QR code of the specified version.
     */
    static byte[] codewordsFor(int version) {
        var codewords = new byte[QrCodeParameters.codewordCapacity(version)];
        new Random(version).nextBytes(codewords);
        return codewords;
    }

    /**
     * Renders the matrix as one string per row ({@code #} for set, {@code .} for cleared), so that
     * a failed comparison points at the offending row instead of at a bare boolean.
     */
    static List<String> rowsOf(BitMatrix matrix) {
        var rows = new ArrayList<String>(matrix.size());
        for (var y = 0; y < matrix.size(); y += 1) {
            var row = new StringBuilder(matrix.size());
            for (var x = 0; x < matrix.size(); x += 1) {
                row.append(matrix.get(x, y) ? '#' : '.');
            }
            rows.add(row.toString());
        }
        return rows;
    }

    /**
     * Renders a QR code the same way, but through its public accessors, so that a comparison with
     * the verified data covers the accessors as well as the encoding.
     */
    static List<String> rowsOf(QrCode qrCode) {
        var rows = new ArrayList<String>(qrCode.getSize());
        for (var y = 0; y < qrCode.getSize(); y += 1) {
            var row = new StringBuilder(qrCode.getSize());
            for (var x = 0; x < qrCode.getSize(); x += 1) {
                row.append(qrCode.getModule(x, y) ? '#' : '.');
            }
            rows.add(row.toString());
        }
        return rows;
    }

    /**
     * Hashes the matrix the way the verified data exporter does: over the rendered rows joined by
     * newlines, so that the fixtures survive a change to the internal storage layout.
     */
    static String sha256(BitMatrix matrix) {
        var content = String.join("\n", rowsOf(matrix));
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void assertMatricesEqual(BitMatrix expected, BitMatrix actual) {
        assertThat(actual.size()).isEqualTo(expected.size());
        assertThat(rowsOf(actual)).isEqualTo(rowsOf(expected));
    }

    /**
     * Transposes the matrix the obvious way, bit by bit — the oracle for the delta-swap transpose.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    static void naiveTranspose(BitMatrix matrix) {
        var size = matrix.size();
        for (var y = 0; y < size; y += 1) {
            for (var x = y + 1; x < size; x += 1) {
                var a = matrix.get(x, y);
                var b = matrix.get(y, x);
                matrix.set(x, y, b);
                matrix.set(y, x, a);
            }
        }
    }
}

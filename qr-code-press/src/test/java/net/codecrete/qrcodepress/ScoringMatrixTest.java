/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static net.codecrete.qrcodepress.TestMatrices.assertMatricesEqual;
import static net.codecrete.qrcodepress.TestMatrices.codewordsFor;
import static net.codecrete.qrcodepress.TestMatrices.naiveTranspose;
import static net.codecrete.qrcodepress.TestMatrices.pattern;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scoring matrix tests.
 * <p>
 * The versions here &mdash; 1, 11, 12 and 40 &mdash; are the smallest and largest of each
 * {@link BitMatrix} row layout, so both the compact and the wide layout are covered on either side
 * of the boundary at 64 columns.
 * </p>
 */
class ScoringMatrixTest {

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 1, 11, 12, 40 })
    @DisplayName("the column view is the transpose of the row view")
    void columnsAreTransposeOfRows(int version) {
        var matrix = symbolOf(version);

        assertColumnsAreTranspose(matrix);
    }

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 1, 11, 12, 40 })
    @DisplayName("the row view is the symbol: the fixed patterns with the payload filled in")
    void rowsAreTheSymbol(int version) {
        var codewords = codewordsFor(version);

        var matrix = ScoringMatrix.ofSymbol(codewords, version);

        var expected = FixedPatterns.createMatrix(version);
        MatrixEncoder.fillPayload(expected, codewords, version);
        assertMatricesEqual(expected, matrix.rows());
        assertThat(matrix.size()).isEqualTo(QrCodeParameters.size(version));
    }

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 1, 11, 12, 40 })
    @DisplayName("XOR keeps both views in sync")
    void xorKeepsViewsInSync(int version) {
        var matrix = symbolOf(version);

        matrix.xor(maskPair(QrCodeParameters.size(version), 0xABCDEF));

        assertColumnsAreTranspose(matrix);
    }

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 1, 12, 40 })
    @DisplayName("XOR twice restores the matrix and stays in sync")
    void xorTwiceRestoresAndStaysInSync(int version) {
        var matrix = symbolOf(version);
        var original = matrix.rows().copy();
        var mask = maskPair(QrCodeParameters.size(version), 0xABCDEF);

        matrix.xor(mask);
        matrix.xor(mask);

        assertMatricesEqual(original, matrix.rows());
        assertColumnsAreTranspose(matrix);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 1, 11, 12, 40 })
    @DisplayName("setting a format bit updates both views")
    void setFormatBitUpdatesBothViews(int version) {
        var matrix = symbolOf(version);
        var size = QrCodeParameters.size(version);

        // coordinates spanning word boundaries and both axes
        int[][] bits = {
                { 8, 0, 1 }, { 0, 8, 1 }, { 8, 8, 1 }, { 7, 8, 1 }, { 8, 7, 1 },
                { size - 1, 8, 1 }, { 8, size - 1, 1 }, { size - 1, size - 1, 1 },
                { 8, 8, 0 }
        };

        for (var bit : bits) {
            var x = bit[0];
            var y = bit[1];
            var value = bit[2] != 0;

            matrix.setFormatBit(x, y, value);

            assertThat(matrix.rows().get(x, y)).isEqualTo(value);
            assertThat(matrix.columns().get(y, x)).isEqualTo(value);
        }

        assertColumnsAreTranspose(matrix);
    }

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = { 1, 12, 40 })
    @DisplayName("finish applies the mask to the row view only")
    void finishAppliesMaskToRowsOnly(int version) {
        var matrix = symbolOf(version);
        var mask = maskPair(QrCodeParameters.size(version), 0xABCDEF);

        var expectedRows = matrix.rows().copy();
        expectedRows.xor(mask.rows());
        // the column view is discarded after mask selection, so finish must leave it untouched
        var expectedColumns = matrix.columns().copy();

        var result = matrix.finish(mask);

        assertThat(result).isSameAs(matrix.rows());
        assertMatricesEqual(expectedRows, result);
        assertMatricesEqual(expectedColumns, matrix.columns());
    }

    private static ScoringMatrix symbolOf(int version) {
        return ScoringMatrix.ofSymbol(codewordsFor(version), version);
    }

    @SuppressWarnings("SameParameterValue")
    private static MaskPair maskPair(int size, int seed) {
        var rows = pattern(size, seed);
        var columns = rows.copy();
        columns.transpose();
        return new MaskPair(rows, columns);
    }

    private static void assertColumnsAreTranspose(ScoringMatrix matrix) {
        var transposed = matrix.rows().copy();
        naiveTranspose(transposed);

        assertMatricesEqual(transposed, matrix.columns());
    }
}

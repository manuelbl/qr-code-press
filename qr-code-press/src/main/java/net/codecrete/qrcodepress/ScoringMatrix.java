/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * A QR code symbol paired with its transpose, kept in sync, used while selecting the mask pattern.
 * <p>
 * The {@link #rows()} view is the matrix as stored; the {@link #columns()} view is its transpose.
 * Penalty rules that scan rows read {@code rows()}; rules that scan columns read {@code columns()},
 * so the column rules reuse the row algorithm instead of duplicating it.
 * </p>
 * <p>
 * Every mutation ({@link #xor(MaskPair)}, {@link #setFormatBit(int, int, boolean)}) updates both
 * views together, so they cannot drift apart. This class owns that invariant; callers never
 * maintain the transpose by hand.
 * </p>
 * <p>
 * It owns a second one: {@link #ofSymbol(byte[], int)} builds the matrix itself, from the version's
 * fixed patterns, so every scoring matrix carries the three finder patterns. That is the
 * precondition {@link Penalty#calculate(ScoringMatrix, int)} needs for its early stop, and holding
 * it here means the penalty rules cannot be handed a grid that breaks it.
 * </p>
 */
final class ScoringMatrix {

    private final BitMatrix rows;
    private final BitMatrix columns;

    private ScoringMatrix(BitMatrix rows, BitMatrix columns) {
        this.rows = rows;
        this.columns = columns;
    }

    /**
     * Creates a scoring matrix for the symbol the given codewords form: the version's fixed
     * patterns, with the payload filled into the modules they leave free.
     * <p>
     * This is the only way to obtain a scoring matrix, which is what guarantees that a scored
     * matrix is a symbol and not just any bit grid. Neither the payload nor the mask nor the format
     * information reaches the finder patterns, so they are present for every matrix scored.
     * </p>
     * <p>
     * The {@link #rows()} view is mutated in place by {@link #xor(MaskPair)},
     * {@link #setFormatBit(int, int, boolean)} and {@link #finish(MaskPair)}, and is the matrix
     * {@link #finish(MaskPair)} returns. The {@link #columns()} view is built as its transpose.
     * </p>
     *
     * @param codewords the interleaved data and error correction codewords
     * @param version   the QR code version (1&ndash;40)
     * @return a new scoring matrix
     */
    static ScoringMatrix ofSymbol(byte[] codewords, int version) {
        var rows = FixedPatterns.createMatrix(version);
        MatrixEncoder.fillPayload(rows, codewords, version);
        var columns = rows.copy();
        columns.transpose();
        return new ScoringMatrix(rows, columns);
    }

    /**
     * Returns the matrix as stored.
     *
     * @return the row view
     */
    BitMatrix rows() {
        return rows;
    }

    /**
     * Returns the transpose of the matrix.
     *
     * @return the column view
     */
    BitMatrix columns() {
        return columns;
    }

    /**
     * Returns the side length (in modules) of the matrix.
     *
     * @return the size
     */
    int size() {
        return rows.size();
    }

    /**
     * XORs the given mask into both views, keeping them in sync.
     *
     * @param mask the mask pair to XOR in
     */
    void xor(MaskPair mask) {
        rows.xor(mask.rows());
        columns.xor(mask.columns());
    }

    /**
     * Sets a format-information bit, updating both views so they stay in sync.
     *
     * @param x     the x-coordinate in the {@link #rows()} view
     * @param y     the y-coordinate in the {@link #rows()} view
     * @param value the bit value
     */
    @SuppressWarnings("SuspiciousNameCombination")
    void setFormatBit(int x, int y, boolean value) {
        rows.set(x, y, value);
        columns.set(y, x, value);
    }

    /**
     * Applies the chosen mask to the finished matrix and returns it.
     * <p>
     * Only the {@link #rows()} view is updated: the {@link #columns()} view is discarded once the
     * mask pattern has been selected, so its mask XOR is skipped. After {@code finish} the two
     * views are no longer in sync.
     * </p>
     *
     * @param mask the chosen mask pattern's mask pair
     * @return the finished module matrix (the {@link #rows()} view)
     */
    BitMatrix finish(MaskPair mask) {
        rows.xor(mask.rows());
        return rows;
    }
}

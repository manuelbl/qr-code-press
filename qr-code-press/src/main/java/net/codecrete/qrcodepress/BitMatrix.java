/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.Objects;

/**
 * Square matrix of binary pixels.
 * <p>
 * The bits are stored in a {@code long} array in row-major order (y-coordinates specify the row,
 * x-coordinates specify the column). A row always starts at a word boundary, so that the penalty
 * rules can scan it word by word. In each row, the bits at column positions outside the logical
 * size are always 0.
 * </p>
 * <p>
 * A row holds its modules in one, two or three words, one per 64 columns, which is what
 * {@link #usedWordsPerRow()} reports. This is the library's <b>three row layouts</b>, and each one
 * covers a range of QR code versions:
 * </p>
 * <table border="1">
 *   <caption>The row layouts and the versions taking them</caption>
 *   <tr><th>Words of modules</th><th>Sizes</th><th>Versions</th><th>Stride</th></tr>
 *   <tr><td>1</td><td>1&ndash;64</td><td>1&ndash;11</td><td>1</td></tr>
 *   <tr><td>2</td><td>65&ndash;128</td><td>12&ndash;27</td><td>2</td></tr>
 *   <tr><td>3</td><td>129&ndash;{@value #MAX_SIZE}</td><td>28&ndash;40</td><td>4</td></tr>
 * </table>
 * <p>
 * The layout follows from the size alone, so two matrices of the same size always agree on it and
 * an {@link #and(BitMatrix)} or {@link #xor(BitMatrix)} can never mix two of them. It exists for
 * {@link Penalty}, which has an implementation of every rule per layout and is where encoding a QR
 * code spends most of its time: the narrower the row, the fewer words a rule scans, and versions 1
 * to 11 are most QR codes.
 * </p>
 * <p>
 * The <em>stride</em> from one row to the next in {@link #raw()}, which {@link #wordsPerRow()}
 * reports, is that word count rounded up to a power of two, so that a row index is a shift rather
 * than a multiplication. It differs from the word count only for a three-word row, which is
 * allocated a fourth, always-zero padding word: {@link #invert()} clears it,
 * {@link #fillRect(int, int, int, int)} cannot reach it, and {@link #transpose()} never writes it.
 * Operations over the whole matrix therefore ignore the distinction and run flat over
 * {@link #raw()}, which costs them that word for the largest versions and is what lets them
 * vectorize.
 * </p>
 * <p>
 * The maximum supported size is {@value #MAX_SIZE} &times; {@value #MAX_SIZE} bits, the largest
 * with three words of modules per row. Every QR code version fits: version 40, the largest, is 177
 * modules wide.
 * </p>
 */
final class BitMatrix {

    /** Greatest number of 64-bit words of modules a row can hold, that of the widest layout. */
    static final int MAX_USED_WORDS_PER_ROW = 3;

    /**
     * Greatest stride from one row to the next, that of the widest layout.
     * <p>
     * A three-word row is allocated a fourth word, so that the stride stays a power of two and a
     * row index stays a shift. That word is always zero.
     * </p>
     */
    static final int WORDS_PER_ROW = 4;

    /** Maximum supported size, given by the {@value #MAX_USED_WORDS_PER_ROW} words per row. */
    static final int MAX_SIZE = 64 * MAX_USED_WORDS_PER_ROW;

    private final long[] bits;
    private final int size;

    /** Base-2 logarithm of the stride from one row to the next: 0, 1 or 2. */
    private final int rowShift;

    /** Number of words of each row that hold modules: 1, 2 or 3. */
    private final int usedWordsPerRow;

    /**
     * Creates a new instance with the specified size.
     * <p>
     * Initially, all bits are cleared ({@code false}).
     * </p>
     *
     * @param size the size (number of bits in each dimension)
     * @throws IllegalArgumentException if the size is negative or greater than {@value #MAX_SIZE}
     */
    BitMatrix(int size) {
        if (size < 0 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 0 and " + MAX_SIZE);
        }
        // one word of modules per 64 columns, in a stride rounded up to a power of two
        var used = size <= 64 ? 1 : (size <= 128 ? 2 : 3);
        var shift = size <= 64 ? 0 : (size <= 128 ? 1 : 2);
        this.bits = new long[size << shift];
        this.size = size;
        this.rowShift = shift;
        this.usedWordsPerRow = used;
    }

    private BitMatrix(long[] bits, int size, int rowShift, int usedWordsPerRow) {
        this.bits = bits;
        this.size = size;
        this.rowShift = rowShift;
        this.usedWordsPerRow = usedWordsPerRow;
    }

    /**
     * Returns the size of the matrix (number of bits in each dimension).
     *
     * @return the size
     */
    int size() {
        return size;
    }

    /**
     * Returns the number of 64-bit words each row of this matrix occupies.
     * <p>
     * This is the stride from one row to the next in {@link #raw()}: 1, 2 or 4, being
     * {@link #usedWordsPerRow()} rounded up to a power of two. The two differ only for a three-word
     * row, whose fourth word is padding.
     * </p>
     *
     * @return the number of words per row
     */
    int wordsPerRow() {
        return 1 << rowShift;
    }

    /**
     * Returns the number of 64-bit words of each row of this matrix that hold modules.
     * <p>
     * This is the matrix's row layout: 1, 2 or 3, one word per 64 columns. An algorithm reading the
     * bits of a row scans that many words and dispatches on this to the implementation specialized
     * for it.
     * </p>
     *
     * @return the number of words per row holding modules
     */
    int usedWordsPerRow() {
        return usedWordsPerRow;
    }

    /**
     * Returns the bit at the specified coordinate.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @return {@code true} if the bit is set, {@code false} if the bit is cleared
     * @throws IndexOutOfBoundsException if a coordinate is outside the matrix
     */
    boolean get(int x, int y) {
        checkCoordinates(x, y);

        var bitMask = 1L << (x & 0x3f);
        var index = (y << rowShift) + (x >> 6);
        return (bits[index] & bitMask) != 0;
    }

    /**
     * Sets the bit at the specified coordinate.
     *
     * @param x   the x-coordinate
     * @param y   the y-coordinate
     * @param bit {@code true} to set the bit, {@code false} to clear it
     * @throws IndexOutOfBoundsException if a coordinate is outside the matrix
     */
    void set(int x, int y, boolean bit) {
        checkCoordinates(x, y);

        var bitMask = 1L << (x & 0x3f);
        var index = (y << rowShift) + (x >> 6);
        if (bit) {
            bits[index] |= bitMask;
        } else {
            bits[index] &= ~bitMask;
        }
    }

    private void checkCoordinates(int x, int y) {
        Objects.checkIndex(x, size);
        Objects.checkIndex(y, size);
    }

    /**
     * Sets all bits in the specified rectangular area.
     * <p>
     * An empty rectangle (a width or height of 0 or less) is a no-op.
     * </p>
     *
     * @param x      the x-coordinate of the top-left corner
     * @param y      the y-coordinate of the top-left corner
     * @param width  the width of the rectangle
     * @param height the height of the rectangle
     * @throws IllegalArgumentException if the rectangle does not lie within the matrix
     */
    void fillRect(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (x < 0 || y < 0 || width > size - x || height > size - y) {
            throw new IllegalArgumentException("the rectangle must lie within the matrix");
        }

        var startWord = x >> 6;
        var endX = x + width - 1;
        var endWord = endX >> 6;
        var startBit = x & 0x3f;
        var endBit = endX & 0x3f;

        var startMask = -1L << startBit;
        var endMask = -1L >>> (63 - endBit);

        var wordsPerRow = 1 << rowShift;
        var rowBase = y << rowShift;
        var rowEnd = rowBase + wordsPerRow * height;

        if (startWord == endWord) {
            var mask = startMask & endMask;
            for (var idx = rowBase + startWord; idx < rowEnd; idx += wordsPerRow) {
                bits[idx] |= mask;
            }
        } else {
            for (var row = rowBase; row < rowEnd; row += wordsPerRow) {
                bits[row + startWord] |= startMask;
                for (var w = startWord + 1; w < endWord; w += 1) {
                    bits[row + w] = -1L;
                }
                bits[row + endWord] |= endMask;
            }
        }
    }

    /**
     * Copies a row of this matrix over another one.
     *
     * @param sourceY the y-coordinate of the row to copy
     * @param targetY the y-coordinate of the row to overwrite
     * @throws IndexOutOfBoundsException if a coordinate is outside the matrix
     */
    void copyRow(int sourceY, int targetY) {
        Objects.checkIndex(sourceY, size);
        Objects.checkIndex(targetY, size);

        System.arraycopy(bits, sourceY << rowShift, bits, targetY << rowShift, 1 << rowShift);
    }

    /**
     * Inverts all bits in this matrix in place.
     */
    void invert() {
        var lastBit = size - 1;
        var lastWord = lastBit >> 6;
        var lastMask = -1L >>> (63 - (lastBit & 0x3f));
        var wordsPerRow = 1 << rowShift;

        for (var y = 0; y < size; y += 1) {
            var rowBase = y << rowShift;
            for (var w = 0; w < lastWord; w += 1) {
                bits[rowBase + w] = ~bits[rowBase + w];
            }
            bits[rowBase + lastWord] = ~bits[rowBase + lastWord] & lastMask;
            for (var w = lastWord + 1; w < wordsPerRow; w += 1) {
                bits[rowBase + w] = 0;
            }
        }
    }

    /**
     * Transposes this matrix in place (reflects bits across the main diagonal).
     * <p>
     * The matrix is processed as a grid of 64 &times; 64 bit blocks. Each block is transposed with
     * a sequence of delta swaps, and blocks off the diagonal are exchanged pairwise. A compact
     * matrix is a single block.
     * </p>
     */
    void transpose() {
        if (size <= 1) {
            return;
        }

        var numBlocks = (size + 63) >> 6;
        var blockA = new long[64];
        var blockB = new long[64];

        for (var br = 0; br < numBlocks; br += 1) {
            gatherBlock(blockA, br, br);
            transpose64x64(blockA);
            scatterBlock(blockA, br, br);

            for (var bc = br + 1; bc < numBlocks; bc += 1) {
                gatherBlock(blockA, br, bc);
                gatherBlock(blockB, bc, br);
                transpose64x64(blockA);
                transpose64x64(blockB);
                scatterBlock(blockA, bc, br);
                scatterBlock(blockB, br, bc);
            }
        }
    }

    private void gatherBlock(long[] dest, int br, int bc) {
        var rowStart = br << 6;
        var rows = Math.min(size - rowStart, 64);
        for (var i = 0; i < rows; i += 1) {
            dest[i] = bits[((rowStart + i) << rowShift) + bc];
        }
        for (var i = rows; i < 64; i += 1) {
            dest[i] = 0;
        }
    }

    private void scatterBlock(long[] src, int br, int bc) {
        var rowStart = br << 6;
        var rows = Math.min(size - rowStart, 64);
        for (var i = 0; i < rows; i += 1) {
            bits[((rowStart + i) << rowShift) + bc] = src[i];
        }
    }

    private static void transpose64x64(long[] a) {
        deltaSwap(a, 32, 0x00000000FFFFFFFFL);
        deltaSwap(a, 16, 0x0000FFFF0000FFFFL);
        deltaSwap(a, 8, 0x00FF00FF00FF00FFL);
        deltaSwap(a, 4, 0x0F0F0F0F0F0F0F0FL);
        deltaSwap(a, 2, 0x3333333333333333L);
        deltaSwap(a, 1, 0x5555555555555555L);
    }

    private static void deltaSwap(long[] a, int j, long m) {
        for (var k = 0; k < 64; k = (k + j + 1) & ~j) {
            var t = ((a[k] >>> j) ^ a[k + j]) & m;
            a[k + j] ^= t;
            a[k] ^= t << j;
        }
    }

    /**
     * Bitwise ANDs the specified matrix into this one in place.
     *
     * @param other the matrix to AND with
     * @throws IllegalArgumentException if the matrices have different sizes
     */
    void and(BitMatrix other) {
        checkSameSize(other);

        for (var i = 0; i < bits.length; i += 1) {
            bits[i] &= other.bits[i];
        }
    }

    /**
     * Bitwise XORs the specified matrix into this one in place.
     *
     * @param other the matrix to XOR with
     * @throws IllegalArgumentException if the matrices have different sizes
     */
    void xor(BitMatrix other) {
        checkSameSize(other);

        for (var i = 0; i < bits.length; i += 1) {
            bits[i] ^= other.bits[i];
        }
    }

    private void checkSameSize(BitMatrix other) {
        if (other.size != size) {
            throw new IllegalArgumentException("The matrices must have the same size");
        }
    }

    /**
     * Creates a copy of this bit matrix.
     *
     * @return a new matrix with the same contents
     */
    BitMatrix copy() {
        return new BitMatrix(bits.clone(), size, rowShift, usedWordsPerRow);
    }

    /**
     * Returns the underlying raw data.
     * <p>
     * The bits are stored in an array of 64-bit integers, {@link #wordsPerRow()} integers per row,
     * of which the first {@link #usedWordsPerRow()} hold modules.
     * The array is not copied: modifying it modifies this matrix.
     * </p>
     *
     * @return the raw bits
     */
    long[] raw() {
        return bits;
    }

    /**
     * Returns the address of the specified module, an opaque handle {@link #orBit(short, int)}
     * writes through.
     * <p>
     * Resolving the modules once lets a loop that visits a fixed set of them write the bits
     * without any coordinate arithmetic. Because an address encodes the row layout, it is valid
     * only for matrices of the same size as this one.
     * </p>
     * <p>
     * An address is a {@code short} because it fits in one: it locates a word of {@link #raw()}
     * and a bit within that word, which takes 10 and 6 bits at {@value #MAX_SIZE} &times;
     * {@value #MAX_SIZE}, the largest matrix. It is therefore negative for the lowest rows of the
     * largest matrices, which nothing outside this class has to care about.
     * </p>
     * <p>
     * The coordinate is not checked; the caller guarantees that it lies within the matrix.
     * </p>
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @return the address
     */
    short address(int x, int y) {
        return (short) ((((y << rowShift) + (x >> 6)) << 6) | (x & 0x3f));
    }

    /**
     * ORs the specified bit into the module at the specified address.
     * <p>
     * Neither argument is checked; the caller guarantees that the address comes from
     * {@link #address(int, int)} on a matrix of this size and that the bit is 0 or 1.
     * </p>
     *
     * @param address the module's address
     * @param bit     the bit to OR in, 0 or 1
     */
    void orBit(short address, int bit) {
        // Only the word index needs the address widened; a shift count uses its low six bits.
        bits[(address & 0xffff) >>> 6] |= (long) bit << address;
    }

    /**
     * Returns the number of bits set in this matrix (aka population count).
     *
     * @return the number of set bits
     */
    int popCount() {
        var sum = 0;
        for (var word : bits) {
            sum += Long.bitCount(word);
        }
        return sum;
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Computes the Reed-Solomon error correction codewords of a QR code data block.
 * <p>
 * The error correction codewords are the remainder of dividing the data codewords, as a polynomial
 * over GF(256), by the generator polynomial &prod;(x&nbsp;&minus;&nbsp;&alpha;<sup>i</sup>) for
 * i&nbsp;=&nbsp;0&hellip;capacity&nbsp;&minus;&nbsp;1. A decoder can restore up to
 * <em>capacity</em>&nbsp;/&nbsp;2 erroneous codewords per block from them.
 * </p>
 * <p>
 * The division multiplies the generator polynomial by one field element per data codeword. Every
 * such multiple is precomputed, so the division itself is a shift and an exclusive or, with no
 * field arithmetic left in the loop.
 * </p>
 * <p>
 * Instances are immutable and safe to share between threads. They are obtained from
 * {@link #forCapacity(int)}, which caches them so that all callers share a single instance per
 * capacity.
 * </p>
 */
final class ReedSolomon {

    /** The largest number of error correction codewords a block can carry. */
    private static final int MAX_CAPACITY = 255;

    /**
     * Reads and writes eight bytes of a byte array at a time. The bytes are only ever combined
     * with an exclusive or, which acts on each byte separately, so the byte order does not affect
     * the result and the native one is chosen to spare the swap.
     */
    private static final VarHandle EIGHT_BYTES =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());

    /** The cached instances, indexed by capacity. */
    private static final LazyCache<ReedSolomon> INSTANCES =
            new LazyCache<>(MAX_CAPACITY + 1, ReedSolomon::new);

    /** The number of error correction codewords this instance produces. */
    private final int capacity;

    /** The length of a row of {@link #products}: the capacity rounded up to a multiple of eight. */
    private final int rowLength;

    /**
     * The generator polynomial multiplied by every element of the field: row <em>f</em> holds
     * <em>f</em> times the coefficients, from the highest to the lowest power and without the
     * leading coefficient of 1, padded with zeros to {@link #rowLength}. Row 0 is all zeros, so
     * the factor 0 needs no case of its own.
     */
    private final byte[] products;

    private ReedSolomon(int capacity) {
        this.capacity = capacity;
        this.rowLength = (capacity + 7) / 8 * 8;
        this.products = computeProducts(capacity, rowLength);
    }

    /**
     * Returns the instance producing the specified number of error correction codewords.
     *
     * @param capacity the number of error correction codewords (1&ndash;255)
     * @return the instance
     * @throws IllegalArgumentException if the capacity is out of range
     */
    static ReedSolomon forCapacity(int capacity) {
        if (capacity < 1 || capacity > MAX_CAPACITY)
            throw new IllegalArgumentException(
                    "Error correction capacity must be between 1 and " + MAX_CAPACITY);

        return INSTANCES.get(capacity);
    }

    /**
     * Computes the error correction codewords for the specified data codewords and writes them
     * into the specified array.
     * <p>
     * The codewords are written at a regular distance from each other rather than consecutively,
     * so that the caller can interleave the blocks without a buffer per block.
     * </p>
     *
     * @param data   the data codewords of a single block
     * @param target the array the error correction codewords are written to
     * @param offset the index of the first error correction codeword
     * @param stride the distance between two consecutive error correction codewords
     */
    void computeErrorCorrection(ByteSlice data, byte[] target, int offset, int stride) {
        // The remainder is padded to a whole number of eight-byte words, plus the word the shift
        // reads past its end. That padding is zero and stays zero: the rows of the product table
        // are padded with zeros as well, so the shift can only ever move a zero into it.
        var remainder = new byte[rowLength + 8];

        // Long division, one data codeword per step: the remainder shifts up by one coefficient,
        // and a multiple of the generator polynomial is added to cancel the coefficient shifted
        // out. Addition in GF(256) is XOR, so nothing has to be subtracted and eight coefficients
        // are handled in a single machine word.
        var array = data.array();
        var end = data.offset() + data.length();
        for (var index = data.offset(); index < end; index += 1) {
            var row = ((array[index] ^ remainder[0]) & 0xff) * rowLength;

            for (var i = 0; i < rowLength; i += 8)
                EIGHT_BYTES.set(remainder, i, (long) EIGHT_BYTES.get(remainder, i + 1)
                        ^ (long) EIGHT_BYTES.get(products, row + i));
        }

        for (var i = 0; i < capacity; i += 1)
            target[offset + i * stride] = remainder[i];
    }

    /**
     * Computes the generator polynomial for the specified capacity, multiplied by every element of
     * the field.
     *
     * @param capacity  the number of error correction codewords
     * @param rowLength the length of a row of the result
     * @return the products, as 256 rows of the specified length
     */
    private static byte[] computeProducts(int capacity, int rowLength) {
        var generatorPolynomial = computeGeneratorPolynomial(capacity);
        var products = new byte[256 * rowLength];

        for (var factor = 1; factor < 256; factor += 1)
            for (var i = 0; i < capacity; i += 1)
                products[factor * rowLength + i] = (byte) multiply(factor, generatorPolynomial[i]);

        return products;
    }

    /**
     * Computes the generator polynomial for the specified number of error correction codewords.
     *
     * @param capacity the number of error correction codewords
     * @return the coefficients, from the highest to the lowest power, without the leading 1
     */
    private static int[] computeGeneratorPolynomial(int capacity) {
        // Start with the polynomial 1, in ascending powers, and multiply it by (x - a^i) in turn.
        var polynomial = new int[capacity + 1];
        polynomial[0] = 1;

        for (var i = 0; i < capacity; i += 1) {
            var alpha = power(2, i);

            for (var j = capacity; j > 0; j -= 1)
                polynomial[j] = polynomial[j - 1] ^ multiply(polynomial[j], alpha);
            polynomial[0] = multiply(polynomial[0], alpha);
        }

        // The leading coefficient is 1 and is dropped; the rest is reversed, as the division
        // consumes the coefficients from the highest power down.
        var coefficients = new int[capacity];
        for (var i = 0; i < capacity; i += 1)
            coefficients[i] = polynomial[capacity - 1 - i];

        return coefficients;
    }

    // region Galois field arithmetic

    /**
     * Multiplies two elements of GF(256).
     *
     * @param a the first factor (0&ndash;255)
     * @param b the second factor (0&ndash;255)
     * @return the product (0&ndash;255)
     */
    private static int multiply(int a, int b) {
        if (a == 0 || b == 0)
            return 0;

        return EXP[(LOG[a] + LOG[b]) % 255];
    }

    /**
     * Raises an element of GF(256) to the specified power.
     *
     * @param base     the base (0&ndash;255)
     * @param exponent the exponent
     * @return the power (0&ndash;255)
     */
    private static int power(int base, int exponent) {
        if (exponent == 0)
            return 1;
        if (base == 0)
            return 0;

        return EXP[LOG[base] * exponent % 255];
    }

    /**
     * The discrete logarithm in GF(256) to the base &alpha;&nbsp;=&nbsp;2:
     * {@code LOG[a^k] == k}. The entry for 0 is unused.
     */
    private static final int[] LOG = {
            0,   0,   1,  25,   2,  50,  26, 198,   3, 223,  51, 238,  27, 104, 199,  75,
            4, 100, 224,  14,  52, 141, 239, 129,  28, 193, 105, 248, 200,   8,  76, 113,
            5, 138, 101,  47, 225,  36,  15,  33,  53, 147, 142, 218, 240,  18, 130,  69,
           29, 181, 194, 125, 106,  39, 249, 185, 201, 154,   9, 120,  77, 228, 114, 166,
            6, 191, 139,  98, 102, 221,  48, 253, 226, 152,  37, 179,  16, 145,  34, 136,
           54, 208, 148, 206, 143, 150, 219, 189, 241, 210,  19,  92, 131,  56,  70,  64,
           30,  66, 182, 163, 195,  72, 126, 110, 107,  58,  40,  84, 250, 133, 186,  61,
          202,  94, 155, 159,  10,  21, 121,  43,  78, 212, 229, 172, 115, 243, 167,  87,
            7, 112, 192, 247, 140, 128,  99,  13, 103,  74, 222, 237,  49, 197, 254,  24,
          227, 165, 153, 119,  38, 184, 180, 124,  17,  68, 146, 217,  35,  32, 137,  46,
           55,  63, 209,  91, 149, 188, 207, 205, 144, 135, 151, 178, 220, 252, 190,  97,
          242,  86, 211, 171,  20,  42,  93, 158, 132,  60,  57,  83,  71, 109,  65, 162,
           31,  45,  67, 216, 183, 123, 164, 118, 196,  23,  73, 236, 127,  12, 111, 246,
          108, 161,  59,  82,  41, 157,  85, 170, 251,  96, 134, 177, 187, 204,  62,  90,
          203,  89,  95, 176, 156, 169, 160,  81,  11, 245,  22, 235, 122, 117,  44, 215,
           79, 174, 213, 233, 230, 231, 173, 232, 116, 214, 244, 234, 168,  80,  88, 175
    };

    /**
     * The powers of &alpha;&nbsp;=&nbsp;2 in GF(256): {@code EXP[k] == a^k}. The field has 255
     * non-zero elements, so the table wraps around at its last entry.
     */
    private static final int[] EXP = {
            1,   2,   4,   8,  16,  32,  64, 128,  29,  58, 116, 232, 205, 135,  19,  38,
           76, 152,  45,  90, 180, 117, 234, 201, 143,   3,   6,  12,  24,  48,  96, 192,
          157,  39,  78, 156,  37,  74, 148,  53, 106, 212, 181, 119, 238, 193, 159,  35,
           70, 140,   5,  10,  20,  40,  80, 160,  93, 186, 105, 210, 185, 111, 222, 161,
           95, 190,  97, 194, 153,  47,  94, 188, 101, 202, 137,  15,  30,  60, 120, 240,
          253, 231, 211, 187, 107, 214, 177, 127, 254, 225, 223, 163,  91, 182, 113, 226,
          217, 175,  67, 134,  17,  34,  68, 136,  13,  26,  52, 104, 208, 189, 103, 206,
          129,  31,  62, 124, 248, 237, 199, 147,  59, 118, 236, 197, 151,  51, 102, 204,
          133,  23,  46,  92, 184, 109, 218, 169,  79, 158,  33,  66, 132,  21,  42,  84,
          168,  77, 154,  41,  82, 164,  85, 170,  73, 146,  57, 114, 228, 213, 183, 115,
          230, 209, 191,  99, 198, 145,  63, 126, 252, 229, 215, 179, 123, 246, 241, 255,
          227, 219, 171,  75, 150,  49,  98, 196, 149,  55, 110, 220, 165,  87, 174,  65,
          130,  25,  50, 100, 200, 141,   7,  14,  28,  56, 112, 224, 221, 167,  83, 166,
           81, 162,  89, 178, 121, 242, 249, 239, 195, 155,  43,  86, 172,  69, 138,   9,
           18,  36,  72, 144,  61, 122, 244, 245, 247, 243, 251, 235, 203, 139,  11,  22,
           44,  88, 176, 125, 250, 233, 207, 131,  27,  54, 108, 216, 173,  71, 142,   1
    };

    // endregion
}

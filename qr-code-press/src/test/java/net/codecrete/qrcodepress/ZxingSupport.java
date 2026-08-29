/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Version;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * The parts of ZXing this library cross-checks itself against.
 * <p>
 * ZXing is an independent QR code implementation, so agreeing with it on the error correction
 * codewords and on the block layout means both agree with ISO/IEC 18004 rather than merely with
 * each other.
 * </p>
 */
final class ZxingSupport {

    /** ZXing's error correction levels, in this library's index order (0&ndash;3 = L/M/Q/H). */
    private static final ErrorCorrectionLevel[] ECC_LEVELS = {
            ErrorCorrectionLevel.L, ErrorCorrectionLevel.M, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.H
    };

    private ZxingSupport() {
        // non-instantiable
    }

    /**
     * Computes the error correction codewords of a data block with ZXing's Reed-Solomon encoder.
     *
     * @param data      the data codewords of the block
     * @param eccLength the number of error correction codewords
     * @return the error correction codewords
     */
    static byte[] errorCorrection(byte[] data, int eccLength) {
        var buffer = new int[data.length + eccLength];
        for (var i = 0; i < data.length; i += 1)
            buffer[i] = data[i] & 0xff;

        new ReedSolomonEncoder(GenericGF.QR_CODE_FIELD_256).encode(buffer, eccLength);

        var ecc = new byte[eccLength];
        for (var i = 0; i < eccLength; i += 1)
            ecc[i] = (byte) buffer[data.length + i];
        return ecc;
    }

    /**
     * Returns the number of data codewords of each block, in block order.
     *
     * @param version the QR code version (1&ndash;40)
     * @param ecc     the error correction level (0&ndash;3)
     * @return the block lengths
     */
    static int[] blockDataLengths(int version, int ecc) {
        return Arrays.stream(blocks(version, ecc).getECBlocks())
                .flatMapToInt(block -> IntStream.generate(block::getDataCodewords).limit(block.getCount()))
                .toArray();
    }

    /**
     * Returns the number of error correction codewords per block.
     *
     * @param version the QR code version (1&ndash;40)
     * @param ecc     the error correction level (0&ndash;3)
     * @return the number of codewords
     */
    static int blockEccLength(int version, int ecc) {
        return blocks(version, ecc).getECCodewordsPerBlock();
    }

    /**
     * Returns the total number of codewords of a QR code of the specified version.
     *
     * @param version the QR code version (1&ndash;40)
     * @return the number of codewords
     */
    static int totalCodewords(int version) {
        return Version.getVersionForNumber(version).getTotalCodewords();
    }

    /**
     * Converts a QR code into the matrix ZXing's decoder reads.
     *
     * @param qrCode the QR code
     * @return the matrix
     */
    static com.google.zxing.common.BitMatrix toBitMatrix(QrCode qrCode) {
        var bits = new com.google.zxing.common.BitMatrix(qrCode.getSize());
        for (var y = 0; y < qrCode.getSize(); y += 1) {
            for (var x = 0; x < qrCode.getSize(); x += 1) {
                if (qrCode.getModule(x, y))
                    bits.set(x, y);
            }
        }
        return bits;
    }

    /**
     * Decodes a matrix of modules with ZXing.
     * <p>
     * The decoder is driven directly, without the detector in front of it: this library produces
     * the modules, so there is nothing to locate in an image, and what is under test is the
     * encoding rather than ZXing's ability to find a symbol.
     * </p>
     *
     * @param bits the matrix
     * @return the decoding result
     * @throws ChecksumException if the error correction data does not add up
     * @throws FormatException   if the symbol is malformed
     */
    static DecoderResult decode(com.google.zxing.common.BitMatrix bits)
            throws ChecksumException, FormatException {
        return new Decoder().decode(bits);
    }

    /**
     * Decodes a matrix of modules with ZXing, reading the payload as the specified character set.
     * <p>
     * The character set is a hint, used where the QR code announces none of its own. An ECI segment
     * in the symbol overrides it, so a text that only decodes correctly with the hint is one that
     * carries no ECI segment.
     * </p>
     *
     * @param bits    the matrix
     * @param charset the character set to assume
     * @return the decoding result
     * @throws ChecksumException if the error correction data does not add up
     * @throws FormatException   if the symbol is malformed
     */
    static DecoderResult decode(com.google.zxing.common.BitMatrix bits, Charset charset)
            throws ChecksumException, FormatException {
        return new Decoder().decode(bits, Map.of(DecodeHintType.CHARACTER_SET, charset.name()));
    }

    private static Version.ECBlocks blocks(int version, int ecc) {
        return Version.getVersionForNumber(version).getECBlocksForLevel(ECC_LEVELS[ecc]);
    }
}

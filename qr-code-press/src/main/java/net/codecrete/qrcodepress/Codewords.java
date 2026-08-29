/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.List;

/**
 * Turns data segments into the codeword sequence the QR code matrix is filled with.
 * <p>
 * This happens in two stages: the segments become the data codewords, padded to the capacity of
 * the QR code, and the data codewords are then split into blocks, each of which gets its
 * Reed-Solomon error correction codewords. Data and error correction codewords are interleaved, so
 * that a blotch on the printed symbol damages a few codewords of every block instead of wiping out
 * a single block entirely.
 * </p>
 */
final class Codewords {

    private Codewords() {
        // non-instantiable
    }

    /**
     * Builds the data codewords for the specified segments.
     * <p>
     * The result spans the entire data capacity of the QR code: the encoded segments are followed
     * by the terminator and by the padding the specification prescribes.
     * </p>
     *
     * @param segments the segments to encode
     * @param version  the QR code version (1&ndash;40)
     * @param ecc      the error correction level (0&ndash;3)
     * @return the data codewords
     */
    static byte[] buildData(List<DataSegment> segments, int version, int ecc) {
        var capacity = QrCodeParameters.dataCodewordCapacity(version, ecc);
        var bitStream = DataSegment.createBitStream(segments, version, capacity);
        var bitLength = bitStream.length();

        // The bit stream was allocated at the full capacity, so the padding is written straight
        // into it. Everything past the bit stream is still zero, and the final partial codeword,
        // if any, is already padded with zero bits.
        var codewords = bitStream.takeCodewords();

        // The pad codewords alternate between 0xEC and 0x11, hence the two strides of two.
        for (var index = (bitLength + 7) / 8; index < capacity; index += 2)
            codewords[index] = (byte) 0b1110_1100;
        for (var index = (bitLength + 15) / 8; index < capacity; index += 2)
            codewords[index] = (byte) 0b0001_0001;

        return codewords;
    }

    /**
     * Adds the error correction codewords to the specified data codewords.
     * <p>
     * The data codewords are split into blocks, each block gets its own error correction
     * codewords, and the result is the interleaved sequence of all blocks: first the data
     * codewords, then the error correction codewords.
     * </p>
     *
     * @param dataCodewords the data codewords, spanning the entire data capacity
     * @param version       the QR code version (1&ndash;40)
     * @param ecc           the error correction level (0&ndash;3)
     * @return the interleaved data and error correction codewords
     */
    static byte[] addErrorCorrection(byte[] dataCodewords, int version, int ecc) {
        var dataLength = dataCodewords.length;
        var totalLength = QrCodeParameters.codewordCapacity(version);
        var blockCount = QrCodeParameters.blockCount(version, ecc);
        var eccBlockLength = (totalLength - dataLength) / blockCount;

        // The blocks differ in length by at most one codeword, and the shorter ones come first.
        var shortBlockLength = dataLength / blockCount;
        var shortBlockCount = blockCount - dataLength % blockCount;

        var result = new byte[totalLength];
        var reedSolomon = ReedSolomon.forCapacity(eccBlockLength);
        var data = ByteSlice.of(dataCodewords);
        var offset = 0;

        for (var block = 0; block < blockCount; block += 1) {
            var blockLength = block < shortBlockCount ? shortBlockLength : shortBlockLength + 1;

            // The error correction codewords are interleaved as they are computed, so no block
            // needs a buffer of its own.
            reedSolomon.computeErrorCorrection(
                    data.slice(offset, blockLength), result, dataLength + block, blockCount);

            // Interleave: the i-th codeword of every block, then the (i+1)-th, and so on.
            for (var i = 0; i < shortBlockLength; i += 1)
                result[i * blockCount + block] = dataCodewords[offset + i];

            // The last codeword of the long blocks has no counterpart in the short ones. Those
            // codewords follow the interleaved ones, in block order.
            if (block >= shortBlockCount)
                result[blockCount * shortBlockLength + block - shortBlockCount] =
                        dataCodewords[offset + blockLength - 1];

            offset += blockLength;
        }

        return result;
    }
}

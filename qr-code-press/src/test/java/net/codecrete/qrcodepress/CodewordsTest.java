/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static net.codecrete.qrcodepress.QrCodeParameters.MAX_VERSION;
import static net.codecrete.qrcodepress.QrCodeParameters.MIN_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class CodewordsTest {

    private static final byte PAD_EC = (byte) 0b1110_1100;
    private static final byte PAD_11 = (byte) 0b0001_0001;

    // region Verified data

    /**
     * One case per row of the codeword fixture.
     */
    static Stream<Arguments> verifiedCases() {
        Map<Integer, Integer> textIndexByCase = VerifiedData.qrCodeCases().stream()
                .collect(toMap(VerifiedData.QrCodeCase::index, VerifiedData.QrCodeCase::textIndex));

        return VerifiedData.readTsv("codewords.tsv").stream().map(row -> {
            var caseIndex = Integer.parseInt(row[0]);
            return arguments(caseIndex, textIndexByCase.get(caseIndex),
                    Integer.parseInt(row[1]), Integer.parseInt(row[2]), row[3], row[4]);
        });
    }

    @ParameterizedTest(name = "case {0}, version {2}, ECC level {3}")
    @MethodSource("verifiedCases")
    @DisplayName("builds data codewords like verified data")
    void buildDataMatches(int caseIndex, int textIndex, int version, int ecc,
                          String expectedData, String ignoredExpectedFinal) {
        var codewords = Codewords.buildData(VerifiedData.segments(textIndex), version, ecc);

        assertThat(VerifiedData.hex(codewords)).isEqualTo(expectedData);
    }

    @ParameterizedTest(name = "case {0}, version {2}, ECC level {3}")
    @MethodSource("verifiedCases")
    @DisplayName("interleaves the codewords like verified data")
    void addErrorCorrectionMatches(int caseIndex, int ignoredTextIndex, int version, int ecc,
                                   String expectedData, String expectedFinal) {
        var codewords = Codewords.addErrorCorrection(VerifiedData.bytes(expectedData), version, ecc);

        assertThat(VerifiedData.hex(codewords)).isEqualTo(expectedFinal);
    }

    // endregion

    // region Data codewords

    @ParameterizedTest(name = "{0}({1}) at version {2}, ECC level {3}")
    @CsvSource(delimiter = '|', value = {
            // an empty payload: everything past the terminator is padding
            "BINARY       |   0 |  1 | 0",
            "BINARY       |   0 | 40 | 3",
            // a payload ending mid-codeword: the partial codeword keeps its zero bits
            "NUMERIC      |   1 |  1 | 0",
            "NUMERIC      |   7 |  2 | 1",
            "ALPHANUMERIC |   3 |  1 | 0",
            // a payload ending on a codeword boundary
            "BINARY       |   3 |  2 | 1",
            "BINARY       | 100 | 10 | 2",
            "BINARY       |   1 | 40 | 3"
    })
    @DisplayName("pads the data codewords to the capacity, alternating between the two pad codewords")
    void padsToCapacity(DataSegmentMode mode, int payloadLength, int version, int ecc) {
        var segments = segmentsOf(mode, payloadLength);
        var capacity = QrCodeParameters.dataCodewordCapacity(version, ecc);
        var encoded = DataSegment.createBitStream(segments, version, capacity).codewords();

        var codewords = Codewords.buildData(segments, version, ecc);

        assertThat(codewords).hasSize(capacity);
        assertThat(Arrays.copyOf(codewords, encoded.length))
                .as("the encoded segments and the terminator are kept verbatim").isEqualTo(encoded);
        assertThat(Arrays.copyOfRange(codewords, encoded.length, capacity))
                .as("the padding").isEqualTo(padding(capacity - encoded.length));
    }

    @Test
    @DisplayName("adds no padding to a payload filling the capacity exactly")
    void addsNoPaddingWhenFull() {
        // 17 zero bytes plus the 4-bit mode indicator, the 8-bit count indicator and the 4-bit
        // terminator make exactly the 152 bits of a version 1 QR code with ECC level L.
        var segments = segmentsOf(DataSegmentMode.BINARY, 17);

        var codewords = Codewords.buildData(segments, 1, 0);

        assertThat(codewords).hasSize(19).doesNotContain(PAD_EC, PAD_11);
    }

    @ParameterizedTest(name = "version {0}, ECC level {1}")
    @MethodSource("versionsAndEccLevels")
    @DisplayName("fills the data capacity of every version and error correction level")
    void fillsTheDataCapacity(int version, int ecc) {
        var codewords = Codewords.buildData(List.of(), version, ecc);

        assertThat(codewords).hasSize(QrCodeParameters.dataCodewordCapacity(version, ecc));
    }

    // endregion

    // region Error correction and interleaving

    @ParameterizedTest(name = "version {0}, ECC level {1}")
    @MethodSource("versionsAndEccLevels")
    @DisplayName("splits the codewords into the blocks ZXing describes and interleaves them")
    void interleavesTheBlocksZxingDescribes(int version, int ecc) {
        var data = pseudoRandomBytes(QrCodeParameters.dataCodewordCapacity(version, ecc), version * 4L + ecc);
        var blockLengths = ZxingSupport.blockDataLengths(version, ecc);
        var eccLength = ZxingSupport.blockEccLength(version, ecc);

        var codewords = Codewords.addErrorCorrection(data, version, ecc);

        assertThat(codewords).hasSize(ZxingSupport.totalCodewords(version));

        var blocks = deinterleave(codewords, blockLengths, eccLength);
        var offset = 0;
        for (var block = 0; block < blockLengths.length; block += 1) {
            var blockData = Arrays.copyOfRange(data, offset, offset + blockLengths[block]);
            assertThat(blocks[block].data()).as("data of block %d", block).isEqualTo(blockData);
            assertThat(blocks[block].ecc()).as("error correction of block %d", block)
                    .isEqualTo(ZxingSupport.errorCorrection(blockData, eccLength));
            offset += blockLengths[block];
        }
    }

    /** The data and error correction codewords of one block. */
    private record Block(byte[] data, byte[] ecc) {
    }

    /**
     * Undoes the interleaving: the data codewords come first, one from every block that is long
     * enough, then the error correction codewords in the same round-robin order.
     */
    private static Block[] deinterleave(byte[] codewords, int[] blockLengths, int eccLength) {
        var blocks = new Block[blockLengths.length];
        for (var block = 0; block < blocks.length; block += 1)
            blocks[block] = new Block(new byte[blockLengths[block]], new byte[eccLength]);

        var index = 0;
        for (var i = 0; i < Arrays.stream(blockLengths).max().orElseThrow(); i += 1)
            for (var block = 0; block < blocks.length; block += 1)
                if (i < blockLengths[block])
                    blocks[block].data()[i] = codewords[index++];

        for (var i = 0; i < eccLength; i += 1)
            for (Block value : blocks)
                value.ecc()[i] = codewords[index++];

        assertThat(index).as("codewords consumed").isEqualTo(codewords.length);
        return blocks;
    }

    // endregion

    static Stream<Arguments> versionsAndEccLevels() {
        return IntStream.rangeClosed(MIN_VERSION, MAX_VERSION).boxed()
                .flatMap(version -> IntStream.range(0, 4).mapToObj(ecc -> arguments(version, ecc)));
    }

    /** Builds a single segment of the specified mode, or no segment at all if it would be empty. */
    private static List<DataSegment> segmentsOf(DataSegmentMode mode, int payloadLength) {
        if (payloadLength == 0)
            return List.of();

        var payload = new byte[payloadLength];
        if (mode != DataSegmentMode.BINARY)
            Arrays.fill(payload, (byte) '5');
        return List.of(DataSegment.of(mode, payload));
    }

    /** Returns the expected padding of the specified length: 0xEC and 0x11 in alternation. */
    private static byte[] padding(int length) {
        var padding = new byte[length];
        for (var i = 0; i < length; i += 1)
            padding[i] = i % 2 == 0 ? PAD_EC : PAD_11;
        return padding;
    }

    /** Returns reproducible pseudo-random bytes, so a failure can be reproduced from its name. */
    private static byte[] pseudoRandomBytes(int length, long seed) {
        var data = new byte[length];
        new Random(seed).nextBytes(data);
        return data;
    }
}

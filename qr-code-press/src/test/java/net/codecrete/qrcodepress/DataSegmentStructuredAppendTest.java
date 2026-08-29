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
import org.junit.jupiter.params.provider.CsvSource;

import static net.codecrete.qrcodepress.SegmentTestSupport.codewords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DataSegmentStructuredAppendTest {

    @ParameterizedTest
    @CsvSource({ "1, 13, 0xea, 0x0c, 0xea", "16, 16, 0x1c, 0xff, 0x1c" })
    @DisplayName("encodes the position, the total and the parity in 16 bits")
    void encodeStructuredAppend(int position, int total, int parity, int expected1, int expected2) {
        var segment = new DataSegmentStructuredAppend(position, total, parity);

        assertThat(segment.encodedLength()).isEqualTo(16);
        assertThat(codewords(segment)).containsExactly(expected1, expected2);
    }

    @ParameterizedTest
    @CsvSource({ "0, 7", "17, 7", "1, 0", "1, 17", "5, 2" })
    @DisplayName("requires 1 <= position <= total <= 16")
    void rejectsInvalidPositions(int position, int total) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DataSegmentStructuredAppend(position, total, 0x3f));
    }

    @ParameterizedTest
    @CsvSource({ "-1", "256" })
    @DisplayName("requires a parity byte")
    void rejectsInvalidParity(int parity) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DataSegmentStructuredAppend(1, 4, parity))
                .withMessageContaining("parity");
    }

    @Test
    @DisplayName("the header is four bits, without a character count indicator")
    void headerLength() {
        var segment = new DataSegmentStructuredAppend(1, 4, 0);

        assertThat(segment.totalLength(1)).isEqualTo(4 + 16);
        assertThat(segment.totalLength(40)).isEqualTo(4 + 16);
    }
}

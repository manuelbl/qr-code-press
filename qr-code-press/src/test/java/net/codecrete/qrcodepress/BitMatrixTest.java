/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static net.codecrete.qrcodepress.TestMatrices.assertMatricesEqual;
import static net.codecrete.qrcodepress.TestMatrices.naiveTranspose;
import static net.codecrete.qrcodepress.TestMatrices.pattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIndexOutOfBoundsException;

class BitMatrixTest {

    @ParameterizedTest
    @ValueSource(ints = { -1, BitMatrix.MAX_SIZE + 1 })
    @DisplayName("rejects a size outside the supported range")
    void rejectsInvalidSize(int size) {
        assertThatIllegalArgumentException().isThrownBy(() -> new BitMatrix(size));
    }

    @ParameterizedTest(name = "({0}, {1})")
    @CsvSource({ "-1, 0", "0, -1", "21, 0", "0, 21", "64, 3", "3, 64" })
    @DisplayName("get and set reject coordinates outside the matrix")
    void rejectsOutOfRangeCoordinates(int x, int y) {
        var matrix = new BitMatrix(21);

        assertThatIndexOutOfBoundsException().isThrownBy(() -> matrix.get(x, y));
        assertThatIndexOutOfBoundsException().isThrownBy(() -> matrix.set(x, y, true));
    }

    @Test
    @DisplayName("a new matrix has the requested size and no bits set")
    void createsClearedMatrix() {
        var matrix = new BitMatrix(191);

        assertThat(matrix.size()).isEqualTo(191);
        assertThat(matrix.popCount()).isZero();
        assertThat(matrix.get(190, 190)).isFalse();

        matrix.set(190, 190, true);
        assertThat(matrix.get(190, 190)).isTrue();
    }

    @Test
    @DisplayName("get returns what set stored, across word boundaries")
    void getReturnsWhatSetStored() {
        final int size = 131;
        var matrix = new BitMatrix(size);

        for (var y = 0; y < size; y += 1) {
            for (var x = y % 2; x < size; x += 2) {
                matrix.set(x, y, true);
            }
        }

        for (var y = 0; y < size; y += 1) {
            for (var x = 0; x < size; x += 1) {
                assertThat(matrix.get(x, y)).isEqualTo((x + y) % 2 == 0);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 21, 64, 65, 131, BitMatrix.MAX_SIZE })
    @DisplayName("orBit reaches the same bit through an address as the accessors do")
    void addressMatchesTheAccessors(int size) {
        var matrix = new BitMatrix(size);

        for (var y = 0; y < size; y += 1) {
            for (var x = 0; x < size; x += 1) {
                matrix.orBit(matrix.address(x, y), 1);

                assertThat(matrix.get(x, y)).as("bit at (%d, %d)", x, y).isTrue();
                assertThat(matrix.popCount()).as("bits set after (%d, %d)", x, y)
                        .isEqualTo(y * size + x + 1);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 21, 65, BitMatrix.MAX_SIZE })
    @DisplayName("orBit leaves a module unchanged when the bit is 0")
    void orBitOfZeroChangesNothing(int size) {
        var matrix = new BitMatrix(size);
        matrix.set(3, size - 1, true);

        matrix.orBit(matrix.address(3, size - 1), 0);
        matrix.orBit(matrix.address(size - 1, size - 1), 0);

        assertThat(matrix.get(3, size - 1)).as("the set module").isTrue();
        assertThat(matrix.popCount()).as("bits set").isEqualTo(1);
    }

    @Test
    @DisplayName("clearing a bit leaves its neighbours alone")
    void clearsSingleBit() {
        var matrix = new BitMatrix(65);
        matrix.fillRect(0, 0, 65, 65);

        matrix.set(64, 3, false);

        assertThat(matrix.get(64, 3)).isFalse();
        assertThat(matrix.get(63, 3)).isTrue();
        assertThat(matrix.get(64, 2)).isTrue();
        assertThat(matrix.popCount()).isEqualTo(65 * 65 - 1);
    }

    @Nested
    class FillRect {

        @ParameterizedTest(name = "size {0}: x={1}, y={2}, {3}x{4}")
        @CsvSource({
                "192, 0, 0, 1, 1",
                "192, 5, 7, 1, 1",
                "192, 10, 10, 30, 20",
                "192, 0, 0, 64, 1",
                "192, 0, 0, 65, 1",
                "192, 63, 0, 2, 1",
                "192, 63, 0, 1, 1",
                "192, 64, 0, 64, 1",
                "192, 10, 0, 180, 1",
                "192, 0, 0, 192, 1",
                "192, 0, 0, 192, 192",
                "192, 50, 50, 142, 142",
                "192, 127, 0, 2, 192",
                "192, 191, 191, 1, 1",
                "192, 0, 0, 1, 192",
                // a compact matrix, whose rows are a single word
                "64, 0, 0, 1, 1",
                "64, 5, 7, 10, 3",
                "64, 63, 63, 1, 1",
                "64, 0, 0, 64, 64",
                "64, 0, 60, 64, 4",
                "21, 0, 0, 21, 21",
                "21, 20, 0, 1, 21"
        })
        @DisplayName("fills exactly the requested area")
        void fillsExactArea(int size, int x, int y, int width, int height) {
            var matrix = new BitMatrix(size);

            matrix.fillRect(x, y, width, height);

            for (var yy = 0; yy < size; yy += 1) {
                for (var xx = 0; xx < size; xx += 1) {
                    var expected = xx >= x && xx < x + width && yy >= y && yy < y + height;
                    assertThat(matrix.get(xx, yy))
                            .as("bit at (%d, %d)", xx, yy)
                            .isEqualTo(expected);
                }
            }
        }

        @Test
        @DisplayName("preserves bits outside the rectangle")
        void preservesExistingBits() {
            var matrix = new BitMatrix(192);
            matrix.set(5, 5, true);
            matrix.set(150, 150, true);
            matrix.set(191, 191, true);

            matrix.fillRect(20, 20, 80, 80);

            assertThat(matrix.get(5, 5)).isTrue();
            assertThat(matrix.get(150, 150)).isTrue();
            assertThat(matrix.get(191, 191)).isTrue();
        }

        @Test
        @DisplayName("ORs into bits already set inside the rectangle")
        void orsIntoExistingBits() {
            var matrix = new BitMatrix(100);
            matrix.set(50, 50, true);

            matrix.fillRect(40, 40, 30, 30);

            assertThat(matrix.get(50, 50)).isTrue();
            assertThat(matrix.popCount()).isEqualTo(30 * 30);
        }

        @ParameterizedTest(name = "{2}x{3}")
        @CsvSource({ "0, 0, 0, 10", "0, 0, 10, 0", "0, 0, -1, 10", "0, 0, 10, -1" })
        @DisplayName("is a no-op for an empty or negative rectangle")
        void noOpForEmptyRect(int x, int y, int width, int height) {
            var matrix = new BitMatrix(64);

            matrix.fillRect(x, y, width, height);

            assertThat(matrix.popCount()).isZero();
        }

        @ParameterizedTest(name = "x={0}, y={1}, {2}x{3}")
        @CsvSource({
                "-1, 0, 10, 10",
                "0, -1, 10, 10",
                "0, 0, 65, 1",
                "0, 0, 1, 65",
                "60, 0, 5, 1",
                "0, 60, 1, 5",
                "64, 0, 1, 1"
        })
        @DisplayName("rejects a rectangle reaching outside the matrix")
        void rejectsRectOutsideMatrix(int x, int y, int width, int height) {
            var matrix = new BitMatrix(64);

            assertThatIllegalArgumentException().isThrownBy(() -> matrix.fillRect(x, y, width, height));
        }

        @Test
        @DisplayName("combines multiple rectangles")
        void combinesMultipleRects() {
            var matrix = new BitMatrix(192);

            matrix.fillRect(0, 0, 100, 100);
            matrix.fillRect(150, 150, 42, 42);

            assertThat(matrix.popCount()).isEqualTo(100 * 100 + 42 * 42);
            assertThat(matrix.get(0, 0)).isTrue();
            assertThat(matrix.get(99, 99)).isTrue();
            assertThat(matrix.get(100, 100)).isFalse();
            assertThat(matrix.get(149, 149)).isFalse();
            assertThat(matrix.get(150, 150)).isTrue();
            assertThat(matrix.get(191, 191)).isTrue();
        }
    }

    @Nested
    class CopyRow {

        @ParameterizedTest
        @ValueSource(ints = { 1, 21, 63, 64, 65, 177, 192 })
        @DisplayName("overwrites the target row with the source row, in either layout")
        void overwritesTheTargetRow(int size) {
            var matrix = pattern(size, 0xC0FFEE);
            var sourceY = 0;
            var targetY = size - 1;

            matrix.copyRow(sourceY, targetY);

            var expected = pattern(size, 0xC0FFEE);
            for (var y = 0; y < size; y += 1) {
                for (var x = 0; x < size; x += 1) {
                    assertThat(matrix.get(x, y)).as("(%d, %d)", x, y)
                            .isEqualTo(expected.get(x, y == targetY ? sourceY : y));
                }
            }
        }

        @Test
        @DisplayName("copying a row onto itself changes nothing")
        void ontoItselfChangesNothing() {
            var matrix = pattern(65, 0xBEEF);
            var original = matrix.copy();

            matrix.copyRow(7, 7);

            assertMatricesEqual(original, matrix);
        }

        @Test
        @DisplayName("rejects a row outside the matrix")
        void rejectsRowOutsideTheMatrix() {
            var matrix = new BitMatrix(21);

            assertThatIndexOutOfBoundsException().isThrownBy(() -> matrix.copyRow(0, 21));
            assertThatIndexOutOfBoundsException().isThrownBy(() -> matrix.copyRow(-1, 0));
        }
    }

    @Nested
    class Invert {

        @ParameterizedTest
        @ValueSource(ints = { 1, 21, 63, 64, 65, 177, 192 })
        @DisplayName("flips every bit inside the matrix")
        void flipsEveryBit(int size) {
            var matrix = pattern(size, 0xC0FFEE);
            var expected = pattern(size, 0xC0FFEE);

            matrix.invert();

            for (var y = 0; y < size; y += 1) {
                for (var x = 0; x < size; x += 1) {
                    assertThat(matrix.get(x, y)).isEqualTo(!expected.get(x, y));
                }
            }
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 21, 63, 64, 65, 177, 192 })
        @DisplayName("leaves the bits beyond the logical size cleared")
        void leavesPaddingCleared(int size) {
            var matrix = new BitMatrix(size);

            matrix.invert();

            assertThat(matrix.popCount())
                    .as("only bits inside the matrix are set")
                    .isEqualTo(size * size);
        }

        @Test
        @DisplayName("twice restores the original")
        void twiceRestoresOriginal() {
            var matrix = pattern(177, 0xBEEF);
            var original = matrix.copy();

            matrix.invert();
            matrix.invert();

            assertMatricesEqual(original, matrix);
        }
    }

    @Nested
    class And {

        @Test
        @DisplayName("keeps only the bits set in both matrices")
        void combinesBitwise() {
            final int size = 131;
            var a = new BitMatrix(size);
            var b = new BitMatrix(size);
            a.fillRect(10, 10, 50, 50);
            b.fillRect(30, 30, 50, 50);

            a.and(b);

            for (var y = 0; y < size; y += 1) {
                for (var x = 0; x < size; x += 1) {
                    var expected = x >= 30 && x < 60 && y >= 30 && y < 60;
                    assertThat(a.get(x, y)).isEqualTo(expected);
                }
            }
        }

        @Test
        @DisplayName("yields an empty matrix for disjoint matrices")
        void disjointYieldsEmpty() {
            var a = new BitMatrix(80);
            var b = new BitMatrix(80);
            a.fillRect(0, 0, 30, 30);
            b.fillRect(40, 40, 30, 30);

            a.and(b);

            assertThat(a.popCount()).isZero();
        }

        @Test
        @DisplayName("with itself leaves the matrix unchanged")
        void withSelfLeavesUnchanged() {
            var a = new BitMatrix(192);
            a.fillRect(5, 7, 91, 33);
            a.set(191, 191, true);
            var original = a.copy();

            a.and(a);

            assertMatricesEqual(original, a);
        }

        @Test
        @DisplayName("rejects a matrix of a different size")
        void differentSizeThrows() {
            var a = new BitMatrix(64);
            var b = new BitMatrix(65);

            assertThatIllegalArgumentException().isThrownBy(() -> a.and(b));
        }
    }

    @Nested
    class Xor {

        @Test
        @DisplayName("toggles the bits set in the other matrix")
        void togglesBits() {
            final int size = 131;
            var a = new BitMatrix(size);
            var b = new BitMatrix(size);
            a.fillRect(10, 10, 50, 50);
            b.fillRect(30, 30, 50, 50);

            a.xor(b);

            for (var y = 0; y < size; y += 1) {
                for (var x = 0; x < size; x += 1) {
                    var inA = x >= 10 && x < 60 && y >= 10 && y < 60;
                    var inB = x >= 30 && x < 80 && y >= 30 && y < 80;
                    assertThat(a.get(x, y)).isEqualTo(inA ^ inB);
                }
            }
        }

        @Test
        @DisplayName("with itself yields an empty matrix")
        void withSelfYieldsEmpty() {
            var a = new BitMatrix(192);
            a.fillRect(5, 7, 91, 33);
            a.set(191, 191, true);

            a.xor(a);

            assertThat(a.popCount()).isZero();
        }

        @Test
        @DisplayName("twice restores the original")
        void twiceRestoresOriginal() {
            var a = new BitMatrix(100);
            var b = new BitMatrix(100);
            a.fillRect(20, 20, 40, 40);
            b.fillRect(50, 50, 30, 30);
            var original = a.copy();

            a.xor(b);
            a.xor(b);

            assertMatricesEqual(original, a);
        }

        @Test
        @DisplayName("rejects a matrix of a different size")
        void differentSizeThrows() {
            var a = new BitMatrix(64);
            var b = new BitMatrix(65);

            assertThatIllegalArgumentException().isThrownBy(() -> a.xor(b));
        }
    }

    @Nested
    class Transpose {

        @ParameterizedTest(name = "size {0}")
        @ValueSource(ints = { 0, 1, 2, 21, 41, 63, 64, 65, 81, 121, 127, 128, 129, 177, 191, 192 })
        @DisplayName("matches the naive bit-by-bit transpose")
        void matchesNaiveOracle(int size) {
            var deltaSwapped = pattern(size, 0xC0FFEE);
            var naive = deltaSwapped.copy();

            deltaSwapped.transpose();
            naiveTranspose(naive);

            assertMatricesEqual(naive, deltaSwapped);
        }

        @ParameterizedTest(name = "size {0}")
        @ValueSource(ints = { 0, 1, 2, 21, 63, 64, 65, 127, 128, 129, 177, 192 })
        @DisplayName("twice restores the original")
        void twiceRestoresOriginal(int size) {
            var matrix = pattern(size, 0xBEEF);
            var original = matrix.copy();

            matrix.transpose();
            matrix.transpose();

            assertMatricesEqual(original, matrix);
        }

        @Test
        @DisplayName("turns rows into columns")
        void turnsRowsIntoColumns() {
            var matrix = new BitMatrix(5);
            // 1 0 0 0 0
            // 1 1 0 0 0
            // 1 0 1 0 0
            // 1 0 0 1 0
            // 1 0 0 0 1
            for (var y = 0; y < 5; y += 1) {
                matrix.set(0, y, true);
                //noinspection SuspiciousNameCombination
                matrix.set(y, y, true);
            }

            matrix.transpose();

            // 1 1 1 1 1
            // 0 1 0 0 0
            // 0 0 1 0 0
            // 0 0 0 1 0
            // 0 0 0 0 1
            for (var y = 0; y < 5; y += 1) {
                for (var x = 0; x < 5; x += 1) {
                    assertThat(matrix.get(x, y)).isEqualTo(y == 0 || x == y);
                }
            }
        }

        @Test
        @DisplayName("leaves a single bit in place")
        void singleBitIsIdentity() {
            var matrix = new BitMatrix(1);
            matrix.set(0, 0, true);

            matrix.transpose();

            assertThat(matrix.get(0, 0)).isTrue();
            assertThat(matrix.popCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("leaves the bits beyond the logical size cleared")
        void leavesPaddingCleared() {
            var matrix = pattern(65, 0xC0FFEE);

            matrix.transpose();

            assertThat(matrix.popCount())
                    .as("transposing must not spill bits into the padding")
                    .isEqualTo(pattern(65, 0xC0FFEE).popCount());
        }
    }

    @Test
    @DisplayName("copy is independent of the original")
    void copyIsIndependent() {
        var original = pattern(65, 0xC0FFEE);
        var copy = original.copy();

        copy.set(0, 0, !copy.get(0, 0));

        assertThat(copy.get(0, 0)).isNotEqualTo(original.get(0, 0));
        assertThat(copy.size()).isEqualTo(original.size());
    }

    @ParameterizedTest(name = "size {0}")
    @CsvSource({
            "1, 1, 1", "21, 1, 1", "61, 1, 1", "64, 1, 1",
            "65, 2, 2", "97, 2, 2", "125, 2, 2", "128, 2, 2",
            "129, 4, 3", "157, 4, 3", "177, 4, 3", "192, 4, 3"
    })
    @DisplayName("takes one word of modules per 64 columns, in a stride rounded up to a power of two")
    void rowLayoutFollowsFromTheSize(int size, int expectedWordsPerRow, int expectedUsedWordsPerRow) {
        // This table is the band boundaries stated as an executable fact: 61 and 65 are versions 11
        // and 12, 125 and 129 versions 27 and 28, 177 version 40.
        var matrix = new BitMatrix(size);

        assertThat(matrix.wordsPerRow()).isEqualTo(expectedWordsPerRow);
        assertThat(matrix.usedWordsPerRow()).isEqualTo(expectedUsedWordsPerRow);
        assertThat(matrix.raw()).hasSize(expectedWordsPerRow * size);
    }

    @ParameterizedTest(name = "size {0}")
    @ValueSource(ints = { 21, 64, 65, 128, 129, 177, 192 })
    @DisplayName("leaves the padding words of every row zero, whatever is written to the matrix")
    void paddingWordsStayZero(int size) {
        // The whole-matrix operations run flat over raw() rather than skipping the padding word,
        // which is sound only while it is zero. Every way of writing to a matrix has to keep it so.
        var matrix = new BitMatrix(size);
        matrix.fillRect(0, 0, size, size);
        matrix.invert();
        matrix.fillRect(3, 4, size - 3, size - 5);
        matrix.set(size - 1, size - 1, true);
        matrix.transpose();

        var raw = matrix.raw();
        for (var y = 0; y < size; y += 1) {
            for (var w = matrix.usedWordsPerRow(); w < matrix.wordsPerRow(); w += 1) {
                assertThat(raw[matrix.wordsPerRow() * y + w])
                        .as("padding word %d of row %d", w, y)
                        .isZero();
            }
        }
    }

    @ParameterizedTest(name = "size {0}")
    @ValueSource(ints = { 21, 65 })
    @DisplayName("raw exposes the live backing words, one row after the other")
    void rawIsLiveBackingArray(int size) {
        var matrix = new BitMatrix(size);

        matrix.raw()[matrix.wordsPerRow() * 3] = 0b101L;

        assertThat(matrix.get(0, 3)).isTrue();
        assertThat(matrix.get(1, 3)).isFalse();
        assertThat(matrix.get(2, 3)).isTrue();
    }
}

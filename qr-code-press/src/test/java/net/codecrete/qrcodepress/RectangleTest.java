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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QrCode#toRectangles()} and the greedy merging behind it.
 * <p>
 * The merging is a rendering optimization, so what has to hold is not a particular set of
 * rectangles but the invariant the renderers rely on: the rectangles tile exactly the dark modules,
 * without gaps and without overlap.
 * </p>
 */
class RectangleTest {

    @ParameterizedTest
    @CsvSource({
            "A, LOW",
            "A, HIGH",
            "'Hello, world!', MEDIUM",
            "12345678901234567890, QUARTILE",
            "https://github.com/manuelbl/qr-code-press, MEDIUM",
            "'At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis "
                    + "praesentium voluptatum deleniti atque corrupti quos dolores et quas "
                    + "molestias excepturi sint occaecati cupiditate non provident.', HIGH"
    })
    @DisplayName("the rectangles cover exactly the dark modules")
    void rectanglesCoverExactlyTheDarkModules(String text, Ecc errorCorrection) {
        var qrCode = QrCode.encodeText(text, errorCorrection);
        var size = qrCode.getSize();
        var covered = new boolean[size][size];

        for (var rectangle : qrCode.toRectangles()) {
            assertThat(rectangle.width()).isPositive();
            assertThat(rectangle.height()).isPositive();
            assertThat(rectangle.x()).isBetween(0, size - rectangle.width());
            assertThat(rectangle.y()).isBetween(0, size - rectangle.height());

            for (var y = rectangle.y(); y < rectangle.y() + rectangle.height(); y += 1) {
                for (var x = rectangle.x(); x < rectangle.x() + rectangle.width(); x += 1) {
                    assertThat(qrCode.getModule(x, y))
                            .withFailMessage("rectangle %s covers the light module (%d, %d)",
                                    rectangle, x, y)
                            .isTrue();
                    assertThat(covered[y][x])
                            .withFailMessage("module (%d, %d) is covered more than once", x, y)
                            .isFalse();
                    covered[y][x] = true;
                }
            }
        }

        for (var y = 0; y < size; y += 1) {
            for (var x = 0; x < size; x += 1) {
                assertThat(covered[y][x])
                        .withFailMessage("module (%d, %d) is dark but uncovered", x, y)
                        .isEqualTo(qrCode.getModule(x, y));
            }
        }
    }

    @Test
    @DisplayName("adjacent dark modules are merged into one rectangle")
    void adjacentModulesAreMerged() {
        // The top row of the top left finder pattern is seven dark modules wide. Finding it as a
        // single rectangle proves the modules are merged rather than emitted one by one.
        var qrCode = QrCode.encodeText("A", Ecc.MEDIUM);

        assertThat(qrCode.toRectangles()).contains(new QrRectangle(0, 0, 7, 1));
    }

    @Test
    @DisplayName("the merging cuts the number of shapes to draw well below the module count")
    void mergingReducesTheNumberOfShapes() {
        var qrCode = QrCode.encodeText("Hello, world!", Ecc.MEDIUM);

        var darkModules = 0;
        for (var y = 0; y < qrCode.getSize(); y += 1) {
            for (var x = 0; x < qrCode.getSize(); x += 1) {
                if (qrCode.getModule(x, y)) {
                    darkModules += 1;
                }
            }
        }

        assertThat(qrCode.toRectangles()).hasSizeLessThan(darkModules / 2);
    }

    @Test
    @DisplayName("the largest rectangle at a corner is preferred over the first one")
    void theLargestRectangleWins() {
        // A 2x3 block of dark modules: the greedy scan reaches (1, 1) first, and has to choose the
        // 2-wide, 3-tall block over the 2x1 run it sees in the first row.
        var modules = new BitMatrix(5);
        modules.fillRect(1, 1, 2, 3);

        assertThat(RectangleBuilder.build(modules)).containsExactly(new QrRectangle(1, 1, 2, 3));
    }

    @Test
    @DisplayName("an L-shaped area is split into rectangles that tile it")
    void anLShapeIsSplit() {
        // 3 wide and 3 tall, with the top right 2x2 corner light:
        //   X..
        //   X..
        //   XXX
        var modules = new BitMatrix(4);
        modules.fillRect(0, 0, 1, 3);
        modules.fillRect(0, 2, 3, 1);

        assertThat(RectangleBuilder.build(modules))
                .containsExactly(new QrRectangle(0, 0, 1, 3), new QrRectangle(1, 2, 2, 1));
    }

    @Test
    @DisplayName("a QR code without dark modules yields no rectangles")
    void anEmptyMatrixYieldsNoRectangles() {
        assertThat(RectangleBuilder.build(new BitMatrix(21))).isEmpty();
    }

    @Test
    @DisplayName("the source matrix is left untouched")
    void theSourceMatrixIsNotModified() {
        var modules = new BitMatrix(5);
        modules.fillRect(1, 1, 2, 3);

        RectangleBuilder.build(modules);

        assertThat(modules.popCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("the returned list rejects modification")
    void theListIsUnmodifiable() {
        var rectangles = QrCode.encodeText("A", Ecc.MEDIUM).toRectangles();

        assertThat(rectangles).isUnmodifiable();
    }

    @Test
    @DisplayName("a rectangle reports its position and size")
    void rectangleProperties() {
        var rectangle = new QrRectangle(2, 3, 5, 7);

        assertThat(rectangle.x()).isEqualTo(2);
        assertThat(rectangle.y()).isEqualTo(3);
        assertThat(rectangle.width()).isEqualTo(5);
        assertThat(rectangle.height()).isEqualTo(7);
        assertThat(rectangle).isEqualTo(new QrRectangle(2, 3, 5, 7))
                .isNotEqualTo(new QrRectangle(2, 3, 5, 8))
                .hasSameHashCodeAs(new QrRectangle(2, 3, 5, 7))
                .hasToString("QrRectangle[x=2, y=3, width=5, height=7]");
    }
}

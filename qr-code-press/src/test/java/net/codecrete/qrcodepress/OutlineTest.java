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
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QrCode#toOutlines()} and the boundary tracing behind it.
 * <p>
 * What has to hold is the invariant the renderers rely on: filling the polygons &mdash; under the
 * nonzero rule and under the even-odd rule alike &mdash; reproduces exactly the dark modules. That
 * is checked by rasterizing the loops again, across all verified cases. The winding, the
 * alternating axis-parallel edges, the start vertex and the loop order are asserted alongside,
 * since the documentation promises them. The small hand-built matrices pin the connectivity
 * decisions: diagonally touching dark modules stay separate loops, diagonally touching light
 * areas merge into one hole.
 * </p>
 */
class OutlineTest {

    static List<VerifiedData.QrCodeCase> cases() {
        return VerifiedData.qrCodeCases();
    }

    @ParameterizedTest(name = "case {0}")
    @MethodSource("cases")
    @DisplayName("filling the outlines reproduces exactly the dark modules")
    void outlinesFillToTheDarkModules(VerifiedData.QrCodeCase testCase) {
        var qrCode = QrCode.builder()
                .segments(VerifiedData.segments(testCase.textIndex()))
                .errorCorrection(Ecc.of(testCase.requestedEcc()))
                .versionRange(testCase.minVersion(), testCase.maxVersion())
                .boostErrorCorrection(testCase.boostEcc())
                .build();

        var polygons = qrCode.toOutlines();

        assertLoopInvariants(polygons, qrCode);
        assertFillsToTheDarkModules(polygons, qrCode);

        assertThat(polygons).isSortedAccordingTo((a, b) -> {
            var startA = a.vertices().get(0);
            var startB = b.vertices().get(0);
            var byY = Integer.compare(startA.y(), startB.y());
            return byY != 0 ? byY : Integer.compare(startA.x(), startB.x());
        });
    }

    /**
     * Asserts the documented shape of every loop: at least four vertices, strictly alternating
     * horizontal and vertical edges (the closing edge included), the topmost-then-leftmost vertex
     * first, and the winding matching what the loop surrounds &mdash; clockwise where the module
     * right below the start vertex is dark (a group), counterclockwise where it is light (a hole).
     */
    private static void assertLoopInvariants(List<QrPolygon> polygons, QrCode qrCode) {
        for (var polygon : polygons) {
            var vertices = polygon.vertices();
            assertThat(vertices.size()).isGreaterThanOrEqualTo(4).isEven();

            var count = vertices.size();
            for (var i = 0; i < count; i += 1) {
                var from = vertices.get(i);
                var to = vertices.get((i + 1) % count);
                var horizontal = from.y() == to.y();
                assertThat(horizontal ? from.x() != to.x() : from.x() == to.x())
                        .withFailMessage("edge %s to %s is not axis-parallel", from, to)
                        .isTrue();

                var next = vertices.get((i + 2) % count);
                assertThat(horizontal ? next.y() != to.y() : next.y() == to.y())
                        .withFailMessage("edges do not alternate at %s", to)
                        .isTrue();
            }

            var start = vertices.get(0);
            for (var vertex : vertices) {
                assertThat(vertex.y() > start.y()
                        || (vertex.y() == start.y() && vertex.x() >= start.x()))
                        .withFailMessage("%s lies above or left of the start vertex %s",
                                vertex, start)
                        .isTrue();
            }

            // At the topmost-then-leftmost vertex, the module diagonally below-right is inside the
            // group for an outer loop and inside the hole for a hole loop.
            var surroundsDarkModules = qrCode.getModule(start.x(), start.y());
            var area = signedArea(vertices);
            assertThat(area != 0 && (area > 0) == surroundsDarkModules)
                    .withFailMessage("loop starting at %s has signed area %d but surrounds %s "
                            + "modules", start, area, surroundsDarkModules ? "dark" : "light")
                    .isTrue();
        }
    }

    /**
     * The shoelace sum of the loop, positive for clockwise winding (with y extending downwards).
     */
    private static long signedArea(List<QrPoint> vertices) {
        var sum = 0L;
        for (var i = 0; i < vertices.size(); i += 1) {
            var from = vertices.get(i);
            var to = vertices.get((i + 1) % vertices.size());
            sum += (long) from.x() * to.y() - (long) to.x() * from.y();
        }
        return sum;
    }

    /**
     * Rasterizes the loops again and compares against the modules, once under the nonzero rule and
     * once under the even-odd rule.
     * <p>
     * A scanline through the centre of a module row collects the vertical edges crossing it, each
     * at its x grid line with its direction; sweeping the row left to right, the crossings passed
     * so far give the winding number and the crossing parity at each module centre.
     * </p>
     */
    private static void assertFillsToTheDarkModules(List<QrPolygon> polygons, QrCode qrCode) {
        var size = qrCode.getSize();
        // one extra column: the QR code's right border produces crossings at grid line x = size,
        // to the right of every module centre
        var crossings = new int[size][size + 1];

        for (var polygon : polygons) {
            var vertices = polygon.vertices();
            for (var i = 0; i < vertices.size(); i += 1) {
                var from = vertices.get(i);
                var to = vertices.get((i + 1) % vertices.size());
                if (from.x() != to.x()) {
                    continue;
                }
                var delta = to.y() > from.y() ? 1 : -1;
                for (var y = Math.min(from.y(), to.y()); y < Math.max(from.y(), to.y()); y += 1) {
                    crossings[y][from.x()] += delta;
                }
            }
        }

        for (var y = 0; y < size; y += 1) {
            var winding = 0;
            var parity = false;
            for (var x = 0; x < size; x += 1) {
                winding += crossings[y][x];
                parity ^= (crossings[y][x] & 1) != 0;
                assertThat(winding != 0)
                        .withFailMessage("module (%d, %d): nonzero fill %s, but the module is %s",
                                x, y, winding != 0 ? "dark" : "light",
                                qrCode.getModule(x, y) ? "dark" : "light")
                        .isEqualTo(qrCode.getModule(x, y));
                assertThat(parity)
                        .withFailMessage("module (%d, %d): even-odd fill %s, but the module is %s",
                                x, y, parity ? "dark" : "light",
                                qrCode.getModule(x, y) ? "dark" : "light")
                        .isEqualTo(qrCode.getModule(x, y));
            }
        }
    }

    @Test
    @DisplayName("a group of adjacent dark modules is one clockwise loop")
    void adjacentModulesFormOneLoop() {
        // The top left finder pattern is a 7×7 ring: finding its outer boundary as a single square
        // proves the modules are traced as one shape rather than one by one.
        var qrCode = QrCode.encodeText("A", Ecc.MEDIUM);

        assertThat(qrCode.toOutlines()).contains(new QrPolygon(List.of(
                new QrPoint(0, 0), new QrPoint(7, 0), new QrPoint(7, 7), new QrPoint(0, 7))));
    }

    @Test
    @DisplayName("a hole is a counterclockwise loop inside the group")
    void aHoleIsWoundTheOtherWay() {
        // a 3×3 block with a light centre
        var modules = new BitMatrix(6);
        modules.fillRect(1, 1, 3, 3);
        modules.set(2, 2, false);

        assertThat(OutlineBuilder.build(modules)).containsExactly(
                new QrPolygon(List.of(new QrPoint(1, 1), new QrPoint(4, 1),
                        new QrPoint(4, 4), new QrPoint(1, 4))),
                new QrPolygon(List.of(new QrPoint(2, 2), new QrPoint(2, 3),
                        new QrPoint(3, 3), new QrPoint(3, 2))));
    }

    @Test
    @DisplayName("diagonally touching dark modules stay in separate loops")
    void diagonalModulesStaySeparate() {
        var modules = new BitMatrix(5);
        modules.set(1, 1, true);
        modules.set(2, 2, true);

        assertThat(OutlineBuilder.build(modules)).containsExactly(
                new QrPolygon(List.of(new QrPoint(1, 1), new QrPoint(2, 1),
                        new QrPoint(2, 2), new QrPoint(1, 2))),
                new QrPolygon(List.of(new QrPoint(2, 2), new QrPoint(3, 2),
                        new QrPoint(3, 3), new QrPoint(2, 3))));
    }

    @Test
    @DisplayName("diagonally touching light modules merge into one hole")
    void diagonalLightModulesMergeIntoOneHole() {
        // A 4×4 dark block with two diagonally touching light modules: dark modules connect only
        // horizontally and vertically, so the light side connects diagonally too, and the hole
        // loop passes through the shared corner (2, 2) twice.
        var modules = new BitMatrix(4);
        modules.fillRect(0, 0, 4, 4);
        modules.set(1, 1, false);
        modules.set(2, 2, false);

        assertThat(OutlineBuilder.build(modules)).containsExactly(
                new QrPolygon(List.of(new QrPoint(0, 0), new QrPoint(4, 0),
                        new QrPoint(4, 4), new QrPoint(0, 4))),
                new QrPolygon(List.of(new QrPoint(1, 1), new QrPoint(1, 2), new QrPoint(2, 2),
                        new QrPoint(2, 3), new QrPoint(3, 3), new QrPoint(3, 2),
                        new QrPoint(2, 2), new QrPoint(2, 1))));
    }

    @Test
    @DisplayName("a dark centre inside a hole is a loop of its own")
    void aDarkCentreInsideAHoleIsItsOwnLoop() {
        // The structure of a finder pattern: a 7×7 ring, a light ring inside it, a dark 3×3
        // centre. Three loops: the outer boundary and the centre clockwise, the hole in between
        // counterclockwise.
        var modules = new BitMatrix(9);
        modules.fillRect(0, 0, 7, 7);
        for (var y = 1; y < 6; y += 1) {
            for (var x = 1; x < 6; x += 1) {
                modules.set(x, y, false);
            }
        }
        modules.fillRect(2, 2, 3, 3);

        assertThat(OutlineBuilder.build(modules)).containsExactly(
                new QrPolygon(List.of(new QrPoint(0, 0), new QrPoint(7, 0),
                        new QrPoint(7, 7), new QrPoint(0, 7))),
                new QrPolygon(List.of(new QrPoint(1, 1), new QrPoint(1, 6),
                        new QrPoint(6, 6), new QrPoint(6, 1))),
                new QrPolygon(List.of(new QrPoint(2, 2), new QrPoint(5, 2),
                        new QrPoint(5, 5), new QrPoint(2, 5))));
    }

    @Test
    @DisplayName("a QR code without dark modules yields no polygons")
    void anEmptyMatrixYieldsNoPolygons() {
        assertThat(OutlineBuilder.build(new BitMatrix(21))).isEmpty();
    }

    @Test
    @DisplayName("the source matrix is left untouched")
    void theSourceMatrixIsNotModified() {
        var modules = new BitMatrix(5);
        modules.fillRect(1, 1, 2, 3);

        OutlineBuilder.build(modules);

        assertThat(modules.popCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("the returned list rejects modification")
    void theListIsUnmodifiable() {
        var polygons = QrCode.encodeText("A", Ecc.MEDIUM).toOutlines();

        assertThat(polygons).isUnmodifiable();
        assertThat(polygons.get(0).vertices()).isUnmodifiable();
    }

    @Test
    @DisplayName("a polygon copies its vertex list and reports it")
    void polygonProperties() {
        var vertices = new ArrayList<>(List.of(new QrPoint(1, 2), new QrPoint(3, 2)));
        var polygon = new QrPolygon(vertices);
        vertices.clear();

        assertThat(polygon.vertices()).containsExactly(new QrPoint(1, 2), new QrPoint(3, 2));
        assertThat(polygon).isEqualTo(
                new QrPolygon(List.of(new QrPoint(1, 2), new QrPoint(3, 2))));
        assertThat(new QrPoint(1, 2).x()).isEqualTo(1);
        assertThat(new QrPoint(1, 2).y()).isEqualTo(2);
    }
}

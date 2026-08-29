/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.ArrayList;
import java.util.List;

/**
 * Merges the dark modules of a QR code into a small set of rectangles.
 * <p>
 * A greedy algorithm: scanning in reading order, each dark module that is still uncovered becomes
 * the top left corner of the largest rectangle of dark modules that starts there, and that
 * rectangle is then removed from the working copy. Most rectangles end up covering more than a
 * single module, which is what makes drawing a QR code cheap. The result is not the theoretical
 * minimum number of rectangles, but it is non-overlapping and covers exactly the dark modules.
 * </p>
 */
final class RectangleBuilder {

    private RectangleBuilder() {
        // non-instantiable
    }

    /**
     * Merges the dark modules of the specified matrix into rectangles.
     *
     * @param modules the modules; not modified (the algorithm works on a copy)
     * @return the rectangles, in reading order of their top left corner
     */
    static List<QrRectangle> build(BitMatrix modules) {
        // the algorithm is destructive (it clears the modules it has covered), so it needs a copy
        var working = modules.copy();

        var size = working.size();
        var rectangles = new ArrayList<QrRectangle>();

        for (var y = 0; y < size; y += 1) {
            for (var x = 0; x < size; x += 1) {
                if (working.get(x, y)) {
                    rectangles.add(extractLargestRectangle(working, x, y));
                }
            }
        }

        return rectangles;
    }

    /**
     * Finds the largest rectangle of set bits with (x, y) as its top left corner, and clears it.
     * <p>
     * Row by row downwards, the run of set bits starting at column <i>x</i> narrows the rectangle
     * that is still possible; the best area seen over all heights wins.
     * </p>
     */
    private static QrRectangle extractLargestRectangle(BitMatrix modules, int x, int y) {
        var size = modules.size();

        var bestWidth = 1;
        var bestHeight = 1;
        var maxArea = 1;

        var xLimit = size;
        var iy = y;
        while (iy < size && modules.get(x, iy)) {
            var width = 0;
            while (x + width < xLimit && modules.get(x + width, iy)) {
                width += 1;
            }

            var area = width * (iy - y + 1);
            if (area > maxArea) {
                maxArea = area;
                bestWidth = width;
                bestHeight = iy - y + 1;
            }
            xLimit = x + width;
            iy += 1;
        }

        clearRectangle(modules, x, y, bestWidth, bestHeight);
        return new QrRectangle(x, y, bestWidth, bestHeight);
    }

    private static void clearRectangle(BitMatrix modules, int x, int y, int width, int height) {
        for (var iy = y; iy < y + height; iy += 1) {
            for (var ix = x; ix < x + width; ix += 1) {
                modules.set(ix, iy, false);
            }
        }
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.List;

/**
 * A closed loop of the outline of the dark modules of a QR code.
 * <p>
 * Consecutive vertices are joined by axis-parallel edges, alternating between horizontal and
 * vertical; the closing edge from the last vertex back to the first is implied. The first vertex
 * is the topmost one, the leftmost of those. A vertex can occur twice in the loop where the
 * outline touches itself at a single corner point.
 * </p>
 * <p>
 * A loop around a group of dark modules runs clockwise (with <i>y</i> extending downwards), a loop
 * around a hole within such a group runs counterclockwise. With this winding, filling the polygons
 * of a QR code produces the dark modules under both the nonzero rule (the SVG and
 * {@code java.awt.geom} default) and the even-odd rule (the XAML default).
 * </p>
 * <p>
 * Instances are produced by {@link QrCode#toOutlines()}. The vertex list is immutable.
 * </p>
 *
 * @param vertices the corners of the loop, in drawing order
 * @see QrCode#toOutlines()
 */
public record QrPolygon(List<QrPoint> vertices) {

    /**
     * Creates a new instance.
     *
     * @param vertices the corners of the loop, in drawing order (copied)
     */
    public QrPolygon {
        vertices = List.copyOf(vertices);
    }
}

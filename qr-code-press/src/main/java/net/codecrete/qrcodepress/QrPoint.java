/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * A corner point of a {@link QrPolygon}.
 * <p>
 * The point lies on the grid of module corners: the top left corner of the QR code is (0, 0),
 * <i>x</i> extends to the right and <i>y</i> extends downwards. Each unit is one module; no border
 * is included. A coordinate therefore ranges from 0 to the size of the QR code &mdash; one more
 * than the largest module coordinate, since a point names a corner, not a module.
 * </p>
 *
 * @param x the x coordinate
 * @param y the y coordinate
 * @see QrPolygon
 */
public record QrPoint(int x, int y) {
}

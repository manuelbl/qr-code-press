/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * A rectangular block of dark modules within a QR code.
 * <p>
 * The coordinates use the same system as {@link QrCode#getModule(int, int)}: the top left module is
 * at (0, 0), <i>x</i> extends to the right and <i>y</i> extends downwards. Each unit is one module;
 * no border is included.
 * </p>
 * <p>
 * Instances are produced by {@link QrCode#toRectangles()}, which merges adjacent dark modules into
 * larger rectangles so that fewer shapes have to be drawn.
 * </p>
 *
 * @param x      the x coordinate of the top left module
 * @param y      the y coordinate of the top left module
 * @param width  the width, in modules
 * @param height the height, in modules
 * @see QrCode#toRectangles()
 */
public record QrRectangle(int x, int y, int width, int height) {
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.awt;

import net.codecrete.qrcodepress.QrCode;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.util.Arrays;
import java.util.Objects;

/**
 * Renders QR codes with AWT.
 * <p>
 * This is a convenience for callers already working with AWT or Swing. It is not needed to produce
 * an image: {@link QrCode#toPng(int, int, int, int)} writes a PNG without {@code java.desktop}.
 * </p>
 * <pre>{@code
 * var qrCode = QrCode.encodeText("Hello, world!", Ecc.MEDIUM);
 * var image = QrCodeGraphics.toBufferedImage(qrCode, 4, 10);
 * }</pre>
 * <p>
 * The <i>border</i> is the light margin around the QR code, as a multiple of the module size; the
 * specification asks for at least 4. The <i>scale</i> is the size of one module. So
 * {@code toBufferedImage(qrCode, 4, 10)} pads the QR code with four light modules on each side and
 * draws every module as 10&times;10 pixels.
 * </p>
 */
public final class QrCodeGraphics {

    private QrCodeGraphics() {
        // non-instantiable
    }

    /**
     * Creates an image of the specified QR code, in black on white.
     *
     * @param qrCode the QR code
     * @param border the border width, as a multiple of the module size
     * @param scale  the width and height of each module, in pixels
     * @return the image
     */
    public static BufferedImage toBufferedImage(QrCode qrCode, int border, int scale) {
        return toBufferedImage(qrCode, border, scale, Color.BLACK, Color.WHITE);
    }

    /**
     * Creates an image of the specified QR code.
     * <p>
     * The image uses indexed color with 1 bit per pixel and a palette of the two specified colors,
     * so it occupies an eighth of what a grayscale image of the same size would. Both colors may be
     * translucent or fully transparent; a transparent background is the usual way to place a QR
     * code on a colored surface.
     * </p>
     *
     * @param qrCode     the QR code
     * @param border     the border width, as a multiple of the module size
     * @param scale      the width and height of each module, in pixels
     * @param foreground the color of the dark modules
     * @param background the color of the light modules
     * @return the image
     */
    public static BufferedImage toBufferedImage(QrCode qrCode, int border, int scale,
                                                Color foreground, Color background) {
        Objects.requireNonNull(qrCode, "qrCode");
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(background, "background");

        var imageSize = imageSize(qrCode.getSize(), border, scale);
        var image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_BYTE_BINARY,
                paletteOf(foreground, background));

        // The pixels are written through the raster rather than drawn with a Graphics2D: a sample
        // value is a palette index, so no compositing takes place and a translucent color stays
        // exactly what the caller asked for. Sample 0 is the background, so only the dark modules
        // need writing.
        var raster = image.getRaster();
        var darkRow = new int[imageSize];
        Arrays.fill(darkRow, 1);

        for (var rectangle : qrCode.toRectangles()) {
            var x = (rectangle.x() + border) * scale;
            var width = rectangle.width() * scale;
            var yStart = (rectangle.y() + border) * scale;
            var yEnd = yStart + rectangle.height() * scale;
            for (var y = yStart; y < yEnd; y += 1) {
                raster.setSamples(x, y, width, 1, 0, darkRow);
            }
        }

        return image;
    }

    /**
     * Draws the specified QR code into the specified graphics context, in black on white.
     *
     * @param qrCode   the QR code
     * @param graphics the graphics context
     * @param border   the border width, as a multiple of the module size
     * @param scale    the width and height of each module
     * @throws IllegalArgumentException if the border is negative or the scale is not positive
     */
    public static void draw(QrCode qrCode, Graphics2D graphics, double border, double scale) {
        draw(qrCode, graphics, border, scale, Color.BLACK, Color.WHITE);
    }

    /**
     * Draws the specified QR code into the specified graphics context.
     * <p>
     * The QR code is drawn at (0, 0). Use {@link Graphics2D#translate(double, double)} to place it
     * elsewhere. A fully transparent background leaves whatever the context already holds.
     * </p>
     * <p>
     * Unlike the image and PNG output, this draws in the coordinates of the graphics context, so
     * border and scale need not be whole numbers.
     * </p>
     *
     * @param qrCode     the QR code
     * @param graphics   the graphics context
     * @param border     the border width, as a multiple of the module size
     * @param scale      the width and height of each module
     * @param foreground the color of the dark modules
     * @param background the color of the light modules
     * @throws IllegalArgumentException if the border is negative or the scale is not positive
     */
    public static void draw(QrCode qrCode, Graphics2D graphics, double border, double scale,
                            Color foreground, Color background) {
        Objects.requireNonNull(qrCode, "qrCode");
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(background, "background");
        if (!(border >= 0)) { // NOSONAR ensure NaN are handled correctly
            throw new IllegalArgumentException("border must not be negative");
        }
        if (!(scale > 0)) { // NOSONAR ensure NaN are handled correctly
            throw new IllegalArgumentException("scale must be positive");
        }

        var saved = graphics.getPaint();
        try {
            if (background.getAlpha() != 0) {
                var dimension = (qrCode.getSize() + 2 * border) * scale;
                graphics.setPaint(background);
                graphics.fill(new Rectangle2D.Double(0, 0, dimension, dimension));
            }

            // The dark modules are filled as one path tracing their outline, rather than as one
            // rectangle per block: adjacent shapes would show hairline seams under anti-aliasing.
            // The outline winds holes the other way, so the nonzero rule fills them light.
            var path = new Path2D.Double(Path2D.WIND_NON_ZERO);
            for (var polygon : qrCode.toOutlines()) {
                var vertices = polygon.vertices();
                var first = vertices.get(0);
                path.moveTo((first.x() + border) * scale, (first.y() + border) * scale);
                for (var i = 1; i < vertices.size(); i += 1) {
                    var vertex = vertices.get(i);
                    path.lineTo((vertex.x() + border) * scale, (vertex.y() + border) * scale);
                }
                path.closePath();
            }
            graphics.setPaint(foreground);
            graphics.fill(path);
        } finally {
            graphics.setPaint(saved);
        }
    }

    /**
     * Validates the border and scale, and returns the resulting image size in pixels.
     */
    private static int imageSize(int size, int border, int scale) {
        if (border < 0) {
            throw new IllegalArgumentException("border must not be negative");
        }
        if (scale < 1) {
            throw new IllegalArgumentException("scale must be at least 1");
        }

        return (size + 2 * border) * scale;
    }

    /**
     * Creates the two-entry palette: index 0 is the background, index 1 the foreground.
     */
    private static IndexColorModel paletteOf(Color foreground, Color background) {
        var red = new byte[] { (byte) background.getRed(), (byte) foreground.getRed() };
        var green = new byte[] { (byte) background.getGreen(), (byte) foreground.getGreen() };
        var blue = new byte[] { (byte) background.getBlue(), (byte) foreground.getBlue() };
        var alpha = new byte[] { (byte) background.getAlpha(), (byte) foreground.getAlpha() };
        return new IndexColorModel(1, 2, red, green, blue, alpha);
    }
}

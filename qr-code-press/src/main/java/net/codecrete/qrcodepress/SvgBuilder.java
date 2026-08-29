/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.List;
import java.util.Objects;

/**
 * Creates SVG documents and SVG/XAML graphics paths from the rectangles of a QR code.
 */
final class SvgBuilder {

    private SvgBuilder() {
        // non-instantiable
    }

    /**
     * Creates a complete SVG document for the specified QR code.
     *
     * @param qrCode     the QR code
     * @param border     the border width, as a multiple of the module size
     * @param foreground the color of the dark modules, as a CSS color
     * @param background the color of the light modules, as a CSS color
     * @return the SVG document
     */
    static String toSvgString(QrCode qrCode, int border, String foreground, String background) {
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(background, "background");

        var dimension = checkBorder(qrCode.getSize(), border);
        var svg = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" ")
                .append("\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 ")
                .append(dimension).append(' ').append(dimension).append("\" stroke=\"none\">\n")
                .append("\t<rect width=\"100%\" height=\"100%\" fill=\"").append(background).append("\"/>\n")
                .append("\t<path d=\"");

        appendPath(svg, qrCode.toRectangles(), border);

        return svg
                .append("\" fill=\"").append(foreground).append("\"/>\n")
                .append("</svg>\n")
                .toString();
    }

    /**
     * Creates an SVG/XAML graphics path for the specified QR code.
     *
     * @param qrCode the QR code
     * @param border the border width, as a multiple of the module size
     * @return the graphics path
     */
    static String toGraphicsPath(QrCode qrCode, int border) {
        checkBorder(qrCode.getSize(), border);

        var path = new StringBuilder();
        appendPath(path, qrCode.toRectangles(), border);
        return path.toString();
    }

    /**
     * Validates the border and returns the resulting dimension, in modules.
     */
    private static int checkBorder(int size, int border) {
        if (border < 0) {
            throw new IllegalArgumentException("border must not be negative");
        }

        return size + 2 * border;
    }

    /**
     * Appends the path commands for the specified rectangles.
     * <p>
     * The numbers are appended digit by digit rather than formatted, so the path does not depend on
     * the default locale (which would otherwise supply its own minus sign or digits).
     * </p>
     */
    private static void appendPath(StringBuilder path, List<QrRectangle> rectangles, int border) {
        for (var i = 0; i < rectangles.size(); i += 1) {
            var rectangle = rectangles.get(i);

            // the first rectangle starts the path, the others are separated by a space
            if (i != 0) {
                path.append(' ');
            }

            path.append('M').append(rectangle.x() + border).append(',').append(rectangle.y() + border)
                    .append('h').append(rectangle.width())
                    .append('v').append(rectangle.height())
                    .append('h').append(-rectangle.width())
                    .append('z');
        }
    }
}

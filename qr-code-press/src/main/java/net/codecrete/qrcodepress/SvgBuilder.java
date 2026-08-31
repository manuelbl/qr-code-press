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
 * Creates SVG documents and SVG/XAML graphics paths from the outline of a QR code.
 * <p>
 * The dark modules are drawn as a single path of closed sub-paths, one per outline polygon. A
 * single path has no seams between adjacent shapes, where anti-aliased rendering would otherwise
 * show hairlines. The polygons wind holes the other way than groups, so the path needs no
 * fill-rule attribute: both the nonzero rule (the SVG default) and the even-odd rule (the XAML
 * default) fill it to the QR code.
 * </p>
 */
final class SvgBuilder {

    private SvgBuilder() {
        // non-instantiable
    }

    /**
     * Creates a complete SVG document for the specified QR code.
     *
     * <p>
     * Pass {@code null} for {@code background} to omit the background.
     * </p>
     *
     * @param qrCode     the QR code
     * @param border     the border width, as a multiple of the module size
     * @param foreground the color of the dark modules, as a CSS color
     * @param background the color of the light modules, as a CSS color
     * @return the SVG document
     */
    static String toSvgString(QrCode qrCode, int border, String foreground, String background) {
        Objects.requireNonNull(foreground, "foreground");

        var dimension = checkBorder(qrCode.getSize(), border);
        var svg = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" ")
                .append("\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 ")
                .append(dimension).append(' ').append(dimension).append("\" stroke=\"none\">\n");
        if (background != null)
            svg.append("\t<rect width=\"100%\" height=\"100%\" fill=\"").append(background).append("\"/>\n");

        svg.append("\t<path d=\"");

        appendPath(svg, qrCode.toOutlines(), border);

        return svg
                .append("\" fill=\"").append(foreground).append("\" shape-rendering=\"crispEdges\"/>\n")
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
        appendPath(path, qrCode.toOutlines(), border);
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
     * Appends the path commands for the specified polygons, one closed sub-path each.
     * <p>
     * The edges of a polygon are strictly axis-parallel, so each becomes a relative {@code h} or
     * {@code v} command; {@code z} draws the closing edge. The numbers are appended digit by digit
     * rather than formatted, so the path does not depend on the default locale (which would
     * otherwise supply its own minus sign or digits).
     * </p>
     */
    private static void appendPath(StringBuilder path, List<QrPolygon> polygons, int border) {
        for (var i = 0; i < polygons.size(); i += 1) {
            var vertices = polygons.get(i).vertices();

            // the first polygon starts the path, the others are separated by a space
            if (i != 0) {
                path.append(' ');
            }

            var first = vertices.get(0);
            path.append('M').append(first.x() + border).append(',').append(first.y() + border);

            for (var j = 1; j < vertices.size(); j += 1) {
                var from = vertices.get(j - 1);
                var to = vertices.get(j);
                if (to.y() == from.y()) {
                    path.append('h').append(to.x() - from.x());
                } else {
                    path.append('v').append(to.y() - from.y());
                }
            }
            path.append('z');
        }
    }
}

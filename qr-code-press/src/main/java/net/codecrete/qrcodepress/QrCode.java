/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.Collections;
import java.util.List;

/**
 * A QR code carrying text or binary data.
 * <p>
 * QR codes are two-dimensional barcodes, invented by Denso Wave and described in the ISO/IEC 18004
 * standard "QR code bar code symbology specification". The data is represented as a square grid of
 * dark and light squares, which the specification calls <i>modules</i>.
 * </p>
 * <p>
 * The grid comes in 40 sizes, called <i>versions</i>: version 1 is 21&times;21 modules, version 40
 * is 177&times;177. The bigger the version, the more data fits. Part of the capacity is spent on
 * error correction data, so that a QR code remains readable despite reflections, dirt or partial
 * damage; how much is spent is the {@linkplain Ecc error correction level}.
 * </p>
 * <p>
 * Instances are created with the static factory methods for the common cases, or with
 * {@link #builder()} for the full set of options:
 * </p>
 * <pre>{@code
 * QrCode qrCode = QrCode.encodeText("Hello, world!", Ecc.MEDIUM);
 *
 * QrCode qrCode = QrCode.builder()
 *         .text("Hello, world!")
 *         .errorCorrection(Ecc.MEDIUM)
 *         .versionRange(5, 20)
 *         .build();
 * }</pre>
 * <p>
 * A QR code renders to an {@linkplain #toSvgString(int) SVG document}, an
 * {@linkplain #toGraphicsPath(int) SVG/XAML graphics path} or a {@linkplain #toPng(int, int) PNG
 * image}. For a graphics library this one does not support directly, {@link #toRectangles()} gives
 * the dark modules as a short list of rectangles to draw, {@link #toOutlines()} traces them as
 * closed polygons for building a filled path, and {@link #getModule(int, int)} reads the modules
 * one by one.
 * </p>
 * <p>
 * Instances are immutable and safe to share between threads.
 * </p>
 *
 * @see DataSegment
 */
public final class QrCode {

    /** The smallest QR code version (size) the specification defines, namely 1. */
    public static final int MIN_VERSION = QrCodeParameters.MIN_VERSION;

    /** The largest QR code version (size) the specification defines, namely 40. */
    public static final int MAX_VERSION = QrCodeParameters.MAX_VERSION;

    /** The modules of this QR code: {@code false} for light, {@code true} for dark. */
    private final BitMatrix modules;

    private final Ecc errorCorrectionLevel;

    private final int mask;

    /**
     * Creates a new instance.
     *
     * @param modules              the modules (not copied; must not be modified afterward)
     * @param errorCorrectionLevel the error correction level
     * @param mask                 the applied mask pattern (0&ndash;7)
     */
    QrCode(BitMatrix modules, Ecc errorCorrectionLevel, int mask) {
        this.modules = modules;
        this.errorCorrectionLevel = errorCorrectionLevel;
        this.mask = mask;
    }

    // region Factory methods

    /**
     * Creates a QR code representing the specified text.
     * <p>
     * The text is split into segments of the modes that encode it most compactly, and the smallest
     * version that holds it is used. The error correction level is raised above the specified one
     * if that is possible without a larger QR code.
     * </p>
     * <p>
     * The text is encoded in ISO-8859-1 if it can be encoded as such, and in UTF-8 with
     * an ECI segment otherwise.
     * </p>
     *
     * @param text                 the text to encode
     * @param errorCorrectionLevel the minimum error correction level
     * @return the QR code
     * @throws DataTooLongException if the text does not fit into the largest QR code at the
     *                              specified error correction level
     */
    public static QrCode encodeText(String text, Ecc errorCorrectionLevel) {
        return builder().text(text).errorCorrection(errorCorrectionLevel).build();
    }

    /**
     * Creates a QR code representing the specified binary data.
     * <p>
     * The smallest version that holds the data is used. The error correction level is raised above
     * the specified one if that is possible without a larger QR code.
     * </p>
     * <p>
     * The segments start with an ECI segment indicating binary data. Use
     * {@code builder().binary(data).eci(Eci.NONE)} to omit it.
     * </p>
     *
     * @param data                 the data to encode; it is copied, and may be modified afterward
     * @param errorCorrectionLevel the minimum error correction level
     * @return the QR code
     * @throws DataTooLongException if the data does not fit into the largest QR code at the
     *                              specified error correction level
     */
    public static QrCode encodeBinary(byte[] data, Ecc errorCorrectionLevel) {
        return builder().binary(data).errorCorrection(errorCorrectionLevel).build();
    }

    /**
     * Creates a QR code representing the specified data segments.
     * <p>
     * The smallest version that holds the segments is used. The error correction level is raised
     * above the specified one if that is possible without a larger QR code.
     * </p>
     * <p>
     * The segments are used as they are. Building them by hand is only needed to encode the
     * payload in a specific way; the other factory methods and {@link QrCodeBuilder#text(String)}
     * build the most compact segments themselves.
     * </p>
     *
     * @param segments             the segments to encode
     * @param errorCorrectionLevel the minimum error correction level
     * @return the QR code
     * @throws DataTooLongException if the segments do not fit into the largest QR code at the
     *                              specified error correction level
     */
    public static QrCode encodeSegments(List<DataSegment> segments, Ecc errorCorrectionLevel) {
        return builder().segments(segments).errorCorrection(errorCorrectionLevel).build();
    }

    /**
     * Creates a builder for a QR code.
     * <p>
     * The builder covers the full set of encoding options. It is not thread-safe; a QR code built
     * with it is.
     * </p>
     *
     * @return the builder
     */
    public static QrCodeBuilder builder() {
        return new QrCodeBuilder();
    }

    // endregion

    // region Properties

    /**
     * Returns the version (size) of this QR code.
     *
     * @return the version ({@value #MIN_VERSION}&ndash;{@value #MAX_VERSION})
     */
    public int getVersion() {
        return QrCodeParameters.version(modules.size());
    }

    /**
     * Returns the width and height of this QR code, in modules.
     * <p>
     * The size is version &times; 4 + 17, i.e. a value between 21 and 177. It does not include the
     * border: a QR code is to be displayed with a light area of at least four modules around it.
     * </p>
     *
     * @return the size, in modules
     */
    public int getSize() {
        return modules.size();
    }

    /**
     * Returns the error correction level of this QR code.
     * <p>
     * It may be higher than the level that was requested: the encoder raises it as far as the
     * chosen version allows, unless that was turned off.
     * </p>
     *
     * @return the error correction level
     */
    public Ecc getErrorCorrectionLevel() {
        return errorCorrectionLevel;
    }

    /**
     * Returns the data mask pattern applied to this QR code.
     *
     * @return the mask pattern index (0&ndash;7)
     */
    public int getMask() {
        return mask;
    }

    /**
     * Returns the color of the module at the specified coordinates.
     * <p>
     * The module (0, 0) is the top left one; <i>x</i> extends to the right and <i>y</i> extends
     * downwards. Coordinates outside the QR code are light, so the border needs no special case.
     * </p>
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return {@code true} if the module is dark, {@code false} if it is light or outside the QR
     *         code
     */
    public boolean getModule(int x, int y) {
        var size = modules.size();
        return 0 <= x && x < size && 0 <= y && y < size && modules.get(x, y);
    }

    /**
     * Returns the modules of this QR code.
     * <p>
     * The matrix is the one this instance holds. It must not be modified.
     * </p>
     *
     * @return the modules
     */
    BitMatrix modules() {
        return modules;
    }

    // endregion

    // region Output

    /**
     * Returns the dark modules of this QR code as a list of rectangles.
     * <p>
     * Adjacent dark modules are merged into larger rectangles, so that most rectangles cover more
     * than a single module and there are fewer shapes to draw. The rectangles do not overlap, and
     * their union is exactly the set of dark modules.
     * </p>
     * <p>
     * They use the same coordinate system as {@link #getModule(int, int)}: the top left module is
     * at (0, 0) and each unit is one module. No border is included.
     * </p>
     * <p>
     * This is the starting point for drawing a QR code, shape by shape, with a graphics library
     * this one does not support directly. Where adjacent shapes leave hairline gaps (typically with
     * anti-aliasing), fill a single path built from {@link #toOutlines()} instead.
     * </p>
     *
     * @return the rectangles
     * @see QrRectangle
     */
    public List<QrRectangle> toRectangles() {
        return Collections.unmodifiableList(RectangleBuilder.build(modules));
    }

    /**
     * Returns the outline of the dark modules of this QR code, as a list of closed polygons.
     * <p>
     * Each polygon traces the boundary of a group of dark modules connected horizontally or
     * vertically, or of a hole within such a group &mdash; most notably the light ring of a finder
     * pattern. Polygons around groups run clockwise, polygons around holes counterclockwise, so
     * filling all of them as one path produces the QR code under both the nonzero and the even-odd
     * fill rule. This is the geometry the SVG output is built from: a single filled path has no
     * seams between adjacent shapes, where anti-aliased rendering would otherwise show hairlines.
     * </p>
     * <p>
     * The vertices use the same coordinate system as {@link #getModule(int, int)}, on the grid of
     * module corners: the top left corner of the QR code is (0, 0) and each unit is one module. No
     * border is included.
     * </p>
     *
     * @return the polygons, ordered by their start vertex in reading order
     * @see QrPolygon
     */
    public List<QrPolygon> toOutlines() {
        return Collections.unmodifiableList(OutlineBuilder.build(modules));
    }

    /**
     * Creates an SVG document of this QR code, in black on white.
     *
     * @param border the border width, as a multiple of the module size
     * @return the SVG document
     * @throws IllegalArgumentException if the border is negative
     */
    public String toSvgString(int border) {
        return toSvgString(border, "#000000", "#ffffff");
    }

    /**
     * Creates an SVG document of this QR code.
     * <p>
     * The document uses Unix line endings (\n) on all platforms.
     * </p>
     * <p>
     * The colors are CSS color values, such as {@code "#339966"}, {@code "fuchsia"} or
     * {@code "rgba(137, 23, 89, 0.3)"}.
     * </p>
     * <p>
     * If {@code background} is {@code null}, then the background will be omitted and
     * the area between the QR code modules and the border are transparent.
     * </p>
     *
     * @param border     the border width, as a multiple of the module size
     * @param foreground the color of the dark modules (CSS color)
     * @param background the color of the light modules (CSS color)
     * @return the SVG document
     * @throws IllegalArgumentException if the border is negative
     */
    public String toSvgString(int border, String foreground, String background) {
        return SvgBuilder.toSvgString(this, border, foreground, background);
    }

    /**
     * Creates a graphics path of this QR code, valid in SVG and in XAML.
     * <p>
     * The path draws the dark modules, tracing the {@linkplain #toOutlines() outlines}: one closed
     * sub-path per polygon, with holes wound the other way, so both the nonzero and the even-odd
     * fill rule produce the QR code. It uses a coordinate system where each module is one unit wide
     * and tall, and the top left module is offset by <i>border</i> units in both directions. It
     * looks like {@code M3,3h7v7h-7z M4,4v5h5v-5z ... M20,21h1v2h-1z} and goes into an SVG
     * {@code <path d="M3,3h..."/>} or a XAML {@code <Path Data="M3,3h..."/>}.
     * </p>
     *
     * @param border the border width, as a multiple of the module size
     * @return the graphics path
     * @throws IllegalArgumentException if the border is negative
     */
    public String toGraphicsPath(int border) {
        return SvgBuilder.toGraphicsPath(this, border);
    }

    /**
     * Creates a PNG image of this QR code, in black on white.
     * <p>
     * For instance, {@code toPng(4, 10)} surrounds the QR code with a border of four light modules
     * and draws each module as 10&times;10 pixels.
     * </p>
     *
     * @param border the border width, as a multiple of the module size
     * @param scale  the width and height of each module, in pixels
     * @return the PNG image
     * @throws IllegalArgumentException if the border is negative, the scale is less than 1, or the
     *                                  resulting image is too large
     */
    public byte[] toPng(int border, int scale) {
        return toPng(border, scale, 0x000000, 0xffffff);
    }

    /**
     * Creates a PNG image of this QR code.
     * <p>
     * The image uses indexed color with 1 bit per pixel and a palette of the two specified colors,
     * each given as a {@code 0xRRGGBB} value.
     * </p>
     * <p>
     * For instance, {@code toPng(4, 10, 0x0000CC, 0xFFFFFF)} surrounds the QR code with a border of
     * four light modules, draws each module as 10&times;10 pixels, the dark ones in blue, the light
     * ones and the border in white.
     * </p>
     * <p>
     * A palette entry carries no alpha, so a color with bits set above {@code 0xRRGGBB} is
     * rejected rather than truncated. An ARGB value, such as the one
     * {@code java.awt.Color.getRGB()} returns, is not accepted here. Use {@code QrCodeGraphics}
     * for a translucent or transparent color.
     * </p>
     *
     * @param border     the border width, as a multiple of the module size
     * @param scale      the width and height of each module, in pixels
     * @param foreground the color of the dark modules, as a {@code 0xRRGGBB} value
     * @param background the color of the light modules, as a {@code 0xRRGGBB} value
     * @return the PNG image
     * @throws IllegalArgumentException if the border is negative, the scale is less than 1, the
     *                                  resulting image is wider than 32768 pixels, or a color has
     *                                  bits set above {@code 0xRRGGBB}
     */
    public byte[] toPng(int border, int scale, int foreground, int background) {
        return PngBuilder.toPng(this, border, scale, foreground, background);
    }

    // endregion

    @Override
    public String toString() {
        return "QrCode[version=" + getVersion() + ", errorCorrectionLevel=" + errorCorrectionLevel
                + ", mask=" + mask + "]";
    }
}

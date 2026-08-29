/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Creates PNG images of QR codes.
 * <p>
 * The images use indexed color with 1 bit per pixel and a palette of two entries, which is the
 * smallest PNG a QR code can be encoded as. Only the chunks such an image needs are written: IHDR,
 * PLTE, a single IDAT and IEND.
 * </p>
 * <p>
 * The encoder is built on {@link Deflater} and {@link CRC32} alone, so the library keeps working
 * where {@code java.desktop} (and with it {@code ImageIO}) is absent: Android, minimal
 * {@code jlink} images, headless containers.
 * </p>
 */
final class PngBuilder {

    /** The PNG file signature (§5.2 of the PNG specification). */
    private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

    /** Color type 3: each pixel is a palette index. */
    private static final byte COLOR_TYPE_INDEXED = 3;

    /**
     * The largest image this encoder produces, in pixels per side.
     * <p>
     * PNG itself allows more, but a larger image is far more likely to be an arithmetic mistake in
     * the border or scale than an intention &mdash; and at this limit the uncompressed image is
     * already 134 MB.
     * </p>
     */
    static final int MAX_IMAGE_SIZE = 32768;

    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

    private PngBuilder() {
    }

    /**
     * Creates a PNG image of the specified QR code.
     *
     * @param qrCode     the QR code
     * @param border     the border width, as a multiple of the module size
     * @param scale      the width and height of each module, in pixels
     * @param foreground the color of the dark modules, as a 0xRRGGBB value
     * @param background the color of the light modules, as a 0xRRGGBB value
     * @return the PNG image
     * @throws IllegalArgumentException if the border is negative, the scale is less than 1, the
     *                                  image would exceed {@value #MAX_IMAGE_SIZE} pixels per side,
     *                                  or a color has bits set above 0xRRGGBB
     */
    static byte[] toPng(QrCode qrCode, int border, int scale, int foreground, int background) {
        var imageSize = imageSize(qrCode.getSize(), border, scale);
        checkColor(foreground, "foreground");
        checkColor(background, "background");

        var builder = new PngBuilder();
        builder.writeHeader(imageSize, imageSize);
        builder.writePalette(background, foreground);
        builder.writeData(createBitmap(qrCode, border, scale, imageSize));
        builder.writeEnd();
        return builder.stream.toByteArray();
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

        // In long arithmetic, as border and scale are unbounded and could overflow an int. Each
        // factor is checked before they are multiplied: their product would overflow a long too.
        var moduleCount = size + 2L * border;
        if (moduleCount > MAX_IMAGE_SIZE || scale > MAX_IMAGE_SIZE
                || moduleCount * scale > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException(
                    "the image must not be wider than " + MAX_IMAGE_SIZE + " pixels");
        }
        return (int) (moduleCount * scale);
    }

    /**
     * Validates a color.
     * <p>
     * A PLTE entry carries no alpha, so a value with bits above 0xRRGGBB is rejected rather than
     * truncated: it is almost always an ARGB value, and silently dropping the alpha would turn a
     * transparent background into an opaque one. {@code QrCodeGraphics} takes AWT colors and does
     * honor their alpha.
     * </p>
     */
    private static void checkColor(int color, String name) {
        if ((color & 0xff000000) != 0) {
            throw new IllegalArgumentException(name + " must be a 0xRRGGBB value");
        }
    }

    /**
     * Creates the uncompressed pixel data: one filter byte plus one bit per pixel and scanline,
     * with a set bit for a dark module.
     */
    private static byte[] createBitmap(QrCode qrCode, int border, int scale, int imageSize) {
        var size = qrCode.getSize();
        var bytesPerLine = (imageSize + 7) / 8 + 1; // one extra byte at the start for the filter type
        var data = new byte[bytesPerLine * imageSize];

        for (var y = 0; y < size; y += 1) {
            var offset = (border + y) * scale * bytesPerLine;

            for (var x = 0; x < size; x += 1) {
                if (!qrCode.getModule(x, y)) {
                    continue;
                }

                // set the 'scale' pixels this module covers in this scanline
                var end = (border + x + 1) * scale;
                for (var pos = (border + x) * scale; pos < end; pos += 1) {
                    data[offset + pos / 8 + 1] |= (byte) (0x80 >> (pos % 8));
                }
            }

            // the module row is 'scale' pixels tall, so replicate the scanline
            for (var i = 1; i < scale; i += 1) {
                System.arraycopy(data, offset, data, offset + i * bytesPerLine, bytesPerLine);
            }
        }

        return data;
    }

    /** Writes the signature and the IHDR chunk. */
    private void writeHeader(int width, int height) {
        stream.writeBytes(SIGNATURE);

        var header = new byte[] {
                (byte) (width >> 24), (byte) (width >> 16), (byte) (width >> 8), (byte) width,
                (byte) (height >> 24), (byte) (height >> 16), (byte) (height >> 8), (byte) height,
                1, // bit depth
                COLOR_TYPE_INDEXED,
                0, // compression method: deflate
                0, // filter method
                0  // interlace method: none
        };
        writeChunk("IHDR", header);
    }

    /** Writes the PLTE chunk. The palette index of a light module is 0, of a dark module 1. */
    private void writePalette(int background, int foreground) {
        var palette = new byte[] {
                (byte) (background >> 16), (byte) (background >> 8), (byte) background,
                (byte) (foreground >> 16), (byte) (foreground >> 8), (byte) foreground
        };
        writeChunk("PLTE", palette);
    }

    /** Writes the pixel data as a single IDAT chunk. */
    private void writeData(byte[] data) {
        writeChunk("IDAT", deflate(data));
    }

    /** Writes the IEND chunk, which ends the image. */
    private void writeEnd() {
        writeChunk("IEND", new byte[0]);
    }

    private void writeChunk(String type, byte[] data) {
        var typeBytes = type.getBytes(StandardCharsets.US_ASCII);

        writeIntBigEndian(data.length);
        stream.writeBytes(typeBytes);
        stream.writeBytes(data);

        var crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeIntBigEndian((int) crc.getValue());
    }

    private void writeIntBigEndian(int value) {
        stream.write(value >> 24);
        stream.write(value >> 16);
        stream.write(value >> 8);
        stream.write(value);
    }

    /**
     * Compresses the data into a zlib stream (as the PNG compression method requires), including
     * the zlib header and the Adler-32 checksum.
     */
    private static byte[] deflate(byte[] data) {
        var deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {  // NOSONAR Deflater is not an AutoCloseable in JDK 17
            deflater.setInput(data);
            deflater.finish();

            var compressed = new ByteArrayOutputStream(data.length / 8 + 64);
            var buffer = new byte[8192];
            while (!deflater.finished()) {
                compressed.write(buffer, 0, deflater.deflate(buffer));
            }
            return compressed.toByteArray();
        } finally {
            deflater.end();
        }
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.testsupport;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.awt.image.BufferedImage;

/**
 * Reads a QR code back out of a rendered image, with ZXing.
 * <p>
 * The other ZXing cross-checks in this project hand the decoder a matrix of modules. This one goes
 * through the image instead, so ZXing's detector has to find the symbol first: it therefore covers
 * what the matrix tests cannot &mdash; that the renderers place the modules and the quiet zone
 * where a scanner expects them, at the scale and colors asked for.
 * </p>
 * <p>
 * It lives in its own package because the tests of the AWT bridge are in a package of their own.
 * </p>
 */
public final class QrImageDecoder {

    private QrImageDecoder() {
        // non-instantiable
    }

    /**
     * Decodes the QR code in the specified image.
     *
     * @param image the image
     * @return the decoded text
     * @throws AssertionError if the image holds no readable QR code
     */
    public static String decode(BufferedImage image) {
        var bitmap = new BinaryBitmap(new HybridBinarizer(new ImageLuminanceSource(image)));
        try {
            return new QRCodeReader().decode(bitmap).getText();
        } catch (NotFoundException e) {
            throw new AssertionError("no QR code was found in the image", e);
        } catch (Exception e) {
            throw new AssertionError("the QR code in the image could not be decoded", e);
        }
    }

    /**
     * The grayscale view of an image that ZXing's binarizer works on.
     * <p>
     * ZXing ships one of these for AWT images, but only in its {@code javase} artifact, which this
     * project does not otherwise need.
     * </p>
     */
    private static final class ImageLuminanceSource extends LuminanceSource {

        private final byte[] luminances;

        ImageLuminanceSource(BufferedImage image) {
            super(image.getWidth(), image.getHeight());

            luminances = new byte[image.getWidth() * image.getHeight()];
            for (var y = 0; y < image.getHeight(); y += 1) {
                for (var x = 0; x < image.getWidth(); x += 1) {
                    var rgb = image.getRGB(x, y);
                    var red = (rgb >> 16) & 0xff;
                    var green = (rgb >> 8) & 0xff;
                    var blue = rgb & 0xff;
                    // ZXing's own weighting: (R + 2G + B) / 4
                    luminances[y * image.getWidth() + x] = (byte) ((red + 2 * green + blue) / 4);
                }
            }
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            var width = getWidth();
            if (row == null || row.length < width) {
                row = new byte[width];
            }
            System.arraycopy(luminances, y * width, row, 0, width);
            return row;
        }

        @Override
        public byte[] getMatrix() {
            return luminances;
        }
    }
}

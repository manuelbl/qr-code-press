/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.examples.awtdrawing;

import net.codecrete.qrcodepress.Ecc;
import net.codecrete.qrcodepress.QrCode;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Creates a QR code and draws it using round modules, a diagonal color gradient and an image in the center.
 */
public class AwtDrawing {

    private static final String TEXT = "https://en.wikipedia.org/wiki/QR_code";

    // Emoji image, as a classpath resource path
    private static final String EMOJI_RESOURCE = "/emoji.png";

    // Module grid size (in pixels)
    private static final float MODULE_SIZE = 15f;
    // Diameter of circle (in pixels)
    private static final float CIRCLE_DIAMETER = 14f;
    // Border size (as multiple of MODULE_SIZE)
    private static final float BORDER_SIZE = 4f;
    // Number of modules (in each direction) to omit in the center for image overlay.
    // Use an odd size since all QR codes have an odd number of modules.
    // Otherwise, the omitted area is made slightly bigger.
    private static final int OMITTED_CENTER_MODULES = 7;
    // Color in top left corner
    private static final Color TOP_LEFT_COLOR = new Color(227, 113, 52, 255);
    // Color in bottom right corner
    private static final Color BOTTOM_RIGHT_COLOR = new Color(29, 168, 71, 255);

    /**
     * Runs the example.
     *
     * @param args ignored
     * @throws IOException if the PNG file cannot be written
     */
    public static void main(String[] args) throws IOException {
        var qrCode = QrCode.encodeText(TEXT, Ecc.QUARTILE);
        var size = qrCode.getSize();

        var imageExtent = (int) Math.ceil((size + 2 * BORDER_SIZE) * MODULE_SIZE);
        var offscreenImage = new BufferedImage(imageExtent, imageExtent, BufferedImage.TYPE_INT_ARGB);

        var graphics2d = offscreenImage.createGraphics();
        graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // omitted center square (in modules), enlarged by one if the parities of size and omitted area differ
        var centerStart = (size - OMITTED_CENTER_MODULES) / 2;
        var centerEnd = (size + OMITTED_CENTER_MODULES + 1) / 2;

        for (var x = 0; x < size; x++) {
            for (var y = 0; y < size; y++) {
                if (!qrCode.getModule(x, y))
                    continue;

                // skip finder in three corners
                if ((x < 7 && y < 7) || (x < 7 && y >= qrCode.getSize() - 7) || (x >= qrCode.getSize() - 7 && y < 7))
                    continue;

                // skip omitted space in center (for image)
                if (x >= centerStart && x < centerEnd && y >= centerStart && y < centerEnd)
                    continue;

                // Projection of the module center onto the diagonal from the top left
                // to the bottom right corner of the QR code (border excluded)
                var ratio = (x + y + 1f) / (2f * size);
                graphics2d.setColor(mixedColor(TOP_LEFT_COLOR, BOTTOM_RIGHT_COLOR, ratio));

                graphics2d.fill(new Ellipse2D.Float(
                        (BORDER_SIZE + x + 0.5f) * MODULE_SIZE - CIRCLE_DIAMETER / 2f,
                        (BORDER_SIZE + y + 0.5f) * MODULE_SIZE - CIRCLE_DIAMETER / 2f,
                        CIRCLE_DIAMETER, CIRCLE_DIAMETER));
            }
        }

        // draw finders with rounded corners
        graphics2d.setColor(TOP_LEFT_COLOR);
        drawFinder(graphics2d, 0, 0);
        graphics2d.setColor(mixedColor(TOP_LEFT_COLOR, BOTTOM_RIGHT_COLOR, 0.5f));
        drawFinder(graphics2d, 0, qrCode.getSize() - 7);
        drawFinder(graphics2d, qrCode.getSize() - 7, 0);

        // draw emoji into the omitted center square
        var emojiOffset = Math.round((BORDER_SIZE + centerStart) * MODULE_SIZE);
        var emojiExtent = Math.round((centerEnd - centerStart) * MODULE_SIZE);
        graphics2d.drawImage(readEmoji(), emojiOffset, emojiOffset, emojiExtent, emojiExtent, null);

        // save as PNG
        var filepath = new File("qrcode.png");
        ImageIO.write(offscreenImage, "png", filepath);
        System.out.println("QR code saved at: " + filepath.getAbsolutePath());
    }

    private static void drawFinder(Graphics2D graphics2D, int x, int y) {
        var path = new Path2D.Double();
        path.setWindingRule(Path2D.WIND_EVEN_ODD);
        path.append(new RoundRectangle2D.Float(
                (BORDER_SIZE + x) * MODULE_SIZE,
                (BORDER_SIZE + y) * MODULE_SIZE,
                7 * MODULE_SIZE, 7 * MODULE_SIZE,
                4 * MODULE_SIZE, 4 * MODULE_SIZE), false);
        path.append(new RoundRectangle2D.Float(
                (BORDER_SIZE + x + 1) * MODULE_SIZE,
                (BORDER_SIZE + y + 1) * MODULE_SIZE,
                5 * MODULE_SIZE, 5 * MODULE_SIZE,
                2 * MODULE_SIZE, 2 * MODULE_SIZE), false);
        path.append(new Ellipse2D.Float(
                (BORDER_SIZE + x + 2) * MODULE_SIZE,
                (BORDER_SIZE + y + 2) * MODULE_SIZE,
                3 * MODULE_SIZE, 3 * MODULE_SIZE), false);
        graphics2D.fill(path);
    }

    /**
     * Reads the emoji image from the classpath.
     *
     * @return the image
     * @throws IOException if the image cannot be read
     */
    private static BufferedImage readEmoji() throws IOException {
        try (var stream = AwtDrawing.class.getResourceAsStream(EMOJI_RESOURCE)) {
            if (stream == null)
                throw new IOException("Resource not found: " + EMOJI_RESOURCE);
            return ImageIO.read(stream);
        }
    }

    private static Color mixedColor(Color c1, Color c2, float ratio) {
        var red = Math.round(c1.getRed() * (1 - ratio) + c2.getRed() * ratio);
        var green = Math.round(c1.getGreen() * (1 - ratio) + c2.getGreen() * ratio);
        var blue = Math.round(c1.getBlue() * (1 - ratio) + c2.getBlue() * ratio);
        return new Color(red, green, blue);
    }
}

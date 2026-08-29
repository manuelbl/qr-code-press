/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.awt;

import net.codecrete.qrcodepress.Ecc;
import net.codecrete.qrcodepress.QrCode;
import net.codecrete.qrcodepress.testsupport.QrImageDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * The optional AWT bridge.
 * <p>
 * It is tested in its own package because that is where it lives: the library's core does not need
 * {@code java.desktop} and this is the only part that does.
 * </p>
 */
class QrCodeGraphicsTest {

    private static final String TEXT = "Hello, world!";
    private static final Color FOREGROUND = new Color(0x17, 0x1c, 0x80);
    private static final Color BACKGROUND = new Color(0xcc, 0xbc, 0x9b);

    private final QrCode qrCode = QrCode.encodeText(TEXT, Ecc.MEDIUM);

    @Nested
    @DisplayName("toBufferedImage")
    class ToBufferedImage {

        @ParameterizedTest
        @CsvSource({ "0, 1", "4, 3", "2, 10" })
        @DisplayName("paints each module as a scale-by-scale block of the right color")
        void paintsTheModules(int border, int scale) {
            var image = QrCodeGraphics.toBufferedImage(qrCode, border, scale,
                    FOREGROUND, BACKGROUND);

            var imageSize = (qrCode.getSize() + 2 * border) * scale;
            assertThat(image.getWidth()).isEqualTo(imageSize);
            assertThat(image.getHeight()).isEqualTo(imageSize);
            for (var y = 0; y < imageSize; y += 1) {
                for (var x = 0; x < imageSize; x += 1) {
                    var dark = qrCode.getModule(x / scale - border, y / scale - border);
                    assertThat(new Color(image.getRGB(x, y), true))
                            .withFailMessage("pixel (%d, %d) should be %s", x, y,
                                    dark ? "dark" : "light")
                            .isEqualTo(dark ? FOREGROUND : BACKGROUND);
                }
            }
        }

        @Test
        @DisplayName("defaults to black on white")
        void defaultsToBlackOnWhite() {
            var image = QrCodeGraphics.toBufferedImage(qrCode, 4, 2);

            assertThat(image.getRGB(8, 8)).isEqualTo(Color.BLACK.getRGB()); // the finder pattern
            assertThat(image.getRGB(0, 0)).isEqualTo(Color.WHITE.getRGB()); // the border
        }

        @Test
        @DisplayName("uses one bit per pixel and a two-color palette")
        void usesAnIndexedImage() {
            var image = QrCodeGraphics.toBufferedImage(qrCode, 4, 2);

            assertThat(image.getType()).isEqualTo(BufferedImage.TYPE_BYTE_BINARY);
            assertThat(image.getColorModel().getPixelSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("keeps a transparent background transparent")
        void keepsTransparency() {
            var transparent = new Color(0, 0, 0, 0);

            var image = QrCodeGraphics.toBufferedImage(qrCode, 4, 2, FOREGROUND, transparent);

            assertThat(image.getColorModel().hasAlpha()).isTrue();
            assertThat(new Color(image.getRGB(0, 0), true).getAlpha()).isZero();
            assertThat(new Color(image.getRGB(8, 8), true)).isEqualTo(FOREGROUND);
        }

        @Test
        @DisplayName("produces an image a scanner reads back")
        void staysDecodable() {
            var image = QrCodeGraphics.toBufferedImage(qrCode, 4, 4, FOREGROUND, BACKGROUND);

            assertThat(QrImageDecoder.decode(image)).isEqualTo(TEXT);
        }
    }

    @Nested
    @DisplayName("draw")
    class Draw {

        @ParameterizedTest
        @CsvSource({ "0, 1", "4, 3", "3, 2.5" })
        @DisplayName("fills the background and then the dark modules")
        void fillsBackgroundAndModules(double border, double scale) {
            // Drawn into a plain RGB image, which composites, so that the pixels prove the
            // rectangles went to the right place rather than that a palette was set up.
            var imageSize = (int) Math.ceil((qrCode.getSize() + 2 * border) * scale);
            var image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();

            QrCodeGraphics.draw(qrCode, graphics, border, scale, FOREGROUND, BACKGROUND);
            graphics.dispose();

            // sample the middle of each module, away from the edges anti-aliasing may touch
            for (var moduleY = 0; moduleY < qrCode.getSize(); moduleY += 1) {
                for (var moduleX = 0; moduleX < qrCode.getSize(); moduleX += 1) {
                    var x = (int) ((moduleX + border + 0.5) * scale);
                    var y = (int) ((moduleY + border + 0.5) * scale);
                    var dark = qrCode.getModule(moduleX, moduleY);
                    assertThat(new Color(image.getRGB(x, y)))
                            .withFailMessage("module (%d, %d) should be %s", moduleX, moduleY,
                                    dark ? "dark" : "light")
                            .isEqualTo(dark ? FOREGROUND : BACKGROUND);
                }
            }
        }

        @Test
        @DisplayName("leaves the context alone where the background is transparent")
        void skipsATransparentBackground() {
            var image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 40, 40);

            QrCodeGraphics.draw(qrCode, graphics, 0, 1, FOREGROUND, new Color(0, 0, 0, 0));
            graphics.dispose();

            assertThat(new Color(image.getRGB(30, 30))).isEqualTo(Color.RED); // outside the code
            assertThat(new Color(image.getRGB(1, 1))).isEqualTo(Color.RED); // a light module
            assertThat(new Color(image.getRGB(0, 0))).isEqualTo(FOREGROUND); // a dark module
        }

        @Test
        @DisplayName("restores the paint of the context")
        void restoresThePaint() {
            var image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setPaint(Color.RED);

            QrCodeGraphics.draw(qrCode, graphics, 0, 1);

            assertThat(graphics.getPaint()).isEqualTo(Color.RED);
            graphics.dispose();
        }

        @Test
        @DisplayName("draws at the origin, so the caller places it by transforming")
        void drawsAtTheOrigin() {
            var image = new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();

            graphics.translate(10, 20);
            QrCodeGraphics.draw(qrCode, graphics, 0, 1, FOREGROUND, BACKGROUND);
            graphics.dispose();

            assertThat(new Color(image.getRGB(9, 20))).isEqualTo(Color.BLACK); // untouched
            assertThat(new Color(image.getRGB(10, 20))).isEqualTo(FOREGROUND); // the finder pattern
        }
    }

    @Nested
    @DisplayName("arguments")
    class ArgumentChecks {

        @Test
        @DisplayName("a negative border is rejected")
        void rejectsNegativeBorder() {
            var graphics = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QrCodeGraphics.toBufferedImage(qrCode, -1, 1))
                    .withMessageContaining("border");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QrCodeGraphics.draw(qrCode, graphics, -1, 1))
                    .withMessageContaining("border");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QrCodeGraphics.draw(qrCode, graphics, Double.NaN, 1))
                    .withMessageContaining("border");
        }

        @Test
        @DisplayName("a scale that would draw nothing is rejected")
        void rejectsNonPositiveScale() {
            var graphics = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QrCodeGraphics.toBufferedImage(qrCode, 0, 0))
                    .withMessageContaining("scale");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QrCodeGraphics.draw(qrCode, graphics, 0, 0))
                    .withMessageContaining("scale");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QrCodeGraphics.draw(qrCode, graphics, 0, Double.NaN))
                    .withMessageContaining("scale");
        }

        @Test
        @DisplayName("null arguments are rejected")
        void rejectsNull() {
            var graphics = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();

            assertThatNullPointerException()
                    .isThrownBy(() -> QrCodeGraphics.toBufferedImage(null, 0, 1));
            assertThatNullPointerException()
                    .isThrownBy(() -> QrCodeGraphics.toBufferedImage(qrCode, 0, 1, null, Color.WHITE));
            assertThatNullPointerException()
                    .isThrownBy(() -> QrCodeGraphics.toBufferedImage(qrCode, 0, 1, Color.BLACK, null));
            assertThatNullPointerException()
                    .isThrownBy(() -> QrCodeGraphics.draw(qrCode, null, 0, 1));
            assertThatNullPointerException()
                    .isThrownBy(() -> QrCodeGraphics.draw(null, graphics, 0, 1));
        }
    }
}

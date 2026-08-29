/**
 * Rendering of QR codes with AWT.
 * <p>
 * {@code QrCodeGraphics} turns a QR code into a {@code BufferedImage} or draws it into a
 * {@code Graphics2D}, for callers already working with AWT or Swing. This package is the only part
 * of the library that needs {@code java.desktop}; it is not needed to create an image, as
 * {@code QrCode.toPng} writes a PNG on its own.
 * </p>
 */
package net.codecrete.qrcodepress.awt;

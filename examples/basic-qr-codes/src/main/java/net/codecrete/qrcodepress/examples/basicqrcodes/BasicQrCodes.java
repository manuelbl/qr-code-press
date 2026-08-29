/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.examples.basicqrcodes;

import net.codecrete.qrcodepress.Ecc;
import net.codecrete.qrcodepress.Eci;
import net.codecrete.qrcodepress.QrCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders a text too long for a single QR code as a row of Structured Append QR codes in one SVG file.
 * <p>
 * The library returns the sequence as a list of {@link QrCode} instances and each QR code as a
 * graphics path in module coordinates. Turning that into a single document is the job of this
 * example: every QR code is placed in its own group, scaled from module units to millimetres, and
 * translated along the row.
 * </p>
 */
public class BasicQrCodes {

    /**
     * Runs the example.
     *
     * @param args ignored
     * @throws IOException if the SVG file cannot be written
     */
    public static void main(String[] args) throws IOException {

        // Create a QR code and write it to an SVG file
        var qrCode = QrCode.encodeText("Hello, world!", Ecc.MEDIUM);
        var filename = Path.of("hello-world.svg");
        Files.writeString(filename, qrCode.toSvgString(4));
        System.out.printf("Wrote QR code to %s%n",  filename);

        // Create a QR code and write it to an PNG file
        qrCode = QrCode.encodeText("Hello, world!", Ecc.MEDIUM);
        filename = Path.of("hello-world.png");
        Files.write(filename, qrCode.toPng(4, 8));
        System.out.printf("Wrote QR code to %s%n",  filename);

        // Create a QR code with digits only.
        // A more compact representation will automatically be chosen (3.33 bits per digit)
        qrCode = QrCode.encodeText("27182818284590452353602874713526624977572470936999595749669676277240766", Ecc.MEDIUM);
        filename = Path.of("digits.svg");
        Files.writeString(filename, qrCode.toSvgString(4));
        System.out.printf("Wrote QR code to %s%n",  filename);

        // Create a QR code with uppercase letters only.
        // For an alphanumeric subset of characters (not including lower-case letters),
        // a more compact representation will be automatically chosen (5.5 bits per character)
        qrCode = QrCode.encodeText("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG", Ecc.HIGH);
        filename = Path.of("letters.svg");
        Files.writeString(filename, qrCode.toSvgString(4));
        System.out.printf("Wrote QR code to %s%n",  filename);

        // Moderately large QR code
        var text = """
                I was a Flower of the mountain yes when I put the rose in my hair like the Andalusian girls used
                or shall I wear a red yes and how he kissed me under the Moorish wall and I thought well as well
                him as another and then I asked him with my eyes to ask again yes and then he asked me would I
                yes to say yes my mountain flower and first I put my arms around him yes and drew him down to me
                so he could feel my breasts all perfume yes and his heart was going like mad and yes I said yes
                I will Yes.
                """;
        qrCode = QrCode.encodeText(text, Ecc.MEDIUM);
        filename = Path.of("joyce.svg");
        Files.writeString(filename, qrCode.toSvgString(4));
        System.out.printf("Wrote QR code to %s%n",  filename);

        // The full Unicode character set is supported.
        // By default, the library uses UTF-8 encoding and indicates this with an ECI designator.
        qrCode = QrCode.encodeText("🎲 😇 🤒 🏌 ⏭ 🚍", Ecc.MEDIUM);
        filename = Path.of("emojis.svg");
        Files.writeString(filename, qrCode.toSvgString(4));
        System.out.printf("Wrote QR code to %s%n",  filename);

        // Use to QrCode builder to suppress the ECI designator.
        // Most QR code readers will correctly guess the encoding.
        // Some readers always ignore the ECI designator.
        qrCode = QrCode.builder()
                .text("🎲 😇 🤒 🏌 ⏭ 🚍")
                .eci(Eci.NONE, StandardCharsets.UTF_8)
                .errorCorrection(Ecc.MEDIUM)
                .build();
        filename = Path.of("emojis-no-eci.svg");
        Files.writeString(filename, qrCode.toSvgString(4));
        System.out.printf("Wrote QR code to %s%n",  filename);

        // Create a QR code with binary data
        qrCode = QrCode.encodeBinary(new byte[] {
                (byte) 0x47, (byte) 0x49, (byte) 0x46, (byte) 0x38, (byte) 0x39, (byte) 0x61, (byte) 0x01, (byte) 0x00,
                (byte) 0x01, (byte) 0x00, (byte) 0x80, (byte) 0x01, (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x21, (byte) 0xf9, (byte) 0x04, (byte) 0x01, (byte) 0x0a,
                (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x2c, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x02, (byte) 0x4c,
                (byte) 0x01, (byte) 0x00, (byte) 0x3b
        }, Ecc.QUARTILE);
        filename = Path.of("binary-data.svg");
        Files.writeString(filename, qrCode.toSvgString(4));
        System.out.printf("Wrote QR code to %s%n",  filename);

        // Create a QR code with dark blue modules/pixels (SVG)
        qrCode = QrCode.encodeText("Dark blue modules", Ecc.MEDIUM);
        filename = Path.of("blue-modules.svg");
        Files.writeString(filename, qrCode.toSvgString(4, "navy", "white"));
        System.out.printf("Wrote QR code to %s%n",  filename);

        // Create a QR code with dark green modules/pixels (PNG)
        qrCode = QrCode.encodeText("Dark green modules", Ecc.MEDIUM);
        filename = Path.of("green-modules.png");
        Files.write(filename, qrCode.toPng(4, 8, 0x005500, 0xFFFFFF));
        System.out.printf("Wrote QR code to %s%n",  filename);
    }
}

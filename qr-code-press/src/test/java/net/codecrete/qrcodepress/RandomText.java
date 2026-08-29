/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.Random;

/**
 * Generates the long texts the Structured Append tests split across QR codes.
 * <p>
 * The texts are random but reproducible: {@link Random} is specified down to the bit, so a seed
 * yields the same text on every JVM, and the tests can pin the exact number of QR codes a text is
 * split into.
 * </p>
 */
final class RandomText {

    /** The characters QR codes encode in alphanumeric mode. */
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    private RandomText() {
        // non-instantiable
    }

    /**
     * Generates a text of alphanumeric characters.
     * <p>
     * Such a text encodes in alphanumeric mode throughout and survives ISO-8859-1, so a sequence
     * built from it carries no ECI segments.
     * </p>
     *
     * @param length the number of characters
     * @param seed   the seed of the generator
     * @return the text
     */
    static String alphanumeric(int length, long seed) {
        var random = new Random(seed);
        var chars = new char[length];
        for (var i = 0; i < length; i += 1)
            chars[i] = ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length()));
        return new String(chars);
    }

    /**
     * Generates a text of Cyrillic letters.
     * <p>
     * Such a text needs a character set beyond ISO-8859-1, and every one of its characters is a
     * single byte in ISO-8859-5 and two bytes in UTF-8, which is what makes the two encodings of
     * the same text comparable.
     * </p>
     *
     * @param length the number of characters
     * @param seed   the seed of the generator
     * @return the text
     */
    @SuppressWarnings("SameParameterValue")
    static String cyrillic(int length, long seed) {
        var random = new Random(seed);
        var chars = new char[length];
        for (var i = 0; i < length; i += 1)
            chars[i] = (char) (0x0410 + random.nextInt(0x40)); // Cyrillic А-я
        return new String(chars);
    }

    /**
     * Generates a text mixing alphabets in short runs.
     * <p>
     * The runs are what makes the text interesting: they force the payload into segments of all
     * modes, and the emoji among them are four UTF-8 bytes each, so a split that ignores character
     * boundaries is bound to land inside one.
     * </p>
     *
     * @param length the number of characters, counting an emoji as one
     * @param seed   the seed of the generator
     * @return the text
     */
    static String mixed(int length, long seed) {
        var random = new Random(seed);
        var text = new StringBuilder(length);

        var count = 0;
        while (count < length) {
            var alphabet = random.nextInt(4);
            var runLength = Math.min(length - count, 1 + random.nextInt(alphabet == 0 ? 19 : 2));
            for (var i = 0; i < runLength; i += 1) {
                switch (alphabet) {
                    case 0 -> text.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
                    case 1 -> text.append((char) (0x20 + random.nextInt(0x5e))); // basic Latin
                    case 2 -> text.append((char) (0xc0 + random.nextInt(0x0f))); // Latin-1 supplement
                    default -> text.appendCodePoint(0x1f600 + random.nextInt(8)); // emoticons
                }
            }
            count += runLength;
        }

        return text.toString();
    }
}

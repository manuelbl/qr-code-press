/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

/**
 * ECI (Extended Channel Interpretation) designator, specifying the character encoding of the data
 * that follows it.
 * <p>
 * Without an ECI designator, the encoding is ISO-8859-1 (Latin-1) according to the QR code
 * specification. Many QR code scanners, however, either analyze the data and guess the encoding or
 * assume UTF-8.
 * </p>
 * <p>
 * The designators with a well-known meaning are available as constants; {@link #of(int)} creates
 * any other one. {@link #NONE} and {@link #AUTOMATIC} are not designators but instructions to the
 * encoder; see their documentation.
 * </p>
 * <p>
 * Instances are immutable and safe to share between threads.
 * </p>
 */
public final class Eci {

    /** The value of {@link #NONE}. */
    static final int NONE_VALUE = -2;

    /** The value of {@link #AUTOMATIC}. */
    static final int AUTOMATIC_VALUE = -1;

    /** The highest ECI value that can be encoded in a QR code. */
    static final int MAX_VALUE = 999999;

    /**
     * No ECI designator.
     * <p>
     * Not a valid ECI value. Passed to the encoder, it means that no ECI designator is to be added
     * and that the data is to be taken as is.
     * </p>
     */
    public static final Eci NONE = new Eci(NONE_VALUE);

    /**
     * Automatic ECI selection.
     * <p>
     * Not a valid ECI value. Passed to the encoder, it means that the encoding and the matching ECI
     * designator are to be selected automatically: ISO-8859-1 without a designator if the text fits
     * that encoding, UTF-8 with a designator otherwise.
     * </p>
     */
    public static final Eci AUTOMATIC = new Eci(AUTOMATIC_VALUE);

    /**
     * Code page 437 encoding.
     * <p>
     * Also known as CP437, OEM-US, OEM 437, PC-8 or MS-DOS Latin US.
     * </p>
     */
    public static final Eci CODE_PAGE_437 = new Eci(2);

    /** Latin-1 / ISO/IEC 8859-1 encoding (Western European). */
    public static final Eci LATIN_1 = new Eci(3);

    /** Latin-1 / ISO/IEC 8859-1 encoding (Western European). */
    public static final Eci ISO_8859_1 = LATIN_1;

    /** Latin-2 / ISO/IEC 8859-2 encoding (Central European). */
    public static final Eci LATIN_2 = new Eci(4);

    /** Latin-2 / ISO/IEC 8859-2 encoding (Central European). */
    public static final Eci ISO_8859_2 = LATIN_2;

    /** Latin-3 / ISO/IEC 8859-3 encoding. */
    public static final Eci LATIN_3 = new Eci(5);

    /** Latin-3 / ISO/IEC 8859-3 encoding. */
    public static final Eci ISO_8859_3 = LATIN_3;

    /** Latin-4 / ISO/IEC 8859-4 encoding (Baltic). */
    public static final Eci LATIN_4 = new Eci(6);

    /** Latin-4 / ISO/IEC 8859-4 encoding (Baltic). */
    public static final Eci ISO_8859_4 = LATIN_4;

    /** Latin/Cyrillic / ISO/IEC 8859-5 encoding. */
    public static final Eci LATIN_CYRILLIC = new Eci(7);

    /** Latin/Cyrillic / ISO/IEC 8859-5 encoding. */
    public static final Eci ISO_8859_5 = LATIN_CYRILLIC;

    /** Latin/Arabic / ISO/IEC 8859-6 encoding. */
    public static final Eci LATIN_ARABIC = new Eci(8);

    /** Latin/Arabic / ISO/IEC 8859-6 encoding. */
    public static final Eci ISO_8859_6 = LATIN_ARABIC;

    /** Latin/Greek / ISO/IEC 8859-7 encoding. */
    public static final Eci LATIN_GREEK = new Eci(9);

    /** Latin/Greek / ISO/IEC 8859-7 encoding. */
    public static final Eci ISO_8859_7 = LATIN_GREEK;

    /** Latin/Hebrew / ISO/IEC 8859-8 encoding. */
    public static final Eci LATIN_HEBREW = new Eci(10);

    /** Latin/Hebrew / ISO/IEC 8859-8 encoding. */
    public static final Eci ISO_8859_8 = LATIN_HEBREW;

    /** Latin-5 / ISO/IEC 8859-9 encoding (Turkish). */
    public static final Eci LATIN_5 = new Eci(11);

    /** Latin-5 / ISO/IEC 8859-9 encoding (Turkish). */
    public static final Eci ISO_8859_9 = LATIN_5;

    /** Latin-6 / ISO/IEC 8859-10 encoding (Nordic). */
    public static final Eci LATIN_6 = new Eci(12);

    /** Latin-6 / ISO/IEC 8859-10 encoding (Nordic). */
    public static final Eci ISO_8859_10 = LATIN_6;

    /** Latin/Thai / ISO/IEC 8859-11 encoding. */
    public static final Eci LATIN_THAI = new Eci(13);

    /** Latin/Thai / ISO/IEC 8859-11 encoding. */
    public static final Eci ISO_8859_11 = LATIN_THAI;

    /** Latin-7 / ISO/IEC 8859-13 encoding (Baltic Rim). */
    public static final Eci LATIN_7 = new Eci(15);

    /** Latin-7 / ISO/IEC 8859-13 encoding (Baltic Rim). */
    public static final Eci ISO_8859_13 = LATIN_7;

    /** Latin-8 / ISO/IEC 8859-14 encoding (Celtic). */
    public static final Eci LATIN_8 = new Eci(16);

    /** Latin-8 / ISO/IEC 8859-14 encoding (Celtic). */
    public static final Eci ISO_8859_14 = LATIN_8;

    /** Latin-9 / ISO/IEC 8859-15 encoding. */
    public static final Eci LATIN_9 = new Eci(17);

    /** Latin-9 / ISO/IEC 8859-15 encoding. */
    public static final Eci ISO_8859_15 = LATIN_9;

    /** Latin-10 / ISO/IEC 8859-16 encoding (Southeastern European). */
    public static final Eci LATIN_10 = new Eci(18);

    /** Latin-10 / ISO/IEC 8859-16 encoding (Southeastern European). */
    public static final Eci ISO_8859_16 = LATIN_10;

    /**
     * Shift JIS encoding.
     * <p>
     * Also known as SJIS encoding. This is the encoding Kanji mode is built for; see
     * {@link KanjiStrategy}.
     * </p>
     */
    public static final Eci SHIFT_JIS = new Eci(20);

    /** Windows-1250 encoding (Central European). */
    public static final Eci WINDOWS_1250 = new Eci(21);

    /** Windows-1251 encoding (Cyrillic). */
    public static final Eci WINDOWS_1251 = new Eci(22);

    /** Windows-1252 encoding (Western European). */
    public static final Eci WINDOWS_1252 = new Eci(23);

    /** Windows-1256 encoding (Arabic). */
    public static final Eci WINDOWS_1256 = new Eci(24);

    /** Unicode UTF-16 big endian encoding. */
    public static final Eci UTF_16BE = new Eci(25);

    /** Unicode UTF-8 encoding. */
    public static final Eci UTF_8 = new Eci(26);

    /** US-ASCII encoding. */
    public static final Eci US_ASCII = new Eci(27);

    /** Big5 encoding (Traditional Chinese). */
    public static final Eci BIG5 = new Eci(28);

    /** GB/T 2312 encoding (Simplified Chinese). */
    public static final Eci GB2312 = new Eci(29);

    /** KS X 1001 encoding (Korean). */
    public static final Eci KS_X_1001 = new Eci(30);

    /** GBK encoding (Simplified Chinese). */
    public static final Eci GBK = new Eci(31);

    /** GB 18030 encoding (Chinese). */
    public static final Eci GB18030 = new Eci(32);

    /** Unicode UTF-16 little endian encoding. */
    public static final Eci UTF_16LE = new Eci(33);

    /** Unicode UTF-32 big endian encoding. */
    public static final Eci UTF_32BE = new Eci(34);

    /** Unicode UTF-32 little endian encoding. */
    public static final Eci UTF_32LE = new Eci(35);

    /** ISO/IEC 646 invariant set encoding. */
    public static final Eci ISO_646_INV = new Eci(170);

    /**
     * Binary 8-bit data.
     * <p>
     * Not a character encoding but an indication that the data is binary.
     * </p>
     */
    public static final Eci BINARY_DATA = new Eci(899);

    private final int value;

    /**
     * The resolved character set, or {@code null} if it has not been resolved yet.
     * <p>
     * Resolution is idempotent and the result is immutable and safely published through its own
     * final fields, so the unsynchronized read/write of this field is benign: a racing thread
     * either sees {@code null} and resolves again, or sees a fully constructed character set.
     * </p>
     */
    private Charset charset;

    private Eci(int value) {
        this.value = value;
    }

    /**
     * Returns the ECI designator with the specified value.
     *
     * @param value the ECI value (0&ndash;999999)
     * @return the ECI designator
     * @throws IllegalArgumentException if the value is out of range
     */
    public static Eci of(int value) {
        if (value < 0 || value > MAX_VALUE)
            throw new IllegalArgumentException("ECI value must be between 0 and " + MAX_VALUE + ", got " + value);
        return new Eci(value);
    }

    /**
     * Returns the numeric ECI designator value.
     * <p>
     * {@link #NONE} and {@link #AUTOMATIC} return negative values, as they are not designators.
     * </p>
     *
     * @return the value
     */
    public int getValue() {
        return value;
    }

    /**
     * Returns the character set associated with this ECI designator.
     * <p>
     * Only US-ASCII, ISO-8859-1, UTF-8 and the UTF-16 variants are guaranteed to be present in
     * every Java runtime. All others live in the {@code jdk.charsets} module, which minimal
     * runtimes (custom {@code jlink} images in particular) may omit. Two further designators,
     * {@link #LATIN_6} and {@link #LATIN_8}, have no counterpart in the JDK at all.
     * </p>
     *
     * @return the character set
     * @throws EciException if the designator is not associated with a character set, or if the
     *                      character set is not available in this Java runtime
     */
    public Charset getCharset() {
        var resolved = charset;
        if (resolved == null) {
            resolved = resolveCharset();
            charset = resolved;
        }
        return resolved;
    }

    private Charset resolveCharset() {
        var name = value >= 0 && value < CHARSET_NAMES.length ? CHARSET_NAMES[value] : null;
        if (name == null)
            throw new EciException("Unsupported ECI value " + value
                    + ": it is not associated with a character set.");

        try {
            return Charset.forName(name);
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            throw new EciException("Unsupported ECI value " + value + ": the associated character set \""
                    + name + "\" is not available in this Java runtime"
                    + " (it requires the jdk.charsets module).");
        }
    }

    /**
     * The character set names, indexed by ECI value.
     * <p>
     * The names are the ones the JDK understands, which are not always the ones the ECI designator
     * is called after. {@code null} marks the values in the range that have no character set.
     * </p>
     */
    private static final String[] CHARSET_NAMES = {
            "IBM437",       // 0  - CP437
            "ISO-8859-1",   // 1  - Latin-1 / ISO/IEC 8859-1
            "IBM437",       // 2  - CP437
            "ISO-8859-1",   // 3  - Latin-1 / ISO/IEC 8859-1
            "ISO-8859-2",   // 4  - Latin-2 / ISO/IEC 8859-2
            "ISO-8859-3",   // 5  - Latin-3 / ISO/IEC 8859-3
            "ISO-8859-4",   // 6  - Latin-4 / ISO/IEC 8859-4
            "ISO-8859-5",   // 7  - Latin/Cyrillic / ISO/IEC 8859-5
            "ISO-8859-6",   // 8  - Latin/Arabic / ISO/IEC 8859-6
            "ISO-8859-7",   // 9  - Latin/Greek / ISO/IEC 8859-7
            "ISO-8859-8",   // 10 - Latin/Hebrew / ISO/IEC 8859-8
            "ISO-8859-9",   // 11 - Latin-5 / ISO/IEC 8859-9
            "ISO-8859-10",  // 12 - Latin-6 / ISO/IEC 8859-10 (not provided by the JDK)
            "ISO-8859-11",  // 13 - Latin/Thai / ISO/IEC 8859-11 (JDK name: x-iso-8859-11)
            null,           // 14
            "ISO-8859-13",  // 15 - Latin-7 / ISO/IEC 8859-13
            "ISO-8859-14",  // 16 - Latin-8/Celtic / ISO/IEC 8859-14 (not provided by the JDK)
            "ISO-8859-15",  // 17 - Latin-9 / ISO/IEC 8859-15
            "ISO-8859-16",  // 18 - Latin-10 / ISO/IEC 8859-16
            null,           // 19
            "Shift_JIS",    // 20 - Shift JIS
            "windows-1250", // 21 - Windows-1250
            "windows-1251", // 22 - Windows-1251
            "windows-1252", // 23 - Windows-1252
            "windows-1256", // 24 - Windows-1256
            "UTF-16BE",     // 25 - UTF-16BE
            "UTF-8",        // 26 - UTF-8
            "US-ASCII",     // 27 - US-ASCII
            "Big5",         // 28 - Big-5
            "GB2312",       // 29 - GB/T 2312
            "EUC-KR",       // 30 - KS X 1001
            "GBK",          // 31 - GBK
            "GB18030",      // 32 - GB 18030
            "UTF-16LE",     // 33 - UTF-16LE
            "UTF-32BE",     // 34 - UTF-32BE
            "UTF-32LE"      // 35 - UTF-32LE
    };

    /**
     * Determines whether the specified object is an ECI designator with the same value.
     *
     * @param obj the object to compare with
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof Eci other && value == other.value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        return switch (value) {
            case NONE_VALUE -> "Eci.NONE";
            case AUTOMATIC_VALUE -> "Eci.AUTOMATIC";
            default -> "Eci(" + value + ")";
        };
    }
}

/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.profiling;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;

import net.codecrete.qrcodepress.Ecc;
import net.codecrete.qrcodepress.QrCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * The QR code libraries being compared, each behind the one call that turns a text into a module
 * matrix.
 * <p>
 * Every library is called the way its own documentation calls it, and every call stops at the
 * matrix: no border, no scaling, no image. Anything above that would measure a rendering policy
 * rather than an encoder, and the three libraries draw that line in different places.
 * </p>
 * <p>
 * Two differences between the libraries are left in place, because they are what each library does
 * rather than something the harness chose:
 * </p>
 * <ul>
 *   <li>QR Code Press and qrcodegen raise the error correction level when a higher level fits the
 *   same version; ZXing does not.</li>
 *   <li>The three disagree on the ECI segment, in both directions. QR Code Press adds one only when
 *   ISO-8859-1 would be lossy; ZXing adds one to every byte-mode payload, for the reason given at
 *   {@link #ZXING}; qrcodegen never adds one at all, see {@link #NAYUKI}.</li>
 * </ul>
 */
enum Library {

    /** This library, through {@link QrCode#encodeText}. */
    PRESS("press", "net.codecrete.qrcodepress:qr-code-press") {
        @Override
        int encode(String payload, Ecc ecc) {
            return QrCode.encodeText(payload, ecc).getSize();
        }
    },

    /**
     * Nayuki's <a href="https://central.sonatype.com/artifact/io.nayuki/qrcodegen">qrcodegen</a>,
     * which needs no configuration to carry these payloads but declares none of them.
     * <p>
     * Its {@code encodeText} encodes byte mode as UTF-8 and emits no ECI segment, while
     * ISO/IEC 18004 reads byte mode without one as ISO-8859-1. That is harmless only while the text
     * is pure ASCII, where the two encodings agree byte for byte; for anything else, including a
     * merely accented name, a reader that trusts the declaration instead of guessing the character
     * set is entitled to return mojibake.
     * </p>
     */
    NAYUKI("nayuki", "io.nayuki:qrcodegen") {
        @Override
        int encode(String payload, Ecc ecc) {
            return io.nayuki.qrcodegen.QrCode.encodeText(payload, NAYUKI_LEVELS.get(ecc)).size;
        }
    },

    /**
     * ZXing's <a href="https://github.com/zxing/zxing">core</a>, forced to UTF-8.
     * <p>
     * Without the character set hint, ZXing encodes byte mode as ISO-8859-1 and silently replaces
     * whatever does not fit, so 62 of the 200 sample payloads decode back as something other than
     * what was encoded. The hint is therefore not a tuning choice but the condition for comparing
     * like with like.
     * </p>
     * <p>
     * Once the hint is set, ZXing appends the ECI header to <em>every</em> byte-mode payload, also
     * to those ISO-8859-1 would have carried. Its one path that adds ECI selectively,
     * {@code QR_COMPACT}, is unusable: {@code MinimalEncoder} throws on payloads containing
     * surrogate pairs.
     * </p>
     */
    ZXING("zxing", "com.google.zxing:core") {
        @Override
        int encode(String payload, Ecc ecc) {
            try {
                return Encoder.encode(payload, ZXING_LEVELS.get(ecc), ZXING_HINTS).getMatrix().getWidth();
            } catch (WriterException e) {
                // Not a way of continuing past a failure: the harness has no answer to a library
                // that cannot encode a payload, and an encode wrapped in a `catch` would no longer
                // be the thing being measured.
                throw new IllegalStateException("zxing failed to encode: " + payload, e);
            }
        }
    };

    private static final Map<Ecc, io.nayuki.qrcodegen.QrCode.Ecc> NAYUKI_LEVELS = new EnumMap<>(Map.of(
            Ecc.LOW, io.nayuki.qrcodegen.QrCode.Ecc.LOW,
            Ecc.MEDIUM, io.nayuki.qrcodegen.QrCode.Ecc.MEDIUM,
            Ecc.QUARTILE, io.nayuki.qrcodegen.QrCode.Ecc.QUARTILE,
            Ecc.HIGH, io.nayuki.qrcodegen.QrCode.Ecc.HIGH));

    private static final Map<Ecc, ErrorCorrectionLevel> ZXING_LEVELS = new EnumMap<>(Map.of(
            Ecc.LOW, ErrorCorrectionLevel.L,
            Ecc.MEDIUM, ErrorCorrectionLevel.M,
            Ecc.QUARTILE, ErrorCorrectionLevel.Q,
            Ecc.HIGH, ErrorCorrectionLevel.H));

    private static final Map<EncodeHintType, Object> ZXING_HINTS =
            Map.of(EncodeHintType.CHARACTER_SET, "UTF-8");

    /** Versions of the three libraries, filtered into the resource from the pom at build time. */
    private static final Properties VERSIONS = loadVersions();

    private final String label;

    private final String coordinates;

    Library(String label, String coordinates) {
        this.label = label;
        this.coordinates = coordinates;
    }

    /**
     * Returns the name this library goes by on the command line and in the reports.
     *
     * @return the label
     */
    String label() {
        return label;
    }

    /**
     * Returns the Maven coordinates including the version, e.g. {@code io.nayuki:qrcodegen:1.8.0}.
     *
     * @return the coordinates
     */
    String coordinates() {
        return coordinates + ":" + VERSIONS.getProperty(label, "unknown");
    }

    /**
     * Encodes the payload and returns the size of the resulting matrix.
     * <p>
     * The size is what every caller consumes: as a checksum it keeps the encode from being
     * optimized away, and summed over the workload it says how much matrix each library produced
     * for the same input.
     * </p>
     *
     * @param payload the text to encode
     * @param ecc the error correction level, translated to this library's own terms
     * @return the width of the matrix, in modules
     */
    abstract int encode(String payload, Ecc ecc);

    /**
     * Returns the labels of every library, comma-separated, in the form JMH's {@code -p library=}
     * option takes.
     *
     * @return the labels
     */
    static String allLabels() {
        return Arrays.stream(values()).map(Library::label).collect(Collectors.joining(","));
    }

    /**
     * Returns the library going by the given label.
     *
     * @param label the label, as returned by {@link #label()}
     * @return the library
     * @throws IllegalArgumentException if no library goes by that label
     */
    static Library byLabel(String label) {
        var normalized = label.toLowerCase(Locale.ROOT);
        for (var library : values()) {
            if (library.label.equals(normalized))
                return library;
        }
        throw new IllegalArgumentException("Unknown library '" + label + "'.");
    }

    private static Properties loadVersions() {
        var properties = new Properties();
        try (var stream = Library.class.getResourceAsStream("/libraries.properties")) {
            if (stream != null)
                properties.load(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}

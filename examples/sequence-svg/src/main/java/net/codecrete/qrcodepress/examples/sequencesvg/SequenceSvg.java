/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.examples.sequencesvg;

import net.codecrete.qrcodepress.Ecc;
import net.codecrete.qrcodepress.QrCode;
import net.codecrete.qrcodepress.QrCodeSequence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Renders a text too long for a single QR code as a row of Structured Append QR codes in one SVG file.
 * <p>
 * The library returns the sequence as a list of {@link QrCode} instances and each QR code as a
 * graphics path in module coordinates. Turning that into a single document is the job of this
 * example: every QR code is placed in its own group, scaled from module units to millimetres, and
 * translated along the row.
 * </p>
 */
public class SequenceSvg {

    /** The rendered width and height of every QR code, in millimetres. */
    private static final double CODE_SIZE_MM = 60.0;

    /** The space between two QR codes and around the whole row, in millimetres. */
    private static final double SPACING_MM = 20.0;

    /** The colour of the dark modules. */
    private static final String FOREGROUND = "#000000";

    /** The colour of the light modules and of the space around the QR codes. */
    private static final String BACKGROUND = "#ffffff";

    /** The file the SVG document is written to. */
    private static final Path OUTPUT_FILE = Path.of("qr-code-sequence.svg");

    /**
     * The payload: a text far too long for a single QR code.
     * <p>
     * The opening of Letter 1 of <i>Frankenstein; or, The Modern Prometheus</i> by Mary Shelley,
     * published in 1818. Taken from the Project Gutenberg edition
     * (<a href="https://www.gutenberg.org/ebooks/84">ebook 84</a>).
     * </p>
     */
    private static final String TEXT = """
            To Mrs. Saville, England.

            St. Petersburgh, Dec. 11th, 17—.

            You will rejoice to hear that no disaster has accompanied the commencement of an enterprise which
            you have regarded with such evil forebodings. I arrived here yesterday, and my first task is to
            assure my dear sister of my welfare and increasing confidence in the success of my undertaking.

            I am already far north of London, and as I walk in the streets of Petersburgh, I feel a cold
            northern breeze play upon my cheeks, which braces my nerves and fills me with delight. Do you
            understand this feeling? This breeze, which has travelled from the regions towards which I am
            advancing, gives me a foretaste of those icy climes. Inspirited by this wind of promise, my
            daydreams become more fervent and vivid. I try in vain to be persuaded that the pole is the seat of
            frost and desolation; it ever presents itself to my imagination as the region of beauty and delight.
            There, Margaret, the sun is for ever visible, its broad disk just skirting the horizon and diffusing
            a perpetual splendour. There—for with your leave, my sister, I will put some trust in preceding
            navigators—there snow and frost are banished; and, sailing over a calm sea, we may be wafted to a
            land surpassing in wonders and in beauty every region hitherto discovered on the habitable globe.
            Its productions and features may be without example, as the phenomena of the heavenly bodies
            undoubtedly are in those undiscovered solitudes. What may not be expected in a country of eternal
            light? I may there discover the wondrous power which attracts the needle and may regulate a thousand
            celestial observations that require only this voyage to render their seeming eccentricities
            consistent for ever. I shall satiate my ardent curiosity with the sight of a part of the world never
            before visited, and may tread a land never before imprinted by the foot of man. These are my
            enticements, and they are sufficient to conquer all fear of danger or death and to induce me to
            commence this laborious voyage with the joy a child feels when he embarks in a little boat, with his
            holiday mates, on an expedition of discovery up his native river. But supposing all these
            conjectures to be false, you cannot contest the inestimable benefit which I shall confer on all
            mankind, to the last generation, by discovering a passage near the pole to those countries, to reach
            which at present so many months are requisite; or by ascertaining the secret of the magnet, which,
            if at all possible, can only be effected by an undertaking such as mine.
            """;

    /**
     * Runs the example.
     *
     * @param args ignored
     * @throws IOException if the SVG file cannot be written
     */
    public static void main(String[] args) throws IOException {
        var qrCodes = QrCodeSequence.builder()
                .text(TEXT)
                .errorCorrection(Ecc.MEDIUM)
                .versionRange(8, 20)
                .build();

        Files.writeString(OUTPUT_FILE, toSvg(qrCodes), StandardCharsets.UTF_8);

        System.out.printf("Wrote %d QR codes (version %d) to %s%n",
                qrCodes.size(), qrCodes.get(0).getVersion(), OUTPUT_FILE.toAbsolutePath());
    }

    /**
     * Creates an SVG document placing the QR codes in a single horizontal row.
     * <p>
     * The QR codes of a sequence all use the same version and are therefore of the same size in
     * modules. They are nevertheless scaled individually, so that the document keeps its
     * dimensions no matter which version the library picks for the text at hand.
     * </p>
     *
     * @param qrCodes the QR codes, in sequence order
     * @return the SVG document
     */
    private static String toSvg(List<QrCode> qrCodes) {
        var width = qrCodes.size() * CODE_SIZE_MM + (qrCodes.size() + 1) * SPACING_MM;
        var height = CODE_SIZE_MM + 2 * SPACING_MM;

        var svg = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\"")
                .append(" width=\"").append(format(width)).append("mm\"")
                .append(" height=\"").append(format(height)).append("mm\"")
                .append(" viewBox=\"0 0 ").append(format(width)).append(' ').append(format(height))
                .append("\" stroke=\"none\">\n")
                .append("\t<rect width=\"100%\" height=\"100%\" fill=\"").append(BACKGROUND).append("\"/>\n");

        for (var i = 0; i < qrCodes.size(); i += 1) {
            var qrCode = qrCodes.get(i);

            // The graphics path uses module coordinates, with no border of its own: the quiet zone
            // around each QR code is the spacing of the row.
            var scale = CODE_SIZE_MM / qrCode.getSize();
            var x = SPACING_MM + i * (CODE_SIZE_MM + SPACING_MM);

            svg.append("\t<g transform=\"translate(").append(format(x)).append(',').append(format(SPACING_MM))
                    .append(") scale(").append(format(scale)).append(")\">\n")
                    .append("\t\t<path d=\"").append(qrCode.toGraphicsPath(0))
                    .append("\" fill=\"").append(FOREGROUND).append("\"/>\n")
                    .append("\t</g>\n");
        }

        return svg.append("</svg>\n").toString();
    }

    /**
     * Formats a number for the SVG document.
     * <p>
     * The root locale is used so that the decimal separator is a point on every machine; a comma
     * would separate the number into two.
     * </p>
     *
     * @param value the number
     * @return the formatted number
     */
    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}

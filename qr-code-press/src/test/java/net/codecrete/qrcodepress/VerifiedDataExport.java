/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Writes the verified test data in {@code src/test/resources/verified/}.
 * <p>
 * This class is the counterpart of {@link VerifiedData}: one writes the files, the other reads them.
 * It is a development tool rather than a test, run through {@link VerifiedDataExportTest}, which also
 * asserts that what it writes is byte for byte what is committed.
 * </p>
 *
 * <h2>Inputs and outputs</h2>
 * <p>
 * The two directories say which is which, with no exceptions to remember. Everything under
 * {@code input/} is hand-written and only ever read: {@code texts.tsv} and the payload texts it
 * names, {@code cases.tsv}, and {@code sequences.tsv}. Everything under {@code verified/} is written
 * by this class and must never be edited by hand: {@code texts.tsv}, {@code qrcodes.tsv} and
 * {@code qrcodes/}, {@code compaction.tsv}, {@code codewords.tsv}, {@code fixedpatterns.tsv} and
 * its sample dumps, {@code maskpatterns.tsv} and its sample dumps, and
 * {@code structuredappend.tsv}.
 * </p>
 * <p>
 * A manifest and the directory of dumps that belongs to it share a name: {@code X.tsv} describes
 * every case, {@code X/} dumps the sampled ones in full.
 * </p>
 *
 * <h2>Two conventions worth knowing</h2>
 * <p>
 * <b>Light modules are {@code .}, not a space.</b> Trailing whitespace does not survive editors,
 * git hooks or diff tools, and a silently trimmed row is a baffling test failure.
 * </p>
 * <p>
 * <b>Matrix hashes cover the rendered rows, not the raw storage.</b> The library must stay free to
 * change its internal storage layout without invalidating the fixtures.
 * </p>
 *
 * <h2>Why hashes plus samples</h2>
 * <p>
 * Full dumps of all 40 versions &times; 3 fixed-pattern matrices, plus 320 mask patterns, would add
 * tens of megabytes. Hashes give complete coverage compactly; the sampled full dumps make a failure
 * debuggable rather than merely red.
 * </p>
 *
 * @see VerifiedData
 * @see VerifiedDataExportTest
 */
final class VerifiedDataExport {

    /** The directory the hand-written inputs are read from, relative to the module directory. */
    static final Path INPUT_DIR = Path.of("src/test/resources/input");

    /** The directory the verified data is written to, relative to the module directory. */
    static final Path VERIFIED_DIR = Path.of("src/test/resources/verified");

    /**
     * The version the payload texts are segmented for.
     */
    static final int SEGMENT_VERSION = 20;

    /**
     * Versions dumped in full. Chosen for structural transitions: 1 (no alignment patterns),
     * 2 (first alignment pattern), 6/7 (version information appears at 7), 10 and 26/27
     * (count-indicator width changes), 40 (largest).
     * <p>
     * It is the exporter's choice, so it is stated here and only read by {@link VerifiedData}.
     * </p>
     */
    static final int[] SAMPLE_VERSIONS = { 1, 2, 6, 7, 10, 26, 27, 40 };

    /**
     * Count-indicator width boundaries: compaction cost depends on which band the version is in.
     * Both ends of each band are dumped, so an off-by-one in a band boundary cannot slip through.
     */
    private static final int[] COMPACTION_VERSIONS = { 1, 9, 10, 26, 27, 40 };

    private static final HexFormat HEX = HexFormat.of();

    private VerifiedDataExport() {
        // non-instantiable
    }

    /**
     * Writes the verified data.
     *
     * @param outputDir the directory to write to, which is the verified directory itself when
     *                  regenerating and a temporary directory when verifying
     */
    static void export(Path outputDir) {
        if (!Files.isDirectory(INPUT_DIR.resolve("texts")))
            throw new IllegalStateException("inputs not found at " + INPUT_DIR.toAbsolutePath()
                    + " — run this from the qr-code-press module directory");

        var texts = readTexts();
        var segments = exportTexts(outputDir, texts);
        var cases = readCases();

        exportCases(outputDir, cases, segments);
        exportCompaction(outputDir, texts);
        exportCodewords(outputDir, cases, segments);
        exportFixedPatterns(outputDir);
        exportMaskPatterns(outputDir);
        exportStructuredAppend(outputDir);
    }

    // region Inputs

    /**
     * Reads a hand-written input file, without its comment lines and empty lines.
     *
     * @param name the file name, relative to the input directory
     * @return the rows, each split into its columns
     */
    private static List<String[]> readInputTsv(String name) {
        return readString(INPUT_DIR.resolve(name)).lines()
                .filter(line -> !line.startsWith("#") && !line.isEmpty())
                .map(line -> line.split("\t", -1))
                .toList();
    }

    /**
     * One of the payload texts, with the ECI it is encoded with and thus the charset its bytes are
     * in.
     * <p>
     * The ECI is a hand-made choice about a text rather than anything the library decides, so it is
     * stated next to the text in {@code input/texts.tsv}. Both the exported segments and the
     * compaction fixture read it from there, so the two cannot disagree about a text.
     * </p>
     *
     * @param text the text
     * @param eci  the ECI
     */
    private record PayloadText(String text, Eci eci) {

        /** Returns the charset the text is encoded as, UTF-8 where the ECI leaves it open. */
        Charset charset() {
            return Eci.AUTOMATIC.equals(eci) ? StandardCharsets.UTF_8 : eci.getCharset();
        }
    }

    /** Reads the payload texts and their ECIs, which are inputs rather than outputs. */
    private static List<PayloadText> readTexts() {
        var texts = new ArrayList<PayloadText>();
        for (var row : readInputTsv("texts.tsv")) {
            if (Integer.parseInt(row[0]) != texts.size())
                throw new IllegalStateException("input/texts.tsv: expected text index " + texts.size()
                        + " but found " + row[0]);
            texts.add(new PayloadText(readString(INPUT_DIR.resolve(row[1])), parseEci(row[2])));
        }
        return texts;
    }

    /** Parses an ECI designator of the text manifest, {@code auto} or a numeric value. */
    private static Eci parseEci(String designator) {
        return "auto".equals(designator) ? Eci.AUTOMATIC : Eci.of(Integer.parseInt(designator));
    }

    /**
     * One of the case inputs: the parameters the library is driven with.
     *
     * @param index        the case index, which also names the file of expected modules
     * @param textIndex    the index of the payload text
     * @param requestedEcc the requested error correction level
     * @param minVersion   the smallest acceptable version
     * @param maxVersion   the largest acceptable version
     * @param boostEcc     whether the error correction level may be raised
     */
    private record CaseInput(int index, int textIndex, Ecc requestedEcc, int minVersion, int maxVersion,
            boolean boostEcc) {
    }

    /**
     * Reads the case inputs.
     */
    private static List<CaseInput> readCases() {
        return readInputTsv("cases.tsv").stream()
                .map(row -> new CaseInput(Integer.parseInt(row[0]), Integer.parseInt(row[1]),
                        Ecc.valueOf(row[2]), Integer.parseInt(row[3]), Integer.parseInt(row[4]),
                        Boolean.parseBoolean(row[5])))
                .toList();
    }

    // endregion

    // region Payload texts

    /**
     * Segments every payload text and writes the manifest of what each one measures.
     * <p>
     * The counts are the library's reading of the input files rather than a restatement of them, so
     * a text mangled by a line-ending conversion or a stray byte-order mark shows up here.
     * </p>
     */
    private static List<List<DataSegment>> exportTexts(Path outputDir, List<PayloadText> texts) {
        var segments = new ArrayList<List<DataSegment>>(texts.size());
        var out = new StringBuilder("# textIndex\tcharCount\tutf8ByteCount\n");

        for (var i = 0; i < texts.size(); i += 1) {
            var text = texts.get(i).text();
            segments.add(DataSegment.fromText(text, texts.get(i).eci(), null, SEGMENT_VERSION,
                    KanjiStrategy.AUTOMATIC));

            out.append(i).append('\t')
                    .append(text.length()).append('\t')
                    .append(text.getBytes(StandardCharsets.UTF_8).length).append('\n');
        }

        writeText(outputDir.resolve("texts.tsv"), out.toString());
        return segments;
    }

    // endregion

    // region Verified cases

    /** Writes the case manifest and the expected modules of every case. */
    private static void exportCases(Path outputDir, List<CaseInput> cases, List<List<DataSegment>> segments) {
        var out = new StringBuilder("# case\ttextIndex\trequestedEcc\tminVersion\tmaxVersion\tboostEcl"
                + "\teffectiveEcc\teffectiveVersion\teffectiveMask\n");

        for (var testCase : cases) {
            var qrCode = encode(testCase, segments);

            out.append(testCase.index()).append('\t')
                    .append(testCase.textIndex()).append('\t')
                    .append(testCase.requestedEcc().name()).append('\t')
                    .append(testCase.minVersion()).append('\t')
                    .append(testCase.maxVersion()).append('\t')
                    .append(testCase.boostEcc()).append('\t')
                    .append(qrCode.getErrorCorrectionLevel().name()).append('\t')
                    .append(qrCode.getVersion()).append('\t')
                    .append(qrCode.getMask()).append('\n');

            writeLines(outputDir.resolve(String.format("qrcodes/%04d.txt", testCase.index())),
                    TestMatrices.rowsOf(qrCode));
        }

        writeText(outputDir.resolve("qrcodes.tsv"), out.toString());
    }

    /** Builds the QR code of a case. */
    private static QrCode encode(CaseInput testCase, List<List<DataSegment>> segments) {
        return QrCode.builder()
                .segments(segments.get(testCase.textIndex()))
                .errorCorrection(testCase.requestedEcc())
                .versionRange(testCase.minVersion(), testCase.maxVersion())
                .boostErrorCorrection(testCase.boostEcc())
                .build();
    }

    // endregion

    // region Stage fixtures

    /**
     * Writes the segment compaction of every payload text at both ends of every count-indicator
     * band.
     * <p>
     * The text is compacted as the bytes of the charset its ECI declares — UTF-8 where there is
     * none. The encoding is stated in {@code input/texts.tsv} rather than chosen by the library, so
     * the fixture exercises compaction alone and not the text-encoding selection. Kanji mode is
     * only ever reached from Shift-JIS bytes, which is why the charset has to follow the text.
     * </p>
     * <p>
     * Text 0 is empty and yields no rows at all.
     * </p>
     */
    private static void exportCompaction(Path outputDir, List<PayloadText> texts) {
        var out = new StringBuilder("# textIndex\tversion\tinputHex\tsegmentIndex\tmode\tdataLength\tdataHex\n");

        for (var t = 0; t < texts.size(); t += 1) {
            var bytes = texts.get(t).text().getBytes(texts.get(t).charset());
            var inputHex = HEX.formatHex(bytes);

            for (var version : COMPACTION_VERSIONS) {
                var compacted = SegmentCompaction.buildSegments(ByteSlice.of(bytes), version, true);
                for (var s = 0; s < compacted.size(); s += 1) {
                    var segment = compacted.get(s);
                    out.append(t).append('\t')
                            .append(version).append('\t')
                            .append(inputHex).append('\t')
                            .append(s).append('\t')
                            .append(segment.getMode().name()).append('\t')
                            .append(segment.getDataLength()).append('\t')
                            .append(HEX.formatHex(SegmentTestSupport.dataOf(segment))).append('\n');
                }
            }
        }

        writeText(outputDir.resolve("compaction.tsv"), out.toString());
    }

    /**
     * Writes the data codewords (segments, terminator and padding) and the final interleaved
     * codewords with error correction, per case.
     */
    private static void exportCodewords(Path outputDir, List<CaseInput> cases, List<List<DataSegment>> segments) {
        var out = new StringBuilder("# case\tversion\tecc\tdataCodewordsHex\tfinalCodewordsHex\n");

        for (var testCase : cases) {
            var qrCode = encode(testCase, segments);
            var version = qrCode.getVersion();
            var ecc = qrCode.getErrorCorrectionLevel().ordinal();

            var data = Codewords.buildData(segments.get(testCase.textIndex()), version, ecc);
            var interleaved = Codewords.addErrorCorrection(data, version, ecc);

            out.append(testCase.index()).append('\t')
                    .append(version).append('\t')
                    .append(ecc).append('\t')
                    .append(HEX.formatHex(data)).append('\t')
                    .append(HEX.formatHex(interleaved)).append('\n');
        }

        writeText(outputDir.resolve("codewords.tsv"), out.toString());
    }

    /**
     * Writes the fixed patterns of every version: hashes for all 40, plus full dumps for the
     * structurally interesting ones.
     */
    private static void exportFixedPatterns(Path outputDir) {
        var out = new StringBuilder("# version\tsize\tdrawnSha256\treservedSha256\tpayloadAreaSha256"
                + "\tdrawnPopCount\treservedPopCount\tpayloadAreaPopCount\n");

        for (var version = QrCodeParameters.MIN_VERSION; version <= QrCodeParameters.MAX_VERSION; version += 1) {
            var patterns = FixedPatterns.build(version);
            var drawn = patterns.drawn();
            var reserved = patterns.reserved();
            var payloadArea = FixedPatterns.payloadAreaMap(version);

            out.append(version).append('\t')
                    .append(drawn.size()).append('\t')
                    .append(TestMatrices.sha256(drawn)).append('\t')
                    .append(TestMatrices.sha256(reserved)).append('\t')
                    .append(TestMatrices.sha256(payloadArea)).append('\t')
                    .append(drawn.popCount()).append('\t')
                    .append(reserved.popCount()).append('\t')
                    .append(payloadArea.popCount()).append('\n');

            if (isSampleVersion(version)) {
                var prefix = String.format("fixedpatterns/v%02d-", version);
                writeLines(outputDir.resolve(prefix + "drawn.txt"), TestMatrices.rowsOf(drawn));
                writeLines(outputDir.resolve(prefix + "reserved.txt"), TestMatrices.rowsOf(reserved));
                writeLines(outputDir.resolve(prefix + "payloadarea.txt"), TestMatrices.rowsOf(payloadArea));
            }
        }

        writeText(outputDir.resolve("fixedpatterns.tsv"), out.toString());
    }

    /**
     * Writes the mask patterns, already restricted to the payload area, which is how
     * {@link MatrixEncoder} uses them.
     */
    private static void exportMaskPatterns(Path outputDir) {
        var out = new StringBuilder("# version\tpattern\tsha256\tpopCount\n");

        for (var version = QrCodeParameters.MIN_VERSION; version <= QrCodeParameters.MAX_VERSION; version += 1) {
            for (var pattern = 0; pattern < MatrixEncoder.PATTERN_COUNT; pattern += 1) {
                var mask = MatrixEncoder.maskPair(pattern, version).rows();

                out.append(version).append('\t')
                        .append(pattern).append('\t')
                        .append(TestMatrices.sha256(mask)).append('\t')
                        .append(mask.popCount()).append('\n');

                if (isSampleVersion(version))
                    writeLines(outputDir.resolve(String.format("maskpatterns/v%02d-m%d.txt", version, pattern)),
                            TestMatrices.rowsOf(mask));
            }
        }

        writeText(outputDir.resolve("maskpatterns.tsv"), out.toString());
    }

    /** Returns whether the specified version is dumped in full. */
    private static boolean isSampleVersion(int version) {
        for (var sample : SAMPLE_VERSIONS) {
            if (sample == version)
                return true;
        }
        return false;
    }

    // endregion

    // region Structured append

    /**
     * Reads the sequence cases, stated in {@code input/sequences.tsv}: a generator, a length and a
     * seed for the text, and the parameters the split is driven with.
     * <p>
     * A row whose length is {@code search} is a boundary case, expanded in place into the longest
     * text that still fits into the 16 QR codes of a sequence and the next length up, which does
     * not. Every other row yields exactly one case.
     * </p>
     */
    private static List<StructuredAppendCase> readSequenceCases() {
        var cases = new ArrayList<StructuredAppendCase>();

        for (var row : readInputTsv("sequences.tsv")) {
            var sequenceCase = StructuredAppendCase.fromInputRow(row);
            if (StructuredAppendCase.SEARCHED_LENGTH.equals(row[1]))
                cases.addAll(boundaryPair(sequenceCase));
            else
                cases.add(sequenceCase);
        }

        return cases;
    }

    /**
     * Writes the balanced split of every sequence case: the shared version, the number of QR codes,
     * and the payload bytes each QR code carries.
     */
    private static void exportStructuredAppend(Path outputDir) {
        var cases = readSequenceCases();

        var out = new StringBuilder("# case\tgenerator\tlength\tseed\teci\tminVersion\tmaxVersion\tecc"
                + "\toutcome\tversion\tcodeCount\tpayloadLengths\n");

        for (var i = 0; i < cases.size(); i += 1) {
            var sequenceCase = cases.get(i);
            out.append(i).append('\t')
                    .append(sequenceCase.generator()).append('\t')
                    .append(sequenceCase.length()).append('\t')
                    .append(sequenceCase.seed()).append('\t')
                    .append(sequenceCase.eci()).append('\t')
                    .append(sequenceCase.minVersion()).append('\t')
                    .append(sequenceCase.maxVersion()).append('\t')
                    .append(sequenceCase.ecc().name()).append('\t');

            try {
                var sequence = build(sequenceCase);
                var lengths = StructuredAppendCase.payloadLengths(sequence.codes());
                var joined = new StringBuilder();
                for (var length : lengths)
                    joined.append(joined.isEmpty() ? "" : ",").append(length);

                out.append("ok").append('\t')
                        .append(sequence.version()).append('\t')
                        .append(sequence.codes().size()).append('\t')
                        .append(joined).append('\n');
            } catch (DataTooLongException e) {
                out.append("toolong").append('\t').append(-1).append('\t').append(-1).append('\t')
                        .append('-').append('\n');
            }
        }

        writeText(outputDir.resolve("structuredappend.tsv"), out.toString());
    }

    /** Splits the payload of a sequence case over a sequence of QR codes. */
    private static StructuredAppend.Sequence build(StructuredAppendCase sequenceCase) {
        return StructuredAppend.build(sequenceCase.encoded(), sequenceCase.minVersion(),
                sequenceCase.maxVersion(), sequenceCase.ecc().ordinal());
    }

    /** Returns whether the payload of a sequence case fits into the 16 QR codes of a sequence. */
    private static boolean fits(StructuredAppendCase sequenceCase) {
        try {
            build(sequenceCase);
            return true;
        } catch (DataTooLongException e) {
            return false;
        }
    }

    /**
     * Expands a boundary case into the longest text that still fits into 16 QR codes and the next
     * length up, which does not.
     * <p>
     * The search assumes nothing about monotonicity: whatever length it lands on is verified from
     * both sides, and a case that fails to yield such a pair stops the export rather than writing a
     * row that pins nothing.
     * </p>
     */
    private static List<StructuredAppendCase> boundaryPair(StructuredAppendCase template) {
        var lo = 64;
        var hi = 40000;

        while (lo < hi) {
            var mid = lo + (hi - lo + 1) / 2;
            if (fits(template.withLength(mid)))
                lo = mid;
            else
                hi = mid - 1;
        }

        var fitting = template.withLength(lo);
        var overflowing = template.withLength(lo + 1);
        if (!fits(fitting) || fits(overflowing))
            throw new IllegalStateException(
                    "boundary search: no crossing at length " + lo + " for seed " + template.seed());

        return List.of(fitting, overflowing);
    }

    // endregion

    // region Output helpers

    /** Reads a UTF-8 file. */
    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    /**
     * Writes a UTF-8 file without a byte-order mark.
     * <p>
     * Line endings are LF and the content ends with one, unconditionally: the files must be
     * byte-identical regardless of the platform the export ran on.
     * </p>
     */
    private static void writeText(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + path, e);
        }
    }

    /** Writes lines as a UTF-8 file, LF separated and LF terminated. */
    private static void writeLines(Path path, List<String> lines) {
        writeText(path, String.join("\n", lines) + "\n");
    }

    // endregion
}

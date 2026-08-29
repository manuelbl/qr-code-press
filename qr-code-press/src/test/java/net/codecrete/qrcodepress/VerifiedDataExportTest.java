/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that {@link VerifiedDataExport} reproduces the committed verified data byte for byte.
 * <p>
 * The data set is a frozen baseline: an export is expected to be a no-op, and any difference is a
 * change in the library's behaviour rather than a fixture that needs refreshing. This test is what
 * keeps the exporter honest — without it the exporter could drift from the data it supposedly
 * produced and nothing would notice, since both live in this repository now.
 * </p>
 * <p>
 * The comparison is the whole of {@code verified/}, with nothing subtracted: the exporter's inputs
 * live in {@code input/} and never reach it. A file appearing under {@code verified/} that the export
 * does not produce is therefore a finding rather than something to add to an exception list.
 * </p>
 * <p>
 * Setting the system property {@code verified.export.write} regenerates the data in place instead of
 * verifying it, and reports which files changed:
 * </p>
 * <pre>./mvnw test -Dtest=VerifiedDataExportTest -Dverified.export.write=true</pre>
 * <p>
 * That is the only way to regenerate, deliberately: the write path is the very code path this test
 * verifies, so the two cannot rot apart.
 * </p>
 *
 * @see VerifiedDataExport
 */
class VerifiedDataExportTest {

    /** Set this system property to regenerate the verified data instead of verifying it. */
    private static final String WRITE_PROPERTY = "verified.export.write";

    /** The number of characters of a differing line that are reported. */
    private static final int EXCERPT_LENGTH = 120;

    @Test
    @DisplayName("reproduces the committed verified data byte for byte")
    void reproducesTheCommittedVerifiedData(@TempDir Path tempDir) {
        VerifiedDataExport.export(tempDir);

        var differences = compare(tempDir);

        if (Boolean.getBoolean(WRITE_PROPERTY)) {
            write(tempDir, differences);
            return;
        }

        assertThat(differences).as("differences from the committed verified data%n%s",
                String.join(System.lineSeparator(), differences)).isEmpty();
    }

    // region Comparison

    /**
     * Compares an export against the committed verified data, in both directions.
     *
     * @param exported the directory the export was written to
     * @return one description per difference, empty if the two are identical
     */
    private static List<String> compare(Path exported) {
        var actual = relativeFiles(exported);
        var expected = committedOutputs();

        var differences = new ArrayList<String>();

        for (var name : expected) {
            if (!actual.contains(name))
                differences.add(name + ": committed but not produced by the export");
        }
        for (var name : actual) {
            if (!expected.contains(name))
                differences.add(name + ": produced by the export but not committed");
        }
        for (var name : actual) {
            if (expected.contains(name))
                difference(name, VerifiedDataExport.VERIFIED_DIR.resolve(name), exported.resolve(name))
                        .ifPresent(differences::add);
        }

        return differences;
    }

    /**
     * Describes how two files differ, by the first line that does.
     *
     * @param name     the name of the file, for the message
     * @param expected the committed file
     * @param actual   the exported file
     * @return the description, or empty if the two are byte-identical
     */
    private static Optional<String> difference(String name, Path expected, Path actual) {
        var expectedBytes = readBytes(expected);
        var actualBytes = readBytes(actual);
        if (Arrays.equals(expectedBytes, actualBytes))
            return Optional.empty();

        var expectedLines = lines(expectedBytes);
        var actualLines = lines(actualBytes);

        for (var i = 0; i < Math.min(expectedLines.size(), actualLines.size()); i += 1) {
            if (!expectedLines.get(i).equals(actualLines.get(i)))
                return Optional.of(String.format("%s: line %d differs%n  committed: %s%n  exported:  %s",
                        name, i + 1, excerpt(expectedLines.get(i)), excerpt(actualLines.get(i))));
        }

        if (expectedLines.size() != actualLines.size())
            return Optional.of(String.format("%s: %d committed lines, %d exported lines",
                    name, expectedLines.size(), actualLines.size()));

        // Same lines, different bytes: a line ending or the trailing newline.
        return Optional.of(String.format("%s: same lines but %d committed bytes and %d exported bytes"
                + " — a line ending or the trailing newline differs", name, expectedBytes.length, actualBytes.length));
    }

    /** Returns the names of the committed files the exporter is responsible for. */
    private static TreeSet<String> committedOutputs() {
        var verified = VerifiedDataExport.VERIFIED_DIR;
        if (!Files.isDirectory(verified))
            throw new IllegalStateException("verified data not found at " + verified.toAbsolutePath()
                    + " — this test must run with the qr-code-press module directory as its working directory");

        return relativeFiles(verified);
    }

    /** Returns the paths of all files below a directory, relative to it and slash-separated. */
    private static TreeSet<String> relativeFiles(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> directory.relativize(path).toString().replace('\\', '/'))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + directory, e);
        }
    }

    /** Splits file content into lines, without swallowing a missing trailing newline. */
    private static List<String> lines(byte[] content) {
        return new String(content, StandardCharsets.UTF_8).lines().toList();
    }

    /** Shortens a line so that a failure message stays readable. */
    private static String excerpt(String line) {
        return line.length() <= EXCERPT_LENGTH ? line : line.substring(0, EXCERPT_LENGTH) + "… (" + line.length()
                + " characters)";
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    // endregion

    // region Write mode

    /**
     * Copies an export over the committed verified data and reports what changed.
     * <p>
     * A file that is committed but no longer produced is reported and left alone: removing it is a
     * deliberate act, not something a regeneration should do behind the back of whoever ran it.
     * </p>
     *
     * @param exported    the directory the export was written to
     * @param differences the differences found, for the report
     */
    private static void write(Path exported, List<String> differences) {
        if (differences.isEmpty()) {
            System.out.println("verified data regenerated: no files changed");
            return;
        }

        var changed = new ArrayList<String>();
        var stale = new ArrayList<String>();

        for (var name : relativeFiles(exported)) {
            var target = VerifiedDataExport.VERIFIED_DIR.resolve(name);
            if (Files.exists(target) && Arrays.equals(readBytes(target), readBytes(exported.resolve(name))))
                continue;
            try {
                Files.createDirectories(target.getParent());
                Files.copy(exported.resolve(name), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot write " + target, e);
            }
            changed.add(name);
        }

        var produced = relativeFiles(exported);
        for (var name : committedOutputs()) {
            if (!produced.contains(name))
                stale.add(name);
        }

        System.out.println("verified data regenerated: " + changed.size() + " file(s) changed");
        changed.forEach(name -> System.out.println("  changed: " + name));
        stale.forEach(name -> System.out.println("  stale (committed but no longer produced): " + name));
    }

    // endregion
}

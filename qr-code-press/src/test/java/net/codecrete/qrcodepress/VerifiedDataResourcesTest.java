/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the verified data covers relevant cases.
 */
class VerifiedDataResourcesTest {

    @Test
    @DisplayName("compaction fixture covers every segment mode")
    void compactionFixtureCoversEveryMode() {
        var modes = VerifiedData.readTsv("compaction.tsv").stream()
                .map(row -> row[4].toUpperCase(Locale.ROOT))
                .distinct();

        // Kanji mode is the one that went missing once: every fixture row is a text encoded as the
        // exporter encodes it, so a text dropped or re-encoded can silently take a whole mode with
        // it while every remaining assertion still passes.
        assertThat(modes).containsExactlyInAnyOrder("NUMERIC", "ALPHANUMERIC", "KANJI", "BINARY");
    }

    @Test
    @DisplayName("compaction fixture covers every non-empty payload text")
    void compactionFixtureCoversEveryNonEmptyText() {
        var expected = VerifiedData.readTsv("texts.tsv").stream()
                .filter(row -> Integer.parseInt(row[1]) > 0)
                .map(row -> Integer.parseInt(row[0]))
                .toList();

        var actual = VerifiedData.readTsv("compaction.tsv").stream()
                .map(row -> Integer.parseInt(row[0]))
                .distinct()
                .sorted()
                .toList();

        // A text with nothing to segment yields no fixture rows, so the empty text is absent by
        // construction. SegmentCompactionTest derives its cases from the fixture itself and cannot
        // tell that absence from a text the exporter dropped, which is what this pins: every text
        // of a non-zero character count is present, and no other text is.
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("expected modules form a square of dark and light cells")
    void modulesAreWellFormed() {
        var rows = VerifiedData.read("qrcodes/0000.txt").lines().toList();

        assertThat(rows).isNotEmpty()
                .allMatch(row -> row.length() == rows.size())
            .allMatch(row -> row.chars().allMatch(c -> c == '#' || c == '.'));
    }
}

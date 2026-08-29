/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.profiling;

import net.codecrete.qrcodepress.Ecc;

/**
 * Reports what each library produced for the sample set, as opposed to how fast it produced it.
 * <p>
 * The {@code compare} mode times three libraries over the same payloads and then prints this report.
 * The two halves belong together: the libraries do not encode the payloads into the same QR codes —
 * the error correction level is raised by two of the three, and an ECI segment is added by all three
 * under different conditions — so without this report the timings would be read as if the same work
 * had been done three times.
 * </p>
 * <p>
 * Nothing here is timed, so the output is the same on every run and on every machine. It changes
 * when the payloads change, when a library is upgraded, or when this library's encoding changes —
 * and the last of those is the interesting one.
 * </p>
 */
@SuppressWarnings("java:S106")
final class Comparison {

    private static final Ecc[] ECC_LEVELS = Ecc.values();

    private static final int MAX_VERSION = 40;

    private Comparison() {
        // no instances
    }

    /** Prints the report. */
    static void run() {
        var payloads = SampleData.payloads();

        System.out.printf("Workload: %d payloads × %d ECC levels = %d encodes per library%n",
                payloads.size(), ECC_LEVELS.length, payloads.size() * ECC_LEVELS.length);
        System.out.println();

        var libraries = Library.values();
        var histograms = new int[libraries.length][MAX_VERSION + 1];
        var moduleSums = new long[libraries.length];

        for (var i = 0; i < libraries.length; i++) {
            for (var payload : payloads) {
                for (var eccLevel : ECC_LEVELS) {
                    var size = libraries[i].encode(payload, eccLevel);
                    moduleSums[i] += size;
                    histograms[i][versionOf(size)]++;
                }
            }
        }

        printLibraries(libraries);
        printModuleSums(libraries, moduleSums);
        printHistogram(libraries, histograms);
    }

    private static void printLibraries(Library[] libraries) {
        System.out.println("Libraries");
        for (var library : libraries)
            System.out.printf("  %-8s %s%n", library.label(), library.coordinates());
        System.out.println();
    }

    private static void printModuleSums(Library[] libraries, long[] moduleSums) {
        // Summed matrix width, not module count: proportional to the size of the QR codes produced
        // and, unlike a count of the dark modules, unaffected by which mask happened to win.
        System.out.println("Total matrix size (sum of widths, in modules — smaller is denser encoding)");
        for (var i = 0; i < libraries.length; i++)
            System.out.printf("  %-8s %d%n", libraries[i].label(), moduleSums[i]);
        System.out.println();
    }

    private static void printHistogram(Library[] libraries, int[][] histograms) {
        System.out.print("Versions reached");
        for (var library : libraries)
            System.out.printf("  %8s", library.label());
        System.out.println();

        for (var version = 1; version <= MAX_VERSION; version++) {
            if (!isUsed(histograms, version))
                continue;
            System.out.printf("  version %2d    ", version);
            for (var histogram : histograms)
                System.out.printf("  %8d", histogram[version]);
            System.out.println();
        }
    }

    private static boolean isUsed(int[][] histograms, int version) {
        for (var histogram : histograms) {
            if (histogram[version] != 0)
                return true;
        }
        return false;
    }

    /** Derives the version from the matrix width, which is the only size all three libraries report. */
    private static int versionOf(int size) {
        return (size - 17) / 4;
    }
}

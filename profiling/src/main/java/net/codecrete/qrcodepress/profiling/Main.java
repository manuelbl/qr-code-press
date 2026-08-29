/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.profiling;

import java.io.IOException;
import java.util.Locale;

/**
 * Entry point of the profiling harness.
 * <p>
 * The same program serves the scenarios that need the same workload but not the same runner:
 * </p>
 * <ul>
 *   <li>{@code benchmark} — runs JMH over QR Code Press alone, for a number robust enough to be
 *   compared against a number recorded with an earlier version of the library.</li>
 *   <li>{@code profile [N]} — runs a plain timed loop, long enough for a sampling profiler and free
 *   of harness frames, so a profile shows the library and nothing else. This one measures
 *   QR Code Press only as well: a profile is read frame by frame, and another library's frames
 *   answer a question the profile was not opened to ask.</li>
 *   <li>{@code compare} — runs the same JMH benchmark over every library and then reports what each
 *   one produced for the same payloads, which is what keeps the three rows from being read as three
 *   timings of the same work.</li>
 *   <li>{@code bands} — runs a different JMH benchmark, one encode per row layout of the module
 *   matrix, for a change that applies to some versions and not others and would disappear into the
 *   noise of the main workload.</li>
 * </ul>
 * <p>
 * {@code benchmark} and {@code compare} differ in one thing only, the set of libraries measured,
 * which each one passes to JMH as {@code -p library=…}. Every mode names the benchmark class it
 * runs, so adding a class does not change what an existing mode measures.
 * </p>
 */
@SuppressWarnings("java:S106")
public final class Main {

    /**
     * Default number of iterations of the profile loop, sized at roughly 30 seconds — the time a
     * sampling profiler needs for a readable profile.
     */
    static final int DEFAULT_PROFILE_ITERATIONS = 1500;

    private Main() {
        // no instances
    }

    /**
     * Runs the harness in the mode given as the first argument.
     *
     * @param args the mode ({@code benchmark}, {@code compare}, {@code bands}, {@code profile} or
     *             {@code help}) and its arguments
     * @throws IOException if JMH cannot write its results
     */
    public static void main(String[] args) throws IOException {
        var mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";

        switch (mode) {
            case "benchmark" -> runBenchmark(args, Library.PRESS.label());

            case "compare" -> {
                runBenchmark(args, Library.allLabels());
                System.out.println();
                Comparison.run();
            }

            case "bands" -> runJmh(args, PenaltyBandBenchmark.class.getSimpleName());

            case "profile" -> {
                var iterations = DEFAULT_PROFILE_ITERATIONS;
                if (args.length > 1) {
                    try {
                        iterations = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        System.err.printf("Invalid iteration count '%s'.%n", args[1]);
                        System.exit(1);
                    }
                }
                ProfileLoop.run(iterations);
            }

            default -> {
                printUsage();
                if (!"help".equals(mode))
                    System.exit(1);
            }
        }
    }

    /**
     * Runs {@link EncodeTextBenchmark} over the given libraries.
     * <p>
     * The libraries come first, and a {@code -p library=…} of the caller's own would therefore add
     * to them rather than replace them: the mode is the way to choose which libraries are measured.
     * </p>
     */
    private static void runBenchmark(String[] args, String libraries) throws IOException {
        runJmh(args, EncodeTextBenchmark.class.getSimpleName(), "-p", "library=" + libraries);
    }

    /**
     * Runs JMH over the named benchmark class, with everything after the mode appended to JMH's own
     * command line, so options such as {@code -prof gc} or {@code -f 2} work without being plumbed
     * through here.
     * <p>
     * The class name is JMH's include pattern, and every mode passes one. Without it a run would
     * measure every benchmark on the class path, so a mode's output would change whenever a
     * benchmark class is added.
     * </p>
     */
    private static void runJmh(String[] args, String... leadingArgs) throws IOException {
        var jmhArgs = new String[leadingArgs.length + args.length - 1];
        System.arraycopy(leadingArgs, 0, jmhArgs, 0, leadingArgs.length);
        System.arraycopy(args, 1, jmhArgs, leadingArgs.length, args.length - 1);
        org.openjdk.jmh.Main.main(jmhArgs);
    }

    private static void printUsage() {
        System.out.println("QR Code Press profiling harness");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  ./mvnw compile exec:exec -Dprofiling.args=\"benchmark\"");
        System.out.println("      Run the JMH benchmark over QR Code Press alone.");
        System.out.println("  ./mvnw compile exec:exec -Dprofiling.args=\"compare\"");
        System.out.println("      Run it over every library, then report what each one produced");
        System.out.println("      for the same payloads.");
        System.out.println("  ./mvnw compile exec:exec -Dprofiling.args=\"bands\"");
        System.out.println("      Run one encode per row layout of the module matrix, so that a");
        System.out.println("      change confined to some versions is visible on a row of its own.");
        System.out.println("  ./mvnw compile exec:exec -Dprofiling.args=\"profile [N]\"");
        System.out.printf("      Run a plain loop of N iterations (default %d) over QR Code Press,%n",
                DEFAULT_PROFILE_ITERATIONS);
        System.out.println("      suitable for a sampling profiler.");
        System.out.println();
        System.out.println("The library is resolved from the local Maven repository. Install it first:");
        System.out.println("  (cd ../qr-code-press && ./mvnw install)");
    }
}

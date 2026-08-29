/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.profiling;

import net.codecrete.qrcodepress.Ecc;
import net.codecrete.qrcodepress.QrCode;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Measures one encode per row layout of the module matrix, so that a change to a layout is
 * attributable to the versions it applies to.
 * <p>
 * The matrix stores a row in one, two or three 64-bit words depending on its size, and the penalty
 * rules have an implementation per layout. The main workload of {@link EncodeTextBenchmark} is
 * weighted towards versions 1 to 11 and reaches version 20 at the very most, so a change confined
 * to the wider layouts moves it by little more than its noise. This benchmark states each layout on
 * a row of its own instead:
 * </p>
 * <table border="1">
 *   <caption>The versions measured and what each one stands for</caption>
 *   <tr><th>Version</th><th>Size</th><th>Words per row</th><th>Role</th></tr>
 *   <tr><td>11</td><td>61</td><td>1</td><td>the control: no change to a wider layout should move it</td></tr>
 *   <tr><td>20</td><td>97</td><td>2</td><td>the middle of the two-word layout</td></tr>
 *   <tr><td>27</td><td>125</td><td>2</td><td>the top of it, the last version before three words</td></tr>
 *   <tr><td>35</td><td>157</td><td>3</td><td>the three-word layout</td></tr>
 * </table>
 * <p>
 * An invocation is one whole {@code QrCode.encodeText}, not the penalty rules alone, which are
 * package-private and out of reach from here. Segmentation, Reed-Solomon and filling the payload
 * are therefore timed along with the scoring, and they dilute the signal by an amount that grows
 * with the version. The version 11 row is what calibrates that: it runs the same untouched code as
 * before any change to a wider layout, so a change that moves it is measuring something other than
 * what it meant to.
 * </p>
 * <p>
 * The payloads are built here rather than taken from {@link SampleData}, whose ceiling of 382 bytes
 * is what keeps the version mix of the main workload fixed and every number recorded against it
 * comparable. Reaching version 27 or 35 would mean raising that ceiling.
 * </p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class PenaltyBandBenchmark {

    /**
     * The single error correction level measured.
     * <p>
     * A payload reaches a different version at each level, so the level has to be fixed for a
     * version to name a size. It is not a parameter, because a second one would multiply the rows
     * without adding a layout.
     * </p>
     */
    private static final Ecc ECC_LEVEL = Ecc.MEDIUM;

    /**
     * The text the payloads are cut from, repeated as far as needed.
     * <p>
     * It is lower case throughout, so every payload encodes as a single byte-mode segment and its
     * version grows monotonically with its length. A pattern that mixed in digits or upper case
     * would be compacted into segments that shift as it grows, and the search below would no longer
     * find the shortest payload of a version.
     * </p>
     */
    private static final String PAYLOAD_PATTERN =
            "https://shop.example.com/catalog/article/reference/gallery/support/".repeat(64);

    /** The version to encode, one benchmark row each, one per row layout plus its edges. */
    @Param({"11", "20", "27", "35"})
    public int version;

    private String payload;

    /** Builds the payload reaching the version JMH set, and checks that it reaches exactly that one. */
    @Setup(Level.Trial)
    public void setUp() {
        payload = payloadForVersion(version);
    }

    /**
     * Encodes the payload of this row's version.
     *
     * @return the module count, which JMH consumes so that nothing can be optimized away
     */
    @Benchmark
    public int encodeOne() {
        return QrCode.encodeText(payload, ECC_LEVEL).getSize();
    }

    /**
     * Returns the shortest prefix of {@link #PAYLOAD_PATTERN} that encodes at the given version.
     * <p>
     * The prefix grows one character at a time, so the first one to reach the version is the
     * shortest, and reaching it means reaching it exactly: the version before it was lower, and a
     * single character cannot skip a version. Overshooting nonetheless would mean the pattern is no
     * longer a single monotonically growing segment, which is why it is checked rather than assumed.
     * </p>
     *
     * @param version the QR code version to reach
     * @return the payload
     */
    private static String payloadForVersion(int version) {
        for (var length = 1; length <= PAYLOAD_PATTERN.length(); length += 1) {
            var candidate = PAYLOAD_PATTERN.substring(0, length);
            var reached = QrCode.encodeText(candidate, ECC_LEVEL).getVersion();
            if (reached >= version) {
                if (reached != version)
                    throw new IllegalStateException(String.format(
                            "No payload of version %d: %d characters already reach version %d",
                            version, length, reached));
                return candidate;
            }
        }

        throw new IllegalStateException(
                "PAYLOAD_PATTERN is too short to reach version " + version);
    }
}

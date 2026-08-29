/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.profiling;

import net.codecrete.qrcodepress.Ecc;
import net.codecrete.qrcodepress.QrCode;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Plain timed loop over the sample payloads, for a sampling profiler to attach to.
 * <p>
 * Deliberately without JMH: a profile of this run should show {@code MatrixEncoder},
 * {@code Penalty} and {@code BitMatrix}, not the frames of a benchmark harness. The trade is that
 * this run says nothing about variance — that is what {@link EncodeTextBenchmark} is for.
 * </p>
 * <p>
 * The checksum serves two purposes: the result of every generated QR code is consumed, so nothing
 * can be optimized away, and two runs printing the same checksum did the same work.
 * </p>
 */
final class ProfileLoop {

    /**
     * Passes over the whole set before the timed section.
     * <p>
     * One pass is 800 calls, which leaves the encoder in the interpreter and the tier-1 compiler.
     * Twenty passes are past C2's compilation thresholds, so both the timing and an attached
     * profiler see steady-state code rather than the JVM warming up.
     * </p>
     */
    private static final int WARMUP_ITERATIONS = 20;

    private static final Ecc[] ECC_LEVELS = Ecc.values();

    private ProfileLoop() {
        // no instances
    }

    /**
     * Runs the loop and prints the elapsed time and the checksum.
     *
     * @param iterations number of passes over the whole set of payloads
     */
    static void run(int iterations) {
        var payloads = SampleData.payloads();
        var callCount = (long) iterations * payloads.size() * ECC_LEVELS.length;

        System.out.printf("Profile loop: %d iterations × %d payloads × %d ECC levels%n",
                iterations, payloads.size(), ECC_LEVELS.length);
        System.out.printf("Total encodeText calls: %s%n", groupDigits(callCount));

        encode(payloads, WARMUP_ITERATIONS);

        var start = System.nanoTime();
        var checksum = encode(payloads, iterations);
        var elapsedNanos = System.nanoTime() - start;

        System.out.printf("Elapsed: %s (checksum=%d)%n", formatElapsed(elapsedNanos), checksum);
    }

    private static long encode(List<String> payloads, int iterations) {
        var checksum = 0L;
        for (var iteration = 0; iteration < iterations; iteration++) {
            for (var payload : payloads) {
                for (var eccLevel : ECC_LEVELS) {
                    var qrCode = QrCode.encodeText(payload, eccLevel);
                    checksum += qrCode.getSize();
                }
            }
        }
        return checksum;
    }

    private static String formatElapsed(long nanos) {
        // Rounded to tenths first, so a run of 59.98 s does not print as 00:00:60.0.
        var tenths = Math.round(nanos / 100_000_000.0);
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%d",
                tenths / 36_000, tenths / 600 % 60, tenths / 10 % 60, tenths % 10);
    }

    private static String groupDigits(long value) {
        var symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setGroupingSeparator('\'');
        return new DecimalFormat("#,##0", symbols).format(value);
    }
}

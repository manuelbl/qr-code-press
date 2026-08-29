/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.profiling;

import net.codecrete.qrcodepress.Ecc;

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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Measures encoding the whole sample set, once per library.
 * <p>
 * One invocation encodes every payload at every error correction level, so the score is the time for
 * one full pass and the mix of versions and encoding modes is the same in every invocation. A single
 * encode call would be too short to time reliably and would measure one payload rather than the
 * workload.
 * </p>
 * <p>
 * The libraries are a {@link Param} rather than a benchmark method each, so a run produces one row
 * per library in a single table and a fourth library would be a single entry in {@link Library}.
 * Which of them a run measures is the mode's choice, passed as {@code -p library=…}: the
 * {@code benchmark} mode measures this library alone, the {@code compare} mode all of them. The
 * rows of a {@code compare} run are comparable in time but not in output: see {@link Library} for
 * what each library is actually asked to do, and the report {@code compare} prints after the table
 * for what each one produced.
 * </p>
 * <p>
 * Sized at roughly five seconds of measurement per library. With one fork, JMH's error and standard
 * deviation describe the variance <em>within</em> one JVM: a different compilation plan between JVM
 * starts shows up as an unexplained shift between runs rather than as error bars. Re-run with
 * {@code -f 2} if a measurement ever looks suspicious.
 * </p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class EncodeTextBenchmark {

    /** The library under measurement, one benchmark row each; the mode narrows the set. */
    @Param({"press", "nayuki", "zxing"})
    public String library;

    private final List<String> payloads = SampleData.payloads();

    private final Ecc[] eccLevels = Ecc.values();

    private Library encoder;

    /** Resolves the label JMH sets into the library it names. */
    @Setup(Level.Trial)
    public void setUp() {
        encoder = Library.byLabel(library);
    }

    /**
     * Encodes every payload at every error correction level.
     *
     * @return the summed module count, which JMH consumes so that nothing can be optimized away
     */
    @Benchmark
    public long encodeAll() {
        var checksum = 0L;
        for (var payload : payloads) {
            for (var eccLevel : eccLevels) {
                checksum += encoder.encode(payload, eccLevel);
            }
        }
        return checksum;
    }
}

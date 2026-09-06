# Profiling harness

Measures the time to generate a set of QR codes, so you can hold a change against a number recorded
with an earlier version. It also runs the same set in a form a sampling profiler can attach to, and
against two other Java QR code libraries.

The workload is `QrCode.encodeText()` over 400 deterministic payloads at all four error correction
levels, 1600 calls per pass. The payloads are real-world shaped: URLs, delimited data, names and
towns with accents, emoji messages. A fifth of them are long enough to reach well past version 11,
where penalty scoring dominates, and the set covers all three `BitMatrix` row layouts: 80 % of the
encodes land in versions 1 to 11, 15 % in 12 to 27 and 5 % in 28 and above. See `SampleData` for how
they are composed.


## Running

The library is resolved from the local Maven repository, so **install it first**, and again after
every library change, or the harness measures the previous version:

```sh
cd ../qr-code-press
./mvnw install
```

Then, from this directory:

```sh
./mvnw compile exec:exec "-Dprofiling.args=benchmark"
./mvnw compile exec:exec "-Dprofiling.args=profile [N]"
./mvnw compile exec:exec "-Dprofiling.args=compare"
```

Without arguments, the harness prints its usage.


### `benchmark`: the number to record

Runs JMH over QR Code Press alone: 1 fork, 5 × 1 s warmup, 5 × 1 s measurement, about 10 s end to
end. The score is the average time of a full pass over the whole set, and it is the number to record
in the [log](LOG.md).

```
Benchmark                      (library)  Mode  Cnt   Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  18.871 ± 0.646  ms/op
```

This is the mode for measuring a change to the library, so it measures nothing else.

Add `-prof gc` for the allocation figure that the log tracks alongside the mean:

```sh
./mvnw compile exec:exec "-Dprofiling.args=benchmark -prof gc"
```

Everything after `benchmark` goes to JMH's own command line, so any JMH option works. `-lprof`
lists the profilers available on this machine, and `-f 2` runs two forks. The one option to leave
alone is `-p library=`, which the mode itself passes. Choose the libraries by choosing the mode.

With one fork, `Error` and `StdDev` describe the variance *within* a single JVM. A different
compilation plan between JVM starts therefore shows up as an unexplained shift between runs rather
than as error bars, so if a measurement looks suspicious, re-run it with `-f 2` before believing it.

Two things in the output are expected. On JDK 25, JMH 1.37 prints a handful of
`WARNING: ... sun.misc.Unsafe` lines as the fork starts; they come from JMH's own internals, not
from the library. And JMH reports that compiler blackholes are in use, which is why every entry in
the [log](LOG.md) records its JDK.


### `compare`: the other libraries

Runs the same JMH benchmark over qrcodegen and ZXing as well, one row per library, about a minute
end to end. The other two libraries are the slow part.

It then prints the library versions, the average QR code version and the version histogram, which
say what each library produced rather than how fast:

```
Benchmark                      (library)  Mode  Cnt    Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5   18.768 ± 0.516  ms/op
EncodeTextBenchmark.encodeAll     nayuki  avgt    5  614.850 ± 7.698  ms/op
EncodeTextBenchmark.encodeAll      zxing  avgt    5  940.699 ± 1.293  ms/op
```

For details see [COMPARISON](COMPARISON.md).


### `profile [N]`: the run to attach a profiler to

Runs a plain loop of `N` passes over QR Code Press, 1500 by default, which is about 28 s, long
enough for a sampling profiler to fill in. This mode measures this library alone. A profile is read
frame by frame, and another library's frames answer a question the profile was not opened to ask.
It deliberately uses no JMH, so a profile of this run shows `MatrixEncoder`, `Penalty` and
`BitMatrix` rather than the frames of a benchmark harness.

```
Profile loop: 1500 iterations × 400 payloads × 4 ECC levels
Total encodeText calls: 2'400'000
Elapsed: 00:00:28.3 (checksum=123444000)
```

The loop warms up before the timed section, so the elapsed time and the profile both describe
steady-state code. The checksum keeps the results from being optimized away and confirms that two
runs did the same work. If it differs between runs, they did not.

**Attaching a profiler.** Start the run and attach to the `net.codecrete.qrcodepress.profiling.Main`
process: IntelliJ IDEA Ultimate's *Run > Attach Profiler to Process* (async-profiler underneath), or
`asprof` directly if async-profiler is installed. Raise `N` if the run ends before the profiler has
enough samples.

**Flight Recorder.** JVM options for the harness process go through `-Dprofiling.jvmArgs`:

```sh
./mvnw compile exec:exec \
    "-Dprofiling.jvmArgs=-XX:StartFlightRecording=filename=profile.jfr,settings=profile" \
    "-Dprofiling.args=profile"
```

Open `profile.jfr` in JDK Mission Control or IntelliJ IDEA. This records the harness JVM, which is
the right one for `profile` but *not* for `benchmark`. JMH measures in a forked JVM that these
options never reach, so use JMH's own profiler there instead:

```sh
./mvnw compile exec:exec "-Dprofiling.args=benchmark -prof jfr"
```

It writes the recording to a directory named after the benchmark, here in `profiling/`.


## Log

Measurements for every relevant change to the library are recorded in [LOG](LOG.md).


## Latest benchmark results

Apple M5 Pro (arm64), Temurin 25.0.2+10

```
Benchmark                      (library)  Mode  Cnt   Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  18.871 ± 0.646  ms/op
```

Dell (Intel Core Ultra 5), Temurin 25.0.4.1+1

```
Benchmark                      (library)  Mode  Cnt   Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  18.126 ± 1.256  ms/op
```

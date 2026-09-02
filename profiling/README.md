# Profiling harness

Measures the time to generate a set of QR codes, so you can hold a change against a number recorded
with an earlier version. It also runs the same set in a form a sampling profiler can attach to, and
against two other Java QR code libraries.

The workload is `QrCode.encodeText()` over 200 deterministic payloads at all four error correction
levels, 800 calls per pass. The payloads are real-world shaped: URLs, delimited data, names and
towns with accents, emoji messages. About a tenth of them are long enough to reach versions 10 to
20, where penalty scoring dominates, since that is where nearly every optimization so far has landed.
See `SampleData` for how they are composed.


## Running

The library is resolved from the local Maven repository, so **install it first**, and again after
every library change, or the harness measures the previous version:

```sh
cd ../qr-code-press
./mvnw install
```

Then, from this directory:

```sh
./mvnw compile exec:exec -Dprofiling.args="benchmark"
./mvnw compile exec:exec -Dprofiling.args="profile [N]"
./mvnw compile exec:exec -Dprofiling.args="compare"
./mvnw compile exec:exec -Dprofiling.args="bands"
```

Without arguments, the harness prints its usage.


### `benchmark`: the number to record

Runs JMH over QR Code Press alone: 1 fork, 5 × 1 s warmup, 5 × 1 s measurement, about 10 s end to
end. The score is the average time of a full pass over the whole set, and it is the number to record
in the log below:

```
Benchmark                      (library)  Mode  Cnt  Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  5.191 ± 0.030  ms/op
```

This is the mode for measuring a change to the library, so it measures nothing else. The other two
libraries do not move when this one changes, and they do not encode the payloads into the same QR
codes. What they do belongs to `compare`.

Add `-prof gc` for the allocation figure that the log tracks alongside the mean:

```sh
./mvnw compile exec:exec -Dprofiling.args="benchmark -prof gc"
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
the log below records its JDK.


### `compare`: the other libraries

Runs the same JMH benchmark over qrcodegen and ZXing as well, one row per library, about 30 s end to
end. It then prints the library versions, the total matrix size and the version histogram, which
say what each library produced rather than how fast:

```
Benchmark                      (library)  Mode  Cnt    Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5    5.256 ± 0.235  ms/op
EncodeTextBenchmark.encodeAll     nayuki  avgt    5  144.345 ± 1.006  ms/op
EncodeTextBenchmark.encodeAll      zxing  avgt    5  219.436 ± 1.222  ms/op
```

The report is not timed and prints the same thing on every run and every machine, so a change to it
is a change in what the libraries encode. It is in the same mode as the timings because the three
rows above are comparable in time but not in output. Without it, they would be read as three
timings of the same work.

JMH options pass through here as they do for `benchmark`, with the same exception for
`-p library=`.

See [COMPARISON.md](COMPARISON.md) for the results, for how each library is called, for why ZXing
has to be forced to UTF-8 before it can be compared at all, and for what qrcodegen leaves out of
every code it writes.


### `bands`: one row per module matrix layout

Runs a different benchmark, `PenaltyBandBenchmark`, which encodes a single payload at each of four
versions, about 20 s end to end. `BitMatrix` stores a row in one, two or three 64-bit words
depending on its size, and `Penalty` has an implementation per layout, so a change to one layout
applies to some versions and not others:

```
Benchmark                       (version)  Mode  Cnt    Score   Error  Units
PenaltyBandBenchmark.encodeOne         11  avgt    5   9.734 ± 0.014  us/op
PenaltyBandBenchmark.encodeOne         20  avgt    5  28.331 ± 0.356  us/op
PenaltyBandBenchmark.encodeOne         27  avgt    5  41.250 ± 0.167  us/op
PenaltyBandBenchmark.encodeOne         35  avgt    5  75.874 ± 1.731  us/op
```

Version 11 is one word per row, versions 20 and 27 are two, version 35 is three; 27, at 125 modules,
is the largest version still taking two. This mode exists because the `benchmark` workload cannot
resolve such a change: it is weighted towards versions 1 to 11 and reaches version 20 at the very
most, so a wide layout getting twice as fast moves it by little more than its noise.

An invocation times a whole `encodeText`, not the penalty rules alone — they are package-private,
and this is a separate project. Segmentation, Reed-Solomon and filling the payload are timed along
with the scoring and dilute the signal by an amount that grows with the version, so a row's *change*
between two runs understates the change to the rule. The version 11 row is the control: a change to
a wider layout must leave it where it was, and a run that moves it is measuring something else.

The payloads are built by `PenaltyBandBenchmark` rather than taken from `SampleData`, whose ceiling
of 382 bytes is what fixes the version mix of the main workload and keeps every number in the log
below comparable. Reaching version 27 or 35 would mean raising that ceiling.

JMH options pass through as they do for `benchmark`, this mode passing none of its own. `-p
version=20` narrows the run to a single row, which is the quick way to re-measure one layout.


### `profile [N]`: the run to attach a profiler to

Runs a plain loop of `N` passes over QR Code Press, 1500 by default, which is about 13 s, long
enough for a sampling profiler to fill in. This mode measures this library alone. A profile is read
frame by frame, and another library's frames answer a question the profile was not opened to ask.
It deliberately uses no JMH, so a profile of this run shows `MatrixEncoder`, `Penalty` and
`BitMatrix` rather than the frames of a benchmark harness.

```
Profile loop: 1500 iterations × 200 payloads × 4 ECC levels
Total encodeText calls: 1'200'000
Elapsed: 00:00:13.2 (checksum=48306000)
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
    -Dprofiling.jvmArgs="-XX:StartFlightRecording=filename=profile.jfr,settings=profile" \
    -Dprofiling.args="profile"
```

Open `profile.jfr` in JDK Mission Control or IntelliJ IDEA. This records the harness JVM, which is
the right one for `profile` but *not* for `benchmark`. JMH measures in a forked JVM that these
options never reach, so use JMH's own profiler there instead:

```sh
./mvnw compile exec:exec -Dprofiling.args="benchmark -prof jfr"
```

It writes the recording to a directory named after the benchmark, in this directory.


## Log

One section per change, in chronological order, each with the `profile` output and the JMH table.

Every entry records the machine and the JDK, because the numbers are comparable only within one
machine.

### Compact `BitMatrix` rows for versions 1 to 11

A row of at most 64 columns now occupies a single `long` instead of four, and `Penalty` scores it
with a rule form of its own. Versions 1 to 11 (size 61 and below) take that layout, which is most
of the workload. The finder-pattern rule could not simply be narrowed, because its sliding window
runs past the last column, into a word a compact row does not have. The compact form matches whole
words instead, branchlessly. Output is unchanged, and the checksum below is the one the earlier runs
recorded.

Apple M5 Pro (arm64), Temurin 25.0.2+10.

```
Profile loop: 1500 iterations × 200 payloads × 4 ECC levels
Total encodeText calls: 1'200'000
Elapsed: 00:00:19.3 (checksum=48306000)
```

```
Benchmark                      Mode  Cnt   Score   Error  Units
EncodeTextBenchmark.encodeAll  avgt    5  13.236 ± 0.225  ms/op
```

Measured against the same machine and JDK immediately before the change, for comparison:

```
Benchmark                      Mode  Cnt   Score   Error  Units
EncodeTextBenchmark.encodeAll  avgt    5  22.098 ± 0.388  ms/op
```

Measured on their own at size 61, compact against wide, `streaks` and `twoByTwoBlocks` are each
about 4× faster, which is simply the four-to-one drop in words scanned. `finderPatterns` is about
23× faster, most of that the branchless match rather than the layout. That match is separable and
would speed up versions 12 to 40 as well, at the price of carrying the shifts across word
boundaries — which is what *Branchless finder-pattern match for wide rows* below went on to do.

### Payload target table

`MatrixEncoder.fillPayload` walked the zigzag on every encode: for each module of the symbol it read
the payload-area map, derived a word index and a bit mask from the coordinates, and branched on the
codeword bit. Which module a codeword bit lands on depends on the version alone, so the walk is now
done once per version and cached as a table of packed `BitMatrix` addresses, and filling in the
codewords is a flat pass over it. Each bit costs one table read, one shift and one
read-modify-write, with no branch, since a light module shifts a zero into place and leaves its word
unchanged. A sampling profile put `fillPayload` at 36% of an encode before the change and 7% after
it. Output is unchanged, and the checksum below is the one the earlier runs recorded.

Apple M5 Pro (arm64), Temurin 25.0.2+10.

```
Profile loop: 1500 iterations × 200 payloads × 4 ECC levels
Total encodeText calls: 1'200'000
Elapsed: 00:00:13.2 (checksum=48306000)
```

```
Benchmark                      (library)  Mode  Cnt  Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  8.544 ± 0.022  ms/op
```

Measured against the same machine and JDK immediately before the change, for comparison:

```
Benchmark                      (library)  Mode  Cnt   Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  13.229 ± 0.432  ms/op
```

The table costs one `int` per payload module of each version encoded, about 4 KB at version 5 and
105 KB at version 40. It lives in `LazyCache` beside the fixed patterns and the mask pairs, which
are larger still.


### Reed-Solomon division by table lookup

`ReedSolomon` no longer does field arithmetic per coefficient. It precomputes every multiple of the
generator polynomial once per capacity, 256 rows, each the polynomial times one field element. A
division step is then a row lookup plus a shift-and-XOR of the remainder, and the log, exp and
modulo of the old inner loop are gone. The remainder and the rows are padded to whole eight-byte
words, which lets the step run through a `byte[]` view `VarHandle` and handle eight coefficients per
machine word. Capacity 30, the largest QR uses, takes four word operations instead of thirty scalar
ones. The padding is zero and provably stays zero, since the rows are
zero-padded too and the shift can only move a zero into it. The codewords are now written straight
into the interleaved result at their stride, so no block needs a buffer of its own. A sampling
profile put `computeErrorCorrection` at 28% of an encode before the change. Output is unchanged,
and the checksum below is the one the earlier runs recorded.

The table costs 256 rows of the capacity rounded up to eight bytes, 8 KB at capacity 30, and lives
in the `LazyCache` that already caches an instance per capacity. QR uses 13 distinct capacities in
all, so a process that encodes every version at every error correction level holds about 60 KB.

Apple M5 Pro (arm64), Temurin 25.0.2+10.

```
Profile loop: 1500 iterations × 200 payloads × 4 ECC levels
Total encodeText calls: 1'200'000
Elapsed: 00:00:10.0 (checksum=48306000)
```

```
Benchmark                      (library)  Mode  Cnt  Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  6.731 ± 0.036  ms/op
```

Measured against the same machine and JDK immediately before the change, for comparison:

```
Benchmark                      (library)  Mode  Cnt  Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  8.643 ± 0.120  ms/op
```

### A wide `BitMatrix` row holds three words, not four

A row wider than 64 columns was allocated four `long`s and scanned as four. Only three of them can
ever hold a module: version 40, the largest QR code, is 177 modules wide, and 177 bits fit in three
words. The fourth was always zero, and every penalty rule scanning a wide row read it anyway.

The rules now scan three, and `BitMatrix.MAX_SIZE` drops from 256 to 192 accordingly, that being the
largest size with three words of modules per row. The allocated stride stays four so that it remains
a power of two and a row index remains a shift rather than a multiplication; `usedWordsPerRow()`
reports the three, `wordsPerRow()` the four. The padding word is still cleared by `invert()`,
unreachable by `fillRect` and never written by `transpose()`, so `and`, `xor` and `popCount` go on
running flat over the whole array, which is what lets them vectorize, and pay for that one word.

Output is unchanged. `bands` is the mode that resolves this, `benchmark` reaching version 20 at the
very most.

Apple M5 Pro (arm64), Temurin 25.0.2+10.

```
Benchmark                       (version)  Mode  Cnt    Score   Error  Units
PenaltyBandBenchmark.encodeOne         11  avgt    5    9.970 ± 0.396  us/op
PenaltyBandBenchmark.encodeOne         20  avgt    5   45.366 ± 0.422  us/op
PenaltyBandBenchmark.encodeOne         27  avgt    5   67.640 ± 0.665  us/op
PenaltyBandBenchmark.encodeOne         35  avgt    5  133.835 ± 1.305  us/op
```

Measured against the same machine and JDK immediately before the change, for comparison:

```
Benchmark                       (version)  Mode  Cnt    Score   Error  Units
PenaltyBandBenchmark.encodeOne         11  avgt    5    9.672 ± 0.063  us/op
PenaltyBandBenchmark.encodeOne         20  avgt    5   52.010 ± 1.248  us/op
PenaltyBandBenchmark.encodeOne         27  avgt    5   74.641 ± 2.807  us/op
PenaltyBandBenchmark.encodeOne         35  avgt    5  142.754 ± 2.059  us/op
```

Version 11 is the control and did not move. Versions 20, 27 and 35 gained 13 %, 9 % and 6 %; the
share is smaller the larger the version, since the parts of an encode that are not penalty scoring
grow with it. On the main workload, where only about a tenth of the payloads reach a wide row at all:

```
Benchmark                      (library)  Mode  Cnt  Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  6.545 ± 0.138  ms/op
```

### Branchless finder-pattern match for wide rows

The finder-pattern rule matched a compact row by whole words and a wide row by sliding a 15-bit
window along it one column at a time, two comparisons per column. The window ran four columns past
the last, into a word a compact row does not have, which is why the two forms differed at all.

Both now use the same identity. It matches a whole word at once, and a word takes the bits it needs
past its own end from the next word, or zeros where there is no next word — which is what the rule
wants beyond the edge of the symbol anyway. `matchesInWord` is that identity, called once per word
of modules in the row; the compact form passes zero for both neighbours and folds back to what it
already did. So the layouts no longer differ in how they match, only in how many words they match.

Output is unchanged, and version 11 confirms it: the compact path is the same code as before,
reached through a call that inlines away, and it did not move.

Apple M5 Pro (arm64), Temurin 25.0.2+10.

```
Benchmark                       (version)  Mode  Cnt   Score   Error  Units
PenaltyBandBenchmark.encodeOne         11  avgt    5   9.834 ± 0.040  us/op
PenaltyBandBenchmark.encodeOne         20  avgt    5  36.327 ± 1.890  us/op
PenaltyBandBenchmark.encodeOne         27  avgt    5  49.024 ± 0.063  us/op
PenaltyBandBenchmark.encodeOne         35  avgt    5  76.838 ± 0.943  us/op
```

Measured against the same machine and JDK immediately before the change, for comparison:

```
Benchmark                       (version)  Mode  Cnt    Score   Error  Units
PenaltyBandBenchmark.encodeOne         11  avgt    5    9.970 ± 0.396  us/op
PenaltyBandBenchmark.encodeOne         20  avgt    5   45.366 ± 0.422  us/op
PenaltyBandBenchmark.encodeOne         27  avgt    5   67.640 ± 0.665  us/op
PenaltyBandBenchmark.encodeOne         35  avgt    5  133.835 ± 1.305  us/op
```

Versions 20, 27 and 35 gain 20 %, 28 % and 43 %, the share growing with the version because the
window it replaces cost one iteration per column. Version 35 is now 1.86× the speed it was two
entries ago. The main workload, a tenth of which reaches a wide row:

```
Benchmark                      (library)  Mode  Cnt  Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  5.624 ± 0.147  ms/op
```

### Three row layouts instead of two

`BitMatrix` had a row of one word or of three. A row of two covers versions 12 to 27, which is
sizes 65 to 125, and those were paying for a third word that held nothing. There are now three
layouts, one word of modules per 64 columns, and `usedWordsPerRow()` reports which. `isCompact()` is
gone: it was a two-valued predicate on a three-valued property, and `!isCompact()` no longer names
one implementation. Each rule dispatches on the word count in a `switch` whose case labels match the
names of the three forms it selects, `…OneWord`, `…TwoWords` and `…ThreeWords`, each with its loop
over the words unrolled.

The finder-pattern rule is the exception to the triplication. Its bit identity lives once, in
`matchesInWord`; the three forms differ only in how many times they call it and which neighbouring
words they pass, so the identity is not written down three times.

The stride of the new layout is 2, a power of two like the others, so a row index is still a shift.
It has no padding word, and versions 12 to 27 now also allocate half the memory they did — which is
most of what the mask pair and fixed pattern caches hold for those versions.

Output is unchanged.

Apple M5 Pro (arm64), Temurin 25.0.2+10.

```
Benchmark                       (version)  Mode  Cnt   Score   Error  Units
PenaltyBandBenchmark.encodeOne         11  avgt    5   9.734 ± 0.014  us/op
PenaltyBandBenchmark.encodeOne         20  avgt    5  28.331 ± 0.356  us/op
PenaltyBandBenchmark.encodeOne         27  avgt    5  41.250 ± 0.167  us/op
PenaltyBandBenchmark.encodeOne         35  avgt    5  75.874 ± 1.731  us/op
```

Measured against the same machine and JDK immediately before the change, for comparison:

```
Benchmark                       (version)  Mode  Cnt   Score   Error  Units
PenaltyBandBenchmark.encodeOne         11  avgt    5   9.834 ± 0.040  us/op
PenaltyBandBenchmark.encodeOne         20  avgt    5  36.327 ± 1.890  us/op
PenaltyBandBenchmark.encodeOne         27  avgt    5  49.024 ± 0.063  us/op
PenaltyBandBenchmark.encodeOne         35  avgt    5  76.838 ± 0.943  us/op
```

Versions 20 and 27 gain 22 % and 16 %. Versions 11 and 35 keep their layout and did not move, which
is the result to check first: the split is meant to add a layout, not to disturb the two that were
already there.

```
Benchmark                      (library)  Mode  Cnt  Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  5.155 ± 0.072  ms/op
```

Over the four entries since the per-layout benchmark was added, version 20 went from 52.010 to
28.331 µs and version 35 from 142.754 to 75.874 µs, both about 1.85×, and the main workload from
6.731 to 5.155 ms.

### Segmentation merges blocks in an array

`SegmentCompaction` held its blocks in an `ArrayList` and merged them with `set` and
`subList(…).clear()`, so every payload paid for the list, for its backing array as it grew, and for
a sublist view per merge. The blocks now live in a `Block[]`, sized exactly by a counting pass over
the per-byte modes, and a merge sweep compacts it in place: it reads with one index and writes with
another, a merged block overwriting the block that absorbed it, so nothing shifts and nothing is
reallocated. The sweep runs front to back, the direction the compaction wants; running it from the
back only ever served to shift fewer list elements. The two merge rules, which were an enum with a
subclass body each, are now a pair of predicates passed to the sweep, and `Block.segmentLength`
spells out the four mode formulas rather than calling through `DataSegmentMode`.

Output is unchanged, and the checksum below is the one the earlier runs recorded.

Apple M5 Pro (arm64), Temurin 25.0.2+10.

```
Profile loop: 1500 iterations × 200 payloads × 4 ECC levels
Total encodeText calls: 1'200'000
Elapsed: 00:00:07.6 (checksum=48306000)
```

```
Benchmark                                         (library)  Mode  Cnt        Score   Error   Units
EncodeTextBenchmark.encodeAll                         press  avgt   10        5.094 ± 0.034   ms/op
EncodeTextBenchmark.encodeAll:gc.alloc.rate.norm      press  avgt   10  3696523.408 ± 0.257    B/op
```

Measured against the same machine and JDK immediately before the change, for comparison:

```
Benchmark                                         (library)  Mode  Cnt        Score   Error   Units
EncodeTextBenchmark.encodeAll                         press  avgt   10        5.157 ± 0.030   ms/op
EncodeTextBenchmark.encodeAll:gc.alloc.rate.norm      press  avgt   10  3886475.898 ± 0.227    B/op
```

A pass allocates 190 KB less, 4.9 % of what it allocated, or about 237 bytes per `encodeText`. The
time it buys is 1.2 %, which is barely more than the error bars: segmentation is a small part of an
encode, and the allocations it dropped were short-lived ones the collector was already handling
cheaply. Both runs are `-f 2`, ten measurement iterations over two JVMs, because the effect is
smaller than the shift a single fork can show between runs. This is the first entry to record
`gc.alloc.rate.norm`; it is the number this change was made for, and unlike the mean it is
reproducible to the byte.

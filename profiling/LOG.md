# Log

This file contains measurements of older versions of the library.


## Compact `BitMatrix` rows for versions 1 to 11

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


## Payload target table

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


## Reed-Solomon division by table lookup

`ReedSolomon` no longer does field arithmetic per coefficient. It precomputes every multiple of the
generator polynomial once per capacity, 256 rows, each the polynomial times one field element. A
division step is then a row lookup plus a shift-and-XOR of the remainder, and the log, exp and
modulo of the old inner loop are gone. The remainder and the rows are padded to whole eight-byte
words, which lets the step run through a `byte[]` view `VarHandle` and handle eight coefficients per
machine word. Capacity 30, the largest a QR code uses, takes four word operations instead of thirty
scalar ones. The padding is zero and provably stays zero, since the rows are zero-padded too and the
shift can only move a zero into it. The codewords are now written straight into the interleaved
result at their stride, so no block needs a buffer of its own. A sampling profile put
`computeErrorCorrection` at 28% of an encode before the change. Output is unchanged, and the
checksum below is the one the earlier runs recorded.

The table costs 256 rows of the capacity rounded up to eight bytes, 8 KB at capacity 30, and lives
in the `LazyCache` that already caches an instance per capacity. QR codes use 13 distinct capacities
in all, so a process that encodes every version at every error correction level holds about 60 KB.

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


## A wide `BitMatrix` row holds three words, not four

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


## Branchless finder-pattern match for wide rows

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


## Three row layouts instead of two

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


## Segmentation merges blocks in an array

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

Dell Core Ultra 5.

```
Benchmark                      (library)  Mode  Cnt  Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5  4.909 ± 0.080  ms/op
```


## Segmentation is optimal, not merely merged

`SegmentCompaction` used to merge adjacent blocks whenever a merge shortened the bit stream, which
is a local rule and therefore not always the shortest segmentation. A dynamic programme over the
blocks replaced it, the same one the .NET
[QrCodeGenerator](https://github.com/manuelbl/QrCodeGenerator) uses, and the result is now the
shortest bit stream of any segmentation. This is the one entry in this log whose change is to the
*output*, so the numbers below say what it cost rather than what it bought. One case in the verified
data moved, by 12 bits.

The merge passes ran repeatedly over the block array and allocated nothing; the dynamic programme
runs once and allocates five small arrays, two of them per block. The segment list is sized by
counting the assigned modes rather than from the block count, since most blocks end up sharing a
segment and the block count would over-allocate the list on nearly every payload.

Dell Core Ultra 5, Temurin 25.0.4.1+1.

```
Benchmark                                         (library)  Mode  Cnt        Score      Error   Units
EncodeTextBenchmark.encodeAll                         press  avgt   20        4.556 ±    0.046   ms/op
EncodeTextBenchmark.encodeAll:gc.alloc.rate.norm      press  avgt   20  3813271.982 ± 7406.911    B/op
```

Measured against the same machine and JDK immediately before the change, for comparison:

```
Benchmark                                         (library)  Mode  Cnt        Score       Error   Units
EncodeTextBenchmark.encodeAll                         press  avgt   20        4.587 ±     0.183   ms/op
EncodeTextBenchmark.encodeAll:gc.alloc.rate.norm      press  avgt   20  3757320.164 ±  54317.980    B/op
```

The mean does not move: the two runs differ by less than either error bar. A pass allocates about
56 KB more, 1.5 % of the total, or some 70 bytes per `encodeText`. Both runs are `-f 4`, twenty
measurement iterations over four JVMs, and even so the allocation figure is not the reproducible
one the previous entry recorded — its error is thousands of bytes rather than fractions of one,
because the arrays are small enough that escape analysis eliminates some of them in some forks and
not in others. That is also why the comparison needs four forks: at two, the baseline's own spread
was wider than the difference being measured.


## Minor improvements

Apple M5 Pro (arm64), Temurin 25.0.2+10.

```
Benchmark                      (library)  Mode  Cnt    Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5    5.014 ± 0.028  ms/op
EncodeTextBenchmark.encodeAll     nayuki  avgt    5  145.123 ± 1.003  ms/op
EncodeTextBenchmark.encodeAll      zxing  avgt    5  218.286 ± 2.925  ms/op


Workload: 200 payloads × 4 ECC levels = 800 encodes per library

Libraries
  press    net.codecrete.qrcodepress:qr-code-press:0.10.0-SNAPSHOT
  nayuki   io.nayuki:qrcodegen:1.8.0
  zxing    com.google.zxing:core:3.5.4
```


## The workload changed here

Everything above this line was measured over a set of 200 payloads, a tenth of them long enough to
reach versions 10 to 20. Everything below it is measured over 400 payloads, a fifth of them 400 to
900 characters long, which spread the encodes over versions 1 to 36. **The two are not comparable**,
and no entry above has been restated in the new set's terms.

The set now mirrors the one the .NET library
[QrCodeGenerator](https://github.com/manuelbl/QrCodeGenerator) profiles against, so a measurement
here and a measurement there describe the same workload. The generators share no random numbers,
only the distribution.

Two things went with it:

- `SampleData` no longer caps a payload at 382 bytes. The cap kept every number in the log above
  comparable by fixing the version mix; the new set deliberately gives that up in exchange for a
  version spread that reaches the whole encoder.
- The `bands` mode and `PenaltyBandBenchmark` are gone, along with the `### bands` section that
  documented them. The mode existed because the old workload could not resolve a change confined to
  the wider `BitMatrix` row layouts — it reached version 20 at the very most. The new one carries
  15 % of its encodes in two-word rows and 5 % in three-word rows, so `benchmark` now resolves such
  a change on its own. The band tables in the entries above are left as they were measured; the
  mode that produced them no longer exists.

HEAD measured both ways, so the step across the line is a number rather than a mystery.

Apple M5 Pro (arm64), Temurin 25.0.2+10.

```
Benchmark                      (library)  Mode  Cnt   Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5   4.883 ± 0.205  ms/op     (200 payloads)
EncodeTextBenchmark.encodeAll      press  avgt    5  19.050 ± 0.490  ms/op     (400 payloads)
```

A pass costs 3.9 times as much, for twice the payloads: the long tail is the rest of it, since
penalty scoring grows with the square of the version and a fifth of the set now sits above version
11. `compare` grows worse than that — qrcodegen and ZXing go from 145 and 218 ms/op to 608 and
942 — yet a `compare` run still takes about a minute, because JMH's iterations are bounded by time
rather than by the number of operations.

`DEFAULT_PROFILE_ITERATIONS` is unchanged at 1500, which is not an oversight: the heavier workload
puts a `profile` run at 28.3 s, back on the "roughly 30 seconds" its javadoc has always claimed and
had quietly drifted below as the library got faster.

```
Profile loop: 1500 iterations × 400 payloads × 4 ECC levels
Total encodeText calls: 2'400'000
Elapsed: 00:00:28.3 (checksum=123444000)
```

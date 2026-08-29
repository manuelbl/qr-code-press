# Comparison with other Java QR code libraries

Runs the harness workload against QR Code Press, [qrcodegen](https://github.com/nayuki/QR-Code-generator)
and [ZXing](https://github.com/zxing/zxing), and reports both how fast each library encodes and what
it encoded the payloads into.

The second half matters as much as the first. The three libraries do not turn these payloads into
the same QR codes, so a timing table on its own would be read as three timings of the same work.


## Running

The mode needs the library installed first, like every other mode of the harness:

```sh
cd ../qr-code-press && ./mvnw install
```

```sh
./mvnw compile exec:exec -Dprofiling.args="compare"
```

`compare` is the mode for everything on this page. It runs JMH once per library, three rows in one
table, about 30 s end to end, and then prints the library versions, the total matrix size and the
version histogram. Only the first half is timed; the report prints the same thing on every run and
every machine.

The other two modes measure QR Code Press alone and have nothing to say here. `benchmark` compares
a change to the library against a number recorded with an earlier version, where the other two
libraries would only be a constant. `profile` is for attaching a sampling profiler, where a profile
is read frame by frame and another library's frames answer a question the profile was not opened to
ask.


## Results

Apple M5 Pro (arm64), Temurin 25.0.2+10, zxing-core 3.5.4, qrcodegen 1.8.0.

```
Benchmark                      (library)  Mode  Cnt    Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5    8.664 ± 0.129  ms/op
EncodeTextBenchmark.encodeAll     nayuki  avgt    5  145.560 ± 1.854  ms/op
EncodeTextBenchmark.encodeAll      zxing  avgt    5  223.098 ± 0.512  ms/op
```

One score is one pass over the whole set: 200 payloads at 4 error correction levels, 800 encodes.

```
Total matrix size (sum of widths, in modules — smaller is denser encoding)
  press    32204
  nayuki   32280
  zxing    32464
```

So the fastest of the three is also the one producing the smallest QR codes, by a small margin of
about 0.2 % against qrcodegen and 0.8 % against ZXing. The version histogram behind those totals is
printed under them; the three libraries agree on the version for most payloads and differ on the
ones where segment compaction or the ECI segment tips the balance.


## What is being compared

Every library is called the way its own documentation calls it, and every call stops at the module
matrix, with no border, no scaling and no image:

| | call | size read from |
|---|---|---|
| `press` | `QrCode.encodeText(text, ecc)` | `getSize()` |
| `nayuki` | `QrCode.encodeText(text, ecc)` | `size` |
| `zxing` | `Encoder.encode(text, level, {CHARACTER_SET: "UTF-8"})` | `getMatrix().getWidth()` |

Going one level higher would have meant ZXing's `QRCodeWriter`, which also allocates a scaled
matrix with a quiet zone. The other two libraries leave that to the caller at this point, and this
library does it in `toRectangles()` and the renderers rather than in `encodeText`.

The payloads are the harness's own set, unchanged and identical for all three libraries. They are
described in [README.md](README.md) and built by `SampleData`.


### Why ZXing is forced to UTF-8

Measured against **zxing-core 3.5.4**. Both figures below are properties of that version and should
be re-checked when it is upgraded.

`Encoder.encode(text, level)` without hints encodes byte mode as ISO-8859-1 and silently replaces
whatever does not fit. Of the 200 payloads, 62 contain characters outside ISO-8859-1: emoji, and
Turkish, Polish, Hungarian and Romanian names and towns. All 62 come back from a decoder as
something other than what went in. ZXing throws no exception and the QR codes are perfectly valid;
they simply carry the wrong text. Measuring that against an encoder that carries the text correctly
would compare two different jobs, so the character set hint is not a tuning choice here but the
condition for comparing like with like.

The hint has a price, and it is charged to ZXing: once set, ZXing appends the ECI header to *every*
byte-mode payload, including the 138 that ISO-8859-1 would have carried without one. QR Code Press
adds an ECI segment only when ISO-8859-1 would be lossy. ZXing does have a path that adds ECI
selectively, the `QR_COMPACT` hint, and it is unusable. Its `MinimalEncoder` throws
`WriterException: Internal error: failed to encode` on 196 of the 800 encodes, across all 47
payloads containing surrogate pairs. There is no third option through the public API.

### Why qrcodegen needs no configuration, and what it leaves out

qrcodegen carries every payload correctly without being asked to: its `encodeText` encodes byte mode
as UTF-8. But it emits no ECI segment, ever, and 79 of the 200 payloads need one.

ISO/IEC 18004 defines byte mode without an ECI segment as ISO-8859-1. A strictly conforming reader
therefore decodes qrcodegen's UTF-8 bytes as Latin-1, and

```
best-seller/730181/Justo José/Nicosia/Opening hours: ...
```

comes back as

```
best-seller/730181/Justo JosÃ©/Nicosia/Opening hours: ...
```

Omitting the ECI segment is safe only while the text is pure ASCII, where UTF-8 and ISO-8859-1 agree
byte for byte, which is 121 of the 200 payloads. The remaining 79 are misdeclared. 62 of them
contain characters outside Latin-1, and the other 17 contain nothing worse than an accented name.
Those are misdeclared just the same. `é` is one byte in ISO-8859-1 and two in UTF-8, so being
inside Latin-1 is no protection. Being inside ASCII is.

In practice these codes are usually read correctly, because most readers guess the character set
instead of trusting what the code declares. That is how the payloads survive here too, since ZXing's
decoder detects UTF-8 and recovers all 200. But the guess is outside the standard, and a reader that
declines to guess is entitled to return the mojibake above.

The omission also flatters qrcodegen's totals slightly, since the ECI header it never writes is one
that QR Code Press pays for on 62 payloads and ZXing on 195 of the 200 (every payload it does not
put in alphanumeric mode). Even so, qrcodegen sums to more matrix than QR Code Press.


### Differences left in place

Two more differences are not corrected, because they are what each library does rather than
something the harness chose. Both are visible in the totals above.

- **Error correction boosting.** QR Code Press and qrcodegen raise the error correction level when a
  higher level fits the same version, so a code nominally encoded at `LOW` is often better protected
  than that. ZXing does not.
- **Segment compaction.** QR Code Press splits a payload into segments of the cheapest mode and
  merges them while that shortens the bit stream. qrcodegen's `encodeText` picks one mode for the
  whole string, and ZXing does the same on the path measured here. Both libraries have an optimizing
  variant, `QrSegmentAdvanced.makeSegmentsOptimally` and the `QR_COMPACT` hint, and neither is what
  their `encodeText` equivalent calls.


## Caveats

The usual one for benchmarks applies twice over here. The numbers are comparable only within one
machine and one JDK, and only for this payload set. The set is chosen to exercise *this* library's
encoder, weighted towards versions 1 to 11, with about a tenth of the payloads in the version 10 to
20 range, and a set of different shape would move the ratios. It also says nothing about decoding,
about the other symbologies ZXing supports, or about anything above the module matrix.

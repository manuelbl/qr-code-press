# Comparison with other Java QR code libraries

QR Code Press is compared with other QR code generator libraries:

- [qrcodegen](https://github.com/nayuki/QR-Code-generator)
- [ZXing](https://github.com/zxing/zxing)

Each library is used with its default settings as far as possible; the differences that remain are
listed below.


## Running

The mode needs the library installed first, like every other mode of the harness:

```sh
cd ../qr-code-press && ./mvnw install
```

```sh
./mvnw compile exec:exec "-Dprofiling.args=compare"
```

`compare` is the mode for everything on this page. It runs JMH once per library, three rows in one
table, about a minute end to end, and then prints the library versions, the average QR code version
and the version histogram. Only the first half is timed; the report prints the same thing on every
run and every machine.


## Results

### Speed

Apple M5 Pro (arm64), Temurin 25.0.2+10, zxing-core 3.5.4, qrcodegen 1.8.0.

```
Benchmark                      (library)  Mode  Cnt    Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5   18.768 ± 0.516  ms/op
EncodeTextBenchmark.encodeAll     nayuki  avgt    5  614.850 ± 7.698  ms/op
EncodeTextBenchmark.encodeAll      zxing  avgt    5  940.699 ± 1.293  ms/op
```

Dell (Intel Core Ultra 5), Temurin 25.0.4.1+1, zxing-core 3.5.4, qrcodegen 1.8.0.

```
Benchmark                      (library)  Mode  Cnt     Score    Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5    17.051 ±  0.571  ms/op
EncodeTextBenchmark.encodeAll     nayuki  avgt    5   878.847 ± 26.523  ms/op
EncodeTextBenchmark.encodeAll      zxing  avgt    5  1224.043 ± 45.887  ms/op
```

One score is one pass over the whole set: 400 payloads at 4 error correction levels, 1600 encodes.

QR Code Press is about 20 to 70 times faster than the other libraries.


### QR code size

```
Average QR code version (smaller is denser encoding)
  press    8.609
  nayuki   8.784
  zxing    8.834
```

QR Code Press produces the smallest QR codes.


## What is being compared

Every library is called the way its own documentation calls it, and every call stops at the module
matrix, with no border, no scaling and no image:

| | call |
|---|---|
| `press` | `QrCode.encodeText(text, ecc)` |
| `nayuki` | `QrCode.encodeText(text, ecc)` |
| `zxing` | `Encoder.encode(text, level, {CHARACTER_SET: "UTF-8"})` |

The payloads are the harness's own set, unchanged and identical for all three libraries. They are
described in [README.md](README.md) and built by `SampleData`.


## Differences between the libraries

- *ZXing* is called through `Encoder`, not `QRCodeWriter`, which would also allocate a scaled matrix
  with a quiet zone and so measure rendering rather than encoding.

- *ZXing* is forced to UTF-8. Otherwise it silently replaces non-representable characters with `?`.
  As a side effect, it adds an ECI header to every QR code to declare the character set. QR Code
  Press uses UTF-8 only when ISO-8859-1 would be lossy, and adds the ECI header only then.

- *ZXing* does not compact data segments by default, resulting in bigger QR codes.

- *ZXing* has the `QR_COMPACT` option, which would use UTF-8 and an ECI header only if needed and
  would compact the data segments. It is unusable, though: it throws
  `WriterException: Internal error: failed to encode` for about a quarter of the sample payloads,
  and every one of those contains a Unicode surrogate pair.

- *qrcodegen* does not compact data segments by default, resulting in bigger QR codes.

- *qrcodegen* encodes text in UTF-8, increasing the size of QR codes with payloads outside the ASCII
  range but within the ISO-8859-1 range.

- *qrcodegen* does not add an ECI header to declare the use of UTF-8, which can lead to mojibake. In
  practice, most scanners guess the character set when no ECI header is present and still show the
  correct text.

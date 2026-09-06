# Comparison with other Java QR code libraries

This library is compared with other QR code generator libraries:

- [qrcodegen](https://github.com/nayuki/QR-Code-generator)
- [ZXing](https://github.com/zxing/zxing)

The libraries are compared with their default settings (as far as possible).
See details below.


## Running

The mode needs the library installed first, like every other mode of the harness:

```sh
cd ../qr-code-press && ./mvnw install
```

```sh
./mvnw compile exec:exec "-Dprofiling.args=compare"
```

`compare` is the mode for everything on this page. It runs JMH once per library, three rows in one
table, about 30 s end to end, and then prints the library versions, the total matrix size and the
version histogram. Only the first half is timed; the report prints the same thing on every run and
every machine.


## Results

### Speed

Apple M5 Pro (arm64), Temurin 25.0.2+10, zxing-core 3.5.4, qrcodegen 1.8.0.

```
Benchmark                      (library)  Mode  Cnt    Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5   18.768 ± 0.516  ms/op
EncodeTextBenchmark.encodeAll     nayuki  avgt    5  614.850 ± 7.698  ms/op
EncodeTextBenchmark.encodeAll      zxing  avgt    5  940.699 ± 1.293  ms/op
```

Dell (Intel Core Ultra 5), Temurin-25.0.4.1+1, zxing-core 3.5.4, qrcodegen 1.8.0.

```
Benchmark                      (library)  Mode  Cnt    Score   Error  Units
EncodeTextBenchmark.encodeAll      press  avgt    5    4.482 ± 0.123  ms/op
EncodeTextBenchmark.encodeAll     nayuki  avgt    5  208.636 ± 4.611  ms/op
EncodeTextBenchmark.encodeAll      zxing  avgt    5  287.485 ± 7.229  ms/op
```

One score is one pass over the whole set: 400 payloads at 4 error correction levels, 1600 encodes.

This library is about 20 to 60 times faster than the other libraries.


### QR code size

```
Total matrix size (sum of widths, in modules — smaller is denser encoding)
  press    82296
  nayuki   83420
  zxing    83736
```

This library also produces the smallest QR codes.


## What is being compared

Every library is called the way its own documentation calls it, and every call stops at the module
matrix, with no border, no scaling and no image:

| | call | size read from |
|---|---|---|
| `press` | `QrCode.encodeText(text, ecc)` | `getSize()` |
| `nayuki` | `QrCode.encodeText(text, ecc)` | `size` |
| `zxing` | `Encoder.encode(text, level, {CHARACTER_SET: "UTF-8"})` | `getMatrix().getWidth()` |

The payloads are the harness's own set, unchanged and identical for all three libraries. They are
described in [README.md](README.md) and built by `SampleData`.


## Differences between Libraries

- *ZXing* is run with `Encoder.encode(text, level, {CHARACTER_SET: "UTF-8"})`. Using `QRCodeWriter`
would also allocate a scaled matrix with a quiet zone, leading to an unfair comparison.

- *ZXing* is forced to use UTF-8. Otherwise it would silently replace non-representable characters with `?`.
As a side effect, it will add an ECI header to all QR codes to indicate the character set.
QR Code Press only uses UTF-8 if necessary, and only adds a ECI header if UTF-8 is used.

- *ZXing* does not compact data segments by default, resulting in bigger QR codes.

- *ZXing* has the `QR_COMPACT` option. It would use UTF-8 and an ECI header only if needed,
and it would compact the data segments. However, it is buggy and throws
`WriterException: Internal error: failed to encode` for about a quarter of the sample data.
It seems unable to deal with Unicode surrogate pairs.

- *qrcodegen* does not compact data segments by default resulting in bigger QR codes.

- *qrcodegen* encodes text in UTF-8, increasing the size for QR codes with payloads outside the ASCII
range but within the ISO-8859-1 range.

- *qrcodegen* does not add an ECI header to indicate the use of UTF-8. This can potentially lead
to decoding issues resulting in Mojibake. In practice, most QR code scanners handle it such that the
correct text results as they correctly guess the character set encoding if no ECI header is used.


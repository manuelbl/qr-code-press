# QR Code Press

QR Code Press is a Java library for generating QR codes.

## Main features

- Supports encoding all 40 versions (sizes) and all 4 error correction levels, as per the
  QR Code Model 2 standard
- Output formats: list of rectangles, raw modules of the QR symbol, SVG document,
  SVG/XAML graphics path, and PNG
- Computes optimal segment modes for the smallest possible QR code
- Structured Append: splits long text across up to 16 linked QR codes
- ECI support (~50 designators), including automatic Latin-1 / UTF-8 selection, and Kanji mode
- Opt-in encoding diagnostics: per-mask penalty breakdown and chosen segments
- High speed
- Compatible with Java 17
- No runtime dependencies


## Public API

### Entry points

Static factory methods cover the common cases; a fluent builder covers the full parameter set.

```java
QrCode qr = QrCode.encodeText("Hello, world!", Ecc.MEDIUM);
QrCode qr = QrCode.encodeBinary(bytes, Ecc.HIGH);
QrCode qr = QrCode.encodeSegments(segments, Ecc.LOW);

QrCode qr = QrCode.builder()
        .text("昨夜のコンサートは最高でした。")
        .errorCorrection(Ecc.QUARTILE)
        .versionRange(5, 20)
        .eci(Eci.SHIFT_JIS)
        .kanjiStrategy(KanjiStrategy.AUTOMATIC)
        .build();
```

Structured Append has a builder type of its own, so options that mean nothing for a sequence are
simply not there instead of being rejected at runtime:

```java
List<QrCode> codes = QrCodeSequence.builder()
        .text(longText)
        .errorCorrection(Ecc.MEDIUM)
        .versionRange(10, 29)
        .eci(Eci.LATIN_CYRILLIC)
        .build();
```

### Diagnostics

`QrCodeBuilder` has a second terminal method, `buildWithDiagnostics()` instead of `build()`. It
turns off the penalty early-stop and is slower, so it carries a name of its own and the cost is
visible at the call site. The forced mask is an ordinary builder input, not a diagnostic.

```java
QrCode qr = QrCode.builder().text(t).errorCorrection(Ecc.MEDIUM).forceMask(3).build();

EncodingInfo info = QrCode.builder().text(t).errorCorrection(Ecc.MEDIUM).buildWithDiagnostics();
// info.qrCode(), info.penalties() (8 entries), info.segments()
```

### Accessors and output

```java
int     getSize()
boolean getModule(int x, int y)
int     getVersion()
Ecc     getErrorCorrectionLevel()
int     getMask()

List<QrRectangle> toRectangles()
String            toSvgString(int border)                                        // black on white
String            toSvgString(int border, String foreground, String background)
String            toGraphicsPath(int border)
byte[]            toPng(int border, int scale)                                   // black on white
byte[]            toPng(int border, int scale, int foreground, int background)
```

There is no bulk module accessor. `toRectangles()` covers bulk rendering.

### Errors

Unchecked and rooted in the JDK types:

- `DataTooLongException extends IllegalArgumentException`
- `EciException extends IllegalArgumentException`
- `null` arguments → `NullPointerException` (via `Objects.requireNonNull`)
- out-of-range arguments → `IllegalArgumentException`

Acronyms follow modern Java style: `Eci`, not `ECI`.

### Immutability and thread safety

`QrCode` and `DataSegment` are immutable and safe to share between threads. All factory methods and
builder terminal methods are safe to call concurrently. A single builder *instance* is not
thread-safe, per the usual Java convention.

Byte payloads are copied **once** when they cross the public API boundary; internal segments are
zero-copy views into arrays the library owns.

## PNG and the optional AWT bridge

The PNG encoder has no dependencies and writes a 1-bit indexed PNG with a two-entry palette. That
keeps the core on `java.base` alone, so the library works on Android, in minimal `jlink` images and
in headless container JVMs.

The `net.codecrete.qrcodepress.awt` package adds `BufferedImage` and `Graphics2D` helpers for
callers already in AWT or Swing: `QrCodeGraphics.toBufferedImage(qrCode, border, scale, …)` and
`QrCodeGraphics.draw(qrCode, graphics, border, scale, …)`. The image is 1-bit indexed like the PNG
and honours a translucent or fully transparent colour.

The package is optional, declared in `module-info.java` as:

```java
module net.codecrete.qrcodepress {
    requires static java.desktop;
    exports net.codecrete.qrcodepress;
    exports net.codecrete.qrcodepress.awt;
}
```

## Charsets

The library resolves an ECI designator to a `Charset` lazily, only when it is used. Java guarantees
only US-ASCII, ISO-8859-1, UTF-8 and UTF-16*; everything else (Shift_JIS, IBM437, ISO-8859-2…6, …)
lives in the `jdk.charsets` module, which some minimal runtimes omit. When a charset is
unavailable, the library throws `EciException` naming the charset and the `jdk.charsets`
requirement, rather than a bare `UnsupportedCharsetException`. Passing an explicit `Charset` to the
builder always bypasses resolution.

A sequence accepts single-byte character sets and UTF-8 only. Each of its QR codes has to decode on
its own, so the text may only be cut where a character ends. Shift-JIS, Big5, GB 18030 and UTF-16
are not supported, and neither is Kanji mode.

## Performance

Speed is a main goal. It comes from:

- QR codes stored as packed bit arrays (`long[]`), one bit per module
- bit operations covering 64 modules at once
- the in-place 64×64 delta-swap transpose, so column-wise penalty rules reuse the row algorithm
- penalty scoring that stops as soon as the running score exceeds the best mask so far
- per-version caches of the payload zigzag, the generator polynomial multiples for error correction,
  the fixed patterns and the mask patterns

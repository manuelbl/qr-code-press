# QR Code Press

Easy to use and super fast QR code generator for Java.

## Features

- All 40 versions (sizes) and all 4 error correction levels of the QR Code Model 2 standard
- Output as a list of rectangles, raw modules, SVG document, SVG/XAML graphics path, or PNG
- Picks the segment modes that yield the smallest possible QR code
- Structured Append: long text split across up to 16 linked QR codes
- ECI support, including automatic Latin-1 / UTF-8 selection, and Kanji mode
- Optional AWT bridge for `BufferedImage` and `Graphics2D`, with transparency
- Opt-in encoding diagnostics: per-mask penalty breakdown and chosen segments
- Java 17 or higher, no runtime dependencies

## Getting started

```xml
<dependency>
    <groupId>net.codecrete.qrcodepress</groupId>
    <artifactId>qr-code-press</artifactId>
    <version>0.9.0</version>
</dependency>
```

```java
import net.codecrete.qrcodepress.Ecc;
import net.codecrete.qrcodepress.QrCode;

var qrCode = QrCode.encodeText("Hello, world!", Ecc.MEDIUM);
Files.write(Path.of("hello.png"), qrCode.toPng(4, 10));
```

That writes a PNG with a border of 4 modules and 10×10 pixels per module.

## Creating a QR code

Static factory methods cover the common cases:

```java
QrCode.encodeText("https://www.example.com", Ecc.MEDIUM);
QrCode.encodeBinary(bytes, Ecc.HIGH);
QrCode.encodeSegments(segments, Ecc.LOW);
```

The builder covers everything else:

```java
var qrCode = QrCode.builder()
        .text("昨夜のコンサートは最高でした。")
        .errorCorrection(Ecc.QUARTILE)
        .versionRange(5, 20)
        .eci(Eci.SHIFT_JIS)
        .kanjiStrategy(KanjiStrategy.AUTOMATIC)
        .build();
```

By default the library picks the smallest version that fits and then raises the error correction
level as far as that version allows, so you get more error correction for free.

## Output formats

```java
byte[] png  = qrCode.toPng(4, 10);                          // black on white
byte[] png2 = qrCode.toPng(4, 10, 0x00335c, 0xf5f5f5);      // 0xRRGGBB colors

String svg  = qrCode.toSvgString(4);
String svg2 = qrCode.toSvgString(4, "#00335c", "white");    // any CSS color

String path = qrCode.toGraphicsPath(4);                     // for SVG or XAML
```

For a graphics library not supported directly, ask for the dark modules as rectangles. Adjacent
modules are merged, so there are far fewer rectangles than modules:

```java
for (var r : qrCode.toRectangles()) {
    graphics.fillRect(r.x(), r.y(), r.width(), r.height());
}
```

Or read the modules one by one. Coordinates outside the QR code are light, so the border needs no
special case:

```java
for (int y = -4; y < qrCode.getSize() + 4; y++) {
    for (int x = -4; x < qrCode.getSize() + 4; x++) {
        boolean dark = qrCode.getModule(x, y);
    }
}
```

### AWT

If you already work with AWT or Swing, the optional `net.codecrete.qrcodepress.awt` package saves a
round trip through PNG bytes. It is the only part of the library that needs `java.desktop`, and the
only one that supports translucent colors:

```java
var image = QrCodeGraphics.toBufferedImage(qrCode, 4, 10);
QrCodeGraphics.draw(qrCode, graphics2D, 4, 10, Color.BLACK, new Color(0, 0, 0, 0));
```

## Long text: Structured Append

The library splits text too long for a single QR code across up to 16 linked QR codes. A scanner
reads them in any order and reassembles the text.

```java
List<QrCode> codes = QrCodeSequence.builder()
        .text(longText)
        .errorCorrection(Ecc.MEDIUM)
        .versionRange(10, 29)
        .build();
```

The library spreads the text as evenly as possible over the fewest QR codes. If it fits into a
single one, you get a single one, without the Structured Append overhead.

## Character encoding

The library encodes text in ISO-8859-1 when possible, and in UTF-8 with an ECI segment otherwise.
That is what scanners expect and needs no configuration.

Pass an explicit designator to override it, for instance `Eci.SHIFT_JIS` for Japanese text or
`Eci.UTF_8` to force UTF-8. Kanji mode packs two Japanese characters into 13 bits and applies
automatically to Shift-JIS text; `kanjiStrategy(...)` forces it on or off.

The library resolves an ECI designator to a `Charset` only when it needs one. Java guarantees only
US-ASCII, ISO-8859-1, UTF-8 and UTF-16; the rest live in the `jdk.charsets` module, which some
minimal runtimes leave out. When a charset is missing, the library throws an `EciException` naming
it and the missing module rather than a bare `UnsupportedCharsetException`.

## Diagnostics

Diagnostics report the penalty score of each of the eight mask patterns and the segments the
library split the text into:

```java
var info = QrCode.builder().text(text).errorCorrection(Ecc.MEDIUM).buildWithDiagnostics();
info.qrCode();
info.penalties();   // 8 entries
info.segments();
```

This is slower than `build()`, because it scores every mask in full. To pin a specific mask
instead, use `forceMask(3)` on the builder. That is an ordinary option and costs nothing.


## Documentation

- [Javadoc](https://javadoc.io/doc/net.codecrete.qrcodepress/qr-code-press)


## Examples

| Example | Demonstrates |
|---|---|
| [examples/basic-qr-codes/](https://github.com/manuelbl/qr-code-press/tree/v0.9.0/examples/basic-qr-codes/) | various QR codes as SVG or PNG files, with binary data and emojis, with and without ECI designator, colored modules |
| [examples/sequence-svg/](https://github.com/manuelbl/qr-code-press/tree/v0.9.0/examples/sequence-svg/) | splitting a long text across a sequence of Structured Append QR codes and composing them into a single SVG file as a horizontal row, scaled to a fixed physical size |
| [examples/awt-drawing/](https://github.com/manuelbl/qr-code-press/tree/v0.9.0/examples/awt-drawing/) | rendering a styled QR code using AWT and Graphics2D |

Each example is an independent Maven project.

## Building

```sh
cd qr-code-press
./mvnw verify
```

Requires JDK 17 or higher. `verify` runs the tests and lints the javadoc.

## License

MIT License. See [LICENSE](LICENSE).

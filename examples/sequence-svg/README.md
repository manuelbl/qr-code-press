# Sequence to SVG

Renders a text too long for a single QR code as a row of *Structured Append* QR codes in a single
SVG file.

The excerpt from Mary Shelley's *Frankenstein* (1818, public domain) does not fit into one QR code.
`QrCodeSequence` splits it across four linked QR codes, which a scanner supporting Structured
Append reassembles into the original text.

What the example demonstrates:

- `QrCodeSequence.builder()` and how the version range decides how many QR codes are needed
- composing several QR codes into one document with `QrCode.toGraphicsPath(0)`, rather than
  emitting one SVG per QR code with `QrCode.toSvgString(...)`
- scaling from module coordinates to millimetres, so the document has a defined physical size no
  matter which version the library picks
- treating the quiet zone as a property of the layout: the QR codes are rendered without a border
  of their own and the spacing of the row provides it

## Running

The library is resolved from the local Maven repository, so install it first:

```sh
cd ../../qr-code-press
./mvnw install
```

Then run the example:

```sh
./mvnw compile exec:java
```

It writes `qr-code-sequence.svg` into the current directory: four QR codes of 60 mm, 20 mm apart
and 20 mm from the edges, black on white.

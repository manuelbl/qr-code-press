# Basic QR codes

Generates several QR codes in different formats.


What the example demonstrates:

- Generating QR codes as SVG or PNG files
- QR codes with only digits or letters, using a more compact encoding
- QR codes with moderately long text
- QR codes with emojis (with and without ECI designator)
- QR code with binary data
- SVGs and PNGs with colored modules


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

It writes multiple SVG and PNG files to the project directory.

# AWT Drawing

Creates a QR code and renders it into a buffered image using AWT and Graphics2D.

Several stylings are applied:

- The QR code modules (pixels) are drawn a circles.
- The finder marks in three corners of the QR code are drawn as rectangles with rounded corners.
- The modules and find marks are colored using a diagonal color gradient.
- An image is loaded and drawn in the center of the QR code.

The rendered QR code is then saved as a PNG image.

What the example demonstrates:

- `QrCode.getModule()` to access the individual modules
- creating an offscreen bitmap, drawing to it and saving it as a PNG file

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

It writes `qrCode.png` into the current directory.

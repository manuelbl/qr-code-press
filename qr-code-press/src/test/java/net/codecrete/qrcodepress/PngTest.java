/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.codecrete.qrcodepress.testsupport.QrImageDecoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The dependency-free PNG encoder.
 * <p>
 * Three layers of assertion: the chunk structure and pixel data are pinned against committed
 * snapshots, the file is decoded again by {@code ImageIO} (an independent decoder) and compared to
 * the modules, and the checksums are recomputed from scratch.
 * </p>
 */
class PngTest {

    private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

    @Nested
    @DisplayName("snapshots")
    class Snapshots {

        @Test
        @DisplayName("black on white matches the committed snapshot")
        void blackOnWhite() {
            var png = QrCode.encodeText("Hello, world!", Ecc.MEDIUM).toPng(4, 3);

            assertMatchesSnapshot(png, "hello-world.png");
        }

        @Test
        @DisplayName("a colored QR code matches the committed snapshot")
        void colored() {
            var png = QrCode.encodeText("The quick brown fox", Ecc.MEDIUM)
                    .toPng(2, 5, 0x171c80, 0xccbc9b);

            assertMatchesSnapshot(png, "fox-colored.png");
        }

        /**
         * Compares a PNG to a snapshot chunk by chunk.
         * <p>
         * Everything but the compressed pixel data is compared byte for byte. The pixel data is
         * compared decompressed, because the compressed form comes from {@link java.util.zip
         * .Deflater} &mdash; the JDK's zlib, whose exact output is not this library's to promise.
         * The pixels are.
         * </p>
         */
        private void assertMatchesSnapshot(byte[] png, String snapshotName) {
            var actual = PngImage.parse(png);
            var expected = PngImage.parse(RenderSnapshots.bytes(snapshotName));

            assertThat(actual.chunkTypes()).isEqualTo(expected.chunkTypes());
            assertThat(actual.chunk("IHDR")).isEqualTo(expected.chunk("IHDR"));
            assertThat(actual.chunk("PLTE")).isEqualTo(expected.chunk("PLTE"));
            assertThat(actual.chunk("IEND")).isEqualTo(expected.chunk("IEND"));
            assertThat(actual.pixelData()).isEqualTo(expected.pixelData());
        }
    }

    @Nested
    @DisplayName("file structure")
    class Structure {

        private final PngImage png = PngImage.parse(
                QrCode.encodeText("Hello, world!", Ecc.MEDIUM).toPng(4, 3));

        @Test
        @DisplayName("holds exactly the chunks a 1-bit indexed image needs, in order")
        void chunkSequence() {
            assertThat(png.chunkTypes()).containsExactly("IHDR", "PLTE", "IDAT", "IEND");
        }

        @Test
        @DisplayName("declares a 1-bit indexed image of the expected size")
        void header() {
            var header = png.chunk("IHDR");

            var imageSize = (21 + 2 * 4) * 3;
            assertThat(readInt(header, 0)).isEqualTo(imageSize); // width
            assertThat(readInt(header, 4)).isEqualTo(imageSize); // height
            assertThat(header[8]).isEqualTo((byte) 1); // bit depth
            assertThat(header[9]).isEqualTo((byte) 3); // color type: indexed
            assertThat(header[10]).isEqualTo((byte) 0); // compression method: deflate
            assertThat(header[11]).isEqualTo((byte) 0); // filter method
            assertThat(header[12]).isEqualTo((byte) 0); // interlace method: none
        }

        @Test
        @DisplayName("holds the light color at palette index 0 and the dark one at index 1")
        void palette() {
            var colored = PngImage.parse(
                    QrCode.encodeText("A", Ecc.MEDIUM).toPng(0, 1, 0x171c80, 0xccbc9b));

            assertThat(colored.chunk("PLTE")).containsExactly(
                    0xcc, 0xbc, 0x9b, // background, and so palette index 0
                    0x17, 0x1c, 0x80);
        }

        @Test
        @DisplayName("uses the unfiltered scan lines the palette indices are read from directly")
        void scanlines() {
            var qrCode = QrCode.encodeText("Hello, world!", Ecc.MEDIUM);
            var imageSize = (qrCode.getSize() + 2 * 4) * 3;
            var bytesPerLine = (imageSize + 7) / 8 + 1;

            var pixels = png.pixelData();

            assertThat(pixels).hasSize(bytesPerLine * imageSize);
            for (var y = 0; y < imageSize; y += 1) {
                assertThat(pixels[y * bytesPerLine])
                        .withFailMessage("scanline %d is filtered", y)
                        .isEqualTo((byte) 0);
            }
        }

        @Test
        @DisplayName("carries a correct CRC on every chunk")
        void checksums() {
            // PngImage.parse verifies them, so reaching here is the assertion; that it has teeth
            // is shown by flipping a byte of the pixel data.
            var corrupted = QrCode.encodeText("Hello, world!", Ecc.MEDIUM).toPng(4, 3);
            corrupted[corrupted.length - 20] ^= 0x01;

            assertThatIllegalArgumentException().isThrownBy(() -> PngImage.parse(corrupted))
                    .withMessageContaining("CRC");
        }
    }

    @Nested
    @DisplayName("decoded again")
    class RoundTrip {

        @ParameterizedTest
        @CsvSource({
                "A, 0, 1",
                "'Hello, world!', 4, 3",
                "https://github.com/manuelbl/qr-code-press, 2, 7",
                "'At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis "
                        + "praesentium voluptatum deleniti atque corrupti quos dolores.', 4, 2"
        })
        @DisplayName("every pixel matches the module it belongs to")
        void pixelsMatchTheModules(String text, int border, int scale) {
            var qrCode = QrCode.encodeText(text, Ecc.MEDIUM);
            var foreground = 0x171c80;
            var background = 0xccbc9b;

            var image = read(qrCode.toPng(border, scale, foreground, background));

            var imageSize = (qrCode.getSize() + 2 * border) * scale;
            assertThat(image.getWidth()).isEqualTo(imageSize);
            assertThat(image.getHeight()).isEqualTo(imageSize);
            for (var y = 0; y < imageSize; y += 1) {
                for (var x = 0; x < imageSize; x += 1) {
                    var dark = qrCode.getModule(x / scale - border, y / scale - border);
                    assertThat(image.getRGB(x, y) & 0xffffff)
                            .withFailMessage("pixel (%d, %d) should be %s", x, y,
                                    dark ? "dark" : "light")
                            .isEqualTo(dark ? foreground : background);
                }
            }
        }

        @Test
        @DisplayName("a scanner reads the rendered image back")
        void staysDecodable() {
            var text = "Hello, world!";
            var qrCode = QrCode.encodeText(text, Ecc.MEDIUM);

            var image = read(qrCode.toPng(4, 4));

            assertThat(QrImageDecoder.decode(image)).isEqualTo(text);
        }

        private BufferedImage read(byte[] png) {
            try {
                var image = ImageIO.read(new ByteArrayInputStream(png));
                assertThat(image).isNotNull();
                return image;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Nested
    @DisplayName("arguments")
    class ArgumentChecks {

        private final QrCode qrCode = QrCode.encodeText("A", Ecc.MEDIUM);

        @Test
        @DisplayName("a negative border is rejected")
        void rejectsNegativeBorder() {
            assertThatIllegalArgumentException().isThrownBy(() -> qrCode.toPng(-1, 1))
                    .withMessageContaining("border");
        }

        @Test
        @DisplayName("a scale below one is rejected")
        void rejectsNonPositiveScale() {
            assertThatIllegalArgumentException().isThrownBy(() -> qrCode.toPng(0, 0))
                    .withMessageContaining("scale");
        }

        @Test
        @DisplayName("a border or scale that would overrun the image size is rejected")
        void rejectsTooLargeImages() {
            assertThatIllegalArgumentException().isThrownBy(() -> qrCode.toPng(0, 100_000))
                    .withMessageContaining("32768");
            assertThatIllegalArgumentException().isThrownBy(() -> qrCode.toPng(100_000, 1))
                    .withMessageContaining("32768");
            // the product would be negative if it were computed in int arithmetic
            assertThatIllegalArgumentException().isThrownBy(() -> qrCode.toPng(0, 200_000_000))
                    .withMessageContaining("32768");
            // and it wraps around in long arithmetic too if both factors are large
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> qrCode.toPng(Integer.MAX_VALUE, Integer.MAX_VALUE))
                    .withMessageContaining("32768");
        }

        @Test
        @DisplayName("the largest allowed image is still produced")
        void acceptsTheLargestImage() {
            // A QR code has an odd number of modules, so no border and scale hit the limit exactly;
            // the largest image a version 1 code reaches is 1560 pixels per module.
            var scale = PngBuilder.MAX_IMAGE_SIZE / 21;

            var png = qrCode.toPng(0, scale);

            assertThat(readInt(PngImage.parse(png).chunk("IHDR"), 0)).isEqualTo(21 * scale);
            assertThatIllegalArgumentException().isThrownBy(() -> qrCode.toPng(0, scale + 1));
        }

        @Test
        @DisplayName("a color with bits above 0xRRGGBB is rejected rather than truncated")
        void rejectsColorsBeyondRgb() {
            // an ARGB value, as java.awt.Color.getRGB() returns it
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> qrCode.toPng(0, 1, 0xff171c80, 0xccbc9b))
                    .withMessageContaining("foreground");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> qrCode.toPng(0, 1, 0x171c80, 0x80ccbc9b))
                    .withMessageContaining("background");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> qrCode.toPng(0, 1, -1, 0xccbc9b))
                    .withMessageContaining("foreground");
        }

        @Test
        @DisplayName("the full 0xRRGGBB range is accepted")
        void acceptsTheFullRgbRange() {
            assertThat(qrCode.toPng(0, 1, 0x000000, 0xffffff)).isNotEmpty();
        }
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
    }

    /**
     * A parsed PNG file: the chunks, with their CRCs verified.
     * <p>
     * Parsing the file rather than trusting the encoder is the point &mdash; this reads the bytes
     * the way the PNG specification says a decoder must.
     * </p>
     */
    private record PngImage(List<String> chunkTypes, List<byte[]> chunkData) {

        static PngImage parse(byte[] png) {
            assertThat(png).startsWith(SIGNATURE);

            var types = new ArrayList<String>();
            var data = new ArrayList<byte[]>();

            var offset = SIGNATURE.length;
            while (offset < png.length) {
                var length = readInt(png, offset);
                var type = new String(png, offset + 4, 4, StandardCharsets.US_ASCII);
                var content = Arrays.copyOfRange(png, offset + 8, offset + 8 + length);

                var crc = new CRC32();
                crc.update(png, offset + 4, length + 4);
                if (crc.getValue() != (readInt(png, offset + 8 + length) & 0xffffffffL)) {
                    throw new IllegalArgumentException("chunk " + type + " has a wrong CRC");
                }

                types.add(type);
                data.add(content);
                offset += length + 12;
            }

            assertThat(offset).as("the last chunk ends at the end of the file")
                    .isEqualTo(png.length);
            return new PngImage(types, data);
        }

        byte[] chunk(String type) {
            var index = chunkTypes.indexOf(type);
            assertThat(index).as("chunk " + type).isNotNegative();
            return chunkData.get(index);
        }

        /** Returns the decompressed pixel data: the scanlines with their filter bytes. */
        @SuppressWarnings("resource")  // Inflater is not an AutoCloseable in JDK 17
        byte[] pixelData() {
            var inflater = new Inflater();
            try {
                inflater.setInput(chunk("IDAT"));
                var output = new ByteArrayOutputStream();
                var buffer = new byte[8192];
                while (!inflater.finished()) {
                    output.write(buffer, 0, inflater.inflate(buffer));
                }
                return output.toByteArray();
            } catch (DataFormatException e) {
                throw new IllegalArgumentException("IDAT is not a valid zlib stream", e);
            } finally {
                inflater.end();
            }
        }
    }
}

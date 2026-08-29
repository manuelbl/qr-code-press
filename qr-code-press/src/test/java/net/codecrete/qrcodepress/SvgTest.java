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
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * The SVG document and the SVG/XAML graphics path.
 * <p>
 * Both are pinned against committed snapshots, so any change to the markup shows up as a diff
 * rather than as a QR code that silently stopped scanning.
 * </p>
 */
class SvgTest {

    private static final String URL = "https://github.com/manuelbl/qr-code-press";

    @Nested
    @DisplayName("SVG document")
    class SvgDocument {

        @Test
        @DisplayName("matches the committed snapshot")
        void matchesTheSnapshot() {
            var qrCode = QrCode.encodeText("Hello, world!", Ecc.MEDIUM);

            assertThat(qrCode.toSvgString(4)).isEqualTo(RenderSnapshots.text("hello-world.svg"));
        }

        @Test
        @DisplayName("sizes the view box to the QR code plus twice the border")
        void viewBoxIncludesTheBorder() {
            var qrCode = QrCode.encodeText(URL, Ecc.QUARTILE); // version 4, so 33 modules

            assertThat(qrCode.toSvgString(0)).contains("viewBox=\"0 0 33 33\"");
            assertThat(qrCode.toSvgString(4)).contains("viewBox=\"0 0 41 41\"");
        }

        @Test
        @DisplayName("uses the specified colors")
        void usesTheSpecifiedColors() {
            var svg = QrCode.encodeText("A", Ecc.MEDIUM).toSvgString(2, "#ff0000", "rgb(0, 0, 0)");

            assertThat(svg)
                    .contains("fill=\"rgb(0, 0, 0)\"/>")
                    .contains("fill=\"#ff0000\"/>")
                    .doesNotContain("#ffffff");
        }

        @Test
        @DisplayName("uses Unix line endings, whatever the platform")
        void usesUnixLineEndings() {
            var svg = QrCode.encodeText("A", Ecc.MEDIUM).toSvgString(0);

            assertThat(svg).doesNotContain("\r").endsWith("</svg>\n");
        }
    }

    @Nested
    @DisplayName("graphics path")
    class GraphicsPath {

        @Test
        @DisplayName("matches the committed snapshot")
        void matchesTheSnapshot() {
            var qrCode = QrCode.encodeText(URL, Ecc.QUARTILE);

            assertThat(qrCode.toGraphicsPath(3)).isEqualTo(RenderSnapshots.text("url.path"));
        }

        @Test
        @DisplayName("draws one closed sub-path per rectangle, offset by the border")
        void drawsOneSubPathPerRectangle() {
            var qrCode = QrCode.encodeText("A", Ecc.MEDIUM);

            var path = qrCode.toGraphicsPath(3);

            // the first rectangle is the top row of the top left finder pattern
            assertThat(path).startsWith("M3,3h7v1h-7z ").endsWith("z");
            assertThat(path.split("z", -1)).hasSize(qrCode.toRectangles().size() + 1);
        }

        @Test
        @DisplayName("is the same path the SVG document embeds")
        void isThePathOfTheSvgDocument() {
            var qrCode = QrCode.encodeText("A", Ecc.MEDIUM);

            assertThat(qrCode.toSvgString(4)).contains("d=\"" + qrCode.toGraphicsPath(4) + "\"");
        }

        @ParameterizedTest
        @ValueSource(strings = { "en-US", "de-DE", "tr-TR", "ar-EG", "hi-IN-u-nu-deva" })
        @DisplayName("does not depend on the default locale")
        void doesNotDependOnTheLocale(String languageTag) {
            // Locales differ in their digits and in their minus sign, and the path has both.
            var qrCode = QrCode.encodeText("A", Ecc.MEDIUM);
            var expected = qrCode.toGraphicsPath(0);
            var saved = Locale.getDefault();

            try {
                Locale.setDefault(Locale.forLanguageTag(languageTag));
                assertThat(qrCode.toGraphicsPath(0)).isEqualTo(expected).startsWith("M0,0h7v1h-7z");
            } finally {
                Locale.setDefault(saved);
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
            assertThatIllegalArgumentException().isThrownBy(() -> qrCode.toSvgString(-1))
                    .withMessageContaining("border");
            assertThatIllegalArgumentException().isThrownBy(() -> qrCode.toGraphicsPath(-1))
                    .withMessageContaining("border");
        }

        @Test
        @DisplayName("a border of zero is allowed")
        void acceptsZeroBorder() {
            assertThat(qrCode.toSvgString(0)).contains("viewBox=\"0 0 21 21\"");
            assertThat(qrCode.toGraphicsPath(0)).startsWith("M0,0");
        }

        @Test
        @DisplayName("null colors are rejected")
        void rejectsNullColors() {
            assertThatNullPointerException().isThrownBy(() -> qrCode.toSvgString(0, null, "#fff"));
            assertThatNullPointerException().isThrownBy(() -> qrCode.toSvgString(0, "#000", null));
        }
    }
}

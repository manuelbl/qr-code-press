/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.Charset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Pins the ECI designator to character set mapping.
 * <p>
 * The expected character sets are named by their JDK canonical name, so the test fails if a
 * designator is silently mapped to a different character set than intended &mdash; the JDK name and
 * the ECI name differ in several cases.
 * </p>
 */
class EciTest {

    static Stream<Arguments> eciWithCharset() {
        return Stream.of(
                arguments(Eci.CODE_PAGE_437, "IBM437"),
                arguments(Eci.LATIN_1, "ISO-8859-1"),
                arguments(Eci.ISO_8859_1, "ISO-8859-1"),
                arguments(Eci.LATIN_2, "ISO-8859-2"),
                arguments(Eci.ISO_8859_2, "ISO-8859-2"),
                arguments(Eci.LATIN_3, "ISO-8859-3"),
                arguments(Eci.ISO_8859_3, "ISO-8859-3"),
                arguments(Eci.LATIN_4, "ISO-8859-4"),
                arguments(Eci.ISO_8859_4, "ISO-8859-4"),
                arguments(Eci.LATIN_CYRILLIC, "ISO-8859-5"),
                arguments(Eci.ISO_8859_5, "ISO-8859-5"),
                arguments(Eci.LATIN_ARABIC, "ISO-8859-6"),
                arguments(Eci.ISO_8859_6, "ISO-8859-6"),
                arguments(Eci.LATIN_GREEK, "ISO-8859-7"),
                arguments(Eci.ISO_8859_7, "ISO-8859-7"),
                arguments(Eci.LATIN_HEBREW, "ISO-8859-8"),
                arguments(Eci.ISO_8859_8, "ISO-8859-8"),
                arguments(Eci.LATIN_5, "ISO-8859-9"),
                arguments(Eci.ISO_8859_9, "ISO-8859-9"),
                arguments(Eci.LATIN_THAI, "x-iso-8859-11"),
                arguments(Eci.ISO_8859_11, "x-iso-8859-11"),
                arguments(Eci.LATIN_7, "ISO-8859-13"),
                arguments(Eci.ISO_8859_13, "ISO-8859-13"),
                arguments(Eci.LATIN_9, "ISO-8859-15"),
                arguments(Eci.ISO_8859_15, "ISO-8859-15"),
                arguments(Eci.LATIN_10, "ISO-8859-16"),
                arguments(Eci.ISO_8859_16, "ISO-8859-16"),
                arguments(Eci.SHIFT_JIS, "Shift_JIS"),
                arguments(Eci.WINDOWS_1250, "windows-1250"),
                arguments(Eci.WINDOWS_1251, "windows-1251"),
                arguments(Eci.WINDOWS_1252, "windows-1252"),
                arguments(Eci.WINDOWS_1256, "windows-1256"),
                arguments(Eci.UTF_16BE, "UTF-16BE"),
                arguments(Eci.UTF_8, "UTF-8"),
                arguments(Eci.US_ASCII, "US-ASCII"),
                arguments(Eci.BIG5, "Big5"),
                arguments(Eci.GB2312, "GB2312"),
                arguments(Eci.KS_X_1001, "EUC-KR"),
                arguments(Eci.GBK, "GBK"),
                arguments(Eci.GB18030, "GB18030"),
                arguments(Eci.UTF_16LE, "UTF-16LE"),
                arguments(Eci.UTF_32BE, "UTF-32BE"),
                arguments(Eci.UTF_32LE, "UTF-32LE")
        );
    }

    @ParameterizedTest(name = "ECI {0} is {1}")
    @MethodSource("eciWithCharset")
    @DisplayName("the associated character set is available and is the expected one")
    void charsetIsTheExpectedOne(Eci eci, String charsetName) {
        assertThat(eci.getCharset().name()).isEqualTo(charsetName);
    }

    @ParameterizedTest(name = "ECI {0}")
    @MethodSource("eciWithCharset")
    @DisplayName("the character set is resolved once and cached")
    void charsetIsCached(Eci eci, String ignoredCharsetName) {
        assertThat(eci.getCharset()).isSameAs(eci.getCharset());
    }

    static Stream<Eci> eciWithoutCharset() {
        return Stream.of(Eci.NONE, Eci.AUTOMATIC, Eci.ISO_646_INV, Eci.BINARY_DATA,
                Eci.of(14), Eci.of(19), Eci.of(36), Eci.of(100), Eci.of(Eci.MAX_VALUE));
    }

    @ParameterizedTest(name = "ECI {0}")
    @MethodSource("eciWithoutCharset")
    @DisplayName("a designator without a character set fails, naming the value")
    void charsetWithoutAssociationFails(Eci eci) {
        assertThatExceptionOfType(EciException.class)
                .isThrownBy(eci::getCharset)
                .withMessageContaining("Unsupported ECI value " + eci.getValue())
                .withMessageContaining("not associated with a character set");
    }

    static Stream<Arguments> eciWithUnavailableCharset() {
        return Stream.of(
                arguments(Eci.LATIN_6, "ISO-8859-10"),
                arguments(Eci.ISO_8859_10, "ISO-8859-10"),
                arguments(Eci.LATIN_8, "ISO-8859-14"),
                arguments(Eci.ISO_8859_14, "ISO-8859-14")
        );
    }

    @ParameterizedTest(name = "ECI {0} would be {1}")
    @MethodSource("eciWithUnavailableCharset")
    @DisplayName("an absent character set fails, naming the character set and jdk.charsets")
    void charsetUnavailableFails(Eci eci, String charsetName) {
        // guards the test itself: these must be absent for the assertion below to mean anything
        assertThat(Charset.isSupported(charsetName)).isFalse();

        assertThatExceptionOfType(EciException.class)
                .isThrownBy(eci::getCharset)
                .withMessageContaining("Unsupported ECI value " + eci.getValue())
                .withMessageContaining('"' + charsetName + '"')
                .withMessageContaining("not available in this Java runtime")
                .withMessageContaining("jdk.charsets");
    }

    @Test
    @DisplayName("EciException is an IllegalArgumentException")
    void eciExceptionIsIllegalArgument() {
        assertThatIllegalArgumentException().isThrownBy(Eci.BINARY_DATA::getCharset);
    }

    @Test
    @DisplayName("the character set actually encodes text of its script")
    void charsetEncodesItsScript() {
        assertThat("é".getBytes(Eci.LATIN_1.getCharset())).containsExactly(0xe9);
        assertThat("é".getBytes(Eci.UTF_8.getCharset())).containsExactly(0xc3, 0xa9);
        assertThat("日".getBytes(Eci.SHIFT_JIS.getCharset())).containsExactly(0x93, 0xfa);
    }

    @ParameterizedTest(name = "value {0}")
    @ValueSource(ints = { 0, 1, 26, 899, 999999 })
    @DisplayName("of() accepts every encodable value")
    void ofAcceptsEncodableValues(int value) {
        assertThat(Eci.of(value).getValue()).isEqualTo(value);
    }

    @ParameterizedTest(name = "value {0}")
    @ValueSource(ints = { -1000000, -3, Eci.AUTOMATIC_VALUE, Eci.NONE_VALUE, 1000000, Integer.MAX_VALUE })
    @DisplayName("of() rejects values that cannot be encoded, including the sentinels")
    void ofRejectsUnencodableValues(int value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Eci.of(value))
                .withMessageContaining("between 0 and 999999");
    }

    @SuppressWarnings({ "EqualsWithItself", "AssertBetweenInconvertibleTypes" })
    @Test
    @DisplayName("designators are equal if their values are equal")
    void equalityIsByValue() {
        assertThat(Eci.UTF_8)
                .isEqualTo(Eci.UTF_8)
                .isEqualTo(Eci.of(26))
                .hasSameHashCodeAs(Eci.of(26))
                .isNotEqualTo(Eci.LATIN_1)
                .isNotEqualTo(Eci.of(100))
                .isNotEqualTo(null)
                .isNotEqualTo(26)
                .isNotEqualTo("Eci.UTF_8");
    }

    @Test
    @DisplayName("the aliased designators are the same instance")
    void aliasesAreTheSameInstance() {
        assertThat(Eci.ISO_8859_1).isSameAs(Eci.LATIN_1);
        assertThat(Eci.ISO_8859_11).isSameAs(Eci.LATIN_THAI);
        assertThat(Eci.ISO_8859_16).isSameAs(Eci.LATIN_10);
    }

    @Test
    @DisplayName("the hash code is the ECI value")
    void hashCodeIsTheValue() {
        assertThat(Eci.LATIN_1).hasSameHashCodeAs(3);
        assertThat(Eci.UTF_8).hasSameHashCodeAs(26);
        assertThat(Eci.GBK).hasSameHashCodeAs(31);
    }

    @Test
    @DisplayName("toString names the sentinels and shows the value otherwise")
    void toStringIsInformative() {
        assertThat(Eci.NONE).hasToString("Eci.NONE");
        assertThat(Eci.AUTOMATIC).hasToString("Eci.AUTOMATIC");
        assertThat(Eci.UTF_8).hasToString("Eci(26)");
        assertThat(Eci.of(899)).hasToString("Eci(899)");
    }
}

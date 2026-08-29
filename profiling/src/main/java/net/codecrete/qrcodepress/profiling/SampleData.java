/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress.profiling;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * Deterministic set of real-world payloads for measuring {@code QrCode.encodeText}.
 * <p>
 * Each pool stands for an encoder path: ASCII URLs go into alphanumeric and byte compaction,
 * accented names into ISO-8859-1, Turkish and Polish names and the emoji messages into UTF-8 with an
 * ECI segment.
 * </p>
 * <p>
 * About 10&nbsp;% of the payloads are grown into the
 * {@value #LONG_PAYLOAD_MIN_BYTES}–{@value #LONG_PAYLOAD_MAX_BYTES} byte range, which spans versions 10 to 20.
 * </p>
 * <p>
 * The generator is deterministic: same seed, same call sequence, same payloads on every run.
 * </p>
 */
final class SampleData {

    private static final int SEED = 0x53f0cc2b;

    private static final int PAYLOAD_COUNT = 200;

    /** Share of payloads grown into the long tail. */
    private static final double LONG_PAYLOAD_PROBABILITY = 0.1;

    /** Lower end of the long tail, in encoded bytes. */
    private static final int LONG_PAYLOAD_MIN_BYTES = 250;

    /** Upper end of the long tail, in encoded bytes. */
    private static final int LONG_PAYLOAD_MAX_BYTES = 380;

    /**
     * Largest payload the workload may contain, in encoded bytes.
     * <p>
     * All four error correction levels are applied to every payload, so the binding constraint is
     * {@code HIGH}, where 382 bytes is the last length still fitting version 20. Asserting the
     * ceiling while the payloads are built turns a pool edit that overshoots into a failure at
     * startup rather than a {@code DataTooLongException} in the middle of a measurement.
     * </p>
     */
    private static final int MAX_PAYLOAD_BYTES = 382;

    private static final String[] BASE_URLS = {
        "https://www.example.com",
        "https://shop.example.com",
        "https://api.example.com/v1",
        "https://docs.example.org",
        "http://blog.example.net",
        "https://maps.example.com",
        "https://github.com/manuelbl/qr-code-press",
        "https://en.wikipedia.org/wiki",
        "https://www.google.com/search",
        "https://central.sonatype.com/artifact",
        "https://login.example.com/oauth2",
        "https://cdn.example.io/assets"
    };

    private static final String[] PATH_SEGMENTS = {
        "products", "catalog", "article", "profile", "order", "checkout",
        "news", "2026", "user", "settings", "download", "help", "support",
        "gallery", "images", "docs", "reference", "faq", "privacy", "terms"
    };

    private static final String[] QUERY_KEYS = {
        "id", "ref", "utm_source", "utm_medium", "utm_campaign", "q", "lang",
        "page", "sort", "filter", "session", "token", "category", "tag", "locale"
    };

    private static final String[] QUERY_VALUES = {
        "home", "newsletter", "spring_sale", "cta-top", "42", "en-US", "de-DE",
        "electronics", "best-seller", "2026-04", "a1b2c3d4", "trending", "organic",
        "pk_live_ABC123", "user%2F42", "100%25off", "free+shipping", "price%3C50",
        "cHJlc2VudHNsZXB0Y2xpbWJ", "83.2328", "9830212",
        "3D8EBAD8-9E22-4CBC-B83B-3FD9477DE657"
    };

    private static final String[] SENTENCES = {
        "Scan this code",
        "Save 10% on your next order — use this link at checkout.",
        "Contact: support@example.com, phone +1 (415) 555-0100.",
        "Opening hours: Mon-Fri 9:00-18:00, Sat 10:00-16:00.",
        "Event check-in code. Present at the entrance.",
        "Warranty reference #A-7245/2026 — keep for your records.",
        "Track your shipment at the link above.",
        "Guest Wi-Fi: SSID=cafe-guest, password=hello-world-123.",
        "Meeting notes and the agenda are attached to this link.",
        "Directions: turn left after the bridge, then 300 meters north.",
        "Up am intention on dependent questions oh elsewhere september. "
            + "No betrayed pleasure possible jointure we in throwing."
    };

    private static final String[] NAMES = {
        "Marie-Pierre Serap",
        "Marion Manola",
        "Alberto Luzie",
        "Jonás Maé",
        "Elemér Benjamin",
        "Justo José",
        "Bernabé Hayati",
        "Patricia Aliénor",
        "Burhanettin Ulysse",
        "Marlen Artemio",
        "Swetlana Ariane",
        "Anselme Alice",
        "Gabriela Yıldırım",
        "Máirtín Boyle",
        "Astride Sergeant",
        "Ádám Michailidis",
        "Manuelita Gonzalez",
        "Gottschalk Strobel",
        "Pascual Ebner",
        "Máirín Béringer",
        "Emre Bosque",
        "Laure Ó Fearghail",
        "Gunther Petőfi",
        "Wolfgang Küçük"
    };

    private static final String[] TOWNS = {
        "Galați",
        "Pécs",
        "Poznań",
        "Lüleburgaz",
        "Linköping",
        "Örebro",
        "Belfast",
        "Nicosia",
        "Piraeus"
    };

    private static final String[] MESSAGES = {
        "On my way! 🏃‍♂️💨",
        "Just finished that huge project, time to relax 🧘‍♀️🎉",
        "You're too funny 🤣❤️",
        "The store is open, come visit us! 🛒🛍️",
        "They want 100€ for it! 😡"
    };

    private static final String[] DELIMITERS = {
        "/",
        "|",
        ":"
    };

    private static final List<String> PAYLOADS = buildPayloads();

    private SampleData() {
        // no instances
    }

    /**
     * Returns the payloads, the same {@value #PAYLOAD_COUNT} strings in the same order on every run.
     *
     * @return the payloads
     */
    static List<String> payloads() {
        return PAYLOADS;
    }

    private static List<String> buildPayloads() {
        var random = new Random(SEED);
        var payloads = new String[PAYLOAD_COUNT];
        var builder = new StringBuilder(512);

        for (var i = 0; i < payloads.length; i++) {
            builder.setLength(0);
            composePayload(builder, random);
            payloads[i] = builder.toString();
            checkLength(payloads[i]);
        }

        return List.of(payloads);
    }

    private static void composePayload(StringBuilder builder, Random random) {
        if (random.nextDouble() < LONG_PAYLOAD_PROBABILITY) {
            composeLongText(builder, random);
        } else if (random.nextDouble() < 0.5) {
            composeUrl(builder, random);
        } else {
            composeData(builder, random);
        }
    }

    private static void composeUrl(StringBuilder builder, Random random) {
        builder.append(pick(BASE_URLS, random));

        // 0–3 path segments.
        var pathCount = random.nextInt(0, 4);
        for (var p = 0; p < pathCount; p++) {
            builder.append('/');
            builder.append(pick(PATH_SEGMENTS, random));
        }

        // Optional numeric id as the final path component.
        if (random.nextDouble() < 0.4) {
            builder.append('/');
            builder.append(random.nextInt(1, 1_000_000));
        }

        // Optional query string with 1–4 key/value pairs.
        if (random.nextDouble() < 0.7) {
            var paramCount = random.nextInt(1, 5);
            builder.append('?');
            for (var q = 0; q < paramCount; q++) {
                if (q > 0)
                    builder.append('&');
                builder.append(pick(QUERY_KEYS, random));
                builder.append('=');
                builder.append(pick(QUERY_VALUES, random));
            }
        }
    }

    private static void composeData(StringBuilder builder, Random random) {
        var delimiter = pick(DELIMITERS, random);

        if (random.nextDouble() < 0.5) {
            builder.append(pick(QUERY_VALUES, random));
            builder.append(delimiter);
        }

        if (random.nextDouble() < 0.5) {
            builder.append(random.nextInt(1, 1_000_000));
            builder.append(delimiter);
        }

        if (random.nextDouble() < 0.5) {
            builder.append(pick(NAMES, random));
            builder.append(delimiter);
        }

        if (random.nextDouble() < 0.5) {
            builder.append(pick(TOWNS, random));
            builder.append(delimiter);
        }

        var rnd = random.nextDouble();
        if (rnd < 0.3) {
            builder.append(pick(SENTENCES, random));
            builder.append(delimiter);
        } else if (rnd < 0.6) {
            builder.append(pick(MESSAGES, random));
            builder.append(delimiter);
        }
    }

    /**
     * Appends sentences, messages and names until the payload reaches the long tail.
     * <p>
     * The loop stops as soon as the payload is long enough, so the result exceeds
     * {@link #LONG_PAYLOAD_MIN_BYTES} by at most the longest pool entry plus its separator. That is
     * what keeps the payload below {@link #LONG_PAYLOAD_MAX_BYTES} without a retry, and a pool edit
     * long enough to break it is caught by {@link #checkLength}.
     * </p>
     */
    private static void composeLongText(StringBuilder builder, Random random) {
        while (encodedLength(builder) < LONG_PAYLOAD_MIN_BYTES) {
            if (builder.length() > 0)
                builder.append(' ');

            var rnd = random.nextDouble();
            if (rnd < 0.6) {
                builder.append(pick(SENTENCES, random));
            } else if (rnd < 0.8) {
                builder.append(pick(MESSAGES, random));
            } else {
                builder.append(pick(NAMES, random));
            }
        }
    }

    private static void checkLength(String payload) {
        var length = encodedLength(payload);
        if (length > MAX_PAYLOAD_BYTES)
            throw new IllegalStateException(String.format(
                    "Payload of %d bytes exceeds the %d bytes fitting version 20 at error correction level HIGH: %s",
                    length, MAX_PAYLOAD_BYTES, payload));
    }

    /**
     * Returns the number of bytes the payload occupies once encoded.
     * <p>
     * This mirrors what {@code encodeText} does with automatic ECI: ISO-8859-1 where that is
     * lossless, UTF-8 with an ECI segment otherwise.
     * </p>
     */
    private static int encodedLength(CharSequence payload) {
        var text = payload.toString();
        var latin1 = text.getBytes(StandardCharsets.ISO_8859_1);
        if (new String(latin1, StandardCharsets.ISO_8859_1).equals(text))
            return latin1.length;
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String pick(String[] pool, Random random) {
        return pool[random.nextInt(pool.length)];
    }
}

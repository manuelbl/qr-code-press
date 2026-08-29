/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Access to the committed render snapshots in {@code src/test/resources/render}.
 * <p>
 * The snapshots were produced by this library and reviewed by hand; they exist so that a change in
 * the rendered output has to be an intentional one.
 * </p>
 */
final class RenderSnapshots {

    private RenderSnapshots() {
        // non-instantiable
    }

    /**
     * Returns the specified snapshot as bytes.
     *
     * @param name the file name within the {@code render} resource directory
     * @return the contents
     */
    static byte[] bytes(String name) {
        try (var stream = RenderSnapshots.class.getResourceAsStream("/render/" + name)) {
            if (stream == null) {
                throw new IllegalArgumentException("missing render snapshot: " + name);
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the specified snapshot as UTF-8 text.
     *
     * @param name the file name within the {@code render} resource directory
     * @return the contents
     */
    static String text(String name) {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }
}

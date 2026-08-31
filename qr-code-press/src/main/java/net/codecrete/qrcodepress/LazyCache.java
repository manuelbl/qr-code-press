/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntFunction;

/**
 * A fixed-size cache of values computed on first use, keyed by a dense small integer.
 * <p>
 * The keys of this library's caches are dense and known in advance &mdash; a version (1&ndash;40),
 * an error correction capacity (1&ndash;255), or a mask pattern crossed with a version.
 * So the entries live in a plain array rather than a map.
 * </p>
 * <p>
 * Values are computed at most once per key from the caller's point of view: threads racing on the
 * same key may each compute a value, but only one is ever published, and every caller receives that
 * one. This is sound only because the computation is pure and its results are interchangeable.
 * </p>
 * <p>
 * The cached values are shared. Callers must not mutate them. The compute function must not
 * return {@code null} as a null value is indistinguishable from an empty slot. It would be
 * recomputed on every lookup.
 * </p>
 *
 * @param <T> the type of the cached values
 */
final class LazyCache<T> {

    private final AtomicReferenceArray<T> entries;
    private final IntFunction<T> compute;

    /**
     * Creates a cache for keys from {@code 0} to {@code size - 1}.
     *
     * @param size    the number of keys
     * @param compute the function computing the value of a key, called at most once per key and
     *                thread
     */
    LazyCache(int size, IntFunction<T> compute) {
        this.entries = new AtomicReferenceArray<>(size);
        this.compute = compute;
    }

    /**
     * Returns the value of the specified key, computing it if this is its first use.
     *
     * @param index the key
     * @return the shared value, which must not be mutated
     */
    T get(int index) {
        var value = entries.get(index);
        if (value == null) {
            value = compute.apply(index);
            if (!entries.compareAndSet(index, null, value))
                // lost the race (against another thread computing it),
                // use the other thread's value
                value = entries.get(index);
        }

        return value;
    }
}

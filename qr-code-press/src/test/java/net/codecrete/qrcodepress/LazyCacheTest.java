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
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LazyCacheTest {

    /** Counts how often the compute function ran, so that caching can be observed. */
    private final AtomicInteger computations = new AtomicInteger();

    private LazyCache<String> cacheOf(int size) {
        return new LazyCache<>(size, index -> {
            computations.incrementAndGet();
            return "value " + index;
        });
    }

    @Test
    @DisplayName("the value is computed on the first get")
    void computesOnFirstGet() {
        var cache = cacheOf(4);

        assertThat(cache.get(2)).isEqualTo("value 2");
        assertThat(computations).hasValue(1);
    }

    @Test
    @DisplayName("a key is computed only once, however often it is fetched")
    void doesNotRecomputeOnSubsequentGets() {
        var cache = cacheOf(4);

        for (var i = 0; i < 10; i += 1)
            assertThat(cache.get(2)).isEqualTo("value 2");

        assertThat(computations).hasValue(1);
    }

    @ParameterizedTest(name = "key {0}")
    @ValueSource(ints = { 0, 1, 40 })
    @DisplayName("every get of a key yields the identical instance")
    void returnsTheSameInstanceForTheSameKey(int key) {
        var cache = cacheOf(41);

        var first = cache.get(key);

        assertThat(cache.get(key)).isSameAs(first);
    }

    @Test
    @DisplayName("keys are cached independently of one another")
    void computesEachKeyIndependently() {
        var cache = cacheOf(4);

        assertThat(cache.get(0)).isEqualTo("value 0");
        assertThat(cache.get(3)).isEqualTo("value 3");
        assertThat(cache.get(0)).isEqualTo("value 0");

        assertThat(computations).hasValue(2);
    }
}

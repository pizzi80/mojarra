package com.sun.faces.util;

import java.util.Collections;
import java.util.function.Function;

/**
 * Simple LRU Cache using a synchronized {@link LRUMap}
 */
public class LRUCache<K,V> extends Cache<K,V> {

    // super.Factory as a Function<K,V> to be used with computeIfAbsent
    private final Function<K,V> factoryFunction;

    public LRUCache( Factory<K,V> factory , int maxCapacity ) {
        super(factory, Collections.synchronizedMap(new LRUMap<>(maxCapacity)));
        this.factoryFunction = k -> {
            try {
                return factory.newInstance(k);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Override
    public V get(final K key) {
        return cache.computeIfAbsent(key, factoryFunction);
    }

}

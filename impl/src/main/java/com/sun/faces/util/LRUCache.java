package com.sun.faces.util;

/**
 * Simple LRU Cache using a synchronized {@link LRUMap}
 */
public class LRUCache<K,V> extends Cache<K,V> {

    private final Object lock = new Object();

    public LRUCache( Factory<K,V> factory , int maxCapacity ) {
        super(factory, new LRUMap<>(maxCapacity));
    }

    @Override
    public V get(final K key) {
        V result = cache.get(key);

        if (result == null) {

            try {
                result = factory.newInstance(key);
            } catch (InterruptedException ie) {
                // will never happen. Just for testing
                throw new RuntimeException(ie);
            }

            // put could be used instead if it didn't matter whether we replaced
            // an existing entry
            final V oldResult;
            synchronized (lock) {
                oldResult = cache.putIfAbsent(key, result);
            }
            if (oldResult != null) {
                result = oldResult;
            }
        }

        return result;
    }

    @Override
    public V remove(K key) {
        synchronized (lock) {
            return super.remove(key);
        }
    }

}

package com.sun.faces.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import jakarta.faces.FacesException;

/**
 * A minimal {@link ConcurrentCache} implementation.
 */
public final class LazyConcurrentCache<K, V> extends ConcurrentCache<K, V> {

    private final ConcurrentMap<K, Lazy<K,V>> cache = new ConcurrentHashMap<>();

    public LazyConcurrentCache(Factory<K, V> factory) {
        super(factory);
    }

    @Override
    public V get(final K key) throws ExecutionException {
        final Lazy<K,V> lazy = cache.computeIfAbsent(key, $ -> new Lazy<>());

        try {
            return lazy.get(key, getFactory());
        }
        catch (InterruptedException ie) {
            throw new FacesException(ie);
        }
        catch (Exception e) {
            // Factory has failed. Remove the placeholder so subsequent requests can try again.
            cache.remove(key, lazy);

            // Mojarra relies on ExecutionException all over the place
            if (e instanceof ExecutionException) throw (ExecutionException) e;

            // Note: RuntimeExceptions will be unwrapped later by DefaultFaceletCache.unwrapIOException
            throw new ExecutionException(e);
        }
    }

    @Override
    public boolean containsKey(K key) {
        try {
            return get(key) != null;
        }
        catch (ExecutionException ignored) {}

        return false;
    }

    /**
     * Thread-safe Lazy evaluation
     */
    private static class Lazy<K, V> {

        private volatile V value;

        public V get(K key, Factory<K, V> factory) throws Exception {
            V result = value;
            if (result == null) {
                synchronized (this) {
                    result = value;
                    if (result == null) {
                        result = factory.newInstance(key);
                        value = result;
                    }
                }
            }
            return result;
        }
    }

}

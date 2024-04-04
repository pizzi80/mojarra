package com.sun.faces.util;

import static java.util.Objects.requireNonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * A simple {@link ConcurrentMap} with LRU policy backed by a {@link Collections#synchronizedMap} over a {@link LRUMap}
 * (which is in turn backed by a {@link java.util.LinkedHashMap} with access order and fixed capacity.
 * <br>
 * Note that a {@link ConcurrentMap} should not contains null keys and values, so we enforced null checks.
 *
 * @author Paolo Bernardi
 */
public class ConcurrentLRUMap<K,V> implements ConcurrentMap<K,V> , Serializable {

    @Serial
    private static final long serialVersionUID = -1282880659063646211L;

    private final LRUMap<K,V> lru;
    private final Map<K,V> map;

    /**
     * Create a {@link ConcurrentLRUMap} with max capacity of 23 elements,
     * which translate internally to a {@link LRUMap}
     * with 32 buckets and the default load factor (0.75)
     */
    public ConcurrentLRUMap() {
        this(23);
    }

    /**
     * Create a {@link ConcurrentLRUMap} with the passed maxCapacity
     */
    public ConcurrentLRUMap(int maxCapacity) {
        lru = new LRUMap<>(maxCapacity);
        map = Collections.synchronizedMap(lru);
    }

    // ------------------------------------------------------- LRUMap Custom methods

    /**
     * Remove and return the eldest element from the Map if we've reached the maximum capacity.
     * @return the eldest element, if we've reached the maximum capacity, null otherwise.
     */
    public V popEldestEntry() {
        synchronized (map) {
            return lru.popEldestEntry();
        }
    }

    // -------------------------------------------------------  Map interface

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return key != null && map.containsKey(key);
    }

    @Override
    public V get(Object key) {
        return key == null ? null : map.get(key);
    }

    @Override
    public V put(K key, V value) {
        requireNonNull(key);
        requireNonNull(value);

        return map.put(key, value);
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> m) {
        // skip if null or empty
        if ( m == null || m.isEmpty() ) return;
        // try to remove null keys and null values
        try {
            m.keySet().removeIf(Objects::isNull);
            m.values().removeIf(Objects::isNull);
        } catch (Exception ignored){}
        // skip if empty (this is the case when there was only null key or values)
        if ( m.isEmpty() ) return;
        // putAll non null key/values
        map.putAll(m);
    }

    @Override
    public boolean containsValue(Object value) {
        return value != null && map.containsValue(value);
    }

    @Override
    public V remove(Object key) {
        return key == null ? null : map.remove(key);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        requireNonNull(key);
        requireNonNull(value);
        return map.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        requireNonNull(key);
        requireNonNull(value);
        return map.remove(key, value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        requireNonNull(key);
        requireNonNull(oldValue);
        requireNonNull(newValue);
        return map.replace(key, oldValue, newValue);
    }

    @Override
    public V replace(K key, V value) {
        requireNonNull(key);
        requireNonNull(value);
        return map.replace(key, value);
    }

}

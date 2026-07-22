package com.sun.faces.util;

import static java.util.Objects.requireNonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link ConcurrentMap} with LRU eviction policy backed by an {@link LRUMap}
 * All the access method are synchronized using an internal {@link ReentrantLock}
 * and custom internal data structures to iterate over keys and values without the need
 * of external locking
 * <br>
 * Note that a {@link ConcurrentMap} should not contains null keys and values, so we enforced null checks.
 *
 * @author Paolo Bernardi
 */
public class ConcurrentLruMap<K, V> implements ConcurrentMap<K, V>, Serializable {

    @Serial
    private static final long serialVersionUID = -1282880659063646211L;

    private final LRUMap<K, V> lru;
    private final Map<K, V> sync;

    public ConcurrentLruMap() { this(23); }

    public ConcurrentLruMap(int maxCapacity) {
        this.lru  = new LRUMap<>(maxCapacity);
        this.sync = Collections.synchronizedMap(lru);
    }


    // ---- specifici LRU ----
    public Map.Entry<K, V> popEldestEntry() {
        synchronized (sync) { return lru.popEldestEntry(); }
    }

    /** Snapshot consistente e *staccato*, preso sotto lock: sicuro da iterare. */
    public List<Map.Entry<K, V>> snapshot() {
        synchronized (sync) {
            List<Entry<K, V>> out = new ArrayList<>(lru.size());
            for (Map.Entry<K, V> e : lru.entrySet()) out.add(Map.entry(e.getKey(), e.getValue()));
            return out;
        }
    }

    // ---- Map ----
    @Override public int size()                      { return sync.size(); }
    @Override public boolean isEmpty()               { return sync.isEmpty(); }
    @Override public boolean containsKey(Object k)   { return k != null && sync.containsKey(k); }
    @Override public boolean containsValue(Object v) { return v != null && sync.containsValue(v); }
    @Override public V get(Object k)                 { return k == null ? null : sync.get(k); }
    @Override public V put(K k, V v)                 { return sync.put(requireNonNull(k), requireNonNull(v)); }
    @Override public V remove(Object k)              { return k == null ? null : sync.remove(k); }
    @Override public void clear()                    { sync.clear(); }
    @Override public void putAll(Map<? extends K, ? extends V> m) { if (m != null && !m.isEmpty()) sync.putAll(m); }

    @Override public Set<K> keySet()                 { return sync.keySet(); }
    @Override public Collection<V> values()          { return sync.values(); }
    @Override public Set<Entry<K, V>> entrySet()     { return sync.entrySet(); }

    // ---- ConcurrentMap (synchronizedMap li implementa già atomici) ----
    @Override public V putIfAbsent(K k, V v)            { return sync.putIfAbsent(requireNonNull(k), requireNonNull(v)); }
    @Override public boolean remove(Object k, Object v) { return k != null && v != null && sync.remove(k, v); }
    @Override public boolean replace(K k, V o, V n)     { return sync.replace(requireNonNull(k), requireNonNull(o), requireNonNull(n)); }
    @Override public V replace(K k, V v)                { return sync.replace(requireNonNull(k), requireNonNull(v)); }

}

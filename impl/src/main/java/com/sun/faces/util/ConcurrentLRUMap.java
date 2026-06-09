package com.sun.faces.util;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
public class ConcurrentLRUMap<K,V> implements ConcurrentMap<K,V> , Serializable {

    @Serial
    private static final long serialVersionUID = -1282880659063646211L;

    private final ReentrantLock lock = new ReentrantLock();
    private final LRUMap<K,V> lru;

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
    }

    // ------------------------------------------------------- LRUMap Custom methods

    /**
     * Lock the access
     */
    protected void lock() {
        lock.lock();
    }

    /**
     * Unlock the access
     */
    protected void unlock() {
        lock.unlock();
    }

    /**
     * Remove and return the eldest element from the Map if we've reached the maximum capacity.
     * @return the eldest element, if we've reached the maximum capacity, null otherwise.
     */
    public Map.Entry<K,V> popEldestEntry() {
        return execAtomically(lock, lru::popEldestEntry);
    }

    // -------------------------------------------------------  Map interface

    @Override
    public int size() {
        return execAtomically(lock, lru::size);
    }

    @Override
    public boolean isEmpty() {
        return execAtomically(lock, lru::isEmpty);
    }

    @Override
    public boolean containsKey(Object key) {
        return key != null && execAtomically(lock, () -> lru.containsKey(key));
    }

    @Override
    public V get(Object key) {
        return key == null ? null : execAtomically(lock, () -> lru.get(key));
    }

    @Override
    public V put(K key, V value) {
        requireNonNull(key);
        requireNonNull(value);
        return execAtomically(lock, () -> lru.put(key, value));
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
        // putAll non-null key/values
        execAtomically( lock , () -> lru.putAll(m) );
    }

    @Override
    public boolean containsValue(Object value) {
        return value != null &&  execAtomically( lock , () -> lru.containsValue(value) );
    }

    @Override
    public V remove(Object key) {
        return key == null ? null : execAtomically( lock , () -> lru.remove(key) );
    }

    @Override
    public void clear() {
        execAtomically(lock,lru::clear);
    }

    @Override
    public Set<K> keySet() {
        return new SynchronizedSet<>(lru.keySet(),lock);
    }

    @Override
    public Collection<V> values() {
        return new SynchronizedCollection<>(lru.values(),lock);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new SynchronizedSet<>(lru.entrySet(),lock);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        requireNonNull(key);
        requireNonNull(value);
        return execAtomically( lock , () -> lru.putIfAbsent(key, value) );
    }

    @Override
    public boolean remove(Object key, Object value) {
        requireNonNull(key);
        requireNonNull(value);
        return execAtomically( lock , () -> lru.remove(key, value) );
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        requireNonNull(key);
        requireNonNull(oldValue);
        requireNonNull(newValue);
        return execAtomically( lock , () -> lru.replace(key, oldValue, newValue) );
    }

    @Override
    public V replace(K key, V value) {
        requireNonNull(key);
        requireNonNull(value);
        return execAtomically( lock , () -> lru.replace(key, value) );
    }

    // Inner class --------------------------------------------------------------------------------

    public static class SynchronizedCollection<E> implements Collection<E>, Serializable {

        @Serial
        private static final long serialVersionUID = -7885887913249312765L;

        final Collection<E> c;          // Backing Collection
        final ReentrantLock mutex;      // Lock on which to synchronize (Serializable)

        SynchronizedCollection(Collection<E> c, ReentrantLock mutex) {
            this.c = requireNonNull(c);
            this.mutex = requireNonNull(mutex);
        }

        @Override
        public int size() {
            return execAtomically(mutex, c::size);
        }

        @Override
        public boolean isEmpty() {
            return execAtomically(mutex, c::isEmpty);
        }

        @Override
        public boolean contains(Object o) {
            return execAtomically(mutex, () -> c.contains(o));
        }

        @Override
        public Object[] toArray() {
            return execAtomically(mutex, () -> c.toArray());
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return execAtomically(mutex, () -> c.toArray(a));
        }

        @Override
        public <T> T[] toArray(IntFunction<T[]> f) {
            return execAtomically(mutex, () -> c.toArray(f));
        }

        @Override
        public boolean add(E e) {
            return execAtomically(mutex, () -> c.add(e));
        }

        @Override
        public boolean remove(Object o) {
            return execAtomically(mutex, () -> c.remove(o));
        }

        @Override
        public boolean containsAll(Collection<?> coll) {
            return execAtomically(mutex, () -> c.containsAll(coll));
        }

        @Override
        public boolean addAll(Collection<? extends E> coll) {
            return execAtomically(mutex, () -> c.addAll(coll));
        }

        @Override
        public boolean removeAll(Collection<?> coll) {
            return execAtomically(mutex, () -> c.removeAll(coll));
        }

        @Override
        public boolean retainAll(Collection<?> coll) {
            return execAtomically(mutex, () -> c.retainAll(coll));
        }

        @Override
        public void clear() {
            execAtomically(mutex, c::clear);
        }

        @Override
        public String toString() {
            return execAtomically(mutex, c::toString);
        }

        @Override
        public void forEach(Consumer<? super E> consumer) {
            execAtomically(mutex, () -> c.forEach(consumer));
        }

        @Override
        public boolean removeIf(Predicate<? super E> filter) {
            return execAtomically(mutex, () -> c.removeIf(filter));
        }

        // iterator ---------------------------------------------

        @Override
        public Iterator<E> iterator() {
            return new SynchronizedIterator<>(c.iterator(), mutex);
        }

        @Override
        public Spliterator<E> spliterator() {
            return c.spliterator();
        }

        @Override
        public Stream<E> stream() {
            return c.stream();
        }

        @Override
        public Stream<E> parallelStream() {
            return c.parallelStream();
        }

        // serialization ----------------------------------------------------

        @Serial
        private void writeObject(ObjectOutputStream s) throws IOException {
            execAtomically(mutex, s::defaultWriteObject);
        }

    }

    public static class SynchronizedSet<E> extends SynchronizedCollection<E> implements Set<E> {

        @Serial
        private static final long serialVersionUID = -2374533697256931095L;

        SynchronizedSet(Collection<E> c, ReentrantLock lock) {
            super(c, lock);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return execAtomically(mutex, () -> c.equals(o));
        }

        @Override
        public int hashCode() {
            return execAtomically(mutex, c::hashCode);
        }

    }

    public static class SynchronizedIterator<E> implements Iterator<E>, Serializable {

        @Serial
        private static final long serialVersionUID = 7441796146522523309L;

        private final Iterator<E> i;
        private final ReentrantLock lock;

        SynchronizedIterator(Iterator<E> i, ReentrantLock lock) {
            this.i = requireNonNull(i);
            this.lock = requireNonNull(lock);
        }

        @Override
        public boolean hasNext() {
            return execAtomically(lock, i::hasNext);
        }

        @Override
        public E next() {
            return execAtomically(lock, i::next);
        }

        @Override
        public void remove() {
            execAtomically(lock, i::remove);
        }

        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            execAtomically(lock, () -> {
                while (i.hasNext()) {
                    action.accept(i.next());
                }
            });
        }
    }


    // Concurrency --------------------------------------------------------------------------------

    @FunctionalInterface
    public interface Action {
        void execute() throws Exception;
    }

    /**
     * Execute the passed task and return the computed result atomically using the passed lock.
     * @param lock The {@link Lock} to be used for atomic execution
     * @param task The {@link FunctionalInterface} to be executed atomically
     */
    public static void execAtomically(Lock lock, Action task) {
        lock.lock();

        try {
            task.execute();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Execute the passed task and return the computed result atomically using the passed lock.
     * @param lock The {@link Lock} to be used for atomic execution
     * @param task The {@link Supplier} to be executed atomically
     * @return The result of the passed task.
     */
    public static <R> R execAtomically(Lock lock, Supplier<R> task) {
        lock.lock();

        try {
            return task.get();
        }
        finally {
            lock.unlock();
        }
    }

}

/*
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.faces.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A concurrent caching mechanism.
 */
public class Cache<K, V> extends ConcurrentCache<K, V> {

    private final ConcurrentMap<K, V> cache = new ConcurrentHashMap<>();

    // -------------------------------------------------------- Constructors

    /**
     * Constructs this cache using the specified <code>Factory</code>.
     *
     * @param factory a factory to create or retrieve the element that need to be cached
     */
    public Cache(final Factory<K,V> factory) {
        super(factory);
    }

    // ------------------------------------------------------ Public Methods

    /**
     * If a value isn't associated with the specified key, a new value will be created
     * using the <code>Factory</code> specified via the constructor.
     *
     * @param key the key the value is associated with
     * @return the value for the specified key, if any
     */
    public V get(final K key) {
        // Steady state is a cache hit, so probe with a plain get() first: ConcurrentHashMap.get() is cheaper than
        // computeIfAbsent(), which does extra work even when the key is already present. Fall back to computeIfAbsent()
        // only on a miss, which still resolves the populate race atomically. The factory never caches null (a null
        // value would simply re-resolve on the next call here, exactly as computeIfAbsent() already behaves).
        V value = cache.get(key);
        if ( value == null ) {
            value = cache.computeIfAbsent(key, getFactory());
        }
        return value;
    }

    @Override
    public boolean containsKey(final K key) {
        return cache.containsKey(key);
    }

    public V remove(final K key) {
        return cache.remove(key);
    }

}

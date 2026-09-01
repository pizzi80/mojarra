/*
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.faces.facelets.impl;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.faces.FacesException;
import jakarta.faces.view.facelets.FaceletCache;

import com.sun.faces.util.ConcurrentCache;
import com.sun.faces.util.ExpiringConcurrentCache;
import com.sun.faces.util.LazyConcurrentCache;
import com.sun.faces.util.Util;

/**
 * Default FaceletCache implementation.
 */
final class DefaultFaceletCache extends FaceletCache<DefaultFacelet> {

    /**
     * Constructor
     *
     * @param refreshPeriodInSeconds cache refresh period (in seconds). 0 means 'always refresh', negative value means 'never
     * refresh'
     */
    DefaultFaceletCache(final long refreshPeriodInSeconds) {

        final long refreshPeriodInMillis = refreshPeriodInSeconds >= 0 ? refreshPeriodInSeconds * 1000 : -1;

        // Never Expire (if not specified, this is the Production default)
        if (refreshPeriodInSeconds < 0) {
            _faceletCache = new LazyConcurrentCache<>(url -> new Record(getMemberFactory().newInstance(url)));
            _metadataFaceletCache = new LazyConcurrentCache<>(url -> new Record(getMetadataMemberFactory().newInstance(url)));
        }
        // No caching if refreshPeriodInMillis is 0
        else if (refreshPeriodInSeconds == 0) {
            _faceletCache = new NoCache(url -> new Record(getMemberFactory().newInstance(url)));
            _metadataFaceletCache = new NoCache(url -> new Record(getMetadataMemberFactory().newInstance(url)));
        }
        // Expiring
        else {
            // We will be delegating object storage to the ExpiringConcurrentCache
            // Create Factory objects here for the cache. The objects will be delegating to our
            // own instance factories
            ConcurrentCache.Factory<URL, Record> faceletFactory = url -> createExpiringRecord(url, getMemberFactory(), refreshPeriodInMillis);
            ConcurrentCache.Factory<URL, Record> metadataFaceletFactory = url -> createExpiringRecord(url, getMetadataMemberFactory(), refreshPeriodInMillis);

            ExpiringConcurrentCache.ExpiryChecker<URL, Record> checker = new ExpiryChecker();
            _faceletCache = new ExpiringConcurrentCache<>(faceletFactory, checker);
            _metadataFaceletCache = new ExpiringConcurrentCache<>(metadataFaceletFactory, checker);
        }
    }

    private static ExpiringRecord createExpiringRecord(URL url, MemberFactory<DefaultFacelet> factory, long refreshPeriodInMillis) throws IOException {
        return new ExpiringRecord(System.currentTimeMillis(), Util.getLastModified(url), factory.newInstance(url), refreshPeriodInMillis);
    }

    @Override
    public DefaultFacelet getFacelet(URL url) throws IOException {
        Util.notNull("url", url);
        DefaultFacelet f = null;

        try {
            f = _faceletCache.get(url).getFacelet();
        } catch (ExecutionException e) {
            _unwrapIOException(e);
        }
        return f;
    }

    @Override
    public boolean isFaceletCached(URL url) {
        Util.notNull("url", url);

        return _faceletCache.containsKey(url);
    }

    @Override
    public DefaultFacelet getViewMetadataFacelet(URL url) throws IOException {
        Util.notNull("url", url);

        DefaultFacelet f = null;

        try {
            f = _metadataFaceletCache.get(url).getFacelet();
        } catch (ExecutionException e) {
            _unwrapIOException(e);
        }
        return f;
    }

    @Override
    public boolean isViewMetadataFaceletCached(URL url) {
        Util.notNull("url", url);

        return _metadataFaceletCache.containsKey(url);
    }

    private void _unwrapIOException(ExecutionException e) throws IOException {
        Throwable t = e.getCause();
        if (t instanceof IOException) {
            throw (IOException) t;
        }
        if (t.getCause() instanceof IOException) {
            throw (IOException) t.getCause();
        }
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        throw new FacesException(t);
    }

    private final ConcurrentCache<URL, Record> _faceletCache;
    private final ConcurrentCache<URL, Record> _metadataFaceletCache;

    /**
     * This class holds the Facelet instance.
     */
    private static class Record {
        private final DefaultFacelet facelet;
        Record(DefaultFacelet facelet) {this.facelet = facelet;}
        DefaultFacelet getFacelet() { return facelet; }
    }

    /**
     * This class holds the Facelet instance and its original URL's last modified time. It also produces the time when the
     * next expiry check should be performed
     */
    private static class ExpiringRecord extends Record {
        ExpiringRecord(long creationTime, long lastModified, DefaultFacelet facelet, long refreshIntervalInMillis) {
            super(facelet);
            _creationTime = creationTime;
            _lastModified = lastModified;
            _refreshInterval = refreshIntervalInMillis;

            // There is no point in calculating the next refresh time if we are refreshing always/never
            _nextRefreshTime = _refreshInterval > 0 ? new AtomicLong(creationTime + refreshIntervalInMillis) : null;
        }

        long getLastModified() {
            return _lastModified;
        }

        long getNextRefreshTime() {
            // There is no point in calculating the next refresh time if we are refreshing always/never
            return _refreshInterval > 0 ? _nextRefreshTime.get() : 0;
        }

        long getAndUpdateNextRefreshTime() {
            // There is no point in calculating the next refresh time if we are refreshing always/never
            return _refreshInterval > 0 ? _nextRefreshTime.getAndSet(System.currentTimeMillis() + _refreshInterval) : 0;
        }

        private final long _lastModified;
        private final long _refreshInterval;
        private final long _creationTime;
        private final AtomicLong _nextRefreshTime;
    }

    private static class ExpiryChecker implements ExpiringConcurrentCache.ExpiryChecker<URL, Record> {

        @Override
        public boolean isExpired(URL url, Record r) {
            final ExpiringRecord record = (ExpiringRecord) r;
            if (System.currentTimeMillis() > record.getNextRefreshTime()) {
                record.getAndUpdateNextRefreshTime();
                long lastModified = Util.getLastModified(url);
                // The record is considered expired if its original last modified time
                // is older than the URL's current last modified time
                return lastModified > record.getLastModified();
            }
            return false;
        }
    }

    /**
     * ConcurrentCache implementation that does no caching (always creates new instances)
     */
    private static class NoCache extends ConcurrentCache<URL, Record> {
        public NoCache(ConcurrentCache.Factory<URL, Record> f) {
            super(f);
        }

        @Override
        public Record get(final URL key) throws ExecutionException {
            try {
                return getFactory().newInstance(key);
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        }

        @Override
        public boolean containsKey(final URL key) {
            return false;
        }
    }
}

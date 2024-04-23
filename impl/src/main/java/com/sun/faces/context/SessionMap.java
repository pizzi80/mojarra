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

package com.sun.faces.context;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.faces.util.FacesLogger;
import com.sun.faces.util.Util;

import jakarta.faces.application.ProjectStage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * @see jakarta.faces.context.ExternalContext#getSessionMap()
 */
public class SessionMap extends BaseContextMap<Object> {

    private static final Logger LOGGER = FacesLogger.APPLICATION.getLogger();
    private static final String MUTEX = SessionMap.class.getName()+".MUTEX";

    private final HttpServletRequest request;
    private final ProjectStage stage;

    // ------------------------------------------------------------ Constructors

    public SessionMap(HttpServletRequest request, ProjectStage stage) {
        this.request = request;
        this.stage = stage;
    }

    // -------------------------------------------------------- Methods from Map

    @Override
    public void clear() {
        HttpSession session = getSession(false);
        if (session != null) {
            for (Enumeration<String> e = session.getAttributeNames(); e.hasMoreElements();) {
                String name = e.nextElement();
                session.removeAttribute(name);
            }
        }
    }

    // Supported by maps if overridden
    @Override
    public void putAll(Map<? extends String, ?> map) {
        HttpSession session = getSession(true);
        for (Object object : map.entrySet()) {
            Map.Entry entry = (Map.Entry) object;
            String key = (String)entry.getKey();
            Object value = entry.getValue();
            if (ProjectStage.Development.equals(stage) && !(value instanceof Serializable)) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, "faces.context.extcontext.sessionmap.nonserializable", new Object[]{key, value.getClass().getName()});
                }
            }
            // noinspection NonSerializableObjectBoundToHttpSession
            session.setAttribute(key, value);
        }
    }

    @Override
    public Object get(Object key) {
        Util.notNull("key", key);
        HttpSession session = getSession(false);
        return session != null ? session.getAttribute(key.toString()) : null;
    }

    @Override
    public Object put(String key, Object value) {
        Util.notNull("key", key);
        HttpSession session = getSession(true);
        Object currentValue = session.getAttribute(key);
        if (value != null && ProjectStage.Development.equals(stage) && !(value instanceof Serializable)) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "faces.context.extcontext.sessionmap.nonserializable", new Object[] { key, value.getClass().getName() });
            }
        }
        // noinspection NonSerializableObjectBoundToHttpSession
        boolean doSet = true;
        if (value != null && currentValue != null) {
            int valueHash = System.identityHashCode(value);
            int currentValueHash = System.identityHashCode(currentValue);
            doSet = valueHash != currentValueHash;
        }
        if (doSet) {
            session.setAttribute(key, value);
        }
        return currentValue;
    }

    @Override
    public Object remove(Object key) {
        if (key == null) {
            return null;
        }
        HttpSession session = getSession(false);
        if (session != null) {
            String keyString = key.toString();
            Object currentValue = session.getAttribute(keyString);
            session.removeAttribute(keyString);
            return currentValue;
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        HttpSession session = getSession(false);
        return session != null && session.getAttribute(key.toString()) != null;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SessionMap && super.equals(obj);
    }

    @Override
    public int hashCode() {
        HttpSession session = getSession(false);
        int hashCode = 7 * (session != null ? session.hashCode() : super.hashCode());
        if (session != null) {
            for (Map.Entry<String, Object> stringObjectEntry : entrySet()) {
                hashCode += stringObjectEntry.hashCode();
            }
        }
        return hashCode;
    }

    // --------------------------------------------- Methods from BaseContextMap

    @Override
    protected Iterator<Map.Entry<String, Object>> getEntryIterator() {
        HttpSession session = getSession(false);
        if (session != null) {
            return new EntryIterator(session.getAttributeNames());
        } else {
            return Collections.emptyIterator();
        }
    }

    @Override
    protected Iterator<String> getKeyIterator() {
        HttpSession session = getSession(false);
        if (session != null) {
            return new KeyIterator(session.getAttributeNames());
        } else {
            return Collections.emptyIterator();
        }
    }

    @Override
    protected Iterator<Object> getValueIterator() {
        HttpSession session = getSession(false);
        if (session != null) {
            return new ValueIterator(session.getAttributeNames());
        } else {
            return Collections.emptyIterator();
        }
    }

    // --------------------------------------------------------- Private Methods

    protected HttpSession getSession(boolean createNew) {
        return request.getSession(createNew);
    }

    // ----------------------------------------------------------- Session Mutex

    // PENDING: to be used when the session is null or invalidated during getMutex access
    private static final Lock shared_mutex = new ReentrantLock();

    public static void createMutex(HttpSession session) {
        session.setAttribute(MUTEX, new ReentrantLock());
    }

    public static Lock getMutex(Object session) {
        if ( session == null ) return shared_mutex;             // PENDING: to avoid NPE in synchronized blocks
        if ( session instanceof HttpSession httpSession ) {
            try {
                Lock mutex = (Lock) httpSession.getAttribute(MUTEX);
                // if the mutex was removed in the meantime -> return the shared_mutex...?
                if ( mutex == null ) {
                    LOGGER.warning("getMutex(session) is returning a shared mutex because the Mutex attribute was removed from session in the meantime");
                    return shared_mutex;
                }
                return mutex;
            }
            catch (IllegalStateException sessionInvalidated) {
                LOGGER.warning("getMutex(session) is returning a shared mutex because the session has been invalidated in the meantime");
                return shared_mutex;
            }
        }
        // it the session was not an HttpSession, return the shared mutex
        // which is the case?
        LOGGER.warning("getMutex(session): session it's not an HttpSession. returning the shared lock as a mutex object");
        //return session;
        return shared_mutex;
    }

    // do we really need to remove the mutex from session?
    // this could create a race condition and / or a NPE in synchronized block calling getMutex
    public static void removeMutex(HttpSession session) {
        session.removeAttribute(MUTEX);
    }

} // END SessionMap

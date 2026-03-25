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

import java.io.Serializable;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
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
        HttpSession session = getSessionIfExists();
        if (session != null) {
            try {
                for (Enumeration<String> e = session.getAttributeNames(); e.hasMoreElements(); ) {
                    String name = e.nextElement();
                    session.removeAttribute(name);
                }
            }
            // expired in the meantime, end of the story
            catch (IllegalStateException ignored) {}
        }
    }

    // Supported by maps if overridden
    @Override
    public void putAll(Map<? extends String, ?> map) {
        HttpSession session = getSessionOrCreate();
        for (Map.Entry<? extends String, ?> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (ProjectStage.Development.equals(stage) && !(value instanceof Serializable)) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, "faces.context.extcontext.sessionmap.nonserializable", new Object[]{key, value.getClass().getName()});
                }
            }
            session.setAttribute(key, value);
        }
    }

    @Override
    public Object get(Object key) {
        Util.notNull("key", key);
        return getAttribute(getSessionIfExists(), key);
    }

    @Override
    public Object put(String key, Object value) {
        Util.notNull("key", key);
        HttpSession session = getSessionOrCreate();
        Object currentValue = session.getAttribute(key);
        if (value != null && ProjectStage.Development.equals(stage) && !(value instanceof Serializable)) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "faces.context.extcontext.sessionmap.nonserializable", new Object[] { key, value.getClass().getName() });
            }
        }

        // set the value only if the currentValue is null or not the exact same object
        final boolean doSet = currentValue == null || currentValue != value;

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
        return removeAttribute(getSessionIfExists(), key);
    }

    @Override
    public boolean containsKey(Object key) {
        return getAttribute(getSessionIfExists(), key) != null;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SessionMap && super.equals(obj);
    }

    @Override
    public int hashCode() {
        HttpSession session = getSessionIfExists();

        if (session != null) {
            try {
                int hashCode = 7 * session.hashCode();

                for (Map.Entry<String, Object> stringObjectEntry : entrySet()) {
                    hashCode += stringObjectEntry.hashCode();
                }

                return hashCode;
            }
            // expired in the meantime, end of the story
            catch (IllegalStateException ignored){}
        }

        return super.hashCode();
    }

    // --------------------------------------------- Methods from BaseContextMap

    @Override
    protected Iterator<Map.Entry<String, Object>> getEntryIterator() {
        HttpSession session = getSessionIfExists();
        if (session != null) {
            try {
                return new EntryIterator(session.getAttributeNames());
            }
            // expired in the meantime, end of the story
            catch (IllegalStateException ignored){}
        }

        return Collections.emptyIterator();
    }

    @Override
    protected Iterator<String> getKeyIterator() {
        HttpSession session = getSessionIfExists();
        if (session != null) {
            try {
                return new KeyIterator(session.getAttributeNames());
            }
            // expired in the meantime, end of the story
            catch (IllegalStateException ignored) {}
        }

        return Collections.emptyIterator();
    }

    @Override
    protected Iterator<Object> getValueIterator() {
        HttpSession session = getSessionIfExists();
        if (session != null) {
            try {
                return new ValueIterator(session.getAttributeNames());
            }
            // expired in the meantime, end of the story
            catch (IllegalStateException ignored) {}
        }

        return Collections.emptyIterator();
    }

    // --------------------------------------------------------- Private Methods

    private HttpSession getSessionIfExists() {
        return getSession(false);
    }

    private HttpSession getSessionOrCreate() {
        return getSession(true);
    }

    protected HttpSession getSession(boolean createNew) {
        return request.getSession(createNew);
    }

    // ----------------------------------------------------------- Session Mutex

    // PENDING: to be used when the session is null or invalidated during getMutex access
    // NOTE: we have to use ReentrantLock instead of the interface Lock because the latter is not Serializable
    private static final ReentrantLock shared_mutex = new ReentrantLock();

    public static void createMutex(HttpSession session) {
        session.setAttribute(MUTEX, new ReentrantLock());
    }

    public static ReentrantLock getMutex(Object session) {
        if ( session == null ) return shared_mutex;             // PENDING: to avoid NPE in synchronized blocks
        if ( session instanceof HttpSession httpSession ) {
            try {
                final ReentrantLock mutex = (ReentrantLock) httpSession.getAttribute(MUTEX);
                // if the mutex was removed in the meantime -> return the shared_mutex...?
                if ( mutex == null ) {
                    LOGGER.fine("getMutex(session) is returning a shared mutex because the Mutex attribute has been removed from session in the meantime");
                    return shared_mutex;
                }
                return mutex;
            }
            catch (IllegalStateException expired) {
                LOGGER.fine("getMutex(session) is returning a shared mutex because the session has been invalidated in the meantime");
                return shared_mutex;
            }
        }
        // it the session was not an HttpSession, return the shared mutex, which is the case? (Portlet?)
        LOGGER.fine("getMutex(session): session it's not an HttpSession. returning the shared lock as a mutex object");
        // return session;
        return shared_mutex;
    }

    // do we really need to remove the mutex from session?
    // this could create a race condition and / or a NPE in synchronized block calling getMutex
    public static void removeMutex(HttpSession session) {
        removeAttribute(session, MUTEX);
    }

    // ------------------------------------------------------------------- Utils

    private static Object getAttribute(HttpSession session, Object key) {
        if (session == null) {
            return null;
        }
        try {
            return session.getAttribute(key.toString());
        }
        // expired in the meantime, end of the story
        catch (IllegalStateException ignored) {}

        return null;
    }

    private static Object removeAttribute(HttpSession session, Object key) {
        if (session == null) {
            return null;
        }

        String keyString = key.toString();
        try {
            Object currentValue = session.getAttribute(keyString);
            session.removeAttribute(keyString);
            return currentValue;
        }
        // expired in the meantime, end of the story
        catch (IllegalStateException ignored) {}

        return null;
    }

} // END SessionMap

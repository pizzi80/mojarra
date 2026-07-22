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
// Portions Copyright [2018] [Payara Foundation and/or its affiliates]

package com.sun.faces.cdi.clientwindow;

import static com.sun.faces.config.WebConfiguration.BooleanWebContextInitParameter.EnableDistributable;
import static com.sun.faces.config.WebConfiguration.WebContextInitParameter.NumberOfClientWindows;
import static com.sun.faces.context.SessionMap.getMutex;
import static java.util.logging.Level.FINEST;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.PassivationCapable;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

import com.sun.faces.config.WebConfiguration;
import com.sun.faces.util.FacesLogger;
import com.sun.faces.util.LRUMap;

/**
 * The manager that deals with CDI ClientWindowScoped beans.
 */
public class ClientWindowScopeContextManager {

    private static final Logger LOGGER = FacesLogger.CLIENTWINDOW.getLogger();

    private static final String CLIENT_WINDOW_CONTEXTS = "com.sun.faces.cdi.clientwindow.clientWindowContexts";

    private final boolean distributable;

    public ClientWindowScopeContextManager() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        distributable = WebConfiguration.getInstance(facesContext.getExternalContext())
                                        .isOptionEnabled(EnableDistributable);
    }

    /**
     * Create the bean.
     *
     * @param <T> the type.
     * @param facesContext the faces context.
     * @param contextual the contextual.
     * @param creational the creational.
     * @return the value or null if not found.
     */
    public <T> T createBean(FacesContext facesContext, Contextual<T> contextual, CreationalContext<T> creational) {
        LOGGER.log(FINEST, "Creating @ClientWindowScoped CDI bean using contextual: {0}", contextual);

        if (!(contextual instanceof PassivationCapable)) {
            throw new IllegalArgumentException("ClientWindowScoped bean " + contextual.toString() + " must be PassivationCapable, but is not.");
        }

        T contextualInstance = contextual.create(creational);

        if (contextualInstance != null) {
            String passivationCapableId = ((PassivationCapable) contextual).getId();

            getContextMap(facesContext).put(passivationCapableId, new ClientWindowScopeContextObject(passivationCapableId, contextualInstance));
        }

        return contextualInstance;
    }

    /**
     * Get the value from the ClientWindow-map (or null if not found).
     *
     * @param <T> the type.
     * @param facesContext the faces context.
     * @param contextual the contextual.
     * @return the value or null if not found.
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(FacesContext facesContext, Contextual<T> contextual) {

        Map<String, ClientWindowScopeContextObject> contextMap = getContextMap(facesContext);

        if (contextMap != null) {
            if (!(contextual instanceof PassivationCapable)) {
                throw new IllegalArgumentException("ClientWindowScoped bean " + contextual.toString() + " must be PassivationCapable, but is not.");
            }

            ClientWindowScopeContextObject<T> contextObject = contextMap.get(((PassivationCapable) contextual).getId());

            if (contextObject != null) {
                return contextObject.getContextualInstance();
            }
        }

        return null;
    }

    /**
     * Get the context map.
     *
     * @param facesContext the Faces context.
     * @return the context map.
     */
    private Map<String, ClientWindowScopeContextObject> getContextMap(FacesContext facesContext) {
        return getContextMap(facesContext, true);
    }

    /**
     * Get the context map.
     *
     * @param facesContext the Faces context.
     * @param create flag to indicate if we are creating the context map.
     * @return the context map.
     */
    private Map<String, ClientWindowScopeContextObject> getContextMap(FacesContext facesContext, boolean create) {
        final ExternalContext externalContext = facesContext.getExternalContext();
        final Object session = externalContext.getSession(create);

        if (session != null) {
            final Map<String, Object> sessionMap = externalContext.getSessionMap();
            final Map<Object, Map<String, ClientWindowScopeContextObject>> clientWindowScopeContexts = getClientWindowScopeContexts(externalContext, sessionMap, session, create);
            final String clientWindowId = getCurrentClientWindowId(externalContext);

            if (clientWindowScopeContexts != null && clientWindowId != null) {
                Map<String, ClientWindowScopeContextObject> result = clientWindowScopeContexts.get(clientWindowId);

                if (result == null && create) {
                    Map<String, ClientWindowScopeContextObject> newWindowMap = new ConcurrentHashMap<>();   // size?

                    // atomic insert
                    result = clientWindowScopeContexts.putIfAbsent(clientWindowId, newWindowMap);

                    // if it was null, the current value is our newWindowMap
                    if (result == null) {
                        result = newWindowMap;

                        if (distributable) {
                            // Marca la sessione come "dirty" per la replicazione in cluster
                            sessionMap.put(CLIENT_WINDOW_CONTEXTS, clientWindowScopeContexts);
                        }
                    }
                }

                return result;
            }
        }

        return null;
    }

    /**
     * Called when a session destroyed.
     *
     * @param httpSessionEvent the HTTP session event.
     */
    @SuppressWarnings("unchecked")
    public void sessionDestroyed(HttpSessionEvent httpSessionEvent) {
        if (LOGGER.isLoggable(FINEST)) {
            LOGGER.log(FINEST, "Cleaning up session for CDI @ClientWindowScoped beans");
        }

        HttpSession session = httpSessionEvent.getSession();

        var clientWindowScopeContexts = (Map<Object, Map<String, ClientWindowScopeContextObject>>) session.getAttribute(CLIENT_WINDOW_CONTEXTS);

        if (clientWindowScopeContexts != null) {
            clientWindowScopeContexts.clear();
            session.removeAttribute(CLIENT_WINDOW_CONTEXTS);
        }
    }

    // Utils ------------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<Object, Map<String, ClientWindowScopeContextObject>> getClientWindowScopeContexts(
            ExternalContext externalContext, Map<String, Object> sessionMap, Object session, boolean create) {

        var clientWindowScopeContext = (Map<Object, Map<String, ClientWindowScopeContextObject>>) sessionMap.get(CLIENT_WINDOW_CONTEXTS);

        if (clientWindowScopeContext == null && create) {

            final int numberOfClientWindows = getNumberOfClientWindows(externalContext);

            synchronized (getMutex(session)) {
                // double-check-locking!
                clientWindowScopeContext = (Map<Object, Map<String, ClientWindowScopeContextObject>>) sessionMap.get(CLIENT_WINDOW_CONTEXTS);
                // create and store if needed
                if ( clientWindowScopeContext == null ) {
                    clientWindowScopeContext = Collections.synchronizedMap(new LRUMap<>(numberOfClientWindows));
                    sessionMap.put(CLIENT_WINDOW_CONTEXTS, clientWindowScopeContext);
                }
            }
        }

        return clientWindowScopeContext;
    }

    /**
     * Get the number of maximum client windows to be stored in session.
     */
    private static int getNumberOfClientWindows(ExternalContext externalContext) {
        // get from init params
        try {
            return Integer.parseInt(WebConfiguration.getInstance(externalContext).getOptionValue(NumberOfClientWindows));
        }
        catch (NumberFormatException nfe) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "Unable to set number of client windows.  Defaulting to {0}", NumberOfClientWindows.getDefaultValue());
            }
        }
        // get from default value
        return Integer.parseInt(NumberOfClientWindows.getDefaultValue());
    }

    private static String getCurrentClientWindowId(ExternalContext externalContext) {
        return externalContext.getClientWindow().getId();
    }

}

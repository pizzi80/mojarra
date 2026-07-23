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

package com.sun.faces.application.view;

import static com.sun.faces.application.view.ViewScopeManager.VIEW_MAP_ID;
import static com.sun.faces.cdi.CdiUtils.getBeanReference;
import static com.sun.faces.context.SessionMap.getMutex;
import static com.sun.faces.util.Util.getCdiBeanManager;
import static java.util.logging.Level.FINEST;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.PassivationCapable;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

import com.sun.faces.util.FacesLogger;

/**
 * The manager that deals with CDI ViewScoped beans.
 */
public class ViewScopeContextManager {

    private static final Logger LOGGER = FacesLogger.APPLICATION_VIEW.getLogger();

    /**
     * Stores the constant to keep track of all the active view scope contexts.
     */
    private static final String ACTIVE_VIEW_CONTEXTS = "com.sun.faces.application.view.activeViewContexts";

    private final BeanManager beanManager;
    private final boolean distributable;

    public ViewScopeContextManager(FacesContext facesContext, boolean distributable) {
        this.beanManager = getCdiBeanManager(facesContext);
        this.distributable = distributable; //WebConfiguration.getInstance(facesContext.getExternalContext()).isOptionEnabled(EnableDistributable);
    }

    /**
     * Clear the current view map using the Faces context.
     *
     * @param facesContext the Faces context.
     */
    public void clear(FacesContext facesContext) {
        LOGGER.log(FINEST, "Clearing @ViewScoped CDI beans for current view map");

        Map<String, ViewScopeContextObject> contextMap = getContextMap(facesContext, false);
        if (contextMap != null) {
            Map<String, Object> viewMap = facesContext.getViewRoot().getViewMap(false);
            if ( viewMap != null ) {
                destroyBeans(viewMap, contextMap);
            }
        }
    }

//    /**
//     * Clear the given view map. Use the version with the viewMapId.
//     *
//     * @param facesContext the Faces context.
//     * @param viewMap the given view map.
//     */
//    @Deprecated
//    public void clear(FacesContext facesContext, Map<String, Object> viewMap) {
//        String viewMapId = ViewScopeManager.locateViewMapId(facesContext, viewMap);
//        if (viewMapId != null) {
//            clear(facesContext, viewMapId, viewMap);
//        } else {
//            LOGGER.log(WARNING, "Cannot locate the view map to clear in the active maps: {0}", viewMap);
//        }
//    }

    /**
     * Clear the given view map.
     *
     * @param facesContext the Faces context.
     * @param viewMapId The ID of the view map
     * @param viewMap the given view map.
     */
    public void clear(FacesContext facesContext, String viewMapId, Map<String, Object> viewMap) {
        if (LOGGER.isLoggable(FINEST)) {
            LOGGER.log(FINEST, "Clearing @ViewScoped CDI beans for given view map: {0}");
        }
        Map<String, ViewScopeContextObject> contextMap = getContextMap(facesContext, viewMapId);
        if (contextMap != null) {
            destroyBeans(viewMap, contextMap);
        }
    }

    /**
     * Create the bean.
     *
     * @param <T> the type.
     * @param facesContext the Faces context.
     * @param contextual the contextual.
     * @param creational the creational.
     * @return the value or null if not found.
     */
    public <T> T createBean(FacesContext facesContext, Contextual<T> contextual, CreationalContext<T> creational) {
        LOGGER.log(FINEST, "Creating @ViewScoped CDI bean using contextual: {0}", contextual);

        if (!(contextual instanceof PassivationCapable passivationCapable)) {
            throw new IllegalArgumentException("ViewScoped bean " + contextual.toString() + " must be PassivationCapable, but is not.");
        }

        T contextualInstance = contextual.create(creational);

        if (contextualInstance != null) {
            String name = getBeanName(contextualInstance);
            facesContext.getViewRoot().getViewMap(true).put(name, contextualInstance);
            String passivationCapableId = passivationCapable.getId();

            getContextMap(facesContext).put(passivationCapableId, new ViewScopeContextObject(passivationCapableId, name));
        }

        return contextualInstance;
    }

    /**
     * Destroy the view scoped beans for the given view and context map.
     *
     * @param viewMap the view map.
     * @param contextMap the context map.
     */
    private void destroyBeans(Map<String, Object> viewMap, Map<String, ViewScopeContextObject> contextMap) {
        if (contextMap != null) {
            // final List<String> removalNameList = new ArrayList<>();

            for (Map.Entry<String, ViewScopeContextObject> entry : contextMap.entrySet()) {
                final String passivationCapableId = entry.getKey();
                final ViewScopeContextObject contextObject = entry.getValue();

                // We can no longer get this from the contextObject. Instead we must call
                // beanManager.createCreationalContext(contextual)
                final Object contextualInstance = viewMap.remove(contextObject.getName());

                // Contextual instance may be null if already removed from view map (and thus already destroyed).
                // This can happen when a mid-request navigation happens and a new view root is being set, and then
                // in the same request a session.invalidate is called.
                // See https://github.com/javaserverfaces/mojarra/issues/3454
                // Also see https://github.com/payara/Payara/issues/2506 for why we can't just clean the contextMap
                // (it contains abstract descriptors for all instances, not just the one we want to destroy here).
                if (contextualInstance != null) {
                    final Contextual contextual = beanManager.getPassivationCapableBean(passivationCapableId);
                    contextual.destroy(contextualInstance, beanManager.createCreationalContext(contextual));
                }

//                removalNameList.add(contextObject.getName());
            }

            // remove all collected names from the viewMap
//            for (String name : removalNameList) {
//                viewMap.remove(name);
//            }

        }
    }

    /**
     * Get the value from the view map (or null if not found).
     *
     * @param <T> the type.
     * @param facesContext the Faces context.
     * @param contextual the contextual.
     * @return the value or null if not found.
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(FacesContext facesContext, Contextual<T> contextual) {
        final Map<String, ViewScopeContextObject> contextMap = getContextMap(facesContext);

        if (contextMap != null) {
            final T result;

            if (!(contextual instanceof PassivationCapable passivationCapable)) {
                throw new IllegalArgumentException("ViewScoped bean " + contextual.toString() + " must be PassivationCapable, but is not.");
            }

            ViewScopeContextObject contextObject = contextMap.get(passivationCapable.getId());

            if (contextObject != null) {
                String name = contextObject.getName();
                LOGGER.log(FINEST, "Getting value for @ViewScoped bean with name: {0}", name);
                result = (T) facesContext.getViewRoot().getViewMap(true).get(name);
            }
            else {
                result = null;
            }

            return result;
        }

        return null;
    }

    /**
     * Get the context map.
     *
     * @param facesContext the Faces context.
     * @return the context map.
     */
    private Map<String, ViewScopeContextObject> getContextMap(FacesContext facesContext) {
        return getContextMap(facesContext, true);
    }

    /**
     * Get the context map.
     *
     * @param facesContext the Faces context.
     * @param create flag to indicate if we are creating the context map.
     * @return the context map.
     */
    private Map<String, ViewScopeContextObject> getContextMap(FacesContext facesContext, boolean create) {
        Map<String, ViewScopeContextObject> result = null;

        try {
            final ExternalContext externalContext = facesContext.getExternalContext();
            final Object session = externalContext.getSession(create);

            if (session != null) {
                final Map<String, Object> sessionMap = externalContext.getSessionMap();

                // get (or create) the global ViewScope Map
                final Map<Object, ConcurrentMap<String, ViewScopeContextObject>> activeViewScopeContexts = getViewScopeContextMap(sessionMap, session, create);

                // get / create the ViewScope for the current View
                final String viewMapId = (String) facesContext.getViewRoot().getTransientStateHelper().getTransient(VIEW_MAP_ID);

                if (activeViewScopeContexts != null && viewMapId != null && create) {
                    synchronized (activeViewScopeContexts) {
                        if (!activeViewScopeContexts.containsKey(viewMapId)) {
                            activeViewScopeContexts.put(viewMapId, new ConcurrentHashMap<>());
                            if (distributable) {
                                // If we are distributable, this will result in a dirtying of the
                                // session data, forcing replication. If we are not distributable,
                                // this is a no-op.
                                sessionMap.put(ACTIVE_VIEW_CONTEXTS, activeViewScopeContexts);
                            }
                        }
                    }
                }

                if (activeViewScopeContexts != null && viewMapId != null) {
                    result = activeViewScopeContexts.get(viewMapId);
                }
            }
        }
        // Session already invalidated or response committed
        catch (IllegalStateException expiredOrReleased) {}

        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, ConcurrentMap<String, ViewScopeContextObject>> getViewScopeContextMap(Map<String, Object> sessionMap, Object session, boolean create) {
        var activeViewContextMap = sessionMap.get(ACTIVE_VIEW_CONTEXTS);
        if (activeViewContextMap == null && create) {
            synchronized (getMutex(session)) {
                activeViewContextMap = sessionMap.get(ACTIVE_VIEW_CONTEXTS);
                if (activeViewContextMap == null) {
                    activeViewContextMap = new ConcurrentHashMap<>();
                    sessionMap.put(ACTIVE_VIEW_CONTEXTS, activeViewContextMap);
                }
            }
        }
        return (Map<Object, ConcurrentMap<String, ViewScopeContextObject>>) activeViewContextMap;
    }

    /**
     * Get the context map.
     *
     * @param facesContext the Faces context.
     * @param viewMapId The viewMapId of the map.
     * @return the context map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, ViewScopeContextObject> getContextMap(FacesContext facesContext, String viewMapId) {
        Map<String, ViewScopeContextObject> result = null;

        try {
            ExternalContext externalContext = facesContext.getExternalContext();
            Map<String, Object> sessionMap = externalContext.getSessionMap();
            var activeViewScopeContexts = (Map<Object, Map<String, ViewScopeContextObject>>) sessionMap.get(ACTIVE_VIEW_CONTEXTS);
            if (activeViewScopeContexts != null) {
                result = activeViewScopeContexts.get(viewMapId);
            }
        }
        catch (IllegalStateException expiredOrReleased) {}

        return result;
    }

    /**
     * Get the name of the bean for the given object.
     * todo: move to {@link com.sun.faces.cdi.CdiUtils}
     *
     * @param instance the object.
     * @return the name.
     */
    private static String getBeanName(Object instance) {
        final String name;

        // @Named("beanName") annotation
        Named named = instance.getClass().getAnnotation(Named.class);
        if (named != null && named.value() != null && !named.value().isBlank()) {
            name = named.value();
        }
        // Class name un-capitalized
        else {
            String className = instance.getClass().getSimpleName();
            name = Character.toLowerCase(className.charAt(0)) + className.substring(1);
        }

        return name;
    }

    /**
     * Called when a session destroyed.
     *
     * @param httpSessionEvent the HTTP session event.
     */
    @SuppressWarnings("unchecked")
    public void sessionDestroyed(HttpSessionEvent httpSessionEvent) {
        if (LOGGER.isLoggable(FINEST)) {
            LOGGER.log(FINEST, "Cleaning up session for CDI @ViewScoped beans");
        }

        HttpSession session = httpSessionEvent.getSession();

        var activeViewScopeContexts = (Map<Object, Map<String, ViewScopeContextObject>>) session.getAttribute(ACTIVE_VIEW_CONTEXTS);

        if (activeViewScopeContexts != null) {
            destroyAllBeans((Map<String, ?>) session.getAttribute(ViewScopeManager.ACTIVE_VIEW_MAPS), activeViewScopeContexts);
            destroyAllBeans(ViewScopeManager.getEvictedViewMaps(session), activeViewScopeContexts);

            activeViewScopeContexts.clear();
            session.removeAttribute(ACTIVE_VIEW_CONTEXTS);
        }
    }

    /**
     * Destroy the view scoped beans of each of the given view maps.
     *
     * @param viewMaps the view maps, mapped by their id, or null when there are none.
     * @param activeViewScopeContexts the context maps of all view maps.
     */
    @SuppressWarnings("unchecked")
    private void destroyAllBeans(Map<String, ?> viewMaps, Map<Object, Map<String, ViewScopeContextObject>> activeViewScopeContexts) {
        if (viewMaps != null) {
            for (Map.Entry<String, ?> viewMapEntry : viewMaps.entrySet()) {
                destroyBeans((Map<String, Object>) viewMapEntry.getValue(), activeViewScopeContexts.get(viewMapEntry.getKey()));
            }
        }
    }

    public void fireInitializedEvent(FacesContext facesContext, UIViewRoot root) {
        getBeanReference(beanManager, ViewScopedCDIEventFireHelperImpl.class).fireInitializedEvent(root);
    }

    public void fireDestroyedEvent(FacesContext facesContext, UIViewRoot root) {
        getBeanReference(beanManager, ViewScopedCDIEventFireHelperImpl.class).fireDestroyedEvent(root);
    }

}

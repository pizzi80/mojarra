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

package com.sun.faces.application.view;

import static com.sun.faces.config.WebConfiguration.BooleanWebContextInitParameter.EnableDistributable;
import static com.sun.faces.config.WebConfiguration.WebContextInitParameter.NumberOfActiveViewMaps;
import static com.sun.faces.context.SessionMap.getMutex;
import static jakarta.faces.application.FacesMessage.SEVERITY_WARN;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.application.ProjectStage;
import jakarta.faces.component.TransientStateHelper;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AbortProcessingException;
import jakarta.faces.event.PostConstructViewMapEvent;
import jakarta.faces.event.PreDestroyViewMapEvent;
import jakarta.faces.event.SystemEvent;
import jakarta.faces.event.ViewMapListener;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import com.sun.faces.application.ApplicationAssociate;
import com.sun.faces.config.WebConfiguration;
import com.sun.faces.util.ConcurrentLruMap;

/**
 * The manager that deals with non-CDI and CDI ViewScoped beans.
 */
public class ViewScopeManager implements HttpSessionListener, ViewMapListener {

    /**
     * Stores the logger.
     */
    private static final Logger LOGGER = Logger.getLogger(ViewScopeManager.class.getName());

    /**
     * warning message when the developer is using a ViewScoped Bean in a stateless (transient) view
     */
    private static final String VIEW_SCOPED_NOT_SUPPORTED_ON_STATELESS_VIEWS_MESSAGE = "@ViewScoped beans are not supported on stateless views";

    /**
     * Stores the constants to keep track of the active view maps.
     */
    public static final String ACTIVE_VIEW_MAPS = "com.sun.faces.application.view.activeViewMaps";

    /**
     * Stores the constant for the maximum active view map size.
     */
    public static final String ACTIVE_VIEW_MAPS_SIZE = "com.sun.faces.application.view.activeViewMapsSize";

    /**
     * Stores the view map.
     */
    public static final String VIEW_MAP = "com.sun.faces.application.view.viewMap";

    /**
     * Stores the view map id.
     */
    public static final String VIEW_MAP_ID = "com.sun.faces.application.view.viewMapId";

    /**
     * Stores the constant to keep track of the ViewScopeManager.
     */
    public static final String VIEW_SCOPE_MANAGER = "com.sun.faces.application.view.viewScopeManager";

    /**
     * Stores the constant to keep track of the view maps which are in use by unfinished requests.
     */
    private static final String VIEW_MAP_USAGES = "com.sun.faces.application.view.viewMapUsages";
    /**
     * Stores the constant to keep track of the view map ids which the current request has acquired.
     */
    private static final String ACQUIRED_VIEW_MAP_IDS = "com.sun.faces.application.view.acquiredViewMapIds";
    /**
     * Stores the CDI context manager.
     */
    private final ViewScopeContextManager contextManager;

    private final boolean distributable;
    private final int numberOfActiveViewMapsInWebXml;

    /**
     * Constructor.
     */
    public ViewScopeManager() {
        final FacesContext context = FacesContext.getCurrentInstance();
        final WebConfiguration config = WebConfiguration.getInstance(context.getExternalContext());
        distributable = config.isOptionEnabled(EnableDistributable);
        contextManager = new ViewScopeContextManager(context, distributable);
        numberOfActiveViewMapsInWebXml = WebConfiguration.getOptionIntValueOrDefault(config, NumberOfActiveViewMaps, 25);
    }

//    /**
//     * Static method that locates the ID for a view map in the active view maps
//     * stored in the session. It just performs a == over the view map because
//     * it should be the same object.
//     *
//     * @param facesContext The faces context
//     * @param viewMap The view to locate
//     * @return located ID
//     */
//    @Deprecated(forRemoval = true)
//    protected static String locateViewMapId(FacesContext facesContext, Map<String, Object> viewMap) {
//        Object session = facesContext.getExternalContext().getSession(true);
//
//        if (session != null) {
//            Map<String, Object> sessionMap = facesContext.getExternalContext().getSessionMap();
//            @SuppressWarnings("unchecked")
//            Map<String, Object> viewMaps = (Map<String, Object>) sessionMap.get(ACTIVE_VIEW_MAPS);
//            if (viewMaps != null) {
//                for (Map.Entry<String,Object> entry : viewMaps.entrySet()) {
//                    if (viewMap == entry.getValue()) {
//                        return entry.getKey();
//                    }
//                }
//            }
//        }
//
//        return null;
//    }

    /**
     * Clear the current view map using the Faces context.
     *
     * @param facesContext the Faces context.
     */
    public void clear(FacesContext facesContext) {
        LOGGER.log(FINEST, "Clearing @ViewScoped beans from current view map");

        if (contextManager != null) {
            contextManager.clear(facesContext);
        }
    }

//    /**
//     * Clear the given view map. Use the version with viewMapId.
//     *
//     * @param facesContext the Faces context.
//     * @param viewMap the view map.
//     */
//    @Deprecated
//    public void clear(FacesContext facesContext, Map<String, Object> viewMap) {
//        String viewMapId = locateViewMapId(facesContext, viewMap);
//        if (viewMapId != null) {
//            this.clear(facesContext, viewMapId, viewMap);
//        } else {
//            LOGGER.log(WARNING, "Cannot locate the view map to clear in the active maps: {0}", viewMap);
//        }
//    }
    
    /**
     * Clear the given view map.
     *
     * @param facesContext the Faces context.
     * @param viewMapId The ID of the view map
     * @param viewMap the view map.
     */
    public void clear(FacesContext facesContext, String viewMapId, Map<String, Object> viewMap) {
        LOGGER.log(FINEST, "Clearing @ViewScoped beans from view map: {0}", viewMap);

        if (contextManager != null) {
            contextManager.clear(facesContext, viewMapId, viewMap);
        }

        destroyBeans(facesContext, viewMap);
    }
    
    /**
     * Destroy the managed beans from the given view map.
     *
     * @param facesContext the Faces Context.
     * @param viewMap the view map.
     */
    public void destroyBeans(FacesContext facesContext, Map<String, Object> viewMap) {
        LOGGER.log(FINEST, "Destroying @ViewScoped beans from view map: {0}", viewMap);
        
        ApplicationAssociate applicationAssociate = ApplicationAssociate.getInstance(facesContext.getExternalContext());
        if (applicationAssociate != null) {
            destroyBeans(applicationAssociate, viewMap);
        }
    }
    
    /**
     * Destroy the managed beans from the given view map.
     *
     * @param applicationAssociate the application associate.
     * @param viewMap the view map.
     */
    private void destroyBeans(ApplicationAssociate applicationAssociate, Map<String, Object> viewMap) {
    }

    /**
     * Get the CDI context manager.
     *
     * @return the CDI context manager.
     */
    ViewScopeContextManager getContextManager() {
        return contextManager;
    }

    private static final Object VIEW_SCOPE_MANAGER_LOCK = new Object();

    /**
     * Get our instance.
     *
     * @param facesContext the FacesContext.
     * @return our instance.
     */
    public static ViewScopeManager getInstance(FacesContext facesContext) {
        final Map<String, Object> appMap = facesContext.getExternalContext().getApplicationMap();
        ViewScopeManager viewScopeManager = (ViewScopeManager) appMap.get(VIEW_SCOPE_MANAGER);
        if (viewScopeManager == null) {
            synchronized (VIEW_SCOPE_MANAGER_LOCK) {
                viewScopeManager = (ViewScopeManager) appMap.get(VIEW_SCOPE_MANAGER);
                if (viewScopeManager == null) {
                    viewScopeManager = new ViewScopeManager();
                    appMap.put(VIEW_SCOPE_MANAGER, viewScopeManager);
                }
            }
        }
        return viewScopeManager;
    }

    /**
     * Is a listener for the given source.
     *
     * @param source the source.
     * @return true if UIViewRoot, false otherwise.
     */
    @Override
    public boolean isListenerForSource(Object source) {
        return source instanceof UIViewRoot;
    }

    /**
     * Process the system event.
     *
     * @param systemEvent the system event.
     * @throws AbortProcessingException when processing needs to be aborted.
     */
    @Override
    public void processEvent(SystemEvent systemEvent) throws AbortProcessingException {
        if (systemEvent instanceof PreDestroyViewMapEvent) {
            processPreDestroyViewMap(systemEvent);
        }

        if (systemEvent instanceof PostConstructViewMapEvent) {
            processPostConstructViewMap(systemEvent);
        }
    }

    /**
     * Process the PostConstructViewMap system event.
     *
     * @param systemEvent the system event.
     */
    private void processPostConstructViewMap(SystemEvent systemEvent) {
        LOGGER.log(FINEST, "Handling PostConstructViewMapEvent");

        final UIViewRoot viewRoot = (UIViewRoot) systemEvent.getSource();
        final Map<String, Object> viewMap = viewRoot.getViewMap(false);

        if (viewMap != null) {
            final FacesContext context = systemEvent.getFacesContext();

            // ViewScoped Bean used inside a stateless view -> warn the developer
            if (viewRoot.isTransient() && context.isProjectStage(ProjectStage.Development)) {
                context.addMessage(viewRoot.getClientId(context), new FacesMessage(SEVERITY_WARN, VIEW_SCOPED_NOT_SUPPORTED_ON_STATELESS_VIEWS_MESSAGE, VIEW_SCOPED_NOT_SUPPORTED_ON_STATELESS_VIEWS_MESSAGE));
                LOGGER.log(WARNING, VIEW_SCOPED_NOT_SUPPORTED_ON_STATELESS_VIEWS_MESSAGE);
            }

            // get or create a Session
            final Object session = context.getExternalContext().getSession(true);

            if (session != null) {
                final Map<String, Object> sessionMap = context.getExternalContext().getSessionMap();
                final int maxNumberOfViewMaps = getOrInitActiveViewMapsSize(sessionMap, numberOfActiveViewMapsInWebXml);
                final ConcurrentLruMap<String,Object> viewMaps = getOrInitActiveViewMaps(session, sessionMap, numberOfActiveViewMapsInWebXml);

                synchronized (viewMaps) {

                    if (viewMaps.size() == maxNumberOfViewMaps) {
                        evictEldestViewMap(context, viewMaps);
                    }

                    // insert new element
                    final String viewMapId = generateViewKey(viewMaps.keySet());
                    viewMaps.put(viewMapId, viewMap);
                    viewRoot.getTransientStateHelper().putTransient(VIEW_MAP_ID, viewMapId);
                    viewRoot.getTransientStateHelper().putTransient(VIEW_MAP, viewMap);
                    acquireViewMap(context, viewMapId);
                    if (distributable) {
                        // If we are distributable, this will result in a dirtying of the
                        // session data, forcing replication. If we are not distributable,
                        // this is a no-op.
                        sessionMap.put(ACTIVE_VIEW_MAPS, viewMaps);
                    }
                }

                if (contextManager != null) {
                    contextManager.fireInitializedEvent(context, viewRoot);
                }
            }
        }
    }

    /**
     * Process the PreDestroyViewMap system event.
     *
     * @param event the system event.
     */
    private void processPreDestroyViewMap(SystemEvent event) {
        LOGGER.log(FINEST, "Handling PreDestroyViewMapEvent");
        
        UIViewRoot viewRoot = (UIViewRoot) event.getSource();
        Map<String, Object> viewMap = viewRoot.getViewMap(false);
        String viewMapId = (String) viewRoot.getTransientStateHelper().getTransient(VIEW_MAP_ID);

        if (viewMap != null && viewMapId != null && !viewMap.isEmpty()) {
            FacesContext facesContext = event.getFacesContext();

            if (contextManager != null) {
                contextManager.clear(facesContext, viewMapId, viewMap);
                contextManager.fireDestroyedEvent(facesContext, viewRoot);
            }

            destroyBeans(facesContext, viewMap);

        }
    }

    /**
     * Create the associated data in the session (if any).
     *
     * @param se the HTTP session event.
     */
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        LOGGER.log(FINEST, "Creating session for @ViewScoped beans");
    }

    /**
     * Destroy the associated data in the session.
     *
     * @param httpSessionEvent the HTTP session event.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void sessionDestroyed(HttpSessionEvent httpSessionEvent) {
        LOGGER.log(FINEST, "Cleaning up session for @ViewScoped beans");

        if (contextManager != null) {
            contextManager.sessionDestroyed(httpSessionEvent);
        }

        HttpSession session = httpSessionEvent.getSession();

        Map<String, Object> activeViewMaps = (Map<String, Object>) session.getAttribute(ACTIVE_VIEW_MAPS);
        if (activeViewMaps != null) {
            Iterator<Object> activeViewMapsIterator = activeViewMaps.values().iterator();
            ApplicationAssociate applicationAssociate = ApplicationAssociate.getInstance(httpSessionEvent.getSession().getServletContext());
            while (activeViewMapsIterator.hasNext()) {
                Map<String, Object> viewMap = (Map<String, Object>) activeViewMapsIterator.next();
                destroyBeans(applicationAssociate, viewMap);
            }

            activeViewMaps.clear();
            session.removeAttribute(ACTIVE_VIEW_MAPS);
            session.removeAttribute(ACTIVE_VIEW_MAPS_SIZE);
        }

        session.removeAttribute(VIEW_MAP_USAGES);
    }

    /**
     * Evict the eldest view map from the given active view maps. Its beans are destroyed immediately, unless an
     * unfinished request is still using it, in which case the last request using it will destroy them.
     *
     * @param facesContext the Faces context.
     * @param viewMaps the active view maps, whose monitor must be held by the caller.
     */
    private void evictEldestViewMap(FacesContext facesContext, Map<String, Object> viewMaps) {
        String eldestViewMapId = viewMaps.keySet().iterator().next();
        @SuppressWarnings("unchecked")
        Map<String, Object> eldestViewMap = (Map<String, Object>) viewMaps.remove(eldestViewMapId);

        if (getViewMapUsages(facesContext).evict(eldestViewMapId, eldestViewMap)) {
            LOGGER.log(FINEST, "Postponing destroy of eldest view map which is still in use: {0}", eldestViewMap);
        } else {
            destroyViewMap(facesContext, eldestViewMapId, eldestViewMap);
        }
    }

    /**
     * Registers that the current request is using the view map of the current view root, if any. This keeps its beans
     * alive until the current request has finished, even when a concurrent request evicts the view map meanwhile. When
     * it has meanwhile been evicted and destroyed by a concurrent request, then it is forgotten, so that the next
     * {@link UIViewRoot#getViewMap(boolean)} creates and registers a new one.
     *
     * @param facesContext the Faces context.
     */
    public static void acquireViewMap(FacesContext facesContext) {
        UIViewRoot viewRoot = facesContext.getViewRoot();

        if (viewRoot == null) {
            return;
        }

        TransientStateHelper transientStateHelper = viewRoot.getTransientStateHelper();
        String viewMapId = (String) transientStateHelper.getTransient(VIEW_MAP_ID);

        if (viewMapId != null && !acquireViewMap(facesContext, viewMapId)) {
            transientStateHelper.putTransient(VIEW_MAP_ID, null);
            transientStateHelper.putTransient(VIEW_MAP, null);
        }
    }

    /**
     * Registers that the current request is using the view map with the given id, so that a concurrent eviction of
     * that view map will not destroy its beans before this request has finished. This is a no-op when the current
     * request has already acquired it.
     *
     * @param facesContext the Faces context.
     * @param viewMapId the view map id.
     * @return false when the view map has meanwhile been evicted and destroyed by a concurrent request, in which case
     * it must no longer be used.
     */
    private static boolean acquireViewMap(FacesContext facesContext, String viewMapId) {
        Set<String> acquiredViewMapIds = getAcquiredViewMapIds(facesContext);

        if (acquiredViewMapIds.contains(viewMapId)) {
            return true;
        }

        Map<String, Object> viewMaps = getActiveViewMaps(facesContext);

        if (viewMaps == null) {
            return true; // The session holds no active view maps at all, hence there is nothing to protect.
        }

        synchronized (viewMaps) {
            ViewMapUsages viewMapUsages = getViewMapUsages(facesContext);

            if (!viewMaps.containsKey(viewMapId) && !viewMapUsages.isEvicted(viewMapId)) {
                return false;
            }

            viewMapUsages.acquire(viewMapId);
        }

        acquiredViewMapIds.add(viewMapId);
        return true;
    }

    /**
     * Registers that the current request has finished using the view maps which it has acquired. Any of those which
     * were meanwhile evicted while this was the last request using it will have their beans destroyed right now.
     *
     * @param facesContext the Faces context.
     */
    public static void releaseViewMaps(FacesContext facesContext) {
        @SuppressWarnings("unchecked")
        Set<String> acquiredViewMapIds = (Set<String>) facesContext.getAttributes().remove(ACQUIRED_VIEW_MAP_IDS);
        Map<String, Object> viewMaps = acquiredViewMapIds != null ? getActiveViewMaps(facesContext) : null;

        if (viewMaps == null) {
            return;
        }

        for (String viewMapId : acquiredViewMapIds) {
            Map<String, Object> evictedViewMap;

            synchronized (viewMaps) {
                evictedViewMap = getViewMapUsages(facesContext).release(viewMapId);
            }

            if (evictedViewMap != null) {
                try {
                    getInstance(facesContext).destroyViewMap(facesContext, viewMapId, evictedViewMap);
                } catch (RuntimeException e) {
                    LOGGER.log(WARNING, "Cannot destroy the @ViewScoped beans of the evicted view map: " + viewMapId, e);
                }
            }
        }
    }

    /**
     * Destroy the given view map which has been removed from the active view maps.
     *
     * @param facesContext the context
     * @param viewMapId the view map id
     * @param viewMap the view map.
     */
    private void destroyViewMap(FacesContext facesContext, String viewMapId, Map<String, Object> viewMap) {
        LOGGER.log(FINEST, "Removing view map: {0}", viewMap);

        if (contextManager != null) {
            contextManager.clear(facesContext, viewMapId, viewMap);
        }

        destroyBeans(facesContext, viewMap);
    }

    /**
     * Get the active view maps of the current session, or null when there is no session or none have been registered.
     *
     * @param facesContext the Faces context.
     * @return the active view maps, or null.
     */
    private static Map<String, Object> getActiveViewMaps(FacesContext facesContext) {
        if (facesContext.getExternalContext().getSession(false) == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> viewMaps = (Map<String, Object>) facesContext.getExternalContext().getSessionMap().get(ACTIVE_VIEW_MAPS);
        return viewMaps;
    }

    /**
     * Get the usages of the view maps of the current session. The caller must hold the monitor of the active view maps.
     *
     * @param facesContext the Faces context.
     * @return the usages of the view maps of the current session.
     */
    private static ViewMapUsages getViewMapUsages(FacesContext facesContext) {
        return (ViewMapUsages) facesContext.getExternalContext().getSessionMap().computeIfAbsent(VIEW_MAP_USAGES, key -> new ViewMapUsages());
    }

    /**
     * Get the view maps of the given session which have been evicted while an unfinished request was still using them,
     * or null when there are none.
     *
     * @param session the HTTP session.
     * @return the view maps which have been evicted while still in use, or null.
     */
    static Map<String, Map<String, Object>> getEvictedViewMaps(HttpSession session) {
        ViewMapUsages viewMapUsages = (ViewMapUsages) session.getAttribute(VIEW_MAP_USAGES);
        return viewMapUsages != null ? viewMapUsages.evictedViewMaps : null;
    }

    /**
     * Get the ids of the view maps which the current request has acquired.
     *
     * @param facesContext the Faces context.
     * @return the ids of the view maps which the current request has acquired.
     */
    private static Set<String> getAcquiredViewMapIds(FacesContext facesContext) {
        @SuppressWarnings("unchecked")
        Set<String> acquiredViewMapIds = (Set<String>) facesContext.getAttributes().computeIfAbsent(ACQUIRED_VIEW_MAP_IDS, key -> new HashSet<>());
        return acquiredViewMapIds;
    }

    /**
     * Keeps track of the amount of unfinished requests which are using each view map, and of the view maps which have
     * been evicted from the active view maps while they were still in use, so that the last request using such a view
     * map can destroy its beans. Guarded by the monitor of the active view maps.
     * <p>
     * The state is transient: unfinished requests do not survive a session passivation, so upon activation no view map
     * is in use anymore and every evicted view map is beyond recovery.
     */
    private static final class ViewMapUsages implements Serializable {

        private static final long serialVersionUID = 1L;

        private transient Map<String, Integer> activeRequests = new HashMap<>();
        private transient Map<String, Map<String, Object>> evictedViewMaps = new HashMap<>();

        /**
         * Registers that one more request is using the view map with the given id.
         */
        private void acquire(String viewMapId) {
            activeRequests.merge(viewMapId, 1, Integer::sum);
        }

        /**
         * Registers that one less request is using the view map with the given id.
         *
         * @return the view map which has meanwhile been evicted and must now be destroyed, or null when other requests
         * are still using it, or when it has not been evicted at all.
         */
        private Map<String, Object> release(String viewMapId) {
            if (activeRequests.computeIfPresent(viewMapId, ViewMapUsages::decrement) != null) {
                return null;
            }

            return evictedViewMaps.remove(viewMapId);
        }

        /**
         * Registers that the view map with the given id has been evicted from the active view maps.
         *
         * @return true when an unfinished request is still using it, in which case its destroy must be postponed until
         * that request releases it, and false when it can be destroyed right now.
         */
        private boolean evict(String viewMapId, Map<String, Object> viewMap) {
            if (!activeRequests.containsKey(viewMapId)) {
                return false;
            }

            evictedViewMaps.put(viewMapId, viewMap);
            return true;
        }

        /**
         * Returns whether the view map with the given id has been evicted while it was still in use, hence whether its
         * beans are still alive.
         */
        private boolean isEvicted(String viewMapId) {
            return evictedViewMaps.containsKey(viewMapId);
        }

        private static Integer decrement(String viewMapId, Integer activeRequests) {
            return activeRequests > 1 ? activeRequests - 1 : null;
        }

        private void readObject(ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
            objectInputStream.defaultReadObject();
            activeRequests = new HashMap<>();
            evictedViewMaps = new HashMap<>();
        }
    }

    // ------------------------------------------------------------------------- UTILS

    private static int getOrInitActiveViewMapsSize(Map<String,Object> sessionMap, int initValue) {
        Integer value = (Integer) sessionMap.get(ACTIVE_VIEW_MAPS_SIZE);
        if (value == null) {
            value = initValue;
            sessionMap.put(ACTIVE_VIEW_MAPS_SIZE, initValue);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentLruMap<String,Object> getOrInitActiveViewMaps(Object session, Map<String,Object> sessionMap, int maxCapacity) {
        ConcurrentLruMap<String,Object> viewMaps = (ConcurrentLruMap<String,Object>) sessionMap.get(ACTIVE_VIEW_MAPS);
        if (viewMaps == null) {
            synchronized (getMutex(session)) {
                viewMaps = (ConcurrentLruMap<String, Object>) sessionMap.get(ACTIVE_VIEW_MAPS);
                if (viewMaps == null) {
                    viewMaps = new ConcurrentLruMap<>(maxCapacity);
                    sessionMap.put(ACTIVE_VIEW_MAPS, viewMaps);
                }
            }
        }
        return viewMaps;
    }

    /**
     * @return a random key that is not contained in the passes keys
     */
    private static String generateViewKey(final Set<String> keys) {
        String key = generateRandomKey();
        while (keys.contains(key)) {
            key = generateRandomKey();
        }
        return key;
    }

    /**
     * Generates a key for the session-scoped active view map registry.
     * <p>
     * The registry is per-session and bounded (default 25 entries), so 64 random bits
     * are already overkill: birthday collision probability is ~3e-18 at full capacity.
     * <p>
     * {@code ThreadLocalRandom} has no shared state, unlike the {@code SecureRandom}
     * instance behind {@code UUID.randomUUID()}, which serializes under load.
     * <p>
     * The caller must still check for absence under the session lock — that check,
     * not this method, is the actual uniqueness guarantee.
     *
     * @return a base-36 key, at most 13 characters
     */
    private static String generateRandomKey() {
        return Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), Character.MAX_RADIX);
    }

}

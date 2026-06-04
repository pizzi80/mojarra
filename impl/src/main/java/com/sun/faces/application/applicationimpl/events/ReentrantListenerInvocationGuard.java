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

package com.sun.faces.application.applicationimpl.events;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.faces.context.FacesContext;
import jakarta.faces.event.SystemEvent;

import com.sun.faces.application.ApplicationImpl;

/**
 * This class encapsulates the behavior to prevent infinite loops when the publishing of one event leads to the queueing
 * of another event of the same type. Special provision is made to allow the case where this guarding mechanisms happens
 * on a per-FacesContext, per-SystemEvent.class type basis.
 */
public final class ReentrantListenerInvocationGuard {
    private ReentrantListenerInvocationGuard() {}

    public static boolean isGuardSet(FacesContext context, Class<? extends SystemEvent> systemEventClass) {
        Boolean result = getGuardHolder(context).get(systemEventClass);

        return result != null && result;
    }

    public static void setGuard(FacesContext context, Class<? extends SystemEvent> systemEventClass) {
        getGuardHolder(context).put(systemEventClass, TRUE);
    }

    public static void clearGuard(FacesContext context, Class<? extends SystemEvent> systemEventClass) {
        getGuardHolder(context).put(systemEventClass, FALSE);
    }

    private static final String IS_PROCESSING_LISTENERS_KEY = ApplicationImpl.class.getName()+".IS_PROCESSING_LISTENERS";

    @SuppressWarnings("unchecked")
    private static Map<Class<? extends SystemEvent>, Boolean> getGuardHolder(FacesContext context) {
        final Map<Object, Object> attributes = context.getAttributes();

        var result = (Map<Class<? extends SystemEvent>, Boolean>) attributes.get(IS_PROCESSING_LISTENERS_KEY);
        if (result == null) {
            result = new HashMap<>(1, 1.0f);
            attributes.put(IS_PROCESSING_LISTENERS_KEY, result);
        }

        return result;
    }

}

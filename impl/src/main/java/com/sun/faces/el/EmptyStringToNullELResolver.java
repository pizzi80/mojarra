/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.faces.el;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//        if ( String.class == targetType && context instanceof org.apache.el.lang.EvaluationContext ) // && context instanceof com.sun.faces.el.ELContextImpl
//            System.out.println("value:["+value+"] - targetType:["+targetType+"]" + " {"+context+"}");

// NOTA: la soluzione proposta da BalusC ad oggi crea dei malfunzionamenti a JSF durante la valutazione di espressioni EL
//       ad esempio se faccio #{'hello'.concat(null)} darebbe errore perché null verrebbe trattato appunto come null e String.concat(null)
//       lancia un NullPointer, invece EL fa una conversione null -> '' e ti salva la vita

// Invece per ottenere il comportamento desiderato in fase di input
// sembra che EL-context in questione sia EvaluationContext
public class EmptyStringToNullELResolver extends ELResolver {

    static final String EVALUATION_CONTEXT_CLASS_NAME = "EvaluationContext";

    static final Map<Class<? extends ELContext>,Boolean> isEvaluationContextCache = new ConcurrentHashMap<>();

    static boolean isEvaluationContext( ELContext context ) {
        return isEvaluationContextCache.computeIfAbsent( context.getClass() , clazz -> clazz.getName().endsWith(EVALUATION_CONTEXT_CLASS_NAME) );
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return String.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertToType(ELContext context, Object value, Class<T> targetType) {

        if (    value == null &&
                targetType == String.class &&
                isEvaluationContext(context) ) {

            context.setPropertyResolved(true);
            //return (T) null;
        }

        return (T) value;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        // NOOP.
    }

}
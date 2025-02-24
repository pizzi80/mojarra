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

package com.sun.faces.facelets.tag.faces.core;

import java.io.IOException;
import java.io.Serializable;

import com.sun.faces.facelets.tag.TagHandlerImpl;
import com.sun.faces.facelets.tag.faces.CompositeComponentTagHandler;
import com.sun.faces.facelets.util.ReflectionUtil;

import jakarta.el.ValueExpression;
import jakarta.faces.application.Resource;
import jakarta.faces.component.EditableValueHolder;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AbortProcessingException;
import jakarta.faces.event.ValueChangeEvent;
import jakarta.faces.event.ValueChangeListener;
import jakarta.faces.view.EditableValueHolderAttachedObjectHandler;
import jakarta.faces.view.facelets.ComponentHandler;
import jakarta.faces.view.facelets.FaceletContext;
import jakarta.faces.view.facelets.TagAttribute;
import jakarta.faces.view.facelets.TagAttributeException;
import jakarta.faces.view.facelets.TagConfig;
import jakarta.faces.view.facelets.TagException;

/**
 * Register an ValueChangeListener instance on the UIComponent associated with the closest parent UIComponent custom
 * action.
 *
 * See
 * <a target="_new" href="http://java.sun.com/j2ee/javaserverfaces/1.1_01/docs/tlddocs/f/valueChangeListener.html">tag
 * documentation</a>.
 *
 * @author Jacob Hookom
 */
public final class ValueChangeListenerHandler extends TagHandlerImpl implements EditableValueHolderAttachedObjectHandler {

    private final TagAttribute binding;
    private final String listenerType;
    private final TagAttribute typeAttribute;

    public ValueChangeListenerHandler(TagConfig config) {
        super(config);
        binding = getAttribute("binding");
        typeAttribute = getAttribute("type");
        if (null != typeAttribute) {
            final String stringType;
            if (!typeAttribute.isLiteral()) {
                FaceletContext ctx = FaceletContext.getCurrentInstance();
                stringType = typeAttribute.getValueExpression(ctx, String.class).getValue(ctx);
            } else {
                stringType = typeAttribute.getValue();
            }
            checkType(stringType);
            listenerType = stringType;
        } else {
            listenerType = null;
        }
    }

    /**
     * See taglib documentation.
     */
    @Override
    public void apply(FaceletContext ctx, UIComponent parent) throws IOException {
        // only process if it's been created
        if (parent == null || !ComponentHandler.isNew(parent)) {
            return;
        }

        if (parent instanceof EditableValueHolder) {
            applyAttachedObject(ctx.getFacesContext(), parent);
        } else if (parent.getAttributes().containsKey(Resource.COMPONENT_RESOURCE_KEY)) {
            // Allow the composite component to know about the target
            // component.
            CompositeComponentTagHandler.getAttachedObjectHandlers(parent).add(this);
        } else {
            throw new TagException(tag, "Parent is not of type EditableValueHolder, type is: " + parent);
        }
    }

    @Override
    public void applyAttachedObject(FacesContext context, UIComponent parent) {
        FaceletContext ctx = FaceletContext.getCurrentInstance(context);
        EditableValueHolder evh = (EditableValueHolder) parent;
        ValueExpression ve = null;
        if (binding != null) {
            ve = binding.getValueExpression(ctx, ValueChangeListener.class);
        }
        ValueChangeListener listener = new LazyValueChangeListener(listenerType, ve);
        evh.addValueChangeListener(listener);
    }

    @Override
    public String getFor() {
        final TagAttribute attr = getAttribute("for");

        final String result;
        if (attr != null) {
            if (attr.isLiteral()) {
                result = attr.getValue();
            } else {
                FaceletContext ctx = FaceletContext.getCurrentInstance();
                result = attr.getValueExpression(ctx, String.class).getValue(ctx);
            }
        } else {
            result = null;
        }
        return result;

    }

    private void checkType(String type) {
        try {
            ReflectionUtil.forName(type);
        } catch (ClassNotFoundException e) {
            throw new TagAttributeException(typeAttribute, "Couldn't qualify ValueChangeListener", e);
        }
    }

    // LazyValueChangeListener ------------------------------------------------------------------------------

    private static class LazyValueChangeListener implements ValueChangeListener, Serializable {

        private static final long serialVersionUID = 7613811124326963180L;

        private final String type;
        private final ValueExpression binding;

        public LazyValueChangeListener(String type, ValueExpression binding) {
            this.type = type;
            this.binding = binding;
        }

        @Override
        public void processValueChange(ValueChangeEvent event) throws AbortProcessingException {
            FacesContext faces = FacesContext.getCurrentInstance();
            if (faces == null) {
                return;
            }

            ValueChangeListener instance = null;
            if (binding != null) {
                instance = binding.getValue(faces.getELContext());
            }
            if (instance == null && type != null) {
                try {
                    instance = ReflectionUtil.newInstance(type);
                } catch (Exception e) {
                    throw new AbortProcessingException("Could not instantiate ValueChangeListener of type " + type, e);
                }
                if (binding != null) {
                    binding.setValue(faces.getELContext(), instance);
                }
            }
            if (instance != null) {
                instance.processValueChange(event);
            }
        }
    }

}

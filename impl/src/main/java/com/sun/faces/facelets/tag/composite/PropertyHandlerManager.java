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

package com.sun.faces.facelets.tag.composite;

import static java.util.Map.entry;

import java.beans.FeatureDescriptor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.sun.faces.facelets.el.TagValueExpression;
import com.sun.faces.util.Util;

import jakarta.el.ValueExpression;
import jakarta.faces.application.ProjectStage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.view.facelets.FaceletContext;
import jakarta.faces.view.facelets.TagAttribute;

class PropertyHandlerManager {

    private static final Map<String, PropertyHandler> ALL_HANDLERS = Map.ofEntries(
        entry("targets", new StringValueExpressionPropertyHandler()),
        entry("targetAttributeName", new StringValueExpressionPropertyHandler()),
        entry("method-signature", new StringValueExpressionPropertyHandler()),
        entry("type", new StringValueExpressionPropertyHandler()),
        entry("default", new DefaultPropertyHandler()),
        entry("displayName", new DisplayNamePropertyHandler()),
        entry("shortDescription", new ShortDescriptionPropertyHandler()),
        entry("expert", new ExpertPropertyHandler()),
        entry("hidden", new HiddenPropertyHandler()),
        entry("preferred", new PreferredPropertyHandler()),
        entry("required", new BooleanValueExpressionPropertyHandler()),
        entry("name", new NamePropertyHandler()),
        entry("componentType", new ComponentTypePropertyHandler())
    );

    private static final Set<String> DEV_ONLY_ATTRIBUTES = Set.of( "displayName", "shortDescription", "export", "hidden", "preferred" );

    private final Map<String, PropertyHandler> managedHandlers;
    private final PropertyHandler genericHandler = new ObjectValueExpressionPropertyHandler();

    // -------------------------------------------------------- Constructors

    private PropertyHandlerManager(Map<String, PropertyHandler> managedHandlers) {
        this.managedHandlers = managedHandlers;
    }

    // ------------------------------------------------- Package Private Methods

    static PropertyHandlerManager getInstance(String[] attributes) {
        Map<String, PropertyHandler> handlers = new HashMap<>(Util.calculateMapCapacity(attributes.length));
        for (String attribute : attributes) {
            handlers.put(attribute, ALL_HANDLERS.get(attribute));
        }
        return new PropertyHandlerManager(handlers);
    }

    PropertyHandler getHandler(FaceletContext ctx, String name) {

        if (ctx.getFacesContext().isProjectStage(ProjectStage.Production) && DEV_ONLY_ATTRIBUTES.contains(name)) {
            return null;
        }

        PropertyHandler h = managedHandlers.get(name);
        return h != null ? h : genericHandler;
    }

    // ---------------------------------------------------------- Nested Classes

    private abstract static class BooleanFeatureDescriptorPropertyHandler implements TypedPropertyHandler {

        @Override
        public Class<?> getEvalType() {
            return Boolean.class;
        }

    } // END BooleanFeatureDescriptorPropertyHandler

    private abstract static class StringFeatureDescriptorPropertyHandler implements TypedPropertyHandler {

        @Override
        public Class<?> getEvalType() {
            return String.class;
        }

    } // END StringPropertyDescriptionPropertyHandler

    private abstract static class TypedValueExpressionPropertyHandler implements TypedPropertyHandler {

        @Override
        public void apply(FaceletContext ctx, String propName, FeatureDescriptor target, TagAttribute attribute) {

            target.setValue(propName, attribute.getValueExpression(ctx, getEvalType()));

        }

        @Override
        public abstract Class<?> getEvalType();

    } // END TypeValueExpressionPropertyHandler

    private static final class NamePropertyHandler extends StringFeatureDescriptorPropertyHandler {

        @Override
        public void apply(FaceletContext context, String propName, FeatureDescriptor target, TagAttribute attribute) {

            ValueExpression ve = attribute.getValueExpression(context, getEvalType());
            String value = ve.getValue(context);

            if (value != null) {
                target.setShortDescription(value);
            }
        }
    }

    private static final class ShortDescriptionPropertyHandler extends StringFeatureDescriptorPropertyHandler {

        @Override
        public void apply(FaceletContext ctx, String propName, FeatureDescriptor target, TagAttribute attribute) {

            ValueExpression ve = attribute.getValueExpression(ctx, getEvalType());
            String value = ve.getValue(ctx);
            if (value != null) {
                target.setShortDescription(value);
            }

        }

    } // END ShortDescriptionPropertyHandler

    private static class StringValueExpressionPropertyHandler extends TypedValueExpressionPropertyHandler {

        @Override
        public Class<?> getEvalType() {
            return String.class;
        }

    } // END StringValueExpressionPropertyHandler

    private static class ObjectValueExpressionPropertyHandler extends TypedValueExpressionPropertyHandler {

        @Override
        public Class<?> getEvalType() {
            return Object.class;
        }

    } // END ObjectValueExpressionPropertyHandler

    /**
     * This PropertyHandler will apply the default-value of a cc:attribute tag, taking an eventually provided type into
     * account.
     */
    private static class DefaultPropertyHandler implements PropertyHandler {

        @Override
        public void apply(FaceletContext ctx, String propName, FeatureDescriptor target, TagAttribute attribute) {

            // try to get the type from the 'type'-attribute and default to
            // Object.class, if no type-attribute was set.
            Class<?> type = Object.class;
            Object obj = target.getValue("type");
            if (null != obj && !(obj instanceof Class)) {
                TagValueExpression typeVE = (TagValueExpression) obj;
                Object value = typeVE.getValue(ctx);
                if (value instanceof Class<?>) {
                    type = (Class<?>) value;
                } else if (value != null) {
                    try {
                        type = Util.loadClass(String.valueOf(value), this);
                    } catch (ClassNotFoundException ex) {
                        // Wrap the ClassNotFoundException into a
                        // RuntimeException, so that it can be unwrapped in the
                        // caller
                        throw new IllegalArgumentException(ex);
                    }
                }
            } else {
                type = null != obj ? (Class<?>) obj : Object.class;
            }
            target.setValue(propName, attribute.getValueExpression(ctx, type));
        }

    }

    private static class ComponentTypePropertyHandler extends StringValueExpressionPropertyHandler {

        @Override
        public void apply(FaceletContext ctx, String propName, FeatureDescriptor target, TagAttribute attribute) {
            super.apply(ctx, UIComponent.COMPOSITE_COMPONENT_TYPE_KEY, target, attribute);

        }

    } // END ComponentTypePropertyHandler

    private static final class PreferredPropertyHandler extends BooleanFeatureDescriptorPropertyHandler {

        @Override
        public void apply(FaceletContext ctx, String propName, FeatureDescriptor target, TagAttribute attribute) {

            ValueExpression ve = attribute.getValueExpression(ctx, getEvalType());
            target.setPreferred(ve.getValue(ctx));

        }

    } // END PreferredPropertyHandler

    private static final class HiddenPropertyHandler extends BooleanFeatureDescriptorPropertyHandler {

        @Override
        public void apply(FaceletContext ctx, String propName, FeatureDescriptor target, TagAttribute attribute) {

            ValueExpression ve = attribute.getValueExpression(ctx, getEvalType());
            target.setHidden(ve.getValue(ctx));

        }

    } // END HiddenPropertyHandler

    private static final class ExpertPropertyHandler extends BooleanFeatureDescriptorPropertyHandler {

        @Override
        public void apply(FaceletContext ctx, String propName, FeatureDescriptor target, TagAttribute attribute) {

            ValueExpression ve = attribute.getValueExpression(ctx, getEvalType());
            target.setExpert(ve.getValue(ctx));

        }

    } // END ExpertPropertyHandler

    private static final class DisplayNamePropertyHandler extends StringFeatureDescriptorPropertyHandler {

        @Override
        public void apply(FaceletContext ctx, String propName, FeatureDescriptor target, TagAttribute attribute) {

            ValueExpression ve = attribute.getValueExpression(ctx, getEvalType());
            target.setDisplayName(ve.getValue(ctx));
        }

    } // END DisplayNamePropertyHandler

    private static class BooleanValueExpressionPropertyHandler extends TypedValueExpressionPropertyHandler {

        @Override
        public Class<?> getEvalType() {
            return Boolean.class;
        }

    } // END BooleanValueExpressionPropertyHandler

}

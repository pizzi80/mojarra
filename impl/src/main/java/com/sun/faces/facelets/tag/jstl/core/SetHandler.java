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

package com.sun.faces.facelets.tag.jstl.core;

import java.io.IOException;
import java.util.Iterator;

import com.sun.faces.facelets.tag.TagHandlerImpl;

import jakarta.el.ValueExpression;
import jakarta.faces.component.UIComponent;
import jakarta.faces.view.facelets.FaceletContext;
import jakarta.faces.view.facelets.TagAttribute;
import jakarta.faces.view.facelets.TagConfig;
import jakarta.faces.view.facelets.TagException;
import jakarta.faces.view.facelets.TextHandler;

/**
 * Simplified implementation of c:set
 *
 * @author Jacob Hookom
 * @version $Id$
 */
public class SetHandler extends TagHandlerImpl {

    private final TagAttribute var;

    private final TagAttribute value;

    private final TagAttribute target;

    private final TagAttribute property;

    private final TagAttribute scope;

    public SetHandler(TagConfig config) {
        super(config);
        value = getAttribute("value");
        var = getAttribute("var");
        target = getAttribute("target");
        property = getAttribute("property");
        scope = getAttribute("scope");

    }

    @Override
    public void apply(FaceletContext context, UIComponent parent) throws IOException {

        markDynamicTransientBuild(ctx);

        StringBuilder bodyValue = new StringBuilder();

        Iterator<TextHandler> iterator = findNextByType(TextHandler.class);
        while (iterator.hasNext()) {
            TextHandler text = iterator.next();
            bodyValue.append(text.getText(context));
        }

        // true if either a value in body or value attr
        boolean valSet = !bodyValue.isEmpty() || value != null && !value.getValue().isEmpty();

        // Apply precedence algorithm for attributes. The JstlCoreTLV doesn't
        // seem to enforce much in the way of this, so I edburns needs to check
        // with an authority on the matter, probably Kin-Man Chung

        ValueExpression veObj;
        ValueExpression lhs;
        String expr;

        if (value != null) {
            veObj = value.getValueExpression(context, Object.class);
        } else {
            veObj = context.getExpressionFactory().createValueExpression(context.getFacesContext().getELContext(), bodyValue.toString(), Object.class);
        }

        // Otherwise, if var is set, ignore the other attributes
        if (var != null) {
            String scopeStr;
            final String varStr = var.getValue(context);

            // If scope is set, check for validity
            if (null != scope) {
                if (scope.getValue().isEmpty()) {
                    throw new TagException(tag, "zero length scope attribute set");
                }

                if (scope.isLiteral()) {
                    scopeStr = scope.getValue();
                } else {
                    scopeStr = scope.getValue(context);
                }
                if (scopeStr.equals("page")) {
                    throw new TagException(tag, "page scope does not exist in Faces, consider using view scope instead.");
                }
                if (scopeStr.equals("request") || scopeStr.equals("session") || scopeStr.equals("application") || scopeStr.equals("view")) {
                    scopeStr = scopeStr + "Scope";
                }
                // otherwise, assume it's a valid scope. With custom scopes,
                // it may be.
                // Conjure up an expression
                expr = "#{" + scopeStr + '.' + varStr + '}';
                lhs = context.getExpressionFactory().createValueExpression(context, expr, Object.class);
                lhs.setValue(context, veObj.getValue(context));
            } else {
                context.getVariableMapper().setVariable(varStr, veObj);
            }
        } else {

            // Otherwise, target, property and value must be set
            if (null == target || null == target.getValue() || target.getValue().isEmpty()
                    || null == property || null == property.getValue() || property.getValue().isEmpty() || !valSet) {

                throw new TagException(tag, "when using this tag either one of var and value, or (target, property, value) must be set.");
            }
            // Ensure that target is an expression
            if (target.isLiteral()) {
                throw new TagException(tag, "value of target attribute must be an expression");
            }
            // Get the value of property
            final String propertyStr;
            if (property.isLiteral()) {
                propertyStr = property.getValue();
            } else {
                propertyStr = property.getValue(context);
            }
            ValueExpression targetVe = target.getValueExpression(context, Object.class);
            Object targetValue = targetVe.getValue(context);
            context.getFacesContext().getELContext().getELResolver().setValue(context, targetValue, propertyStr, veObj.getValue(context));

        }
    }

}

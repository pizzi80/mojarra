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

package com.sun.faces.facelets.tag.faces.html;

import com.sun.faces.facelets.tag.TagAttributesImpl;

import jakarta.faces.view.facelets.Tag;
import jakarta.faces.view.facelets.TagAttribute;
import jakarta.faces.view.facelets.TagAttributes;
import jakarta.faces.view.facelets.TagDecorator;

/**
 * @author Jacob Hookom
 */
public final class HtmlDecorator implements TagDecorator {

    public final static String XhtmlNamespace = "http://www.w3.org/1999/xhtml";

    public final static HtmlDecorator INSTANCE = new HtmlDecorator();

    /**
     *
     */
    public HtmlDecorator() {
        super();
    }

    /*
     * (non-Javadoc)
     *
     * @see com.sun.facelets.tag.TagDecorator#decorate(com.sun.facelets.tag.Tag)
     */
    @Override
    public Tag decorate(Tag tag) {
        if (XhtmlNamespace.equals(tag.getNamespace())) {
            String localName = tag.getLocalName();
            switch (localName) {
                case "a" -> {
                    return new Tag(tag.getLocation(), HtmlLibrary.DEFAULT_NAMESPACE, "commandLink", tag.getQName(), tag.getAttributes());
                }
                case "form" -> {
                    return new Tag(tag.getLocation(), HtmlLibrary.DEFAULT_NAMESPACE, "form", tag.getQName(), tag.getAttributes());
                }
                case "input" -> {
                    TagAttribute attr = tag.getAttributes().get("type");
                    if (attr != null) {
                        TagAttributes na = removeTypeAttribute(tag.getAttributes());
                        String t = attr.getValue();
                        switch (t) {
                            case "text" -> {
                                return new Tag(tag.getLocation(), HtmlLibrary.DEFAULT_NAMESPACE, "inputText", tag.getQName(), na);
                            }
                            case "password" -> {
                                return new Tag(tag.getLocation(), HtmlLibrary.DEFAULT_NAMESPACE, "inputSecret", tag.getQName(), na);
                            }
                            case "hidden" -> {
                                return new Tag(tag.getLocation(), HtmlLibrary.DEFAULT_NAMESPACE, "inputHidden", tag.getQName(), na);
                            }
                            case "submit" -> {
                                return new Tag(tag.getLocation(), HtmlLibrary.DEFAULT_NAMESPACE, "commandButton", tag.getQName(), na);
                            }
                            case "file" -> {
                                return new Tag(tag.getLocation(), HtmlLibrary.DEFAULT_NAMESPACE, "inputFile", tag.getQName(), na);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // OMG: this creates a copy of N arrays only to remove an attribute from a Set of attributes
    private static TagAttributes removeTypeAttribute(TagAttributes attrs) {
        TagAttribute[] tagAttributes = attrs.getAll();

        TagAttribute[] tagAttributesCopy = new TagAttribute[tagAttributes.length - 1];
        int p = 0;
        for (TagAttribute tagAttribute : tagAttributes) {
            if (!"type".equals(tagAttribute.getLocalName())) {
                tagAttributesCopy[p++] = tagAttribute;
            }
        }
        return new TagAttributesImpl(tagAttributesCopy);
    }

}

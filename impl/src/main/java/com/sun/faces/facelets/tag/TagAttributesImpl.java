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

package com.sun.faces.facelets.tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sun.faces.RIConstants;
import com.sun.faces.util.Util;

import jakarta.faces.view.facelets.Tag;
import jakarta.faces.view.facelets.TagAttribute;
import jakarta.faces.view.facelets.TagAttributes;

/**
 * A set of TagAttributesImpl, usually representing all attributes on a Tag.
 *
 * @see jakarta.faces.view.facelets.TagAttribute
 * @author Jacob Hookom
 * @version $Id$
 */
public final class TagAttributesImpl extends TagAttributes {
    private final static TagAttribute[] EMPTY = new TagAttribute[0];

//    private final Map<String,Integer> nsIndex;

    private final TagAttribute[] attrs;

    private final String[] ns;

    private final List nsattrs;

    private Tag tag;

    /**
     *
     */
    public TagAttributesImpl(TagAttribute[] attrs) {
        this.attrs = attrs;

        // grab namespaces => uniq + sort => toArray
        Set<String> set = new HashSet<>(Util.calculateMapCapacity(this.attrs.length));
        for (TagAttribute attr : this.attrs) {
            set.add(attr.getNamespace());
        }
        ns = set.toArray(new String[set.size()]);
        Arrays.sort(ns);

        // init the binarySearch cache
//        nsIndex = new HashMap<>(Util.calculateMapCapacity(ns.length));

        // build the matrix assign attrs
        nsattrs = new ArrayList<>();
        for (int i = 0; i < ns.length; i++) {
            nsattrs.add(new ArrayList<>());
        }

        for (TagAttribute attr : this.attrs) {
            ((List) nsattrs.get(getNamespaceIndex(attr.getNamespace()))).add(attr);
        }
        for (int i = 0; i < ns.length; i++) {
            List r = (List) nsattrs.get(i);
            nsattrs.set(i, r.toArray(new TagAttribute[r.size()]));
        }
    }

    private int getNamespaceIndex(String namespace) {
//        return nsIndex.computeIfAbsent(namespace, $ -> Arrays.binarySearch(ns, namespace));
        return Arrays.binarySearch(ns, namespace);
    }

    /**
     * Return an array of all TagAttributesImpl in this set
     *
     * @return a non-null array of TagAttributesImpl
     */
    @Override
    public TagAttribute[] getAll() {
        return attrs;
    }

    /**
     * Using no namespace, find the TagAttribute
     *
     * @see #get(String, String)
     * @param localName tag attribute name
     * @return the TagAttribute found, otherwise null
     */
    @Override
    public TagAttribute get(String localName) {
        return get(RIConstants.NO_VALUE, localName);
    }

    /**
     * Find a TagAttribute that matches the passed namespace and local name.
     *
     * @param ns namespace of the desired attribute
     * @param localName local name of the attribute
     * @return a TagAttribute found, otherwise null
     */
    @Override
    public TagAttribute get(String ns, String localName) {
        if (ns != null && localName != null) {
            int idx = getNamespaceIndex(ns);
            if (idx >= 0) {
                TagAttribute[] uia = (TagAttribute[]) nsattrs.get(idx);
                for (TagAttribute tagAttribute : uia) {
                    if (localName.equals(tagAttribute.getLocalName())) {
                        return tagAttribute;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get all TagAttributesImpl for the passed namespace
     *
     * @param namespace namespace to search
     * @return a non-null array of TagAttributesImpl
     */
    @Override
    public TagAttribute[] getAll(String namespace) {
        int idx = getNamespaceIndex(Objects.requireNonNullElse(namespace, RIConstants.NO_VALUE));
        if (idx >= 0) {
            return (TagAttribute[]) nsattrs.get(idx);
        }
        return EMPTY;
    }

    /**
     * A list of Namespaces found in this set
     *
     * @return a list of Namespaces found in this set
     */
    @Override
    public String[] getNamespaces() {
        return ns;
    }

    @Override
    public Tag getTag() {
        return tag;
    }

    @Override
    public void setTag(Tag tag) {
        this.tag = tag;
        for (TagAttribute cur : attrs) {
            cur.setTag(tag);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (TagAttribute attr : attrs) {
            sb.append(attr);
            sb.append(' ');
        }
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

}

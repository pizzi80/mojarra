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

import java.util.Arrays;

import com.sun.faces.RIConstants;
import com.sun.faces.facelets.tag.faces.PassThroughAttributeLibrary;
import com.sun.faces.util.Util;

import jakarta.faces.view.facelets.Tag;
import jakarta.faces.view.facelets.TagAttribute;
import jakarta.faces.view.facelets.TagAttributes;

/**
 * A set of TagAttributesImpl, usually representing all attributes on a Tag.
 * <p>
 * Attributes are grouped per namespace once, when the tag is compiled. A tag almost always declares attributes in a
 * single namespace (a few in two), so namespaces are looked up with a linear scan: on one or two entries it is cheaper
 * than hashing or a binary search, and {@link String#equals(Object)} short-circuits on identity for the interned
 * namespace strings coming from the parser.
 * <p>
 * Namespaces, and attributes within a namespace, are kept in declaration order. Namespaces are assumed non-null, as
 * the compiler always passes the empty string for attributes without a namespace.
 *
 * @see jakarta.faces.view.facelets.TagAttribute
 * @author Jacob Hookom
 * @version $Id$
 */
public final class TagAttributesImpl extends TagAttributes {
    private static final TagAttribute[] EMPTY = {};

    private final TagAttribute[] attrs;

    /** Distinct namespaces, in order of first appearance. */
    private final String[] ns;

    /** Attributes of each namespace, parallel to {@link #ns}, in declaration order. */
    private final TagAttribute[][] nsAttrs;

    /** Attributes in a pass-through namespace, in declaration order. */
    private final TagAttribute[] passthroughAttrs;

    private Tag tag;

    /**
     * @param attrs all attributes of the tag, in declaration order
     */
    public TagAttributesImpl(TagAttribute[] attrs) {
        this.attrs = attrs;

        int length = attrs.length;

        // distinct namespaces in order of first appearance, attribute count per namespace,
        // namespace index of each attribute
        String[] found = new String[length];
        int[] counts = new int[length];
        int[] attrNs = new int[length];
        int n = 0;

        for (int i = 0; i < length; i++) {
            String namespace = attrs[i].getNamespace();
            int idx = indexOf(found, n, namespace);
            if (idx < 0) {
                idx = n++;
                found[idx] = namespace;
            }
            counts[idx]++;
            attrNs[i] = idx;
        }

        this.ns = n == length ? found : Arrays.copyOf(found, n);

        // pass-through: one Set lookup per namespace, not per attribute
        boolean[] passthrough = new boolean[n];
        int passthroughCount = 0;

        for (int k = 0; k < n; k++) {
            if (PassThroughAttributeLibrary.NAMESPACES.contains(found[k])) {
                passthrough[k] = true;
                passthroughCount += counts[k];
            }
        }

        // group per namespace (must come after the pass-through count: counts is reused as fill cursor)
        TagAttribute[][] grouped = new TagAttribute[n][];

        if (n == 1) {
            // by far the most common case: a single namespace, its attributes are all of them
            grouped[0] = attrs;
        } else {
            for (int k = 0; k < n; k++) {
                grouped[k] = new TagAttribute[counts[k]];
                counts[k] = 0;
            }
            for (int i = 0; i < length; i++) {
                int idx = attrNs[i];
                grouped[idx][counts[idx]++] = attrs[i];
            }
        }

        this.nsAttrs = grouped;

        // collect pass-through attributes, preserving declaration order across pass-through namespaces
        if (passthroughCount == 0) {
            this.passthroughAttrs = EMPTY;
        } else if (passthroughCount == length) {
            this.passthroughAttrs = attrs;
        } else {
            TagAttribute[] result = new TagAttribute[passthroughCount];
            int j = 0;
            for (int i = 0; i < length; i++) {
                if (passthrough[attrNs[i]]) {
                    result[j++] = attrs[i];
                }
            }
            this.passthroughAttrs = result;
        }
    }

    private static int indexOf(String[] namespaces, int length, String namespace) {
        for (int i = 0; i < length; i++) {
            if (namespaces[i].equals(namespace)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOf(String namespace) {
        return indexOf(ns, ns.length, namespace);
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
     * Return the attributes that are in a pass-through namespace, in the order the tag declares them. Every applied
     * component tag is asked for these and almost none has any, so they are singled out once here, when the tag is
     * compiled, rather than searched per namespace on every apply.
     *
     * @return a non-null array of TagAttribute
     */
    public TagAttribute[] getPassthroughAttributes() {
        return passthroughAttrs;
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
        return get(RIConstants.EMPTY_STRING, localName);
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
            int idx = indexOf(ns);
            if (idx >= 0) {
                for (TagAttribute attr : nsAttrs[idx]) {
                    if (localName.equals(attr.getLocalName())) {
                        return attr;
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
        int idx = indexOf(Util.coalesce(namespace, RIConstants.EMPTY_STRING));
        return idx >= 0 ? nsAttrs[idx] : EMPTY;
    }

    /**
     * A list of Namespaces found in this set, in order of first appearance
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
        for (TagAttribute attr : attrs) {
            attr.setTag(tag);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        if (attrs.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(attrs.length * 64);
        sb.append(attrs[0]);
        for (int i = 1; i < attrs.length; i++) {
            sb.append(' ').append(attrs[i]);
        }
        return sb.toString();
    }
}
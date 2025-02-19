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

package com.sun.faces.facelets.tag.jstl.fn;

import static com.sun.faces.RIConstants.NO_VALUE;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Implementations of JSTL Functions
 *
 * @author Jacob Hookom
 */
public final class JstlFunction {

    private JstlFunction() {
    }

    public static boolean contains(String name, String searchString) {
        if (name == null) {
            name = NO_VALUE;
        }
        if (searchString == null) {
            searchString = NO_VALUE;
        }
        return name.contains(searchString);
    }

    public static boolean containsIgnoreCase(String name, String searchString) {
        if (name == null) {
            name = NO_VALUE;
        }
        if (searchString == null) {
            searchString = NO_VALUE;
        }
        return name.toLowerCase().contains(searchString.toLowerCase());
    }

    public static boolean endsWith(String name, String searchString) {
        if (name == null) {
            name = NO_VALUE;
        }
        if (searchString == null) {
            searchString = NO_VALUE;
        }
        return name.endsWith(searchString);
    }

    public static String escapeXml(String value) {
        if (value == null || value.isEmpty()) {
            value = NO_VALUE;
        }
        StringBuilder b = new StringBuilder(value.length());
        for (int i = 0, len = value.length(); i < len; i++) {
            char c = value.charAt(i);
            if (c == '<') {
                b.append("&lt;");
            } else if (c == '>') {
                b.append("&gt;");
            } else if (c == '\'') {
                b.append("&#039;");
            } else if (c == '"') {
                b.append("&#034;");
            } else if (c == '&') {
                b.append("&amp;");
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    public static int indexOf(String name, String searchString) {
        if (name == null) {
            name = NO_VALUE;
        }
        if (searchString == null) {
            searchString = NO_VALUE;
        }
        return name.indexOf(searchString);
    }

    public static String join(String[] a, String delim) {
        if (a == null || a.length == 0) {
            return NO_VALUE;
        }
        boolean skipDelim = false;
        if (delim == null || delim.isEmpty()) {
            skipDelim = true;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0, len = a.length, delimCount = len - 1; i < len; i++) {
            sb.append(a[i]);
            if (!skipDelim && i < delimCount) {
                sb.append(delim);
            }
        }
        return sb.toString();
    }

    public static int length(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Collection) {
            return ((Collection) obj).size();
        }
        if (obj.getClass().isArray()) {
            return Array.getLength(obj);
        }
        if (obj instanceof String) {
            return ((String) obj).length();
        }
        if (obj instanceof Map) {
            return ((Map) obj).size();
        }
        if (obj instanceof Enumeration) {
            Enumeration e = (Enumeration) obj;
            int count = 0;
            while (e.hasMoreElements()) {
                e.nextElement();
                count++;
            }
            return count;
        }
        if (obj instanceof Iterator) {
            Iterator i = (Iterator) obj;
            int count = 0;
            while (i.hasNext()) {
                i.next();
                count++;
            }
            return count;
        }
        throw new IllegalArgumentException("Object type not supported: " + obj.getClass().getName());
    }

    public static String replace(String value, String before, String after) {
        if (value == null) {
            value = NO_VALUE;
        }
        if (before == null) {
            before = NO_VALUE;
        }
        if (before.isEmpty()) {
            return value;
        }
        if (value.isEmpty()) {
            return NO_VALUE;
        }
        if (after == null) {
            after = NO_VALUE;
        }

        return value.replaceAll(before, after);
    }

    public static String[] split(String value, String d) {
        if (value == null) {
            value = NO_VALUE;
        }
        if (value.isEmpty()) {
            return new String[] { NO_VALUE };
        }
        if (d == null) {
            d = NO_VALUE;
        }
        if (d.isEmpty()) {
            return new String[] { value };
        }

        List<String> tokens = new ArrayList<>();
        for (StringTokenizer st = new StringTokenizer(value, d); st.hasMoreTokens();) {
            tokens.add(st.nextToken());
        }

        return tokens.toArray(new String[tokens.size()]);
    }

    public static boolean startsWith(String value, String p) {
        if (value == null) {
            value = NO_VALUE;
        }
        if (p == null) {
            p = NO_VALUE;
        }
        return value.startsWith(p);
    }

    public static String substring(String v, int s, int e) {
        if (v == null) {
            v = NO_VALUE;
        }
        if (s >= v.length()) {
            return NO_VALUE;
        }
        if (s < 0) {
            s = 0;
        }
        if (e < 0 || e >= v.length()) {
            e = v.length();
        }
        if (e < s) {
            return NO_VALUE;
        }
        return v.substring(s, e);
    }

    public static String substringAfter(String v, String p) {
        if (v == null) {
            v = NO_VALUE;
        }
        if (v.isEmpty()) {
            return NO_VALUE;
        }
        if (p == null) {
            p = NO_VALUE;
        }
        int i = v.indexOf(p);
        if (i == -1) {
            return NO_VALUE;
        }
        return v.substring(i + p.length());
    }

    public static String substringBefore(String v, String s) {
        if (v == null) {
            v = NO_VALUE;
        }
        if (v.isEmpty()) {
            return NO_VALUE;
        }
        if (s == null) {
            s = NO_VALUE;
        }
        int i = v.indexOf(s);
        if (i == -1) {
            return NO_VALUE;
        }
        return v.substring(0, i);
    }

    public static String toLowerCase(String v) {
        if (v == null || v.isEmpty()) {
            return NO_VALUE;
        }
        return v.toLowerCase();
    }

    public static String toUpperCase(String v) {
        if (v == null || v.isEmpty()) {
            return NO_VALUE;
        }
        return v.toUpperCase();
    }

    public static String trim(String v) {
        if (v == null || v.isEmpty()) {
            return NO_VALUE;
        }
        return v.trim();
    }

}

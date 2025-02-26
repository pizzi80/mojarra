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

package com.sun.faces.mock;

import java.security.Principal;
import java.util.Objects;

/**
 * <p>
 * Mock <strong>Principal</strong> object for low-level unit tests.</p>
 */
public class MockPrincipal implements Principal {

    private static final String EMPTY_STRING = "";
    private static final int EMPTY_STRING_HASHCODE = EMPTY_STRING.hashCode();
    private static final String[] EMPTY_STRING_ARRAY = {};

    protected final String name;
    protected final String[] roles;

    public MockPrincipal() {
        super();
        this.name = EMPTY_STRING;
        this.roles = EMPTY_STRING_ARRAY;
    }

    public MockPrincipal(String name) {
        super();
        this.name = name;
        this.roles = EMPTY_STRING_ARRAY;
    }

    public MockPrincipal(String name, String[] roles) {
        super();
        this.name = name;
        this.roles = roles;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public boolean isUserInRole(String role) {
        for (String s : roles) {
            if (role.equals(s)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Principal principal )) {
            return false;
        }

        return Objects.equals(name, principal.getName());
    }

    @Override
    public int hashCode() {
        return name == null ? EMPTY_STRING_HASHCODE : name.hashCode();
    }
}

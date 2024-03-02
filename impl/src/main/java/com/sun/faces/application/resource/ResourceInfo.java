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

package com.sun.faces.application.resource;

import java.util.Objects;

public class ResourceInfo {

    ResourceHelper helper;
    LibraryInfo library;
    ContractInfo contract;
    String libraryName;
    String localePrefix;
    String name;
    String path;
    VersionInfo version;
    boolean doNotCache = false;

    public ResourceInfo(LibraryInfo library, ContractInfo contract, String name, VersionInfo version) {
        this.library = library;
        this.contract = contract;
        this.name = name;
        this.version = version;
        this.helper = library.getHelper();
        this.localePrefix = library.getLocalePrefix();
        this.libraryName = library.getName();
    }

    public ResourceInfo(ContractInfo contract, String name, VersionInfo version, ResourceHelper helper) {
        this.contract = contract;
        this.name = name;
        this.version = version;
        this.helper = helper;
    }

    public ResourceInfo(ResourceInfo other, boolean copyLocalePrefix) {
        helper = other.helper;
        library = new LibraryInfo(other.library, copyLocalePrefix);
        libraryName = library.getName();
        if (copyLocalePrefix) {
            localePrefix = other.localePrefix;
        }
        name = other.name;
        path = other.path;
        version = other.version;
    }

    public void copy(ResourceInfo other) {
        helper = other.helper;
        library = other.library;
        libraryName = other.libraryName;
        localePrefix = other.localePrefix;
        name = other.name;
        path = other.path;
        version = other.version;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if ( ! (obj instanceof ResourceInfo info) ) {
            return false;
        }

        return  Objects.equals(helper, info.helper) &&
                Objects.equals(library, info.library) &&
                Objects.equals(libraryName, info.libraryName) &&
                Objects.equals(localePrefix, info.localePrefix) &&
                Objects.equals(name, info.name) &&
                Objects.equals(path, info.path) &&
                Objects.equals(version, info.version) &&
                doNotCache == info.doNotCache;
    }

    @Override
    public int hashCode() {
        return Objects.hash(helper, library, contract, libraryName, localePrefix, name, path, version, doNotCache);
    }

    public boolean isDoNotCache() {
        return doNotCache;
    }

    public void setDoNotCache(boolean doNotCache) {
        this.doNotCache = doNotCache;
    }

    /**
     * @return return the {@link ResourceHelper} for this resource
     */
    public ResourceHelper getHelper() {
        return helper;
    }

    /**
     * @return the Library associated with this resource, if any.
     */
    public LibraryInfo getLibraryInfo() {
        return library;
    }

    /**
     * @return the Locale prefix, if any.
     */
    public String getLocalePrefix() {
        return localePrefix;
    }

    /**
     * @return return the library name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return the full path (including the library, if any) of the resource.
     */
    public String getPath() {
        return path;
    }

    public String getContract() {
        return null != contract ? contract.toString() : null;
    }

    /**
     * @return return the version of the resource, or <code>null</code> if the resource isn't versioned.
     */
    public VersionInfo getVersion() {
        return version;
    }

}

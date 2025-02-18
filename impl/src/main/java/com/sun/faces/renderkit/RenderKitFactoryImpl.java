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

package com.sun.faces.renderkit;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.faces.util.Util;

import jakarta.faces.context.FacesContext;
import jakarta.faces.render.RenderKit;
import jakarta.faces.render.RenderKitFactory;

public class RenderKitFactoryImpl extends RenderKitFactory {

    private final ConcurrentHashMap<String, RenderKit> renderKits;

    /**
     * Constructor registers default Render kit.
     */
    public RenderKitFactoryImpl() {
        super(null);
        renderKits = new ConcurrentHashMap<>();
        addRenderKit(HTML_BASIC_RENDER_KIT, new RenderKitImpl());
    }

    @Override
    public void addRenderKit(String renderKitId, RenderKit renderKit) {
        Util.notNull("renderKitId", renderKitId);
        Util.notNull("renderKit", renderKit);

        renderKits.put(renderKitId, renderKit);
    }

    @Override
    public RenderKit getRenderKit(FacesContext context, String renderKitId) {
        Util.notNull("renderKitId", renderKitId);

        // PENDING (rogerk) do something with FacesContext ...
        //
        // If an instance already exists, return it.
        //

        return renderKits.get(renderKitId);
    }

    @Override
    public Iterator<String> getRenderKitIds() {
        return renderKits.keySet().iterator();
    }

}

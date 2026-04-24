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

// RenderKitImpl.java

package com.sun.faces.renderkit;

import static com.sun.faces.RIConstants.APPLICATION_XML_CONTENT_TYPE;
import static com.sun.faces.RIConstants.HTML_CONTENT_TYPE;
import static com.sun.faces.RIConstants.TEXT_XML_CONTENT_TYPE;
import static com.sun.faces.RIConstants.XHTML_CONTENT_TYPE;
import static com.sun.faces.config.WebConfiguration.BooleanWebContextInitParameter.EnableJSStyleHiding;
import static com.sun.faces.config.WebConfiguration.BooleanWebContextInitParameter.EnableScriptInAttributeValue;
import static com.sun.faces.config.WebConfiguration.BooleanWebContextInitParameter.PreferXHTMLContentType;
import static com.sun.faces.config.WebConfiguration.WebContextInitParameter.DisableUnicodeEscaping;
import static java.util.Collections.emptyIterator;

import java.io.OutputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.faces.RIConstants;
import com.sun.faces.config.WebConfiguration;
import com.sun.faces.renderkit.html_basic.HtmlResponseWriter;
import com.sun.faces.util.FacesLogger;
import com.sun.faces.util.MessageUtils;
import com.sun.faces.util.Util;

import jakarta.faces.context.FacesContext;
import jakarta.faces.context.ResponseStream;
import com.sun.faces.context.ResponseStreamWrapper;
import jakarta.faces.context.ResponseWriter;
import jakarta.faces.render.ClientBehaviorRenderer;
import jakarta.faces.render.RenderKit;
import jakarta.faces.render.Renderer;
import jakarta.faces.render.ResponseStateManager;

/**
 * <B>RenderKitImpl</B> is a class ...
 * 
 * <B>Lifetime And Scope</B>
 *
 */
public class RenderKitImpl extends RenderKit {

    private static final Logger LOGGER = FacesLogger.RENDERKIT.getLogger();

    private static final String[] SUPPORTED_CONTENT_TYPES_ARRAY = { HTML_CONTENT_TYPE, XHTML_CONTENT_TYPE,
            APPLICATION_XML_CONTENT_TYPE, TEXT_XML_CONTENT_TYPE };

    private static final String SUPPORTED_CONTENT_TYPES = String.join(",", SUPPORTED_CONTENT_TYPES_ARRAY);

    /**
     * Keys are String renderer family. Values are HashMaps. Nested HashMap keys are Strings for the rendererType, and
     * values are the Renderer instances themselves.
     */
    private final ConcurrentHashMap<String, HashMap<String, Renderer>> rendererFamilies = new ConcurrentHashMap<>();

    /**
     * For Behavior Renderers: Keys are Strings for the behaviorRendererType, and values are the behaviorRenderer instances
     * themselves.
     */
    private final ConcurrentHashMap<String, ClientBehaviorRenderer> behaviorRenderers = new ConcurrentHashMap<>();

    private final ResponseStateManager responseStateManager = new ResponseStateManagerImpl();

    private final WebConfiguration webConfig;

    public RenderKitImpl() {
        webConfig = WebConfiguration.getInstance();
    }

    @Override
    public void addRenderer(String family, String rendererType, Renderer renderer) {
        Util.notNull("family", family);
        Util.notNull("rendererType", rendererType);
        Util.notNull("renderer", renderer);

        Map<String, Renderer> renderers = rendererFamilies.computeIfAbsent(family, $ -> new HashMap<>());

        if (LOGGER.isLoggable(Level.FINE) && renderers.containsKey(rendererType)) {
            LOGGER.log(Level.FINE, "rendererType {0} has already been registered for family {1}.  Replacing existing renderer class type {2} with {3}.",
                    new Object[] { rendererType, family, renderers.get(rendererType).getClass().getName(), renderer.getClass().getName() });
        }

        renderers.put(rendererType, renderer);
    }

    @Override
    public Renderer getRenderer(String family, String rendererType) {
        Util.notNull("family", family);
        Util.notNull("rendererType", rendererType);

        Map<String, Renderer> renderers = rendererFamilies.get(family);
        return renderers != null ? renderers.get(rendererType) : null;
    }

    @Override
    public void addClientBehaviorRenderer(String behaviorRendererType, ClientBehaviorRenderer behaviorRenderer) {
        Util.notNull("behaviorRendererType", behaviorRendererType);
        Util.notNull("behaviorRenderer", behaviorRenderer);

        if (LOGGER.isLoggable(Level.FINE) && behaviorRenderers.containsKey(behaviorRendererType)) {
            LOGGER.log(Level.FINE, "behaviorRendererType {0} has already been registered.  Replacing existing behavior renderer class type {1} with {2}.",
                    new Object[] { behaviorRendererType, behaviorRenderers.get(behaviorRendererType).getClass().getName(),
                            behaviorRenderer.getClass().getName() });
        }

        behaviorRenderers.put(behaviorRendererType, behaviorRenderer);
    }

    @Override
    public ClientBehaviorRenderer getClientBehaviorRenderer(String behaviorRendererType) {
        Util.notNull("behaviorRendererType", behaviorRendererType);
        return behaviorRenderers.get(behaviorRendererType);
    }

    @Override
    public Iterator<String> getClientBehaviorRendererTypes() {
        return behaviorRenderers.keySet().iterator();
    }

    @Override
    public synchronized ResponseStateManager getResponseStateManager() {
        return responseStateManager;
    }

    @Override
    public ResponseWriter createResponseWriter(Writer writer, String desiredContentTypeList, String characterEncoding) {
        if (writer == null) {
            return null;
        }

        final FacesContext context = FacesContext.getCurrentInstance();
        String contentType = null;
        boolean contentTypeNullFromResponse = false;

        // Step 1: Check the content type passed into this method
        if (desiredContentTypeList != null) {
            contentType = findMatch(desiredContentTypeList);
        }

        // Step 2: Check the response content type
        if (desiredContentTypeList == null) {
            desiredContentTypeList = context.getExternalContext().getResponseContentType();
            if (desiredContentTypeList != null) {
                contentType = findMatch(desiredContentTypeList);
                if (contentType == null) {
                    contentTypeNullFromResponse = true;
                }
            }
        }

        // Step 3: Check the Accept Header content type
        // Evaluate the accept header in accordance with HTTP specification -
        // Section 14.1
        // Preconditions for this (1 or 2):
        // 1. content type was not specified to begin with
        // 2. an unsupported content type was retrieved from the response
        if (desiredContentTypeList == null || contentTypeNullFromResponse) {
            String[] typeArray = context.getExternalContext().getRequestHeaderValuesMap().get("Accept");
            if (typeArray.length > 0) {
                StringBuilder builder = new StringBuilder();
                builder.append(typeArray[0]);
                for (int i = 1, len = typeArray.length; i < len; i++) {
                    builder.append(',');
                    builder.append(typeArray[i]);
                }
                desiredContentTypeList = builder.toString();
            }

            if (desiredContentTypeList != null) {
                desiredContentTypeList = RenderKitUtils.determineContentType(desiredContentTypeList, SUPPORTED_CONTENT_TYPES,
                        preferXhtml() ? XHTML_CONTENT_TYPE : null);
                if (desiredContentTypeList != null) {
                    contentType = findMatch(desiredContentTypeList);
                }
            }
        }

        // Step 4: Default to text/html
        if (contentType == null) {
            if (desiredContentTypeList == null) {
                contentType = getDefaultContentType();
            } else {
                String[] desiredContentTypes = contentTypeSplit(desiredContentTypeList);
                for (String desiredContentType : desiredContentTypes) {
                    if (RIConstants.ALL_MEDIA.equals(desiredContentType)) {
                        contentType = getDefaultContentType();
                    }
                }
            }
        }

        if (contentType == null) {
            throw new IllegalArgumentException(MessageUtils.getExceptionMessageString(MessageUtils.CONTENT_TYPE_ERROR_MESSAGE_ID));
        }

        if (characterEncoding == null) {
            characterEncoding = RIConstants.CHAR_ENCODING;
        }

        boolean scriptHiding = webConfig.isOptionEnabled(EnableJSStyleHiding);
        boolean scriptInAttributes = webConfig.isOptionEnabled(EnableScriptInAttributeValue);
        WebConfiguration.DisableUnicodeEscaping escaping = WebConfiguration.DisableUnicodeEscaping.getByValue(webConfig.getOptionValue(DisableUnicodeEscaping));
        boolean isPartial = context.getPartialViewContext().isPartialRequest();
        return new HtmlResponseWriter(writer, contentType, characterEncoding, scriptHiding, scriptInAttributes, escaping, isPartial);
    }

    private boolean preferXhtml() {
        return webConfig.isOptionEnabled(PreferXHTMLContentType);
    }

    private String getDefaultContentType() {
        return preferXhtml() ? XHTML_CONTENT_TYPE : HTML_CONTENT_TYPE;
    }

    private static String[] contentTypeSplit(final String contentTypeString) {
        final String[] result = contentTypeString.split(",");
        for (int i = 0; i < result.length; i++) {
            int semicolon = result[i].indexOf(';');
            if (semicolon != -1) {
                result[i] = result[i].substring(0, semicolon);
                result[i] = result[i].trim();
            }
        }
        return result;
    }

    // Helper method that returns the content type if the desired content type is found in the
    // array of supported types.
    private static String findMatch(final String desiredContentTypeList) {
        final String[] desiredTypes = contentTypeSplit(desiredContentTypeList);

        // For each entry in the desiredTypes array, look for a match in
        // the supportedTypes array
        String contentType = null;
        for (String curDesiredType : desiredTypes) {
            for (String curContentType : SUPPORTED_CONTENT_TYPES_ARRAY) {
                if (curDesiredType.contains(curContentType)) {
                    if (curContentType.contains(HTML_CONTENT_TYPE)) {
                        contentType = HTML_CONTENT_TYPE;
                    } else if (curContentType.contains(XHTML_CONTENT_TYPE) || curContentType.contains(APPLICATION_XML_CONTENT_TYPE) || curContentType.contains(TEXT_XML_CONTENT_TYPE)) {
                        contentType = XHTML_CONTENT_TYPE;
                    }
                    break;
                }
            }
            if (contentType != null) {
                break;
            }
        }
        return contentType;
    }

    @Override
    public ResponseStream createResponseStream(final OutputStream out) {
        return new ResponseStreamWrapper(out);
    }

    /**
     * @see jakarta.faces.render.RenderKit#getComponentFamilies()
     */
    @Override
    public Iterator<String> getComponentFamilies() {
        return rendererFamilies.keySet().iterator();
    }

    /**
     * @see jakarta.faces.render.RenderKit#getRendererTypes(String)
     */
    @Override
    public Iterator<String> getRendererTypes(String componentFamily) {
        var family = rendererFamilies.get(componentFamily);

        return family != null ? family.keySet().iterator() : emptyIterator();
    }

    // The test for this class is in TestRenderKit.java

}

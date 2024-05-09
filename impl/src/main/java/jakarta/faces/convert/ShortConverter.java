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

package jakarta.faces.convert;

import static com.sun.faces.util.Util.notNullArgs;

import com.sun.faces.RIConstants;

import jakarta.faces.application.SharedUtils;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;

/**
 * <p>
 * {@link Converter} implementation for <code>java.lang.Short</code> (and short primitive) values.
 * </p>
 */

public class ShortConverter implements Converter<Short> {

    // ------------------------------------------------------ Manifest Constants

    /**
     * <p>
     * The standard converter id for this converter.
     * </p>
     */
    public static final String CONVERTER_ID = "jakarta.faces.Short";

    /**
     * <p>
     * The message identifier of the {@link jakarta.faces.application.FacesMessage} to be created if the conversion to
     * <code>Short</code> fails. The message format string for this message may optionally include the following
     * placeholders:
     * <ul>
     * <li><code>{0}</code> replaced by the unconverted value.</li>
     * <li><code>{1}</code> replaced by an example value.</li>
     * <li><code>{2}</code> replaced by a <code>String</code> whose value is the label of the input component that produced
     * this message.</li>
     * </ul>
     */
    public static final String SHORT_ID = "jakarta.faces.converter.ShortConverter.SHORT";

    /**
     * <p>
     * The message identifier of the {@link jakarta.faces.application.FacesMessage} to be created if the conversion of the
     * <code>Short</code> value to <code>String</code> fails. The message format string for this message may optionally
     * include the following placeholders:
     * <ul>
     * <li><code>{0}</code> relaced by the unconverted value.</li>
     * <li><code>{1}</code> replaced by a <code>String</code> whose value is the label of the input component that produced
     * this message.</li>
     * </ul>
     */
    public static final String STRING_ID = "jakarta.faces.converter.STRING";

    // ------------------------------------------------------- Converter Methods

    /**
     * @throws ConverterException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    public Short getAsObject(FacesContext context, UIComponent component, String value) {
        notNullArgs( context , component );

        // strip (trim) the input if not blank, null otherwise
        value = SharedUtils.trimToNull(value);

        // If the specified value is null, return null
        if (value == null) {
            return null;
        }

        try {
            return Short.valueOf(value);
        }
        catch (NumberFormatException nfe) {
            throw new ConverterException(MessageFactory.getMessage(context, SHORT_ID, value, "32456", MessageFactory.getLabel(context, component)), nfe);
        }
        catch (Exception e) {
            throw new ConverterException(e);
        }
    }

    /**
     * @throws ConverterException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    public String getAsString(FacesContext context, UIComponent component, Short value) {
        notNullArgs( context , component );

        // If the specified value is null, return an empty string
        if (value == null) {
            return RIConstants.NO_VALUE;
        }

        try {
            return Short.toString(value);
        }
        catch (Exception e) {
            throw new ConverterException(MessageFactory.getMessage(context, STRING_ID, value, MessageFactory.getLabel(context, component)), e);
        }
    }

}

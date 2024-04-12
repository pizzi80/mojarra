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

package com.sun.faces.io;

import java.io.IOException;
import java.io.Writer;

/**
 * <p>
 * This is based on {@link java.io.StringWriter} but backed by a {@link StringBuilder} instead.
 * </p>
 * 
 * <p>
 * This class is not thread safe.
 * </p>
 */
public class FastStringWriter extends Writer {

    protected final StringBuilder builder;

    // ------------------------------------------------------------ Constructors

    /**
     * <p>
     * Constructs a new <code>FastStringWriter</code> instance using the default capacity of <code>16</code>.
     * </p>
     */
    public FastStringWriter() {
        builder = new StringBuilder();
    }

    /**
     * <p>
     * Constructs a new <code>FastStringWriter</code> instance using the specified <code>initialCapacity</code>.
     * </p>
     * @param initialCapacity specifies the initial capacity of the buffer
     * @throws IllegalArgumentException if initialCapacity is less than zero
     */
    public FastStringWriter(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException();
        }
        builder = new StringBuilder(initialCapacity);
    }

    /**
     * <p>
     * Constructs a new <code>FastStringWriter</code> instance using the specified <code>builder</code>.
     * </p>
     * @param builder the builder to use as internal buffer
     */
    public FastStringWriter(StringBuilder builder) {
        this.builder = builder;
    }

    // ----------------------------------------------------- Methods from Writer

    /**
     * <p>
     * Write a portion of an array of characters.
     * </p>
     * @param chars Array of characters
     * @param off  Offset from which to start writing characters
     * @param len  Number of characters to write
     * @throws IOException
     */
    @Override
    public void write(char[] chars, int off, int len) throws IOException {
        // this check it's implemented also in the StringBuilder class ... probably can be removed
        if (off < 0 || off > chars.length || len < 0 || off + len > chars.length || off + len < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        builder.append(chars, off, len);
    }

    /**
     * <p>
     * This is a no-op.
     * </p>
     * @throws IOException
     */
    @Override
    public void flush() throws IOException {
    }

    /**
     * <p>
     * This is a no-op.
     * </p>
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
    }

    // ---------------------------------------------------------- Public Methods

    /**
     * Write a single character.
     * @param c the String to be written
     */
    @Override
    public void write(int c) throws IOException {
        builder.append((char)c);
    }

    /**
     * Write a string.
     * @param str String to be written
     */
    @Override
    public void write(String str) {
        builder.append(str);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        builder.append(cbuf);
    }

    /**
     * Write a portion of a string.
     * @param str A String
     * @param off Offset from which to start writing characters
     * @param len Number of characters to write
     */
    @Override
    public void write(String str, int off, int len) {
        builder.append(str, off, off + len);
    }

    /**
     * Return the <code>StringBuilder</code> itself.
     * @return StringBuilder holding the current buffer value.
     */
    public StringBuilder getBuffer() {
        return builder;
    }

    /** @return the buffer's current value as a string. */
    @Override
    public String toString() {
        return builder.toString();
    }

    public void reset() {
        builder.setLength(0);
    }


    // ------------------------------------------------- Append Methods

    @Override
    public Writer append(CharSequence csq) throws IOException {
        builder.append(csq);
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        builder.append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        builder.append(c);
        return this;
    }

}
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

package com.sun.faces.component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;

import jakarta.faces.application.Resource;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.Location;

/**
 * <p>
 * <code>CompositeComponentStackManager</code> is responsible for managing the two different composite component stacks
 * currently used by Mojarra.
 * </p>
 *
 * <p>
 * The stacks are identified by the {@link StackType} enum which has two elements,
 * <code>TreeCreation</code> and <code>Evaluation</code>.
 * </p>
 *
 * <p>
 * The <code>TreeCreation</code> stack represents the composite components that have been pushed by the TagHandlers
 * responsible for building the tree.
 * </p>
 *
 * <p>
 * The <code>Evaluation</code> stack is used by the EL in order to properly resolve nested composite component
 * expressions.
 * </p>
 */
public class CompositeComponentStackManager {

    private static final String MANAGER_KEY = CompositeComponentStackManager.class.getName();

    public enum StackType {
        TreeCreation, Evaluation
    }

    private final StackHandler treeCreation = new TreeCreationStackHandler();
    private final StackHandler runtime = new RuntimeStackHandler();

    // ------------------------------------------------------------ Constructors

    private CompositeComponentStackManager() {
    }

    // ---------------------------------------------------------- Public Methods

    /**
     * @param ctx the <code>FacesContext</code> for the current request
     * @return the <code>CompositeComponentStackManager</code> for the current request
     */
    public static CompositeComponentStackManager getManager(FacesContext ctx) {
        return (CompositeComponentStackManager) ctx.getAttributes().computeIfAbsent(MANAGER_KEY, $ -> new CompositeComponentStackManager());
    }

    /**
     * <p>
     * Pushes the specified composite component to the <code>Evaluation</code> stack.
     * </p>
     *
     * @param compositeComponent the component to push
     * @return <code>true</code> if the component was pushed, otherwise returns <code>false</code>
     */
    public boolean push(UIComponent compositeComponent) {
        return getStackHandler(StackType.Evaluation).push(compositeComponent);
    }

    /**
     * <p>
     * Pushes the specified composite component to the desired <code>StackType</code> stack.
     * </p>
     *
     * @param compositeComponent the component to push
     * @param stackType the stack to push to the component to
     * @return <code>true</code> if the component was pushed, otherwise returns <code>false</code>
     */
    public boolean push(UIComponent compositeComponent, StackType stackType) {
        return getStackHandler(stackType).push(compositeComponent);
    }

    /**
     * <p>
     * Pushes a component derived by the push logic to the <code>Evaluation</code> stack.
     * </p>
     *
     * @return <code>true</code> if the component was pushed, otherwise returns <code>false</code>
     */
    public boolean push() {
        return getStackHandler(StackType.Evaluation).push();
    }

    /**
     * <p>
     * Pushes a component derived by the push logic to the specified stack.
     * </p>
     *
     * @param stackType the stack to push to the component to
     *
     * @return <code>true</code> if the component was pushed, otherwise returns <code>false</code>
     */
    public boolean push(StackType stackType) {
        return getStackHandler(stackType).push();
    }

    /**
     * <p>
     * Pops the top-level component from the stack.
     * </p>
     *
     * @param stackType the stack to pop the top level component from
     */
    public void pop(StackType stackType) {
        getStackHandler(stackType).pop();
    }

    /**
     * <p>
     * Pops the top-level component from the <code>Evaluation</code> stack.
     * </p>
     */
    public void pop() {
        getStackHandler(StackType.Evaluation).pop();
    }

    /**
     * @return the top-level component from the <code>Evaluation</code> stack without removing the element
     */
    public UIComponent peek() {
        return getStackHandler(StackType.Evaluation).peek();
    }

    /**
     * @param stackType the stack to push to the component to
     *
     * @return the top-level component from the specified stack without removing the element
     */
    public UIComponent peek(StackType stackType) {
        return getStackHandler(stackType).peek();
    }

    public UIComponent getParentCompositeComponent(StackType stackType, FacesContext ctx, UIComponent forComponent) {
        return getStackHandler(stackType).getParentCompositeComponent(ctx, forComponent);
    }

    public UIComponent findCompositeComponentUsingLocation(FacesContext ctx, Location location) {

        StackHandler handler = getStackHandler(StackType.TreeCreation);
        Deque<UIComponent> stack = handler.getStack(false);
        if (stack != null) {
            String path = location.getPath();
            for (UIComponent cc : stack) {
                Resource r = (Resource) cc.getAttributes().get(Resource.COMPONENT_RESOURCE_KEY);
                if (path.endsWith('/' + r.getResourceName()) && path.contains(r.getLibraryName())) {
                    return cc;
                }
            }
        } else {
            // runtime eval
            String path = location.getPath();
            UIComponent cc = UIComponent.getCurrentCompositeComponent(ctx);
            while (cc != null) {
                Resource r = (Resource) cc.getAttributes().get(Resource.COMPONENT_RESOURCE_KEY);
                if (path.endsWith('/' + r.getResourceName()) && path.contains(r.getLibraryName())) {
                    return cc;
                }
                cc = UIComponent.getCompositeComponentParent(cc);
            }
        }

        // we could not find the composite component because the location was not found,
        // this will happen if the #{cc} refers to a composite component one level up,
        // so we are going after the current composite component.
        //
        return UIComponent.getCurrentCompositeComponent(ctx);
    }

    // --------------------------------------------------------- Private Methods

    private StackHandler getStackHandler(StackType type) {
        Objects.requireNonNull(type);
        switch (type) {
            case TreeCreation:
                return treeCreation;
            case Evaluation:
                return runtime;
            default:
                throw new IllegalArgumentException("Unsupported stack type: " + type);
        }
    }

    // ------------------------------------------------------ Private Interfaces

    private interface StackHandler {

        boolean push(UIComponent compositeComponent);

        boolean push();

        void pop();

        UIComponent peek();

        UIComponent getParentCompositeComponent(FacesContext ctx, UIComponent forComponent);

        void delete();

        Deque<UIComponent> getStack(boolean create);

    }

    // ---------------------------------------------------------- Nested Classes

    private static abstract class BaseStackHandler implements StackHandler {

        protected Deque<UIComponent> stack;

        // ------------------------------------------- Methods from StackHandler

        @Override
        public void delete() {
            stack = null;
        }

        @Override
        public Deque<UIComponent> getStack(boolean create) {

            if (stack == null && create) {
                stack = new ArrayDeque<>(4); // 4 is enough?
            }
            return stack;
        }

        @Override
        public UIComponent peek() {
            return stack != null ? stack.peek() : null;
        }

    }

    private final class RuntimeStackHandler extends BaseStackHandler {

        // ------------------------------------------- Methods from StackHandler

        @Override
        public void delete() {

            Deque<UIComponent> s = getStack(false);
            if (s != null) {
                s.clear();
            }

        }

        @Override
        public void pop() {

            Deque<UIComponent> s = getStack(false);
            if (s != null && !s.isEmpty()) {
                s.pop();
            }
        }

        @Override
        public boolean push() {

            return push(null);
        }

        @Override
        public boolean push(UIComponent compositeComponent) {

            final Deque<UIComponent> treeStack = treeCreation.getStack(false);

            final UIComponent ccp;

            if (treeStack != null) {
                // We have access to the stack of composite components
                // the tree creation process has made available.
                // Since we can't reliably access the parent composite component
                // of the current composite component, use the index of the
                // current composite component within the stack to locate the
                // parent.
                ccp = compositeComponent;
            } else {
                // no tree creation stack available, so use the runtime stack.
                // If the current stack isn't empty, then use the component
                // on the stack as the current composite component.
                final Deque<UIComponent> stack = getStack(false);

                if (compositeComponent == null) {
                    if (stack != null && !stack.isEmpty()) {
                        ccp = getCompositeParent(stack.peek());
                    } else {
                        ccp = getCompositeParent(UIComponent.getCurrentCompositeComponent(FacesContext.getCurrentInstance()));
                    }
                } else {
                    ccp = compositeComponent;
                }
            }

            if (ccp != null) {
                getStack(true).push(ccp);
                return true;
            }
            return false;
        }

        @Override
        public UIComponent getParentCompositeComponent(FacesContext ctx, UIComponent forComponent) {

            return getCompositeParent(forComponent);

        }

        // ----------------------------------------------------- Private Methods

        private UIComponent getCompositeParent(UIComponent comp) {

            return UIComponent.getCompositeComponentParent(comp);

        }

    } // END RuntimeStackHandler

    private static final class TreeCreationStackHandler extends BaseStackHandler {

        // ------------------------------------------- Methods from StackHandler

        @Override
        public void pop() {

            Deque<UIComponent> stack = getStack(false);
            if (stack != null && !stack.isEmpty()) {
                stack.pop();
                if (stack.isEmpty()) {
                    delete();
                }
            }
        }

        @Override
        public boolean push() {

            return false;

        }

        @Override
        public boolean push(UIComponent compositeComponent) {

            if (compositeComponent != null) {
                assert UIComponent.isCompositeComponent(compositeComponent);
                Deque<UIComponent> s = getStack(true);
                s.push(compositeComponent);
                return true;
            }
            return false;
        }

        @Override
        public UIComponent getParentCompositeComponent(FacesContext ctx, UIComponent forComponent) {

            Deque<UIComponent> s = getStack(false);
            if (s == null) {
                return null;
            }
            else {

                // [ child , element , parent ... ]
                boolean isLastElement = Objects.equals(s.peekLast(), forComponent);

                // if is the last element -> no parent
                if (isLastElement) {
                    return null;
                }

                // return the parent component traversing the stack from tail
                return getPreviousElementFromHead(s, forComponent);  // on the average it's better from head or from tail?
            }
        }

    }

    // Utils -------------------------------------------------------------------------------------

    /**
     * @return the element that has been pushed before targetElement in the stack starting from the head of the LIFO
     * (basically is the element after targetElement in the stack)
     */
    private static <T> T getPreviousElementFromHead(final Deque<T> stack, final T targetElement) {
        Objects.requireNonNull(stack);
        Objects.requireNonNull(targetElement);
        if (stack.isEmpty()) return null;

        // it's LIFO! [ child, element, parent, grandpa ]
        final Iterator<T> iterator = stack.iterator();
        while (iterator.hasNext()) {
            T element = iterator.next();
            if ( Objects.equals(targetElement, element) ) // if the current element is the target element
                return iterator.next();                   // return the next element (the previously pushed)
        }

        // not found
        return null;
    }

    /**
     * @return the element that has been pushed before targetElement in the stack starting from the tail of the LIFO
     * (basically is the element before targetElement navigating the stack in reverse order)
     */
    private static <T> T getPreviousElementFromTail(final Deque<T> stack, final T targetElement) {
        Objects.requireNonNull(stack);
        Objects.requireNonNull(targetElement);
        if (stack.isEmpty()) return null;

        // reverse order of LIFO is FIFO [ grandpa, parent, element, child ]
        T previousElement = null;
        final Iterator<T> iterator = stack.descendingIterator();
        while (iterator.hasNext()) {
            T element = iterator.next();
            if ( Objects.equals(targetElement, element) )  // if the current element is the target element
                return previousElement;                      // return the previous element
            else
                previousElement = element;                   // else save the previousElement
        }

        // not found
        return null;
    }

}

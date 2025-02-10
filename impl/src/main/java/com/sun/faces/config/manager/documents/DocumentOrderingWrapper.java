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

package com.sun.faces.config.manager.documents;

import static java.util.Arrays.asList;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.faces.RIConstants;
import com.sun.faces.config.ConfigurationException;
import com.sun.faces.util.FacesLogger;
import com.sun.faces.util.Timer;
import com.sun.faces.util.Util;

/**
 * This class is used by the config system to order <code>faces-config</code> documents found on the classpath or
 * configured explicitly via the <code>jakarta.faces.CONFIG_FILES</code> context init parameter.
 */
public class DocumentOrderingWrapper {

    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = FacesLogger.CONFIG.getLogger();

    /**
     * {@link Comparator} implementation to aid in sorting <code>faces-config</code> documents.
     */
    private static final Comparator<DocumentOrderingWrapper> COMPARATOR = new DocumentOrderingComparator();

    /**
     * This is the limit on the number of attempts made to sort the documents. Any attempt to exceed this limit will result
     * in an Exception being thrown.
     */
    private static final int MAX_SORT_PASSED = 1000;

    /**
     * Constant for the <code>ordering</code> element.
     */
    private static final String ORDERING = "ordering";

    /**
     * Constant for the <code>before</code> element.
     */
    private static final String BEFORE = "before";

    /**
     * Constant for the <code>after</code> element.
     */
    private static final String AFTER = "after";

    /**
     * Constant for the <code>name</code> element.
     */
    private static final String NAME = "name";

    /**
     * Constant for the <code>others</code> element.
     */
    private static final String OTHERS = "others";

    /**
     * Others keyword for sorting.
     */
    private static final String OTHERS_KEY = DocumentOrderingWrapper.class.getName() + ".OTHERS_KEY";

    /**
     * Return code indicating that element <code>n</code> is to be swapped with <code>n + 1</code>
     */
    private static final int SWAP = -1;

    /**
     * Return code indicating that no swap needs to occur for the elements being compared.
     */
    private static final int DO_NOT_SWAP = 0;

    /**
     * The wrapped Document.
     */
    private final DocumentInfo documentInfo;

    /**
     * The wrapped Document's ID.
     */
    private String id;

    /**
     * The wrapped Document's before IDs.
     */
    private String[] beforeIds;

    /**
     * The wrapped Document's before IDs.
     */
    private String[] afterIds;

    // -------------------------------------------------------- Constructors

    /**
     * Constructs a new <code>DocumentOrderingWrapper</code> for the specified <code>Document</code>.
     * @param document the document info
     */
    public DocumentOrderingWrapper(DocumentInfo document) {
        documentInfo = document;
        init();
    }

    // ------------------------------------------------------ Public Methods

    /**
     * @return the wrapped <code>Document</code>
     */
    public DocumentInfo getDocument() {
        return documentInfo;
    }

    /**
     * @return this <code>Document</code>'s ID, if any
     */
    public String getDocumentId() {
        return id;
    }

    /**
     * @return this <code>Document</code>'s before IDs, if any
     */
    public String[] getBeforeIds() {
        return beforeIds;
    }

    /**
     * @return this <code>Document</code>'s after IDs, if any
     */
    public String[] getAfterIds() {
        return afterIds;
    }

    /**
     * @return <code>true</code> if any before IDs are present, otherwise <code>false</code>
     */
    public boolean isBeforeOrdered() {
        return beforeIds.length != 0;
    }

    /**
     * @return <code>true</code> if any after IDs are present, otherwise, <code>false</code>
     */
    public boolean isAfterOrdered() {
        return afterIds.length != 0;
    }

    /**
     * @return <code>true</code> if this document has any before or after IDs, otherwise <code>false</code>
     */
    public boolean isOrdered() {
        return isBeforeOrdered() || isAfterOrdered();
    }

    /**
     * @param id the id to search for
     * @return <code>true</code> if this document is before the specified <code>id</code>, otherwise <code>false</code>
     */
    public boolean isBefore(String id) {
        return search(beforeIds, id);
    }

    /**
     * @param id the id to search for
     * @return <code>true</code> if this document is after the specified <code>id</code>, otherwise <code>false</code>
     */
    public boolean isAfter(String id) {
        return search(afterIds, id);
    }

    /**
     * @return <code>true</code> if this document is after others, otherwise <code>false</code>
     */
    public boolean isAfterOthers() {
        return search(afterIds, OTHERS_KEY);
    }

    /**
     * @return <code>true</code> if this document is before others, otherwise <code>false</code>
     */
    public boolean isBeforeOthers() {
        return search(beforeIds, OTHERS_KEY);
    }

    @Override
    public String toString() {
        return "Document{" + "id='" + id + '\'' + ", beforeIds=" + (beforeIds == null ? null : asList(beforeIds)) + ", afterIds="
                + (afterIds == null ? null : asList(afterIds)) + '}';
    }

    /**
     * Sort the provided array of <code>Document</code>s per the order specified in the List represented by absoluteOrder.
     *
     * @param documents Documents to sort
     * @param absoluteOrder the absolute order as specified in the /WEB-INF/faces-config.xml
     * @return an array of DocumentOrderingWrappers that may be smaller than the input array of wrappers.
     */
    public static DocumentOrderingWrapper[] sort(DocumentOrderingWrapper[] documents, List<String> absoluteOrder) {

        final List<DocumentOrderingWrapper> sourceList = new ArrayList<>(asList(documents));
        final List<DocumentOrderingWrapper> targetList = new ArrayList<>(documents.length);

        for (String name : absoluteOrder) {
            if (OTHERS.equals(name)) {
                continue;
            }
            boolean found = false;
            Iterator<DocumentOrderingWrapper> sourceIterator = sourceList.iterator();
            while (sourceIterator.hasNext()) {
                DocumentOrderingWrapper wrapper = sourceIterator.next();
                if (!found && name.equals(wrapper.getDocumentId())) {
                    found = true;
                    targetList.add(wrapper);
                    sourceIterator.remove();
                } else if (found && name.equals(wrapper.getDocumentId())) {
                    // we've already processed a document with this name
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.log(Level.WARNING, "faces.configuration.absolute.order.duplicate.document", new Object[] { name });
                    }
                    // only log this once
                    break;
                }
            }
            if (!found && LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "faces.configuration.absolute.order.missing.document", new Object[] { name });
            }
        }

        int othersIndex = absoluteOrder.indexOf(OTHERS);
        if (othersIndex != -1) {
            // any wrappers left in sourceList are considered others.
            // start pushing them into targetList at the index
            for (DocumentOrderingWrapper wrapper : sourceList) {
                targetList.add(othersIndex, wrapper);
            }
        }

        return targetList.toArray(new DocumentOrderingWrapper[targetList.size()]);
    }

    /**
     * Sort the provided array of <code>Document</code>s per the requirements of the 2.0 specification. Note, that this
     * method only provides partial ordering and not absolute ordering.
     * @param documents the documents to sort
     */
    public static void sort(DocumentOrderingWrapper[] documents) {

        Timer t = Timer.getInstance();
        if (t != null) {
            t.startTiming();
        }
        try {
            enhanceOrderingData(documents);
        } catch (CircularDependencyException re) {
            StringBuilder msg = new StringBuilder("Circular dependencies detected!\nDocument Info\n==================\n");
            for (DocumentOrderingWrapper w : documents) {
                msg.append("  ").append(w.toString()).append('\n');
            }
            throw new ConfigurationException(msg.toString());
        }

        // Sort the documents such that specified ordering will be considered.
        //
        // It turns out that some of the specified ordering, if it was not discovered by the sort routine
        // until later in its processing, was not being considered correctly in the ordering algorithm.
        //
        // This preSort method puts all of the documents with specified ordering as early on in the
        // list of documents as possible for Mojarra to consider it quickly, and be
        // able to use its ordering algorithm to the best of its ability.
        preSort(documents);

        // original inner sort algorithm
        int numberOfPasses = innerSort(documents);

        // final sort
        for (int i = 0; i < documents.length; i++) {
            List<String> ids = getIds(documents);
            if (done(documents, ids)) {
                break;
            }
        }

        if (t != null) {
            t.stopTiming();
            t.logResult("\"faces-config\" document sorting complete in " + numberOfPasses + '.');
        }

    }

    // Check to see if the sort is complete, and if not, finish it, if possible.
    private static boolean done(DocumentOrderingWrapper[] documents, List<String> ids) {

        for (int i = 0; i < documents.length; i++) {
            int ii = 0;
            for (String documentId : ids) {
                if (documents[i].getDocumentId().equals(documentId)) {
                    break;
                }
                if (documents[i].isBefore(documentId)) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "done: " + documentId + " should be after " + documents[i].getDocumentId() + " given that it should be before: "
                                + asList(documents[i].getBeforeIds()));
                    }

                    // we have a document that is out of order, and his index is ii, he belongs at index i, and all the documents in between
                    // need to be shifted left.
                    DocumentOrderingWrapper temp = null;
                    for (int j = 0; j < documents.length; j++) {
                        // This is one that is out of order and needs to be moved.
                        if (j == ii) {
                            temp = documents[j];
                        }
                        // this is one in between that needs to be shifted left.
                        if (temp != null && j != i) {
                            documents[j] = documents[j + 1];
                        }
                        // this is where the one that is out of order needs to be moved to.
                        if (j == i) {
                            documents[j] = temp;
                            return false;
                        }
                    }
                }
                ii = ii + 1;
            }
        }

        return true;
    }

    private static List<String> getIds(DocumentOrderingWrapper[] documents) {
        List<String> ids = new ArrayList<>(documents.length);
        for (DocumentOrderingWrapper document : documents) {
            ids.add(document.getDocumentId());
        }
        return ids;
    }

    private static int innerSort(DocumentOrderingWrapper[] documents) {

        int numberOfPasses = 0;
        boolean doMore = true;

        while (doMore) {
            numberOfPasses++;
            if (numberOfPasses == MAX_SORT_PASSED) {
                if (LOGGER.isLoggable(Level.SEVERE)) {
                    StringBuilder msg = new StringBuilder("Exceeded maximum number of attempts to sort the application's faces-config documents.\nDocument Info\n==================");
                    for (DocumentOrderingWrapper w : documents) {
                        msg.append("  ").append(w.toString()).append('\n');
                    }
                    LOGGER.severe(msg.toString());
                }
                throw new ConfigurationException("Exceeded maximum number of attempts to sort the faces-config documents.");
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Starting sort pass number {0}...", numberOfPasses);
            }
            doMore = false;
            for (int i = 0; i < documents.length - 1; i++) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Comparing {0}, {1}", new Object[] { documents[i].id, documents[i + 1].id });
                }
                if (COMPARATOR.compare(documents[i], documents[i + 1]) != DO_NOT_SWAP) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "Swapping {0} with {1}", new Object[] { documents[i].id, documents[i + 1].id });
                    }
                    DocumentOrderingWrapper temp = documents[i];
                    documents[i] = documents[i + 1];
                    documents[i + 1] = temp;
                    doMore = true;
                }
            }

            // compare first and last elements
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Comparing {0}, {1}", new Object[] { documents[0].id, documents[documents.length - 1].id });
            }
            if (COMPARATOR.compare(documents[0], documents[documents.length - 1]) != DO_NOT_SWAP) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Swapping {0} with {1}", new Object[] { documents[0].id, documents[documents.length - 1].id });
                }
                DocumentOrderingWrapper temp = documents[0];
                documents[0] = documents[documents.length - 1];
                documents[documents.length - 1] = temp;
                doMore = true;
            }
        }

        return numberOfPasses;
    }

    // ----------------------------------------------------- Private Methods

    /**
     * Update before/after knowledge of all nodes in the array. Consider the case of A after B, B after C, C after D, and D
     * with no ordering characteristics. When the code below executes, the before/after of specific nodes will be updated so
     * that in the case of the example above we'd have:
     *
     * A -> after B B -> before A, after C C -> before B, A after D D -> before C, B, A
     *
     * So when an attempt is made to sort [A, B, C, D] the end result is [D, C, B, A] No extra enhancement of after ID
     * information is necessary outside of the single node information due to the way the algorithm processes the array.
     *
     * This method also performs cyclic detection. If, after updating the before/after information, the before/after
     * information contains a reference to the document ID we're currently processing, throw an exception.
     */
    private static void enhanceOrderingData(DocumentOrderingWrapper[] wrappers) throws CircularDependencyException {

        for (DocumentOrderingWrapper w : wrappers) {
            // process before IDs other than 'others'
            for (String id : w.getBeforeIds()) {
                if (OTHERS_KEY.equals(id)) {
                    continue;
                }
                for (DocumentOrderingWrapper other : wrappers) {
                    if (id.equals(other.id)) {
                        String[] afterIds = other.getAfterIds();
                        if (Arrays.binarySearch(afterIds, w.id) < 0) {
                            Set<String> newAfterIds = new HashSet<>(Util.calculateMapCapacity(afterIds.length + 1));
                            newAfterIds.addAll(asList(afterIds));
                            newAfterIds.add(w.id);
                            other.afterIds = newAfterIds.toArray(new String[newAfterIds.size()]);
                            Arrays.sort(other.afterIds);
                        }

                        String[] otherBeforeIds = other.getBeforeIds();
                        if (otherBeforeIds.length > 0) {

                            String[] currentBeforeIds = w.getBeforeIds();
                            Set<String> newBeforeIds = new HashSet<>(asList(currentBeforeIds));
                            for (String bid : otherBeforeIds) {
                                if (OTHERS_KEY.equals(bid)) {
                                    continue;
                                }
                                newBeforeIds.add(bid);
                            }
                            if (newBeforeIds.contains(w.id)) {
                                throw new CircularDependencyException();
                            }

                            w.beforeIds = newBeforeIds.toArray(new String[newBeforeIds.size()]);
                            Arrays.sort(w.beforeIds);
                        }
                    }
                }

            }

            // process after IDs other than 'others'
            for (String id : w.getAfterIds()) {
                if (OTHERS_KEY.equals(id)) {
                    continue;
                }
                for (DocumentOrderingWrapper other : wrappers) {
                    if (id.equals(other.id)) {
                        String[] beforeIds = other.getBeforeIds();
                        if (Arrays.binarySearch(beforeIds, w.id) < 0) {
                            Set<String> newBeforeIds = new HashSet<>(Util.calculateMapCapacity(beforeIds.length + 1));
                            newBeforeIds.addAll(asList(beforeIds));
                            newBeforeIds.add(w.id);
                            other.beforeIds = newBeforeIds.toArray(new String[newBeforeIds.size()]);
                            Arrays.sort(other.beforeIds);
                        }
                        String[] otherAfterIds = other.getAfterIds();
                        if (otherAfterIds.length > 0) {
                            String[] currentAfterIds = w.getAfterIds();
                            Set<String> newAfterIds = new HashSet<>(asList(currentAfterIds));
                            for (String bid : otherAfterIds) {
                                if (OTHERS_KEY.equals(bid)) {
                                    continue;
                                }
                                newAfterIds.add(bid);
                            }
                            if ( newAfterIds.contains(w.id) ) {
                                throw new CircularDependencyException();
                            }
                            w.afterIds = newAfterIds.toArray(new String[newAfterIds.size()]);
                            Arrays.sort(w.afterIds);
                        }
                    }
                }
            }
        }
    }

    /**
     * Simple helper method around <code>Arrays.binarySearch()</code>.
     *
     * @param ids an array of IDs
     * @param id the ID to search for
     * @return true if <code>ids</code> contains <code>id</code>
     */
    private static boolean search(String[] ids, String id) {

        return Arrays.binarySearch(ids, id) >= 0;

    }

    /**
     * <p>
     * Performs the initialization necessary to allow sorting of <code>faces-config</code> documents.
     * </p>
     */
    private void init() {

        Element documentElement = documentInfo.getDocument().getDocumentElement();
        String namespace = documentElement.getNamespaceURI();
        id = getDocumentName(documentElement);
        NodeList orderingElements = documentElement.getElementsByTagNameNS(namespace, ORDERING);

        Set<String> beforeIds = null;
        Set<String> afterIds = null;

        if (orderingElements.getLength() > 0) {
            for (int i = 0, len = orderingElements.getLength(); i < len; i++) {
                Node orderingNode = orderingElements.item(i);
                NodeList children = orderingNode.getChildNodes();
                for (int j = 0, jlen = children.getLength(); j < jlen; j++) {
                    Node n = children.item(j);
                    if (beforeIds == null) {
                        beforeIds = extractIds(n, BEFORE);
                    }
                    if (afterIds == null) {
                        afterIds = extractIds(n, AFTER);
                    }
                    if ( beforeIds != null && afterIds != null) {
                        break;
                    }
                }
                if ( beforeIds != null && afterIds != null) {
                    break;
                }
            }
        }

        this.beforeIds = beforeIds != null ? beforeIds.toArray(new String[beforeIds.size()]) : new String[0];
        this.afterIds = afterIds != null ? afterIds.toArray(new String[afterIds.size()]) : new String[0];
        Arrays.sort(this.beforeIds);
        Arrays.sort(this.afterIds);

        // ensure any ID defined in the 'before' array isn't present in the
        // 'after' array and vice versa as a documents can't come before
        // *and* after another.
        checkDuplicatesFast(this.beforeIds, this.afterIds);
        //checkDuplicatesFast(this.afterIds, this.beforeIds); // why check for duplicates two times the same collections?

    }

    private String getDocumentName(Element documentElement) {

        NodeList children = documentElement.getChildNodes();
        String documentName = RIConstants.NO_VALUE;
        if (children != null && children.getLength() > 0) {
            for (int i = 0, len = children.getLength(); i < len; i++) {
                Node n = children.item(i);
                if (NAME.equals(n.getLocalName())) {
                    documentName = getNodeText(n);
                    break;
                }
            }
        }
        return documentName;

    }

    /**
     * Ensure the IDs in <code>source</code> aren't present in <code>searchTarget</code>.
     */
    private void checkDuplicates(String[] source, String[] searchTarget) {

        for (String id : source) {
            if (search(searchTarget, id)) {
                String msg = MessageFormat.format("Document {0} is specified to come before and after {1}.", documentInfo.getDocument().getDocumentURI(), id);
                throw new ConfigurationException(msg);
            }
        }

    }

    /**
     * Ensure the IDs in <code>source</code> aren't present in <code>searchTarget</code>.
     */
    private void checkDuplicatesFast(String[] s1, String[] s2) {
        final Set<String> check = Set.of(s2);

        for (String id : s1) {
            if (check.contains(id)) {
                String msg = MessageFormat.format("Document {0} is specified to come before and after {1}.", documentInfo.getDocument().getDocumentURI(), id);
                throw new ConfigurationException(msg);
            }
        }
    }

    /**
     * Extract and return a <code>Set</code> of IDs contained within the provided <code>Node</code>.
     */
    private Set<String> extractIds(Node n, String nodeName) {

        Set<String> idsList = null;
        if (nodeName.equals(n.getLocalName())) {
            idsList = new HashSet<>();
            NodeList ids = n.getChildNodes();
            for (int k = 0, klen = ids.getLength(); k < klen; k++) {
                Node idNode = ids.item(k);
                if (NAME.equals(idNode.getLocalName())) {
                    String id = getNodeText(idNode);
                    if (id != null) {
                        idsList.add(id);
                    }
                }
                if (OTHERS.equals(idNode.getLocalName())) {
                    if (id != null) {
                        idsList.add(OTHERS_KEY);
                    }
                }
            }
        }
        return idsList;

    }

    // -------------------------------------------------------------------------------- Utilities

    /**
     * Return the textual content, if any, of the provided <code>Node</code>.
     */
    private static String getNodeText(Node node) {
        String res = null;
        if (node != null) {
            res = node.getTextContent();
            if (res != null) {
                res = res.trim();
            }
        }

        return res != null && !res.isEmpty() ? res : null;
    }

    private static void preSort(DocumentOrderingWrapper[] documents) {
        // create a copy of the original array
        final DocumentOrderingWrapper[] copyOfDocuments = copyDocumentArray(documents);

        // prepare temporary data structures
        List<DocumentOrderingWrapper> anonymousAndUnorderedList = new ArrayList<>();
        LinkedHashMap<Integer,Integer> linkedMap = new LinkedHashMap<>();

        // fill the temporary data structures with
        int i = 0;
        for (DocumentOrderingWrapper w : documents) {

            String[] bfs = w.getBeforeIds();
            String[] afs = w.getAfterIds();
            int knowledge = bfs.length + afs.length;

            if ((w.id == null || w.id.isEmpty()) && !w.isOrdered()) {
                anonymousAndUnorderedList.add(w);
            } else {
                linkedMap.put(i, knowledge);
            }
            i++;
        }

        // sort the Map by Value desc
        // linkedMap = sortByValueDescStream(linkedMap);
        linkedMap = sortByValueDesc(linkedMap);

        // prepare the output:
        // first the ordered documents
        // and then the unordered.
        i = 0;
        for (int index : linkedMap.keySet()) {
            documents[i] = copyOfDocuments[index];
            i++;
        }
        for (DocumentOrderingWrapper w : anonymousAndUnorderedList) {
            documents[i] = w;
            i++;
        }
    }

    /**
     * @return a copy of the passed <code>DocumentOrderingWrapper[]</code> array
     */
    private static DocumentOrderingWrapper[] copyDocumentArray(DocumentOrderingWrapper[] documents) {
        DocumentOrderingWrapper[] copyOfDocuments = new DocumentOrderingWrapper[documents.length];
        System.arraycopy(documents, 0, copyOfDocuments, 0, documents.length);
        return copyOfDocuments;
    }

    /** a {@link Comparator} of {@link Map.Entry} where both key and value are {@link Integer} that sort by value desc */
    @SuppressWarnings("all")
    private static final Comparator<Map.Entry<?,? extends Comparable>> ENTRY_COMPARATOR_BY_VALUE_DESC =
            (e1, e2) -> e2.getValue().compareTo(e1.getValue());

    /**
     * @param map the source Map
     * @return a {@link LinkedHashMap} with all the elements of the passed one, sorted by the descending value of the entries
     */
    @SuppressWarnings("unchecked") // pizzi80: todo: when on Java 21+ return a SequencedMap<K,V>
    private static <K, V extends Comparable<? super V>> LinkedHashMap<K, V> sortByValueDesc(Map<K, V> map) {

        final Map.Entry<K,V>[] array = map.entrySet().toArray(new Map.Entry[map.size()]);
        Arrays.sort(array, ENTRY_COMPARATOR_BY_VALUE_DESC);

        final LinkedHashMap<K, V> result = new LinkedHashMap<>(Util.calculateMapCapacity(array.length));
        for (Map.Entry<K, V> entry : array) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    // -------------------------------------------------------------------------------------- Unused Utilities

    private static Map<String, DocumentOrderingWrapper> getDocumentMap(DocumentOrderingWrapper[] documents) {
        Map<String, DocumentOrderingWrapper> configMap = new HashMap<>(Util.calculateMapCapacity(documents.length));

        for (DocumentOrderingWrapper document : documents) {
            String name = document.id;
            if (name != null && !name.isEmpty()) {
                configMap.put(name, document);
            }
        }

        return configMap;
    }

    // ---------------------------------------------------------- Nested Classes

    private static final class DocumentOrderingComparator implements Comparator<DocumentOrderingWrapper> {

        // --------------------------------------------- Methods from Comparator

        @Override
        public int compare(DocumentOrderingWrapper wrapper1, DocumentOrderingWrapper wrapper2) {

            String w1Id = wrapper1.id;
            String w2Id = wrapper2.id;
            boolean w1IsOrdered = wrapper1.isOrdered();
            boolean w2IsOrdered = wrapper2.isOrdered();
            if (w1IsOrdered && !w2IsOrdered) {
                if (wrapper1.isAfterOrdered() && !wrapper1.isBeforeOthers()) {
                    return SWAP;
                }
            }

            boolean w2IsBeforeW1 = wrapper2.isBefore(w1Id);
            boolean w1IsAfterW2 = wrapper1.isAfter(w2Id);

            if (w2IsBeforeW1 || w1IsAfterW2) {
                return SWAP; // move w2 before w1
            }

            // no explicit ID ordering. Check others ordering
            boolean w1IsAfterOthers = wrapper1.isAfterOthers();
            if (w1IsAfterOthers && !wrapper1.isBefore(w2Id) && !(wrapper1.isAfterOthers() && wrapper2.isAfterOthers())) {
                return SWAP;
            }
            boolean w2IsBeforeOthers = wrapper2.isBeforeOthers();
            if (w2IsBeforeOthers && !wrapper2.isAfter(w1Id) && !(wrapper1.isBeforeOthers() && wrapper2.isBeforeOthers())) {
                return SWAP;
            }

            return DO_NOT_SWAP;

        }

    } // END FacesConfigComparator

    private static final class CircularDependencyException extends Exception {

        // -------------------------------------------------------- Constructors

        private static final long serialVersionUID = 739253127985795440L;

        public CircularDependencyException() {
            super();
        }

    } // END CircularDependencyException
}

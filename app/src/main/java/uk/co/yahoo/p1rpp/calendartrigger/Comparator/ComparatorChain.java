/*
 * Copyright (c) 2017. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.Comparator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * A ComparatorChain is a Comparator that wraps one or more Comparators in
 * sequence. The ComparatorChain calls each Comparator in sequence until either
 * 1) any single Comparator returns a non-zero result (and that result is then
 * returned), or 2) the ComparatorChain is exhausted (and zero is returned).
 * This type of sorting is very similar to multi-column sorting in SQL, and this
 * class allows Java classes to emulate that kind of behaviour when sorting a
 * List.
 * <p>
 * To further facilitate SQL-like sorting, the order of any single Comparator in
 * the list can be reversed.
 * <p>
 * Calling a method that adds new Comparators or changes the ascend/descend sort
 * <i>after compare(Object, Object) has been called</i> will result in an
 * UnsupportedOperationException. However, <i>take care</i> to not alter the
 * underlying List of Comparators or the BitSet that defines the sort order.
 * <p>
 * Instances of ComparatorChain are not synchronized. The class is not
 * thread-safe at construction time, but it <i>is</i> thread-safe to perform
 * multiple comparisons after all the setup operations are complete.
 *
 * @since 2.0
 * @version $Id$
 */

public class ComparatorChain<E> implements Comparator<E>, Serializable {

    /** Serialization version from Collections 2.0. */
    private static final long serialVersionUID = -721644942746081630L;

    /** The list of comparators in the chain. */
    private List<Comparator<E>> comparatorChain;
    /** Order - false (clear) = ascend; true (set) = descend. */
    private BitSet orderingBits;
    /** Whether the chain has been "locked". */
    private boolean isLocked = false;

    //-----------------------------------------------------------------------
    /**
     * Construct a ComparatorChain with no Comparators.
     * You must add at least one Comparator before calling
     * the compare(Object,Object) method, or an
     * UnsupportedOperationException is thrown
     */
    public ComparatorChain() {
        comparatorChain = new ArrayList<>();
        orderingBits = new BitSet();
    }
    //-----------------------------------------------------------------------
    /**
     * Add a Comparator to the end of the chain using the
     * forward sort order
     *
     * @param comparator Comparator with the forward sort order
     */
    public void addComparator(Comparator<E> comparator) {
        comparatorChain.add(comparator);
    }

    /**
     * Throws an exception if the {@link ComparatorChain} is empty.
     *
     * @throws UnsupportedOperationException if the {@link ComparatorChain} is empty
     */
    private void checkChainIntegrity() {
        if (comparatorChain.size() == 0) {
            throw new UnsupportedOperationException("ComparatorChains must contain at least one Comparator");
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Perform comparisons on the Objects as per
     * Comparator.compare(o1,o2).
     *
     * @param o1  the first object to compare
     * @param o2  the second object to compare
     * @return -1, 0, or 1
     * @exception UnsupportedOperationException
     *                   if the ComparatorChain does not contain at least one
     *                   Comparator
     */
    public int compare(E o1, E o2) throws UnsupportedOperationException {
        if (isLocked) {
            checkChainIntegrity();
            isLocked = true;
        }

        // iterate over all comparators in the chain
        Iterator<Comparator<E>> comparators = comparatorChain.iterator();
        for (int comparatorIndex = 0; comparators.hasNext(); ++comparatorIndex) {

            Comparator<E> comparator = comparators.next();
            int retval = comparator.compare(o1,o2);
            if (retval != 0) {
                // invert the order if it is a reverse sort
                if (orderingBits.get(comparatorIndex)) {
                    if(Integer.MIN_VALUE == retval) {
                        retval = Integer.MAX_VALUE;
                    } else {
                        retval *= -1;
                    }
                }
                return retval;
            }
        }

        // if comparators are exhausted, return 0
        return 0;
    }

    //-----------------------------------------------------------------------
    /**
     * Implement a hash code for this comparator that is consistent with
     * {@link #equals(Object) equals}.
     *
     * @return a suitable hash code
     * @since 3.0
     */
    @Override
    public int hashCode() {
        int hash = 0;
        if (null != comparatorChain) {
            hash ^= comparatorChain.hashCode();
        }
        if (null != orderingBits) {
            hash ^= orderingBits.hashCode();
        }
        return hash;
    }

    /**
     * Returns <code>true</code> iff <i>that</i> Object is
     * is a {@link Comparator} whose ordering is known to be
     * equivalent to mine.
     * <p>
     * This implementation returns <code>true</code>
     * iff <code><i>object</i>.{@link Object#getClass() getClass()}</code>
     * equals <code>this.getClass()</code>, and the underlying
     * comparators and order bits are equal.
     * Subclasses may want to override this behavior to remain consistent
     * with the {@link Comparator#equals(Object)} contract.
     *
     * @param object  the object to compare with
     * @return true if equal
     * @since 3.0
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (null == object) {
            return false;
        }
        if (object.getClass().equals(this.getClass())) {
            ComparatorChain<?> chain = (ComparatorChain<?>) object;
            return ((null == orderingBits ? null == chain.orderingBits : orderingBits
                .equals(chain.orderingBits)) && (null == comparatorChain ? null == chain.comparatorChain
                                                                         : comparatorChain.equals(chain.comparatorChain)));
        }
        return false;
    }
}

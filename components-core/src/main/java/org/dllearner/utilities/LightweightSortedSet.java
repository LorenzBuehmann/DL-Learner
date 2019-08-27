package org.dllearner.utilities;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.SortedSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Lorenz Buehmann
 */
public class LightweightSortedSet<E> extends HashSet<E> implements SortedSet<E> {

    public LightweightSortedSet() {
        super();
    }

    public LightweightSortedSet(Collection<? extends E> set) {
        super(set);
    }

    @Nullable
    @Override
    public Comparator<? super E> comparator() {
        throw new UnsupportedOperationException("not implemented");
    }

    @NotNull
    @Override
    public SortedSet<E> subSet(E e, E e1) {
        throw new UnsupportedOperationException("not implemented");
    }

    @NotNull
    @Override
    public SortedSet<E> headSet(E e) {
        throw new UnsupportedOperationException("not implemented");
    }

    @NotNull
    @Override
    public SortedSet<E> tailSet(E e) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public E first() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public E last() {
        throw new UnsupportedOperationException("not implemented");
    }
}

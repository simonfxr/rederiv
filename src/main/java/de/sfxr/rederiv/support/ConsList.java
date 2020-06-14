package de.sfxr.rederiv.support;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ConsList<T> implements Iterable<T> {
    private final T hd;
    private final ConsList<T> tl;

    private ConsList(T hd, ConsList<T> tl) {
        this.hd = hd;
        this.tl = tl;
    }

    private ConsList() {
        this.hd = null;
        this.tl = this;
    }

    private static final ConsList<?> NIL = new ConsList<Object>();

    public static <T> ConsList<T> empty() {
        return (ConsList<T>) (Object) NIL;
    }

    public static <T> ConsList<T> singleton(T x) {
        return ConsList.<T>empty().cons(x);
    }

    public ConsList<T> cons(T x) {
        return new ConsList<>(x, this);
    }

    public T head() {
        if (this == NIL) throw new IllegalArgumentException("Tail of empty list is undefined");
        return hd;
    }

    public T headOrElse(T x) {
        return this == NIL ? x : hd;
    }

    public ConsList<T> tail() {
        if (this == NIL) throw new IllegalArgumentException("Tail of empty list is undefined");
        return tl;
    }

    public ConsList<T> safeTail() {
        return tl;
    }

    public boolean isEmpty() {
        return this == NIL;
    }

    public List<T> toList() {
        var xs = new LinkedList<T>();
        for (var l = this; !l.isEmpty(); l = l.tail()) xs.addFirst(l.head());
        return xs;
    }

    @Override
    public String toString() {
        return toList().toString();
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private ConsList<T> i = ConsList.this;
            private T x = null;

            @Override
            public boolean hasNext() {
                if (i.isEmpty()) return false;
                x = i.head();
                i = i.tail();
                return true;
            }

            @Override
            public T next() {
                return x;
            }
        };
    }
}

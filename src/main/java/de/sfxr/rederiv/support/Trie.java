package de.sfxr.rederiv.support;

import com.google.common.base.Preconditions;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class Trie implements Comparable<Trie> {

    private IntervalSet<Trie> root;
    private boolean containsEpsilon;
    private Supplier<Trie> supplier;

    private static final int HDOM = Trie.class.getName().hashCode();

    private static final Trie EMPTY = new Trie(false, IntervalSet.empty());
    private static final Trie EPSILON = new Trie(true, IntervalSet.empty());

    public static Trie fromLit(String val) {
        var chs = val.chars().mapToObj(x -> x).collect(Collectors.toList());
        var t = epsilon();
        for (int i = chs.size() - 1; i >= 0; --i)
            t = t.cons(Interval.of(chs.get(i)));
        return t;
    }

    private void eval() {
        var t = root;
        if (supplier != null) {
            var f = supplier;
            supplier = null;
            var p = f.get();
            if (p.supplier != null)
                p.eval();
            root = p.root;
            containsEpsilon = p.containsEpsilon;
        }
    }

    private Trie(boolean containsEpsilon, IntervalSet<Trie> root) {
        this.supplier = null;
        this.root = root;
        this.containsEpsilon = containsEpsilon;
    }

    private static Trie evaluated(boolean containsEpsilon, IntervalSet<Trie> root) {
        return new Trie(containsEpsilon, root);
    }

    private Trie(Supplier<Trie> supplier) {
        this.supplier = supplier;
    }

    public static Trie empty() {
        return EMPTY;
    }

    public static Trie epsilon() {
        return EPSILON;
    }

    private abstract static class Ordered implements OrderedSemigroup<Trie> {
        @Override
        public int compare(Trie x, Trie y) {
            return x.compareTo(y);
        }
    }

    private static final OrderedSemigroup<Trie> UNION = new Ordered() {
        @Override
        public Trie apply(Trie x, Trie y) {
            return x.union(y);
        }
    };

    private static final OrderedSemigroup<Trie> INTERSECTION = new Ordered() {
        @Override
        public Trie apply(Trie x, Trie y) {
            return x.intersection(y);
        }
    };

    private static final Comparator<IntervalSet<Trie>> TREE_LEAF_ORDERING = IntervalSet
            .comparator(Comparator.<Trie>naturalOrder());

    /**
     * {@link Trie}'s are potentially infinite and thus any total order is not
     * computable, this ordering only evaluates up to one level.
     *
     * @param o
     * @return == 0 => really equal, != 0 => might be equal or not
     */
    @Override
    public int compareTo(Trie o) {
        if (this == o)
            return 0;
        if (o == null)
            return 1;
        var r = Integer.compare(System.identityHashCode(this), System.identityHashCode(o));
        if (r != 0)
            return r;
        eval();
        o.eval();
        r = Boolean.compare(containsEpsilon, o.containsEpsilon);
        if (r != 0)
            return r;
        return TREE_LEAF_ORDERING.compare(root, o.root);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || !(o instanceof Trie))
            return false;
        return compareTo((Trie) o) == 0;
    }

    @Override
    public int hashCode() {
        eval();
        return Objects.hash(HDOM, containsEpsilon, root);
    }

    public Trie union(Trie y) {
        if (y == this)
            return this;
        return new Trie(() -> {
            var x = Trie.this;
            x.eval();
            y.eval();
            return evaluated(x.containsEpsilon || y.containsEpsilon, x.root.union(y.root, UNION));
        });
    }

    public Trie intersection(Trie y) {
        if (y == this)
            return this;
        return new Trie(() -> {
            var x = Trie.this;
            x.eval();
            y.eval();
            return evaluated(x.containsEpsilon && y.containsEpsilon, x.root.intersection(y.root, INTERSECTION));
        });
    }

    public Trie cons(Supplier<Interval<Void>> ivsup) {
        return new Trie(() -> evaluated(false, IntervalSet.of(ivsup.get().withValue(this))));
    }

    public Trie cons(Interval<Void> iv) {
        return new Trie(false, IntervalSet.of(iv.withValue(this)));
    }

    private static final class BFSState {
        private final ConsList<Interval<Void>> prefix;
        private final Trie trie;

        private BFSState(ConsList<Interval<Void>> prefix, Trie trie) {
            if (trie == null)
                throw new NullPointerException();
            this.prefix = Objects.requireNonNull(prefix);
            this.trie = Objects.requireNonNull(trie);
        }
    }

    public Iterator<ConsList<Interval<Void>>> enumerate() {
        return new Iterator<>() {
            private final Queue<BFSState> pending = new LinkedList<>();
            private ConsList<Interval<Void>> produced = null;

            {
                pending.add(new BFSState(ConsList.empty(), Trie.this));
            }

            @Override
            public boolean hasNext() {
                produced = null;
                for (BFSState s; produced == null && (s = pending.poll()) != null;) {
                    s.trie.eval();
                    if (s.trie.containsEpsilon)
                        produced = s.prefix;
                    for (var iv : s.trie.root.asList())
                        pending.add(new BFSState(s.prefix.cons(Interval.of(iv.a, iv.b, null)), iv.v));
                }
                return produced != null;
            }

            @Override
            public ConsList<Interval<Void>> next() {
                var x = produced;
                if (x == null)
                    throw new IllegalStateException("exhausted");
                produced = null;
                return x;
            }
        };
    }

    private final static class FlattenBFS<T, U> implements Iterator<T> {
        Iterator<U> source;
        final Function<U, Iterator<T>> trafo;
        final Queue<Iterator<T>> pending = new LinkedList<>();
        T produced;

        FlattenBFS(Iterator<U> source, Function<U, Iterator<T>> trafo) {
            this.source = Objects.requireNonNull(source);
            this.trafo = trafo;
        }

        @Override
        public boolean hasNext() {
            Preconditions.checkState(produced == null);
            while (produced == null && (!pending.isEmpty() || source != null)) {
                if (source != null) {
                    if (!source.hasNext())
                        source = null;
                    else
                        pending.add(trafo.apply(Objects.requireNonNull(source.next())));
                }
                var it = pending.poll();
                if (it != null && it.hasNext()) {
                    produced = it.next();
                    pending.add(it);
                }
            }
            return produced != null;
        }

        @Override
        public T next() {
            if (produced == null)
                throw new NoSuchElementException();
            var x = produced;
            produced = null;
            return x;
        }
    }

    private final static class StringCP implements Iterator<String> {

        boolean more = true;
        final List<Interval<Void>> ivs;
        final int[] as;

        StringCP(ConsList<Interval<Void>> ivs) {
            this.ivs = new ArrayList<>();
            var rev = ConsList.<Interval<Void>>empty();
            for (var x : ivs) rev = rev.cons(x);
            for (var iv : rev) this.ivs.add(iv);
            this.as = new int[this.ivs.size()];
            int i = 0;
            for (var iv : this.ivs) as[i++] = iv.a;
        }

        @Override
        public boolean hasNext() { return more; }

        @Override
        public String next() {
            var sb = new StringBuilder();
            for (int cp : as) sb.appendCodePoint(cp);
            more = false;
            for (int i = as.length - 1; i >= 0; --i) {
                if (as[i] + 1 < ivs.get(i).b) {
                    as[i]++;
                    more = true;
                    break;
                }
                as[i] = ivs.get(i).a;
            }
            return sb.toString();
        }
    }

    public Iterator<String> enumateStrings() {
        return new FlattenBFS<>(enumerate(), StringCP::new);
    }

    public Stream<String> stream() {
        var splter = Spliterators.spliteratorUnknownSize(enumateStrings(), Spliterator.ORDERED);
        return StreamSupport.stream(splter, false);
    }

    public Stream<String> streamUnique() {
        return stream().distinct();
    }

    public boolean containsEpsilon() {
        eval();
        return containsEpsilon;
    }

    public static Trie of(IntervalSet<Void> ivs) {
        return new Trie(false, ivs.map(x -> epsilon(), UNION));
    }

    public static Trie deferred(Supplier<Trie> sup) {
        return new Trie(sup);
    }

    public boolean isEpsilon() {
        eval();
        return containsEpsilon && root.isEmpty();
    }

    private boolean maybeEpsilon() {
        return this == EPSILON || (supplier == null && containsEpsilon && root.isEmpty());
    }

    private boolean maybeEmpty() {
        return this == EMPTY || (supplier == null && !containsEpsilon && root.isEmpty());
    }

    public Iterable<String> stringIterable() {
        return this::enumateStrings;
    }

    public Trie concat(Trie replacement) {
        if (maybeEmpty()) return EMPTY;
        if (maybeEpsilon()) return replacement;
        return new Trie(() -> {
            var t = Trie.this;
            t.eval();
            var u = evaluated(false, t.root.mapInjectively(c -> c.concat(replacement)));
            return t.containsEpsilon ? u.union(replacement) : u;
        });
    }

    @Override
    public String toString() {
        if (maybeEpsilon()) return "Trie.EPSILON";
        if (maybeEmpty()) return "Trie.EMPTY";
        if (supplier != null) {
            return "Trie<<>>";
        }
        return "Trie{" + containsEpsilon + "," + root + "}";
    }
}

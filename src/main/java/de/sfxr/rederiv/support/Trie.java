package de.sfxr.rederiv.support;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Trie implements Comparable<Trie> {

    private IntervalSet<Trie> root;
    private boolean containsEpsilon;
    private Supplier<Pair<Boolean, IntervalSet<Trie>>> supplier;

    private final static int HDOM = Trie.class.getName().hashCode();

    private final static Trie EMPTY = new Trie(false, IntervalSet.empty());
    private final static Trie EPSILON = new Trie(true, IntervalSet.empty());

    public static Trie fromLit(String val) {
        var chs = val.chars().mapToObj(x -> x).collect(Collectors.toList());
        var t = epsilon();
        for (int i = chs.size() - 1; i >= 0; --i)
            t = t.cons(Interval.of(chs.get(i)));
        return t;
    }

    private void eval() {
        var t = root;
        if (root == null) {
            var p = supplier.get();
            supplier = null;
            root = p.snd;
            containsEpsilon = p.fst;
        }
    }

    private Trie(boolean containsEpsilon, IntervalSet<Trie> root) {
        this.supplier = null;
        this.root = root;
        this.containsEpsilon = containsEpsilon;
    }

    private Trie(Supplier<Pair<Boolean, IntervalSet<Trie>>> supplier) {
        this.supplier = supplier;
    }

    public static Trie empty() {
        return EMPTY;
    }

    public static Trie epsilon() {
        return EPSILON;
    }

    private static abstract class Ordered implements OrderedSemigroup<Trie> {
        @Override
        public int compare(Trie x, Trie y) {
            return x.compareTo(y);
        }
    }

    private final static OrderedSemigroup<Trie> UNION = new Ordered() {
        @Override
        public Trie apply(Trie x, Trie y) {
            return x.union(y);
        }
    };

    private final static OrderedSemigroup<Trie> INTERSECTION = new Ordered() {
        @Override
        public Trie apply(Trie x, Trie y) {
            return x.intersection(y);
        }
    };

    private final static Comparator<IntervalSet<Trie>> TREE_ORDERING = IntervalSet.comparator(Comparator.<Trie>naturalOrder());

    @Override
    public int compareTo(Trie o) {
        eval();
        o.eval();
        var r = Boolean.compare(containsEpsilon, o.containsEpsilon);
        if (r != 0) return r;
        return TREE_ORDERING.compare(root, o.root);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return compareTo((Trie) o) == 0;
    }

    @Override
    public int hashCode() {
        eval();
        return Objects.hash(HDOM, containsEpsilon, root);
    }

    public Trie union(Trie y) {
        if (y == this) return this;
        return new Trie(() -> {
            var x = Trie.this;
            x.eval();
            y.eval();
            return Pair.of(x.containsEpsilon || y.containsEpsilon, x.root.union(y.root, UNION));
        });
    }

    public Trie intersection(Trie y) {
        if (y == this) return this;
        return new Trie(() -> {
            var x = Trie.this;
            x.eval();
            y.eval();
            return Pair.of(x.containsEpsilon && y.containsEpsilon, x.root.intersection(y.root, INTERSECTION));
        });
    }

    public Trie cons(Supplier<Interval<Void>> ivsup) {
        return new Trie(() -> Pair.of(false, IntervalSet.of(ivsup.get().withValue(this))));
    }

    public Trie cons(Interval<Void> iv) {
        return new Trie(false, IntervalSet.of(iv.withValue(this)));
    }

    private final static class BFSState {
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
                for (BFSState s; produced == null && (s = pending.poll()) != null; ) {
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

    public Stream<String> enumateStrings() {
        var stream = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(enumerate(), Spliterator.ORDERED),
                false);
        return stream.flatMap(cl -> fromReversedIntervals(cl));
    }

    public boolean containsEpsilon() {
        eval();
        return containsEpsilon;
    }

    public static Trie deferred(Supplier<Trie> sup) {
        return new Trie(() -> {
           var trie = sup.get();
           trie.eval();
           return Pair.of(trie.containsEpsilon, trie.root);
        });
    }

    public boolean isEpsilon() {
        eval();
        return containsEpsilon && root.isEmpty();
    }

    public Trie concat(Trie replacement) {
        return new Trie(() -> {
            var t = Trie.this;
            t.eval();
            var u = t.root.map(c -> c.concat(replacement), UNION);
            if (t.containsEpsilon) {
                replacement.eval();
                return Pair.of(replacement.containsEpsilon, u.union(replacement.root, UNION));
            } else {
                return Pair.of(false, u);
            }
        });
    }

    public static Stream<String> fromReversedIntervals(ConsList<Interval<Void>> ivs) {
        if (ivs.isEmpty())
            return Stream.of("");
        return fromReversedIntervals(ivs.safeTail()).flatMap(s -> {
            var iv = ivs.head();
            return IntStream.range(iv.a, iv.b).mapToObj(c -> String.format("%s%c", s, c));
        });
    }
}

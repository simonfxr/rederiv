package de.sfxr.rederiv.support;

import com.google.common.base.Preconditions;

import java.util.*;
import java.util.function.Function;

public class IntervalSet<T> {

    private final static int HDOM = IntervalSet.class.getName().hashCode();
    private final static boolean CHECKING = Checking.isCheckingEnabled(IntervalSet.class);
    private final static IntervalSet<?> EMPTY = new IntervalSet<>(0, false);

    public final static <T> Monoid<IntervalSet<T>> unionWith(OrderedSemigroup<T> m) {
        return new Monoid<>() {
            @Override
            public IntervalSet<T> neutral() {
                return IntervalSet.empty();
            }

            @Override
            public IntervalSet<T> apply(IntervalSet<T> x, IntervalSet<T> y) {
                return x.union(y, m);
            }
        };
    }

    public final static <T> Semigroup<IntervalSet<T>> intersectionWith(OrderedSemigroup<T> m) {
        return (x, y) -> x.intersection(y, m);
    }

    private int[] r;
    private T[] v;
    private int n = 0;

    private IntervalSet(int capa, boolean hasValues) {
        r = new int[capa * 2];
        v = hasValues ? (T[]) new Object[capa] : null;
    }

    public static <T> IntervalSet<T> empty() {
        return (IntervalSet<T>) EMPTY;
    }

    public IntervalSet<T> asEmpty() {
        return empty();
    }

    public static <T> IntervalSet of(Interval<T> iv) {
        if (iv.isEmpty())
            return empty();
        var is = new IntervalSet<T>(1, iv.v != null);
        is.n = 1;
        is.set(0, iv);
        return is;
    }

    public static <T> Comparator<IntervalSet<T>> comparator(Comparator<T> cmp) {
        var ivcmp = Interval.comparator(cmp);
        return (x, y) -> {
            for (int i = 0; i < x.n && i < y.n; ++i) {
                int r = ivcmp.compare(x.get(i), y.get(i));
                if (r != 0) return r;
            }
            return Integer.compare(x.n, y.n);
        };
    }

    private void ensureCapacity(int capa) {
        var capa2 = capa * 2;
        if (r.length < capa2) {
            capa = Integer.max(r.length * 2, capa2);
            var rr = new int[capa2];
            var vv = v != null ? (T[]) new Object[capa] : null;
            System.arraycopy(r, 0, rr, 0, n * 2);
            if (v != null)
                System.arraycopy(v, 0, vv, 0, n);
            r = rr;
            v = vv;
        }
    }

    public boolean isEmpty() {
        return n == 0;
    }

    public Interval<T> get(int i) {
        if (CHECKING)
            checkIndex(i);
        var x = v != null ? v[i] : null;
        return Interval.of(r[2 * i], r[2 * i + 1], x);
    }

    private void checkIndex(int i) {
        if (CHECKING) {
            var ii = 2 * i + 1;
            if (!(0 <= ii && ii < r.length))
                throw new IndexOutOfBoundsException();
        }
    }

    private void set(int i, Interval<T> iv) {
        if (CHECKING)
            checkIndex(i);

        r[2 * i] = iv.a;
        r[2 * i + 1] = iv.b;
        if (v != null)
            v[i] = iv.v;
    }

    public List<Interval<T>> asList() {
        return new AbstractList<>() {
            @Override
            public Interval<T> get(int index) {
                return IntervalSet.this.get(index);
            }

            @Override
            public int size() {
                return n;
            }
        };
    }

    private static <T> IntervalSet<T> unsafeBuild(List<Interval<T>> ivs, boolean hasValues) {
        var is = new IntervalSet<T>(ivs.size(), hasValues);
        int i = 0;
        for (var iv : ivs) {
            is.r[i * 2] = iv.a;
            is.r[i * 2 + 1] = iv.b;
            if (hasValues)
                is.v[i] = iv.v;
            ++i;
        }
        return is;
    }

    private void pushInterval(Interval<T> iv, OrderedSemigroup<T> m) {

        if (iv.isEmpty())
            return;

        if (isEmpty()) {
            ensureCapacity(1);
            set(0, iv);
            n = 1;
            return;
        }

        var q = new LinkedList<Interval<T>>();
        q.add(iv);

        while (!q.isEmpty()) {
            iv = q.pop();
            while (n > 0 && get(n - 1).a > iv.a)
                q.push(get(--n));

            if (n == 0) {
                ensureCapacity(1);
                set(0, iv);
                continue;
            }
            // get(n - 1).a <= iv.a
            var u = get(n - 1).union(iv, m).toList();
            var nn = n - 1 + u.size();
            ensureCapacity(nn);
            --n;

            int i = 0;
            for (var uiv : u) {
                Interval<T> last;
                if (i++ == 0 && n > 0 && (last = get(n - 1)).b == uiv.a && (m == null || m.compare(last.v, uiv.v) == 0))
                    set(n - 1, Interval.of(last.a, uiv.b, last.v));
                else
                    set(n++, uiv);
            }
        }
    }

    @Override
    public String toString() {
        return "IntervalSet" + asList().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        IntervalSet<?> that = (IntervalSet<?>) o;
        return this.asList().equals(that.asList());
    }

    @Override
    public int hashCode() {
        return Objects.hash(HDOM, asList());
    }

    public IntervalSet<T> union(IntervalSet<T> y, OrderedSemigroup<T> m) {

        if (isEmpty())
            return y;

        if (y.isEmpty())
            return this;

        var ivs = new ArrayList<Interval<T>>();

        var cmp = Interval.comparator(m);
        var x = this;
        int i, j;

        for (i = 0, j = 0; i < x.n && j < y.n; )
            ivs.add(cmp.compare(x.get(i), y.get(j)) <= 0 ? x.get(i++) : y.get(j++));

        while (i < x.n)
            ivs.add(x.get(i++));

        while (j < y.n)
            ivs.add(y.get(j++));

        return buildSorted(ivs, m);
    }

    public static <T> IntervalSet<T> buildDestructive(List<Interval<T>> ivs, OrderedSemigroup<T> m) {
        if (ivs.isEmpty())
            return IntervalSet.empty();
        Collections.sort(ivs, Interval.comparator(m));
        return buildSorted(ivs, m);
    }

    private static <T> IntervalSet<T> buildSorted(List<Interval<T>> ivs, OrderedSemigroup<T> m) {
        if (ivs.isEmpty())
            return IntervalSet.empty();
        var z = new IntervalSet<T>(0, m != null);
        for (var iv : ivs)
            z.pushInterval(iv, m);
        return z;
    }

    // (a v b) ^ (x v y) = (a ^ x) v (a ^ y) v (b ^ x) v (b ^ y)
    public IntervalSet<T> intersection(IntervalSet<T> y, OrderedSemigroup<T> m) {
        var ivs = new ArrayList<Interval<T>>();
        var x = this;
        var i = 0;
        var j = 0;

        while (i < x.n && j < y.n) {
            var xi = x.get(i);
            while (j < y.n && xi.b <= y.get(j).a)
                ++j;
            Interval<T> z;
            for (var k = j; k < y.n && (z = xi.intersection(y.get(k), m)).isNonEmpty(); ++k)
                ivs.add(z);
            ++i;
        }

        return buildDestructive(ivs, m);
    }

    public <U> IntervalSet<U> map(Function<T, U> f, OrderedSemigroup<U> m) {
        if (isEmpty())
            return IntervalSet.empty();
        var z = new IntervalSet<U>(n, m != null);
        for (int i = 0; i < n; ++i) {
            var iv = get(i);
            z.pushInterval(iv.withValue(f.apply(iv.v)), m);
        }
        return z;
    }
}

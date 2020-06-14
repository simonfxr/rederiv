package de.sfxr.rederiv.support;

import com.google.common.base.Preconditions;
import java.util.*;
import java.util.function.Function;

public class IntervalSet<T> {

    private static final int HDOM = IntervalSet.class.getName().hashCode();
    private static final boolean CHECKING = Checking.isCheckingEnabled(IntervalSet.class);
    private static final IntervalSet<?> EMPTY = new IntervalSet<>(0, false);

    private int card = -1;

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

    private static <T> int cmp(Comparator<T> m, T x, T y) {
        if (m == null) {
            if (x == y && x == null) return 0;
            throw new IllegalStateException("FOO");
        }
        return m.compare(x, y);
    }

    private IntervalSet<T> check(Comparator<T> m) {
        if (CHECKING) {
            if (v != null) {
                Preconditions.checkState(n <= v.length);
                for (int i = n; i < v.length; ++i) Preconditions.checkState(v[i] == null);
            }
            Interval<T> prev = null;
            for (var iv : asList()) {
                if (prev != null)
                    Preconditions.checkArgument(
                            prev.b < iv.a || (prev.b == iv.a && cmp(m, prev.v, iv.v) != 0));
                prev = iv;
            }
        }
        return this;
    }

    public static <T> IntervalSet<T> of(Interval<T> iv) {
        if (iv.isEmpty()) return empty();
        var is = new IntervalSet<T>(1, iv.v != null);
        is.n = 1;
        is.set(0, iv);
        return is;
    }

    private static final Comparator<IntervalSet<Void>> VOID_COMPARATOR = comparator(null);

    public static Comparator<IntervalSet<Void>> comparator() {
        return VOID_COMPARATOR;
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

    public int cardinality() {
        var c = card;
        if (c >= 0) return c;
        c = 0;
        for (var iv : asList()) c += iv.size();
        return card = c;
    }

    public int min() {
        if (n == 0) throw new IllegalStateException("EMPTY");
        return get(0).a;
    }

    public Iterable<Integer> points() {
        throw new IllegalStateException("NYI");
    }

    private void ensureCapacity(int capa) {
        var capa2 = capa * 2;
        if (r.length < capa2) {
            capa = Integer.max(r.length * 2, capa2);
            var rr = new int[capa2];
            var vv = v != null ? (T[]) new Object[capa] : null;
            System.arraycopy(r, 0, rr, 0, n * 2);
            if (v != null) System.arraycopy(v, 0, vv, 0, n);
            r = rr;
            v = vv;
        }
    }

    public boolean isEmpty() {
        return n == 0;
    }

    public boolean isPoint() {
        return n == 1 && get(0).isPoint();
    }

    public boolean containsPoint(int x) {

        for (int lo = 0, hi = n; lo < hi; ) {
            var m = lo + (hi - lo) / 2;
            var iv = get(m);
            if (x < iv.a) hi = m;
            else if (x >= iv.b) lo = m + 1;
            else return true;
        }

        return false;
    }

    public Interval<T> get(int i) {
        return get(i, CHECKING);
    }

    public Interval<T> get(int i, boolean checking) {
        if (checking) checkIndex(i);
        var x = v != null ? v[i] : null;
        return Interval.of(r[2 * i], r[2 * i + 1], x);
    }

    private void checkIndex(int i) {
        if (CHECKING) {
            var ii = 2 * i + 1;
            if (!(0 <= ii && ii < r.length)) throw new IndexOutOfBoundsException();
        }
    }

    private void set(int i, Interval<T> iv) {
        if (CHECKING) checkIndex(i);

        r[2 * i] = iv.a;
        r[2 * i + 1] = iv.b;
        if (v != null) v[i] = iv.v;
    }

    public List<Interval<T>> asList() {
        return new AbstractList<>() {
            @Override
            public Interval<T> get(int index) {
                return IntervalSet.this.get(index, true);
            }

            @Override
            public int size() {
                return n;
            }
        };
    }

    private void unsafePushInterval(Interval<T> iv) {
        ensureCapacity(n + 1);
        set(n++, iv);
    }

    private void pushInterval(Interval<T> iv, OrderedSemigroup<T> m) {

        if (iv.isEmpty()) return;

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
            while (n > 0 && get(n - 1).a > iv.a) q.push(get(--n));

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
                if (i++ == 0
                        && n > 0
                        && (last = get(n - 1)).b == uiv.a
                        && (m == null || m.compare(last.v, uiv.v) == 0))
                    set(n - 1, Interval.of(last.a, uiv.b, last.v));
                else set(n++, uiv);
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

        if (isEmpty()) return y;

        if (y.isEmpty()) return this;

        var ivs = new ArrayList<Interval<T>>();

        var cmp = Interval.comparator(m);
        var x = this;
        int i, j;

        for (i = 0, j = 0; i < x.n && j < y.n; )
            ivs.add(cmp.compare(x.get(i), y.get(j)) <= 0 ? x.get(i++) : y.get(j++));

        while (i < x.n) ivs.add(x.get(i++));

        while (j < y.n) ivs.add(y.get(j++));

        return buildSorted(ivs, m);
    }

    // x.difference(y) = x \ y;
    public IntervalSet<T> difference(IntervalSet<T> y) {
        if (y.isEmpty()) return this;
        var z = new IntervalSet<T>(0, v != null);
        var x = this;

        int i, j;
        for (i = 0, j = 0; i < x.n && j < y.n; ) {
            var xi = x.get(i);
            var yj = y.get(j);
            if (xi.b <= yj.a) {
                z.unsafePushInterval(xi);
                ++i;
            } else if (yj.b <= xi.a) {
                ++j;
            } else {
                // yj.a < xi.b
                // xi.a < yj.b

                var a = xi.a;
                Interval<?> yk;
                int k;
                for (k = 0; k < y.n && (yk = y.get(k)).b <= xi.b; ++k, a = yk.a)
                    z.unsafePushInterval(Interval.of(a, yk.a, xi.v));

                if (k >= y.n) z.unsafePushInterval(Interval.of(a, xi.b, xi.v));
                ++i;
            }
        }

        for (; i < x.n; ++i) z.unsafePushInterval(x.get(i));

        return z.check(null);
    }

    public static <T> IntervalSet<T> buildDestructive(
            List<Interval<T>> ivs, OrderedSemigroup<T> m) {
        if (ivs.isEmpty()) return IntervalSet.empty();
        Collections.sort(ivs, Interval.comparator(m));
        return buildSorted(ivs, m);
    }

    private static <T> IntervalSet<T> buildSorted(List<Interval<T>> ivs, OrderedSemigroup<T> m) {
        if (ivs.isEmpty()) return IntervalSet.empty();
        var z = new IntervalSet<T>(0, m != null);
        for (var iv : ivs) if (!iv.isEmpty()) z.pushInterval(iv, m);
        return z.check(m);
    }

    // (a v b) ^ (x v y) = (a ^ x) v (a ^ y) v (b ^ x) v (b ^ y)
    public IntervalSet<T> intersection(IntervalSet<T> y, OrderedSemigroup<T> m) {
        var ivs = new ArrayList<Interval<T>>();
        var x = this;

        for (int i = 0, j = 0; i < x.n && j < y.n; ++i) {
            while (j < y.n && y.get(j).b <= x.get(i).a) ++j;
            if (j >= y.n) break;
            // here: xi.a < yj.b
            while (i < x.n && x.get(i).b <= y.get(j).a) ++i;
            if (i >= x.n) break;
            // here yj.a < xi.b => overlap
            Interval<T> z;
            for (var k = j; k < y.n && (z = x.get(i).intersection(y.get(k), m)).isNonEmpty(); ++k)
                ivs.add(z);
        }

        return buildDestructive(ivs, m);
    }

    public static <T> IntervalSet<T> unions(Iterable<IntervalSet<T>> xs, OrderedSemigroup<T> m) {
        var y = IntervalSet.<T>empty();
        for (var x : xs) y = y.union(x, m);
        return y;
    }

    public <U> IntervalSet<U> map(Function<T, U> f, OrderedSemigroup<U> m) {
        if (isEmpty()) return IntervalSet.empty();
        var z = new IntervalSet<U>(n, m != null);
        for (int i = 0; i < n; ++i) {
            var iv = get(i);
            z.pushInterval(iv.withValue(f.apply(iv.v)), m);
        }
        return z;
    }

    public T find(int x) {

        for (int lo = 0, hi = n; lo < hi; ) {
            var m = lo + (hi - lo) / 2;
            var iv = get(m);
            if (x < iv.a) hi = m;
            else if (x >= iv.b) lo = m + 1;
            else return iv.v;
        }

        return null;
    }
}

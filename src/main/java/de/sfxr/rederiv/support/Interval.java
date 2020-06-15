package de.sfxr.rederiv.support;

import com.google.common.base.Preconditions;
import java.util.Comparator;
import java.util.Objects;

public final class Interval<T> {

    private static final int HDOM = Interval.class.getName().hashCode();

    private static final boolean CHECKING = Checking.isCheckingEnabled(Interval.class);

    public final int a;
    public final int b;
    public final T v;

    private static final Interval<?> EMPTY = new Interval<>(0, 0, null);

    private static final Interval<Void> UNICODE = new Interval<>(0, 0x110000, null);

    private Interval(int a, int b, T v) {
        this.a = a;
        this.b = b;
        this.v = v;
    }

    public int size() {
        return b - a;
    }

    public boolean isEmpty() {
        return a == b;
    }

    public boolean isNonEmpty() {
        return !isEmpty();
    }

    public Interval<Void> forget() {
        return of(a, b, null);
    }

    public final static <T> Interval<T> covCast(Interval<? extends T> iv) {
        return of(iv.a, iv.b, iv.v);
    }

    public <U> Interval<U> withValue(U value) {
        return of(a, b, value);
    }

    public static <T> Interval<T> of(int a, int b, T v) {
        if (a >= b) return empty();
        return new Interval<>(a, b, v);
    }

    public static <T> Interval<T> empty() {
        return (Interval<T>) EMPTY;
    }

    public static Interval<Void> unicode() {
        return UNICODE;
    }

    public static Interval<Void> of(int a, int b) {
        return of(a, b, null);
    }

    public static Interval<Void> of(int a) {
        return of(a, a + 1);
    }

    public static <T> Comparator<Interval<T>> comparator(Comparator<? super T> cmp) {
        if (cmp == null) return (Comparator<Interval<T>>) (Object) VOID_COMPARATOR;
        return (x, y) -> {
            int r = Integer.compare(x.a, y.a);
            if (r != 0) return r;
            r = Integer.compare(x.b, y.b);
            if (r != 0) return r;
            return cmp.compare(x.v, y.v);
        };
    }

    private static final Comparator<Interval<Void>> VOID_COMPARATOR = comparator((x, y) -> 0);

    public static final Comparator<Interval<Void>> comparator() {
        return VOID_COMPARATOR;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var interval = (Interval<?>) o;
        return a == interval.a && b == interval.b && Objects.equals(v, interval.v);
    }

    @Override
    public int hashCode() {
        return Objects.hash(HDOM, a, b, v);
    }

    @Override
    public String toString() {
        return "Interval{" + "a=" + a + ", b=" + b + ", v=" + v + '}';
    }

    private static final <T> int compare(Comparator<T> cmp, T x, T y) {
        return cmp == null ? 0 : cmp.compare(x, y);
    }

    private static <T> List3<Interval<T>> checkUnion(
            List3<Interval<T>> ret, Interval<T> x, Interval<T> y) {
        if (CHECKING) {
            var l = ret.toList();
            var la = Integer.min(x.a, y.a);
            var rb = Integer.max(x.b, y.b);
            Preconditions.checkState(l.isEmpty() == (la == rb));
            if (!l.isEmpty()) {
                Preconditions.checkState(l.get(0).a == la);
                Preconditions.checkState(l.get(l.size() - 1).b == rb);
            }
        }
        return ret;
    }

    private static final <T> List3<Interval<T>> two(
            int la, int lb, T lv, int rb, T rv, OrderedSemigroup<T> m) {
        var o = compare(m, lv, rv);
        if (o == 0) return List3.of(Interval.of(la, rb, lv));
        return List3.of(Interval.of(la, lb, lv), Interval.of(lb, rb, rv));
    }

    public List3<Interval<T>> union(Interval<T> y, OrderedSemigroup<T> m) {
        var x = this;

        if (x.a == y.a && x.b == y.b && x.a != x.b)
            return List3.of(Interval.of(x.a, x.b, Semigroup.apply(m, x.v, y.v)));

        var ex = x.isEmpty();
        var ey = y.isEmpty();

        if (ex && ey) return checkUnion(List3.of(), x, y);

        if (ex) return checkUnion(List3.of(y), x, y);

        if (ey) return checkUnion(List3.of(x), x, y);

        var swapped = false;
        if (x.a > y.a) {
            swapped = true;
            var t = x;
            x = y;
            y = t;
        }

        // here: x.a <= y.a

        if (x.b <= y.a) {
            if (x.b == y.a && compare(m, x.v, y.v) == 0) {
                return checkUnion(List3.of(Interval.of(x.a, y.b, x.v)), x, y);
            }
            return checkUnion(List3.of(x, y), x, y);
        }

        // here: y.a < x.b => overlapping

        int la, ma, mb, rb;
        T lv, mv, rv;

        la = x.a;
        ma = y.a;
        lv = x.v;
        mv = null;

        if (m != null) mv = swapped ? m.apply(y.v, x.v) : m.apply(x.v, y.v);

        if (y.b <= x.b) {
            // here: x.a <= y.a <= y.b <= x.b
            mb = y.b;
            rb = x.b;
            rv = x.v;
        } else {
            // x.a <= y.a <= x.b <= y.b
            mb = x.b;
            rb = y.b;
            rv = y.v;
        }

        if (la == ma || compare(m, lv, mv) == 0)
            return checkUnion(two(la, mb, mv, rb, rv, m), x, y);

        if (ma == mb || compare(m, mv, rv) == 0)
            return checkUnion(two(la, ma, lv, rb, rv, m), x, y);

        if (mb == rb) return checkUnion(two(la, ma, lv, mb, mv, m), x, y);

        return checkUnion(
                List3.of(Interval.of(la, ma, lv), Interval.of(ma, mb, mv), Interval.of(mb, rb, rv)),
                x,
                y);
    }

    public Interval<T> intersection(Interval<T> y, OrderedSemigroup<T> m) {
        var x = this;

        int a = Math.max(x.a, y.a);
        int b = Math.min(x.b, y.b);

        if (a >= b) return Interval.empty();
        return Interval.of(a, b, m != null ? m.apply(x.v, y.v) : null);
    }

    public boolean isPoint() {
        return size() == 1;
    }
}

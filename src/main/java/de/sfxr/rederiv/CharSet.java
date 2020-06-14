package de.sfxr.rederiv;

import de.sfxr.rederiv.support.Interval;
import de.sfxr.rederiv.support.IntervalSet;
import java.util.*;

public abstract class CharSet extends Re implements Set<Integer> {

    public static final Sparse NONE = new Sparse(IntervalSet.empty());
    public static final Sparse ANY = new Sparse(NONE.chars, true);
    public static final CharSet DIGIT = fromString("0123456789");

    public static final class Sparse extends CharSet {

        public final IntervalSet<Void> chars;
        public final boolean complement;
        private int repr = -1;

        private Sparse(IntervalSet<Void> chars, boolean complement) {
            this.complement = complement;
            this.chars = Objects.requireNonNull(chars);
        }

        public static Sparse from(IntervalSet<Void> chars) {
            return from(chars, false);
        }

        public static Sparse from(IntervalSet<Void> chars, boolean complement) {
            return chars.isEmpty() ? (complement ? ANY : NONE) : new Sparse(chars, complement);
        }

        private Sparse(IntervalSet<Void> chars) {
            this(chars, false);
        }

        @Override
        protected int compareToCharSet(CharSet s) {
            return -s.compareToSparse(this);
        }

        @Override
        protected int compareToSparse(Sparse s) {
            var r = Boolean.compare(complement, s.complement);
            if (r != 0) return r;
            return IntervalSet.comparator().compare(chars, s.chars);
        }

        @Override
        protected String toPattern(int prec) {
            if (chars.isEmpty()) {
                if (!complement)
                    throw new IllegalStateException(
                            "Can't convert the empty character class to a pattern");
                return ".";
            }
            var b = new StringBuilder();
            if (!complement && chars.isPoint()) return b.appendCodePoint(pickOne()).toString();
            b.append('[');
            if (complement) b.append('^');
            for (var iv : chars.asList()) {
                if (iv.isPoint()) {
                    b.appendCodePoint(iv.a);
                } else {
                    b.appendCodePoint(iv.a);
                    if (iv.size() != 2) b.append('-');
                    b.appendCodePoint(iv.b - 1);
                }
            }
            b.append(']');
            return b.toString();
        }

        @Override
        public String litPrefix() {
            return chars.isPoint() ? Character.toString(pickOne()) : "";
        }

        @Override
        public boolean containsChar(int ch) {
            return chars.containsPoint(ch) ^ complement;
        }

        @Override
        public int hashCode() {
            return Objects.hash(complement, chars);
        }

        @Override
        public String toString() {
            return isEmptySet() ? "VOID" : "Sparse{ " + toPattern() + " }";
        }

        @Override
        public CharSet complement() {
            return from(chars, !complement);
        }

        @Override
        public CharSet intersect(CharSet s) {
            return s.intersect(this);
        }

        @Override
        public IntervalSet<Void> toIntervalSet() {
            if (!complement) return chars;
            return IntervalSet.of(Interval.of(0, 0x110000)).difference(chars);
        }

        @Override
        public CharSet intersect(Sparse s) {
            if (s == this) return this;
            if (s == NONE || this == NONE) return NONE;
            if (complement && s.complement) return from(chars.union(s.chars, null), true);
            if (!complement && !s.complement) return from(chars.intersection(s.chars, null));
            var t = this;
            if (t.complement) {
                var u = t;
                t = s;
                s = u;
            }
            return from(t.chars.difference(s.chars), false);
        }

        @Override
        public CharSet union(CharSet s) {
            return s.union(this);
        }

        @Override
        public CharSet union(Sparse s) {
            if (s == this || s == NONE) return this;
            if (this == NONE) return s;
            if (complement || s.complement)
                return this.complement().intersect(s.complement()).complement();
            return from(chars.union(s.chars, null));
        }

        @Override
        public Iterator<Integer> iterator() {
            if (!complement) return chars.points().iterator();
            return new Iterator<>() {
                int cp = 0;
                boolean hasNext = findNext();

                private boolean findNext() {
                    for (; cp < 0x110000; ++cp) if (!chars.containsPoint(cp)) return true;
                    return false;
                }

                @Override
                public boolean hasNext() {
                    return hasNext;
                }

                @Override
                public Integer next() {
                    var n = cp++;
                    hasNext = findNext();
                    return n;
                }
            };
        }

        @Override
        public <T> T[] toArray(T[] a) {
            // if (!complement) return chars.toArray(a);
            // int n = size();
            // if (a.length != n) a = Arrays.copyOf(a, n);
            // int i = 0;
            // for (var x : this) a[i++] = (T) x;
            // return a;
            throw new IllegalStateException("NYI");
        }

        @Override
        public int pickOne() {
            var r = repr;
            if (r > 0) return r;

            if (!complement) {
                r = chars.min();
            } else {
                if (chars.isEmpty()) {
                    r = '0';
                } else {
                    // FIXME: what about <= 0?
                    r = chars.min() - 1;
                }
            }
            repr = r;
            return r;
        }

        @Override
        public int size() {
            var c = chars.cardinality();
            return complement ? 0x110000 - c : c;
        }

        @Override
        public int fromSingletonCharSetNoCapture() {
            return !complement && chars.isPoint() ? pickOne() : -1;
        }
    }

    private static final Sparse[] ASCII_CHARS = new Sparse[128];

    static {
        for (int i = 0; i < ASCII_CHARS.length; ++i) ASCII_CHARS[i] = fromSingleChar(i);
    }

    private static Sparse fromSingleChar(int ch) {
        if (!Character.isValidCodePoint(ch))
            throw new IllegalArgumentException("Invalid code point");
        return new Sparse(IntervalSet.of(Interval.of(ch)));
    }

    @Override
    protected Kind kind() {
        return Kind.CharSet;
    }

    @Override
    protected <R> R visit(Visitor<R> vis) {
        return vis.visit(this);
    }

    public static CharSet setFromChar(int ch) {
        if (0 <= ch && ch < 128) return ASCII_CHARS[ch];
        return fromSingleChar(ch);
    }

    @Override
    public final boolean matchesEmpty() {
        return false;
    }

    protected abstract int compareToCharSet(CharSet s);

    protected abstract int compareToSparse(Sparse s);

    @Override
    protected int compareToRe(CharSet re) {
        return this.compareToCharSet(re);
    }

    @Override
    protected int compareToRe(Re re) {
        return -re.compareToRe(this);
    }

    public static Sparse fromString(String s) {
        if (s.isEmpty()) return NONE;
        int cp = s.codePointAt(0);
        int i = Character.charCount(cp);
        if (i == s.length()) return fromSingleChar(cp);
        var ivs = new ArrayList<Interval<Void>>();
        ivs.add(Interval.of(cp));
        for (; i < s.length(); i += Character.charCount(cp)) {
            cp = s.codePointAt(i);
            ivs.add(Interval.of(cp));
        }
        return Sparse.from(IntervalSet.buildDestructive(ivs, null));
    }

    public abstract IntervalSet<Void> toIntervalSet();

    @Override
    public String litPrefix() {
        return "";
    }

    @Override
    public abstract int hashCode();

    @Override
    public CharSet fromCharSetNoCapture() {
        return this;
    }

    public abstract int pickOne();

    public boolean isEmptySet() {
        return isVoid();
    }

    public abstract boolean containsChar(int ch);

    public abstract CharSet complement();

    public abstract CharSet intersect(CharSet s);

    public abstract CharSet intersect(Sparse s);

    public abstract CharSet union(CharSet s);

    public abstract CharSet union(Sparse s);

    /** Set methods */
    @Override
    @Deprecated
    public final boolean contains(Object o) {
        return (o instanceof Integer) && containsChar((Integer) o);
    }

    @Override
    public abstract Iterator<Integer> iterator();

    @Override
    public Object[] toArray() {
        return toArray(new Object[0]);
    }

    @Override
    public abstract <T> T[] toArray(T[] a);

    @Override
    @Deprecated
    public boolean add(Integer integer) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (c instanceof CharSet) {
            return intersect((CharSet) c).size() == c.size();
        } else {
            for (Object x : c)
                if (!(x instanceof Integer) || !containsChar((Integer) x)) return false;
            return true;
        }
    }

    @Override
    @Deprecated
    public boolean addAll(Collection<? extends Integer> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public void clear() {
        throw new UnsupportedOperationException();
    }
}

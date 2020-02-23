package de.sfxr.rederiv;

import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;
import java.util.*;

public abstract class CharSet extends Re implements Set<Integer> {

    public static final Sparse NONE = new Sparse(HashIntSets.newImmutableSet(new int[0]));
    public static final Sparse ANY = new Sparse(NONE.chars, true);
    public static final CharSet DIGIT = fromString("0123456789");

    public static final class Sparse extends CharSet {

        public final IntSet chars;
        public final boolean complement;
        private int repr = -1;

        private Sparse(IntSet chars, boolean complement) {
            this.complement = complement;
            this.chars = Objects.requireNonNull(chars);
        }

        public static Sparse from(IntSet chars) {
            return from(chars, false);
        }

        public static Sparse from(IntSet chars, boolean complement) {
            return chars.isEmpty() ? (complement ? ANY : NONE) : new Sparse(chars, complement);
        }

        private Sparse(IntSet chars) {
            this(chars, false);
        }

        @Override
        protected int compareToCharSet(CharSet s) {
            return -s.compareToSparse(this);
        }

        @Override
        protected int compareToSparse(Sparse s) {
            var r = Boolean.compare(complement, s.complement);
            if (r == 0) r = Integer.compare(chars.size(), s.chars.size());
            if (r == 0) r = Integer.compare(chars.hashCode(), s.chars.hashCode());
            if (r != 0) return r;
            if (chars.equals(s.chars)) return 0;
            // FIXME: hmm, what to do here?
            throw new UnsupportedOperationException("NYI");
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
            if (!complement && chars.size() == 1) return b.appendCodePoint(pickOne()).toString();
            b.append('[');
            if (complement) b.append('^');
            for (var ch : chars) b.appendCodePoint(ch);
            b.append(']');
            return b.toString();
        }

        @Override
        public String litPrefix() {
            return chars.size() == 1 ? Character.toString(pickOne()) : "";
        }

        @Override
        public boolean containsChar(int ch) {
            return chars.contains(ch) ^ complement;
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
        public CharSet intersect(Sparse s) {
            if (s == this) return this;
            if (s == NONE || this == NONE) return NONE;
            if (complement && s.complement)
                return this.complement().union(s.complement()).complement();
            var u = this;
            if (u.complement
                    || (!u.complement && !s.complement && u.chars.size() < s.chars.size())) {
                var t = u;
                u = s;
                s = t;
            }
            return from(
                    HashIntSets.newImmutableSet(
                            u.chars.stream().filter(s::containsChar).iterator()));
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
            var u = this;
            if (s.chars.size() > u.chars.size()) {
                var t = u;
                u = s;
                s = t;
            }
            var w = HashIntSets.newMutableSet(u.chars);
            w.addAll(s.chars);
            w.shrink();
            return from(w);
        }

        @Override
        public Iterator<Integer> iterator() {
            if (!complement) return chars.iterator();
            return new Iterator<>() {
                int cp = 0;
                boolean hasNext = findNext();

                private boolean findNext() {
                    for (; cp < 0x110000; ++cp) if (!chars.contains(cp)) return true;
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
            if (!complement) return chars.toArray(a);
            int n = size();
            if (a.length != n) a = Arrays.copyOf(a, n);
            int i = 0;
            for (var x : this) a[i++] = (T) x;
            return a;
        }

        @Override
        public int pickOne() {
            var r = repr;
            if (r > 0) return r;

            if (!complement) {
                var i = chars.cursor();
                if (!i.moveNext()) throw new IllegalStateException("Empty CharSet");
                r = i.elem();
            } else {
                for (r = 0; !Character.isValidCodePoint(r) || !containsChar(r); ++r) {}
            }
            repr = r;
            return r;
        }

        @Override
        public int size() {
            return complement ? 0x110000 - chars.size() : chars.size();
        }

        @Override
        public int fromSingletonCharSetNoCapture() {
            return !complement && chars.size() == 1 ? pickOne() : -1;
        }
    }

    private static final Sparse[] ASCII_CHARS = new Sparse[128];

    static {
        for (int i = 0; i < ASCII_CHARS.length; ++i) ASCII_CHARS[i] = fromSingleChar(i);
    }

    private static Sparse fromSingleChar(int ch) {
        if (!Character.isValidCodePoint(ch))
            throw new IllegalArgumentException("Invalid code point");
        return new Sparse(HashIntSets.newImmutableSetOf(ch));
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
        var ss = HashIntSets.newMutableSet();
        ss.add(cp);
        for (; i < s.length(); i += Character.charCount(cp)) {
            cp = s.codePointAt(i);
            ss.add(cp);
        }
        ss.shrink();
        return Sparse.from(ss);
    }

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

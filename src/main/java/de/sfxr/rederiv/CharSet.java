package de.sfxr.rederiv;

import de.sfxr.rederiv.support.Interval;
import de.sfxr.rederiv.support.IntervalSet;
import java.util.*;

public final class CharSet extends Re implements Set<Integer> {

    public static final CharSet NONE = new CharSet(IntervalSet.empty());
    public static final CharSet ANY = new CharSet(NONE.chars, true);
    public static final CharSet DIGIT = fromString("0123456789");

    public final IntervalSet<Void> chars;
    public final boolean complement;
    private int repr = -1;

    private CharSet(IntervalSet<Void> chars, boolean complement) {
        this.complement = complement;
        this.chars = Objects.requireNonNull(chars);
    }

    public static CharSet from(IntervalSet<Void> chars) {
        return from(chars, false);
    }

    public static CharSet from(IntervalSet<Void> chars, boolean complement) {
        return chars.isEmpty() ? (complement ? ANY : NONE) : new CharSet(chars, complement);
    }

    private CharSet(IntervalSet<Void> chars) {
        this(chars, false);
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

    public boolean containsChar(int ch) {
        return chars.containsPoint(ch) ^ complement;
    }

    @Override
    public int hashCode() {
        return Objects.hash(complement, chars);
    }

    @Override
    public String toString() {
        return isEmptySet() ? "VOID" : "CharSet{ " + toPattern() + " }";
    }

    public CharSet complement() {
        return from(chars, !complement);
    }

    public IntervalSet<Void> toIntervalSet() {
        if (!complement) return chars;
        return IntervalSet.of(Interval.of(0, 0x110000)).difference(chars);
    }

    public CharSet intersect(CharSet s) {
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

    public CharSet union(CharSet s) {
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

    private static final CharSet[] ASCII_CHARS = new CharSet[128];

    static {
        for (int i = 0; i < ASCII_CHARS.length; ++i) ASCII_CHARS[i] = fromSingleChar(i);
    }

    private static CharSet fromSingleChar(int ch) {
        if (!Character.isValidCodePoint(ch))
            throw new IllegalArgumentException("Invalid code point");
        return new CharSet(IntervalSet.of(Interval.of(ch)));
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

    @Override
    protected int compareToRe(CharSet re) {
        var r = Boolean.compare(complement, re.complement);
        if (r != 0) return r;
        return IntervalSet.comparator().compare(chars, re.chars);
    }

    @Override
    protected int compareToRe(Re re) {
        return -re.compareToRe(this);
    }

    public static CharSet fromString(String s) {
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
        return CharSet.from(IntervalSet.buildDestructive(ivs, null));
    }

    @Override
    public CharSet fromCharSetNoCapture() {
        return this;
    }

    public boolean isEmptySet() {
        return isVoid();
    }

    /** Set methods */
    @Override
    @Deprecated
    public final boolean contains(Object o) {
        return (o instanceof Integer) && containsChar((Integer) o);
    }

    @Override
    public Object[] toArray() {
        return toArray(new Object[0]);
    }

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

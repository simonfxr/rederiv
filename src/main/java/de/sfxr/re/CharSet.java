package de.sfxr.re;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class CharSet extends Re {

    public final static Dense NONE = new Dense(Collections.emptySet());
    public final static Dense ANY = new Dense(NONE.chars, true);
    public final static CharSet DIGIT = fromString("0123456789");

    public final static class Dense extends CharSet {

        public final Set<Integer> chars;
        public final boolean complement;

        private Dense(Set<Integer> chars, boolean complement) {
            this.complement = complement;
            this.chars = Objects.requireNonNull(chars);
        }

        public static Dense from(Set<Integer> chars, boolean complement) {
            return chars.isEmpty() ? (complement ? ANY : NONE) : new Dense(chars, complement);
        }

        public Dense(Set<Integer> chars) {
            this(chars, false);
        }

        @Override
        protected int compareToCharSet(CharSet s) {
            return -s.compareToDense(this);
        }

        @Override
        protected int compareToDense(Dense s) {
            var r = Integer.compare(chars.size(), s.chars.size());
            if (r == 0) r = Integer.compare(chars.hashCode(), s.chars.hashCode());
            if (r != 0) return r;
            if (chars.equals(s.chars)) return 0;
            // FIXME: hmm, what to do here?
            throw new UnsupportedOperationException("NYI");
        }

        @Override
        protected String toPattern(int prec) {
            if (chars.isEmpty()) return "";
            var b = new StringBuilder();
            if (chars.size() == 1) return b.appendCodePoint(pickOne()).toString();
            b.append('[');
            if (complement) b.append('^');
            for (var ch : chars) b.appendCodePoint(ch);
            b.append(']');
            return b.toString();
        }

        @Override
        public String litPrefix() {
            return chars.size() == 1 ? Character.toString(chars.iterator().next()) : "";
        }

        @Override
        public boolean isEmptySet() {
            return chars.isEmpty();
        }

        @Override
        public boolean containsChar(int ch) {
            return chars.contains(ch) ^ complement;
        }

        @Override
        public int hashCode() {
            return chars.hashCode();
        }

        @Override
        public String toString() {
            return "Dense{ " + toPattern() + " }";
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
        public CharSet intersect(Dense s) {
            if (s == this) return this;
            if (s == NONE || this == NONE) return NONE;
            if (complement && s.complement)
                return this.complement().union(s.complement()).complement();
            if (!complement && !s.complement) {
                var ss = new HashSet<>(chars);
                ss.retainAll(s.chars);
                return from(ss, false);
            }
            var u = this;
            if (u.complement) {
                var t = u; u = s; s = t;
            }
            // FIXME: iterate over the smaller of the two
            var ss = new HashSet<Integer>();
            for (var ch : u.chars)
                if (s.containsChar(ch)) ss.add(ch);
            return from(ss, false);
        }

        @Override
        public CharSet union(CharSet s) {
            return s.union(this);
        }

        @Override
        public CharSet union(Dense s) {
            if (s == this || s == NONE) return this;
            if (this == NONE) return s;
            if (!complement && !s.complement) {
                var ss = new HashSet<>(chars);
                ss.addAll(s.chars);
                return from(ss, false);
            }
            return this.complement().intersect(s.complement()).complement();
        }

        @Override
        public int pickOne() {
            if (isEmptySet())
                throw new IllegalStateException("Empty CharSet");
            return chars.iterator().next();
        }
    }

    private final static Dense[] ASCII_CHAR_SETS = new Dense[128];

    static {
        for (int i = 0; i < ASCII_CHAR_SETS.length; ++i)
            ASCII_CHAR_SETS[i] = fromSingleChar(i);
    }

    private static Dense fromSingleChar(int ch) {
        if (ch < 0)
            throw new IllegalArgumentException();
        return new Dense(Collections.singleton(ch));
    }


    @Override
    protected ReCon typeTag() {
        return ReCon.CHARCLASS;
    }

    @Override
    protected <R> R visit(Visitor<R> vis) {
        return vis.visit(this);
    }

    public static CharSet from(int ch) {
        if (0 <= ch && ch < 128)
            return ASCII_CHAR_SETS[ch];
        return fromSingleChar(ch);
    }

    public static CharSet from(char c) {
        return from((int) c);
    }

    @Override
    public final boolean matchesEmpty() {
        return false;
    }

    protected abstract int compareToCharSet(CharSet s);
    protected abstract int compareToDense(Dense s);

    public static Dense fromString(String s) {
        var ss = new HashSet<Integer>();
        for (int i = 0, cp = 0; i < s.length(); i += Character.charCount(cp)) {
            cp = s.codePointAt(i);
            ss.add(cp);
        }
        return Dense.from(ss, false);
    }

    @Override
    public String litPrefix() {
        return "";
    }

    @Override
    public abstract int hashCode();

    public abstract int pickOne();
    public boolean isEmptySet() { return false; }
    public abstract boolean containsChar(int ch);

    public abstract CharSet complement();

    public abstract CharSet intersect(CharSet s);
    public abstract CharSet intersect(Dense s);

    public abstract CharSet union(CharSet s);
    public abstract CharSet union(Dense s);

}

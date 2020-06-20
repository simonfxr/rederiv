package de.sfxr.rederiv;

import de.sfxr.rederiv.support.Checking;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

public abstract class Re implements ReAlg<Re> {

    private final static boolean CHECKING = Checking.isCheckingEnabled(Re.class);

    public enum Kind {
        Lit,
        CharSet,
        Alt,
        Is,
        Seq,
        Rep,
        Neg
    }

    public interface Visitor<R> {
        R visit(Branch br);

        R visit(Neg neg);

        R visit(Rep rep);

        R visit(Lit l);

        R visit(CharSet cs);

        R visit(Capture cap);
    }

    public static <R> R unreachable() {
        throw new IllegalStateException("UNREACHABLE");
    }

    public static final class Branch extends Re {

        public enum Kind {
            ALT,
            IS,
            SEQ
        };

        public final Kind kind;
        public final Re a, b;
        private final int capCount;
        private final boolean matchesEmpty;

        private Branch(Kind kind, int caps, Re a, Re b) {
            this.kind = kind;
            this.a = Objects.requireNonNull(a);
            this.b = Objects.requireNonNull(b);
            // check right associativity
            if (CHECKING && a instanceof Branch && kind == ((Branch) a).kind)
                throw new IllegalArgumentException();
            this.capCount = caps;
            this.matchesEmpty =
                    kind != Kind.ALT
                            ? a.matchesEmpty() && b.matchesEmpty()
                            : a.matchesEmpty() || b.matchesEmpty();
        }

        private static Branch commuteCanonical(Kind k, BiFunction<Re, Re, Re> build, int ca, Re a, Branch bc) {
            var o = a.compareTo(bc.a);
            if (o == 0) return bc;
            if (o < 0) return new Branch(k, ca + bc.capCount, a, bc);
            var rhs = build.apply(a, bc.b);
            return new Branch(k, bc.a.countCaptures() + rhs.countCaptures(), bc.a, rhs);
        }

        private static Re commuteCanonical(Kind k, Function<Re, Branch> unfold, BiFunction<Re, Re, Re> build, int ca, Re a, int cb, Re b) {
            var aalt = unfold.apply(a);
            var balt = unfold.apply(b);
            if (aalt != null && balt != null) {
                var oo = aalt.a.compareTo(balt.a);
                if (oo == 0)
                    return build.apply(aalt.b, b);
                if (oo < 0)
                    return new Branch(k, ca + cb, aalt.a, build.apply(aalt.b, b));
                return new Branch(k, ca + cb, balt.a, build.apply(balt.b, a));
            }
            if (aalt != null)
                return commuteCanonical(k, build, cb, b, aalt);
            if (balt != null)
                return commuteCanonical(k, build, ca, a, balt);
            return new Branch(k, ca + cb, a, b);
        }

        public static Re alt(Re a, Re b) {
            var ca = a.countCaptures();
            var cb = b.countCaptures();
            var ord = a.compareTo(b);
            if (ord == 0 && ca == 0) return b;
            if (ord == 0 && cb == 0) return a;
            if (ca == 0 && (a.isVoid() || (a.isEmpty() && b.matchesEmpty()))) return b;
            if (ca == 0 && a.isEmpty()) return b.opt();
            if (cb == 0 && (b.isVoid() || (b.isEmpty() && a.matchesEmpty()))) return a;
            if (cb == 0 && b.isEmpty()) return a.opt();
            if ((ca == 0 || cb == 0) && ord > 0) {
                var t = a;
                a = b;
                b = t;
            }
            var caps = ca + cb;
            CharSet r, s;
            if (caps == 0
                    && (r = a.fromCharSetNoCapture()) != null
                    && (s = b.fromCharSetNoCapture()) != null) return r.union(s);
            return commuteCanonical(Kind.ALT, Re::fromAltNoCapture, Branch::alt, ca, a, cb, b);
        }

        public static Re seq(Re a, Re b) {
            var ca = a.countCaptures();
            var cb = b.countCaptures();
            if (ca == 0 && a.isEmpty()) return b;
            if (cb == 0 && b.isEmpty()) return a;
            if (a.isVoid() || b.isVoid()) return a.asVoid();
            var caps = ca + cb;
            String alit, blit;
            if (caps == 0 && (alit = a.fromLit()) != null && (blit = b.fromLit()) != null)
                return Lit.from(alit + blit);
            int x, y;
            if ((x = a.fromSingletonCharSetNoCapture()) >= 0
                    && (y = b.fromSingletonCharSetNoCapture()) >= 0)
                return Lit.fromCodePoints(x, y);
            var aseq = a.fromSeqNoCapture();
            if (aseq != null) {
                a = aseq.a;
                b = seq(aseq.b, b);
            }
            return new Branch(Kind.SEQ, caps, a, b);
        }

        public static Re isect(Re a, Re b) {
            throw new UnsupportedOperationException("NYI");
        }

        @Override
        protected String toPattern(int prec) {
            switch (kind) {
                case SEQ:
                    return Re.parenWhen(prec > 9, a.toPattern(9) + b.toPattern(9));
                case ALT:
                    return Re.parenWhen(prec > 7, a.toPattern(7) + "|" + b.toPattern(7));
                case IS:
                    return Re.parenWhen(prec > 8, a.toPattern(8) + "&" + b.toPattern(8));
            }
            return unreachable();
        }

        @Override
        public String litPrefix() {
            switch (kind) {
                case SEQ:
                    var alit = a.fromLit();
                    return alit != null ? alit + b.litPrefix() : a.litPrefix();
                case ALT:
                case IS:
                    var pa = a.litPrefix();
                    var pb = b.litPrefix();
                    var n = Integer.min(pa.length(), pb.length());
                    int i;
                    for (i = 0; i < n && pa.charAt(i) == pb.charAt(i); ++i) {}
                    return pa.substring(0, i);
            }
            return unreachable();
        }

        @Override
        public boolean matchesEmpty() {
            return matchesEmpty;
        }

        @Override
        public String pp() {
            var sb = new StringBuilder();
            sb.append(kind.toString());
            sb.append(" {");
            Branch br = this, obr = this;
            for (; br != null && br.kind == kind; obr = br, br = br.b.fromBranchNoCapture())
                sb.append(br.a.pp()).append(", ");
            return sb.append(br == null ? obr.b.pp() : br.pp()).append('}').toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, a, b);
        }

        @Override
        public int compareToRe(Branch rhs) {
            int r = kind.compareTo(rhs.kind);
            if (r != 0) return r;
            r = a.compareTo(rhs.a);
            if (r != 0) return r;
            return b.compareTo(rhs.b);
        }

        @Override
        protected int compareToRe(Re re) {
            return -re.compareToRe(this);
        }

        @Override
        protected Re.Kind kind() {
            switch (kind) {
                case SEQ:
                    return Re.Kind.Seq;
                case IS:
                    return Re.Kind.Is;
                case ALT:
                    return Re.Kind.Alt;
            }
            return unreachable();
        }

        @Override
        protected <R> R visit(Visitor<R> vis) {
            return vis.visit(this);
        }

        @Override
        protected Branch fromBranchNoCapture() {
            return this;
        }

        @Override
        public Re stripCaptures() {
            if (capCount == 0) return this;
            var aa = a.stripCaptures();
            var bb = b.stripCaptures();
            if (aa == a && bb == b) return this;
            switch (kind) {
                case ALT:
                    return alt(aa, bb);
                case SEQ:
                    return seq(aa, bb);
                case IS:
                    return isect(aa, bb);
            }
            return unreachable();
        }

        @Override
        public int countCaptures() {
            return capCount;
        }
    }

    public static final class Rep extends Re {
        public final int min, max;
        public final Re re;

        private Rep(int min, int max, Re re) {
            if (min < 0 || min > max) throw new IllegalArgumentException();
            if (min == INF_CARD) throw new IllegalArgumentException();
            this.min = min;
            this.max = max;
            if (min == 0 && max == 0) throw new RuntimeException("BUG: should not happend");
            this.re = Objects.requireNonNull(re);
        }

        public static Re from(int min, int max, Re re) {

            if (min < 0 || min > max) throw new IllegalArgumentException();

            if (re.isVoid()) return re.asVoid();

            if (min == 0 && max == 0) return re.asEmpty();

            if (re.isEmpty() || (min == 1 && max == 1)) return re;

            if (min == Integer.MAX_VALUE) return re.asVoid();

            {
                var cs = re.fromCharSetNoCapture();
                if (cs == Re.Deferred.ANYTHING && min == 0 && max == Integer.MAX_VALUE)
                    return Re.Deferred.ANYTHING;
            }

            if (!(re instanceof Rep)) return new Rep(min, max, re);
            Rep rep = (Rep) re;

            var a = rep.min;
            var b = rep.max;
            var n = min;
            var m = max;

            // x{n, m} = x{n} x{0, m - n}

            // x{0, m} = 0 + x + x^2 + ... + x^n
            // x{n, m} = x{n} x{0, m - n} = x^n (1 + x + x^2 ... x^n)
            // x{a, b}{n} = (x{a} x{0, b - a}){n} = x{a * n} x{0, b - a}{n}
            // = x{a * n} x{0, (b - a) * n}
            // = x{a * n, b * n}

            // x{a}{n, m} = x{a}{n} x{a}{0, m - n} = x{a * n} x{a}{0, m - n}
            // x{a, b}{n, m} = x{a, b}{n} x{a, b}{0, m - n}
            // = x{a * n, b * n} (x{a} x{0, b - a}

            // x{a, b}{n} = x{a * n, b * n}
            // x{a}{0, n} = 1 + x^a + x^(2 *a) + x^(3 a) .. x^(n a) = (x{a * (n + 1)} - 1) /
            // (x{a} -
            // 1)

            // x{a, b}{0, m} = E + x{a, b} + x{a, b}{2} + ... + x{a, b}{m} = E + x{a, b} +
            // x{2 a, 2
            // b} + ... + x{m * a, m * b}
            // if b + 1 >= 2 a:
            // x{a, b}{0, m} = 1 + x{a, m * b}

            // x{a, b}

            // 1 + x{0, n} + x{0, 2 * n} + ...x{0, n * m}

            // x{0, n}{k} = x{0, n * k}

            // x{a, b}{n, m} = x{a, b}{n} x{a, b}{0, m - n}
            // = (x{a} x{0, b - a}){n} x{a, b}{0, m - n}
            // = (

            // x{a}{n} = x{a * n}
            // x{a}{n, m} = x{a}{n} x{a}{n + 1} | ...
            // = x{a}{n} (E | x{a} | x{2 * a} ...)
            // = x{a*n} x{a}{0, m - n}
            //
            // x{0, b}{n, m} = x{0, b * m}
            // x{1, b}
            //
            // x{a, b}{n, m} = (x{a} x{0, b-a}){n, m} = x{a}{n, m} x{0, b-a}{n, m}
            // = x{a}{n, m} x{0, (b - a) * m}
            // x{a, b}{0, m} = x{a}{0, m} x{0, (b - a) * m} =
            // x{0, b} x{n, m} = x{n, b + m}

            // x{a, INF}{n, m} = x{a}{n,m} x* = x{a * n, INF}

            // x{0, b}{n, m} = x{0, b * m}
            // x{1, b}{n, m} = x{n, m} x{0, b - 1}{n, m} = x{n, b * m}
            if (a == 0 || a == 1) return new Rep(a * n, ReAlg.cardMul(b, m), rep.re);

            // x{a, INF}{n, m} = x{a * n, INF}
            if (b == INF_CARD)
                return new Rep(a * n, INF_CARD, rep.re); // FIXME: handle overflow in next line

            // x{a}{n, m} = x{a * n, a * m}
            if (a == b) // FIXME: overflow...
            return new Rep(a * n, ReAlg.cardMul(a, m), rep.re);

            // if b + 1 >= 2 a
            // x{a, b}{0, m} = 1 + x{a, m * b}
            if (n == 0 && b >= 2 * a - 1) {
                if (rep.re.matchesEmpty()) return new Rep(a, ReAlg.cardMul(m, b), rep.re);
                else if (m != 1) // if m == 1 this would lead to endless recursion
                return Lit.EMPTY.alt(rep.re.range(a, ReAlg.cardMul(m, b)));
            }

            // general case
            return new Rep(min, max, re);
        }

        @Override
        public String toPattern(int prec) {
            var multiplicity =
                    max == INF_CARD
                            ? (min == 0 ? "*" : min == 1 ? "+" : "{" + min + ",}")
                            : min == max
                                    ? (min == 1 ? "?" : "{" + min + "}")
                                    : "{" + min + "," + max + "}";
            return parenWhen(prec > 10, re.toPattern(10) + multiplicity);
        }

        @Override
        public boolean matchesEmpty() {
            return min == 0 || re.matchesEmpty();
        }

        @Override
        public String litPrefix() {
            return re.litPrefix().repeat(min);
        }

        @Override
        public String pp() {
            return "Rep{ " + re.pp() + "{" + min + "," + (max == INF_CARD ? "INF" : max) + "} }";
        }

        @Override
        protected Kind kind() {
            return Kind.Rep;
        }

        @Override
        protected <R> R visit(Visitor<R> vis) {
            return vis.visit(this);
        }

        @Override
        public Re stripCaptures() {
            var r = re.stripCaptures();
            return r == re ? this : r.range(min, max);
        }

        @Override
        public int countCaptures() {
            return re.countCaptures();
        }

        @Override
        public int hashCode() {
            return Objects.hash(min, max, re);
        }

        @Override
        protected int compareToRe(Re re) {
            return -re.compareToRe(this);
        }

        @Override
        protected int compareToRe(Rep rhs) {
            int r = Integer.compare(min, rhs.min);
            if (r != 0) return r;
            r = Integer.compare(max, rhs.max);
            if (r != 0) return r;
            return re.compareTo(rhs.re);
        }
    }

    private static String parenWhen(boolean parens, String s) {
        return s.isEmpty() ? "" : parens ? "(?:" + s + ")" : s;
    }

    public static final class Lit extends Re {
        public static final Lit EMPTY = new Lit("");

        public final String val;

        private Lit(String val) {
            this.val = Objects.requireNonNull(val);
        }

        public static Re from(String val) {
            if (val.isEmpty()) return EMPTY;
            int cp = val.codePointAt(0);
            if (val.length() == Character.charCount(cp)) return CharSet.setFromChar(cp);
            return new Lit(val);
        }

        public static Re fromCodePoints(int x, int y) {
            return new Lit(new StringBuilder().appendCodePoint(x).appendCodePoint(y).toString());
        }

        @Override
        public String toPattern(int prec) {
            return Pattern.quote(val);
        }

        @Override
        public String litPrefix() {
            return val;
        }

        @Override
        public String fromLit() {
            return val;
        }

        @Override
        public boolean matchesEmpty() {
            return this == EMPTY;
        }

        @Override
        public String pp() {
            return "Lit{'" + val + "'}";
        }

        @Override
        protected Kind kind() {
            return Kind.Lit;
        }

        @Override
        protected <R> R visit(Visitor<R> vis) {
            return vis.visit(this);
        }

        @Override
        public int hashCode() {
            return val.hashCode();
        }

        @Override
        protected int compareToRe(Re re) {
            return -re.compareToRe(this);
        }

        @Override
        protected int compareToRe(Lit re) {
            return val.compareTo(re.val);
        }
    }

    public static final class Neg extends Re {
        public final Re re;

        private Neg(Re re) {
            this.re = Objects.requireNonNull(re);
        }

        public static Re from(Re re) {
            var neg = re.fromNegNoCapture();
            if (neg != null) return neg;
            var cs = re.fromCharSetNoCapture();
            if (cs != null) return cs.complement();
            if (re.isVoid()) return re.asAnything();
            return new Neg(re);
        }

        @Override
        protected String toPattern(int prec) {
            return "!" + re.toPattern(0);
        }

        @Override
        public String litPrefix() {
            return "";
        }

        @Override
        public boolean matchesEmpty() {
            return !re.matchesEmpty();
        }

        @Override
        protected Kind kind() {
            return Kind.Neg;
        }

        @Override
        protected <R> R visit(Visitor<R> vis) {
            return vis.visit(this);
        }

        @Override
        public String pp() {
            return "Neg{" + re.pp() + '}';
        }

        @Override
        public Re stripCaptures() {
            return from(re.stripCaptures());
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind(), re.hashCode());
        }

        @Override
        protected int compareToRe(Re re) {
            return -re.compareToRe(this);
        }

        @Override
        protected int compareToRe(Neg re) {
            return this.re.compareTo(re.re);
        }

        @Override
        protected Neg fromNegNoCapture() {
            return this;
        }
    }

    public static final class Capture extends Re {
        public final Re re;

        private Capture(Re re) {
            this.re = Objects.requireNonNull(re);
        }

        public static Re from(Re re) {
            var cap = re.fromCapture();
            if (cap != null) return cap;
            return new Capture(re);
        }

        @Override
        protected String toPattern(int prec) {
            return "(" + re.toPattern(0) + ")";
        }

        @Override
        public String litPrefix() {
            return re.litPrefix();
        }

        @Override
        public boolean isEmpty() {
            return re.isEmpty();
        }

        @Override
        public boolean isVoid() {
            return re.isVoid();
        }

        @Override
        public boolean matchesEmpty() {
            return re.matchesEmpty();
        }

        @Override
        protected Kind kind() {
            return re.kind(); // masquerade as underlying
        }

        @Override
        protected <R> R visit(Visitor<R> vis) {
            return vis.visit(this);
        }

        @Override
        public Capture fromCapture() {
            return this;
        }

        @Override
        public String pp() {
            return "Capture{" + re.pp() + '}';
        }

        @Override
        public Re stripCaptures() {
            return re.stripCaptures();
        }

        @Override
        public int hashCode() {
            return re.hashCode();
        }

        @Override
        protected int compareToRe(Re re) {
            return -re.compareToRe(this);
        }

        @Override
        protected int compareToRe(Capture re) {
            return this.re.compareTo(re.re);
        }
    }

    @Override
    public Re alt(Re rhs) {
        return Branch.alt(this, rhs);
    }

    @Override
    public Re seq(Re rhs) {
        return Branch.seq(this, rhs);
    }

    @Override
    public Re isect(Re rhs) {
        return Branch.isect(this, rhs);
    }

    @Override
    public Re neg() {
        return Neg.from(this);
    }

    @Override
    public Re range(int min, int max) {
        return Rep.from(min, max, this);
    }

    public boolean isEmpty() {
        return this == Lit.EMPTY;
    }

    public boolean isVoid() {
        return this == CharSet.NONE;
    }

    public boolean isEmptyOrVoid() {
        return isEmpty() || isVoid();
    }

    public boolean isCapture() {
        return fromCapture() != null;
    }

    public String toPattern() {
        return toPattern(0);
    }

    protected abstract String toPattern(int prec);

    protected String fromLit() {
        return null;
    }

    protected Branch fromBranchNoCapture() {
        return null;
    }

    protected Branch fromAltNoCapture() {
        var br = fromBranchNoCapture();
        return br == null || br.kind != Branch.Kind.ALT ? null : br;
    }

    protected Branch fromSeqNoCapture() {
        var br = fromBranchNoCapture();
        return br == null || br.kind != Branch.Kind.SEQ ? null : br;
    }

    protected Branch fromISectNoCapture() {
        var br = fromBranchNoCapture();
        return br == null || br.kind != Branch.Kind.IS ? null : br;
    }

    protected CharSet fromCharSetNoCapture() {
        return null;
    }

    protected int fromSingletonCharSetNoCapture() {
        return -1;
    }

    protected Neg fromNegNoCapture() {
        return null;
    }

    protected Capture fromCapture() {
        return null;
    }

    public abstract String litPrefix();

    public abstract boolean matchesEmpty();

    protected abstract Kind kind();

    protected abstract <R> R visit(Visitor<R> vis);

    public <R> R visitIgnoreCapture(Visitor<R> vis) {
        return this.unwrapCapture().visit(vis);
    }

    private int compareByKind(Re re) {
        return kind().compareTo(re.kind());
    }

    protected int compareToRe(Lit re) {
        return compareByKind(re);
    }

    protected int compareToRe(Branch re) {
        return compareByKind(re);
    }

    protected int compareToRe(Rep re) {
        return compareByKind(re);
    }

    protected int compareToRe(CharSet re) {
        return compareByKind(re);
    }

    protected int compareToRe(Neg re) {
        return compareByKind(re);
    }

    protected int compareToRe(Capture re) {
        return compareByKind(re);
    }

    protected abstract int compareToRe(Re re);

    private Kind unwrappedKind() {
        return unwrapCapture().kind();
    }

    @Override
    public int compareTo(Re rhs) {
        if (rhs == null) return 1;
        return this.unwrapCapture().compareToRe(rhs.unwrapCapture());
    }

    @Override
    public Re capture() {
        return Capture.from(this);
    }

    private Re unwrapCapture() {
        return this;
    }

    public Re stripCaptures() {
        return this;
    }

    public int countCaptures() {
        return 0;
    }

    @Override
    public final boolean equals(Object obj) {
        return obj instanceof Re && compareTo((Re) obj) == 0;
    }

    @Override
    public abstract int hashCode();

    @Override
    public Re fromLit(String lit) {
        return Lit.from(lit);
    }

    @Override
    public Re fromChar(int cp) {
        return CharSet.setFromChar(cp);
    }

    @Override
    public Re asEmpty() {
        return Lit.EMPTY;
    }

    @Override
    public Re asVoid() {
        return CharSet.NONE;
    }

    @Override
    public Re deriv(int cp) {
        return ReDeriv.deriv(this, cp);
    }

    @Override
    public Set<CharSet> derivClasses() {
        return ReDeriv.derivClasses(this);
    }

    @Override
    public Re anyChar() {
        return CharSet.ANY;
    }

    private final static class Deferred {
        static final Re ANYTHING = new Rep(0, Integer.MAX_VALUE, CharSet.ANY);
    }

    @Override
    public Re asAnything() {
        return Deferred.ANYTHING;
    }

    public abstract String pp();

    @Override
    public final String toString() {
        var pat = "NOPAT";
        try {
            pat = toPattern();
        } catch (Exception ignored) {}
        return "[" + pat + "]:" + pp();
    }
}

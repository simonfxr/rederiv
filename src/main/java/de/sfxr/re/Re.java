package de.sfxr.re;

import java.util.Objects;
import java.util.regex.Pattern;

public abstract class Re implements Comparable<Re> {

    public enum ReCon {
        VOID,
        LIT,
        CHARCLASS,
        SEQ,
        ALT,
        REP,
    }

    public interface Visitor<R> {
        R visit(Branch b);
        R visit(Rep r);
        R visit(Lit l);
        R visit(Void v);
        R visit(CharSet c);
        R visit(Capture c);
    }

    public final static class Branch extends Re {
        public final boolean isSeq;
        public final Re a, b;
        private final int capCount;

        private Branch(boolean isSeq, int caps, Re a, Re b) {
            this.isSeq = isSeq;
            this.a = Objects.requireNonNull(a);
            this.b = Objects.requireNonNull(b);
            this.capCount = caps;
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
            if ((ca == 0 || cb == 0) && ord > 0) { var t = a; a = b; b = t; }
            var aalt = a.fromAltNoCapture();
            if (aalt != null) { a = aalt.a; b = alt(aalt.b, b); };
            return new Branch(false, ca + cb, a, b);
        }

        public static Re seq(Re a, Re b) {
            var ca = a.countCaptures();
            var cb = b.countCaptures();
            if (ca == 0 && a.isEmpty()) return b;
            if (cb == 0 && b.isEmpty()) return a;
            if (a.isVoid() || b.isVoid()) return Void.val;
            var caps = ca + cb;
            String alit, blit;
            if (caps == 0 && (alit = a.fromLit()) != null && (blit = b.fromLit()) != null)
                return Lit.from(alit + blit);
            var aseq = a.fromSeqNoCapture();
            if (aseq != null) { a = aseq.a; b = seq(aseq.b, b); }
            return new Branch(true, caps, a, b);
        }

        @Override
        protected String toPattern(int prec) {
            if (isSeq) {
                return Re.parenWhen(prec > 9, a.toPattern(9) + b.toPattern(9));
            } else {
                return Re.parenWhen(prec > 8, a.toPattern(8) + "|" + b.toPattern(8));
            }
        }

        @Override
        public String litPrefix() {
            if (isSeq) {
                var alit = a.fromLit();
                return  alit != null ?
                    alit + b.litPrefix() :
                    a.litPrefix();
            } else {
                var pa = a.litPrefix();
                var pb = b.litPrefix();
                var n = Integer.min(pa.length(), pb.length());
                int i;
                for (i = 0; i < n && pa.charAt(i) == pb.charAt(i); ++i) {}
                return pa.substring(0, i);
            }
        }

        @Override
        public boolean matchesEmpty() {
            return isSeq ? a.matchesEmpty() && b.matchesEmpty() : a.matchesEmpty() || b.matchesEmpty();
        }

        @Override
        public String toString() {
            return (isSeq ? "Seq {" : "Alt {") + a + ", " + b + "}";
        }

        public Integer compareRe(Branch o) {
            int r = Boolean.compare(isSeq, o.isSeq);
            if (r != 0) return r;
            r = a.compareTo(o.a);
            if (r != 0) return r;
            return b.compareTo(o.b);
        }

        @Override
        protected ReCon typeTag() {
            return isSeq ? ReCon.SEQ : ReCon.ALT;
        }

        @Override
        protected <R> R visit(Visitor<R> vis) {
            return vis.visit(this);
        }

        @Override
        public Branch fromAltNoCapture() {
            return !isSeq ? this : null;
        }

        @Override
        public Branch fromSeqNoCapture() {
            return isSeq ? this : null;
        }

        @Override
        public Re stripCaptures() {
            if (capCount == 0) return this;
            var aa = a.stripCaptures();
            var bb = b.stripCaptures();
            return  aa == a && bb == b ? this :
                    isSeq ? seq(aa, bb) : alt(aa, bb);
        }

        @Override
        public int countCaptures() {
            return capCount;
        }
    }

    public final static class Rep extends Re {
        public final static int INF = Integer.MAX_VALUE;

        public final int min, max;
        public final Re re;

        private Rep(int min, int max, Re re) {
            if (min < 0 || min > max)
                throw new IllegalArgumentException();
            if (min == INF)
                throw new IllegalArgumentException();
            this.min = min;
            this.max = max;
            if (min == 0 && max == 0)
                throw new RuntimeException("BUG: should not happend");
            this.re = Objects.requireNonNull(re);
        }

        static <T> T throwing(RuntimeException e) {
            throw e;
        }

        public static int sub(int a, int b) {
            return a == INF ? a : a - b;
        }
        public static int mult(int a, int b) {
            if (a < 0 || b < 0)
                throw new IllegalArgumentException();
            return a == INF || b == INF ? INF :
                   a < Integer.MAX_VALUE / b ? a * b :
                   throwing(new IllegalArgumentException("Overflow in multiplicity product"));
        }

        public static Re from(int min, int max, Re re) {
            if (re.isVoid() && min == 0 && max == 0)
                throw new IllegalArgumentException();
            if (re.isEmptyOrVoid() || (min == 1 && max == 1))
                return re;

            if (!(re instanceof Rep))
                return new Rep(min, max, re);
            Rep rep = (Rep) re;

            if (min < 0 || min > max)
                throw new IllegalArgumentException();

            var a = rep.min;
            var b = rep.max;
            var n = min;
            var m = max;

            // x{n, m} = x{n} x{0, m - n}

            // x{0, m} = 0 + x + x^2 + ... + x^n
            // x{n, m} = x{n} x{0, m - n} = x^n (1 + x + x^2 ... x^n)
            // x{a, b}{n} = (x{a} x{0, b - a}){n} = x{a * n} x{0, b - a}{n}
            //                                    = x{a * n} x{0, (b - a) * n}
            //                                    = x{a * n, b * n}

            // x{a}{n, m} = x{a}{n} x{a}{0, m - n} = x{a * n} x{a}{0, m - n}
            // x{a, b}{n, m} = x{a, b}{n} x{a, b}{0, m - n}
            //               = x{a * n, b * n} (x{a} x{0, b - a}

            // x{a, b}{n} = x{a * n, b * n}
            // x{a}{0, n} = 1 + x^a + x^(2 *a) + x^(3 a) .. x^(n a) = (x{a * (n + 1)} - 1) / (x{a} - 1)

            // x{a, b}{0, m} = E + x{a, b} + x{a, b}{2} + ... + x{a, b}{m} = E + x{a, b} + x{2 a, 2 b} + ... + x{m * a, m * b}
            // if b + 1 >= 2 a:
            // x{a, b}{0, m} = 1 + x{a, m * b}


            // x{a, b}


            // 1 + x{0, n} + x{0, 2 * n} + ...x{0, n * m}

            // x{0, n}{k} = x{0, n * k}

            // x{a, b}{n, m} = x{a, b}{n} x{a, b}{0, m - n}
            //               = (x{a} x{0, b - a}){n} x{a, b}{0, m - n}
            //               = (

            // x{a}{n} = x{a * n}
            // x{a}{n, m} = x{a}{n}  x{a}{n + 1} | ...
            //             = x{a}{n} (E | x{a} | x{2 * a} ...)
            //              = x{a*n} x{a}{0, m - n}
            //
            // x{0, b}{n, m} = x{0, b * m}
            // x{1, b}
            //
            // x{a, b}{n, m} = (x{a} x{0, b-a}){n, m} = x{a}{n, m} x{0, b-a}{n, m}
            //                                        = x{a}{n, m} x{0, (b - a) * m}
            // x{a, b}{0, m} = x{a}{0, m} x{0, (b - a) * m} =
            // x{0, b} x{n, m} = x{n, b + m}

            // x{a, INF}{n, m} = x{a}{n,m} x* = x{a * n, INF}



            // x{0, b}{n, m} = x{0, b * m}
            // x{1, b}{n, m} = x{n, m} x{0, b - 1}{n, m} = x{n, b * m}
            if (a == 0 || a == 1)
                return new Rep(a * n, mult(b, m), rep.re);

            // x{a, INF}{n, m} = x{a * n, INF}
            if (b == INF)
                return new Rep(a * n, INF, rep.re); // FIXME: handler overflow in next line

            // x{a}{n, m} = x{a * n, a * m}
            if (a == b) // FIXME: overflow...
                return new Rep(a * n, mult(a, m), re);

            // if b + 1 >= 2 a
            // x{a, b}{0, m} = 1 + x{a, m * b}
            if (n == 0 && b >= 2 * a - 1) {
                if (rep.re.matchesEmpty())
                    return new Rep(a, mult(m, b), rep.re);
                else if (m != 1) // if m == 1 this would lead to endless recursion
                    return Lit.EMPTY.alt(rep.re.range(a, mult(m, b)));
            }

            // general case
            return new Rep(min, max, re);
        }

        @Override
        public String toPattern(int prec) {
            var multiplicity = max == INF ? (min == 0 ? "*" : min == 1 ? "+" : "{" + min + ",}") :
                               min == max ? (min == 1 ? "?" : "{" + min + "}") :
                    "{" + min + "," + max + "}";
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
        public String toString() {
            return "Rep{ " + re + "{" + min + "," + (max == INF ? "INF" : max) + "} }";
        }

        public Integer compareRe(Rep o) {
            int r = Integer.compare(min, o.min);
            if (r != 0) return r;
            r = Integer.compare(max, o.max);
            if (r != 0) return r;
            return re.compareTo(o.re);
        }

        @Override
        protected ReCon typeTag() {
            return ReCon.REP;
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
    }

    private static String parenWhen(boolean parens, String s) {
        return s.isEmpty() ? "" : parens ? "(?:" + s + ")" : s;
    }

    public final static class Lit extends Re {
        public final static Lit EMPTY = new Lit("");

        public final String val;

        private Lit(String val) {
            this.val = Objects.requireNonNull(val);
        }

        public static Lit from(String val) {
            return val.isEmpty() ? EMPTY : new Lit(val);
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
        public String toString() {
            return "Lit{'" + val + "'}";
        }

        @Override
        protected ReCon typeTag() {
            return ReCon.LIT;
        }

        @Override
        protected <R> R visit(Visitor<R> vis) {
            return vis.visit(this);
        }
    }

    public final static class Void extends Re {
        public final static Void val = new Void();
        private Void() {}

        @Override
        public String toPattern(int prec) {
            throw new UnsupportedOperationException("Can't render");
        }

        @Override
        public boolean matchesEmpty() {
            return false;
        }

        @Override
        public String litPrefix() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "VOID{}";
        }

        @Override
        protected ReCon typeTag() {
            return ReCon.VOID;
        }

        @Override
        protected <R> R visit(Visitor<R> vis) {
            return vis.visit(this);
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
        protected ReCon typeTag() {
            return re.typeTag(); // masquerade as underlying
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
        public String toString() {
            return "Capture{" + re + '}';
        }

        @Override
        public Re stripCaptures() {
            return re.stripCaptures();
        }
    }

    public Re alt(Re rhs) { return Branch.alt(this, rhs); }
    public Re seq(Re rhs) { return Branch.seq(this, rhs); }
    public Re many() { return range(0, Rep.INF); }
    public Re some() { return range(1, Rep.INF); }
    public Re range(int min, int max) { return Rep.from(min, max, this); }
    public Re opt() { return range(0, 1); }

    public boolean isEmpty() { return this == Lit.EMPTY; }
    public boolean isVoid() { return this == Void.val; }
    public boolean isEmptyOrVoid() { return isEmpty() || isVoid(); }
    public boolean isCapture() { return fromCapture() != null; }

    public String toPattern() { return toPattern(0); }

    protected  abstract String toPattern(int prec);

    public String fromLit() { return null; }
    public Branch fromAltNoCapture() { return null; }
    public Branch fromSeqNoCapture() { return null; }
    public Capture fromCapture() { return null; }

    public abstract String litPrefix();

    public abstract boolean matchesEmpty();

    protected abstract ReCon typeTag();

    protected abstract <R> R visit(Visitor<R> vis);

    public <R> R visitIgnoreCapture(Visitor<R> vis) {
        return this.unwrapCapture().visit(vis);
    }

    @Override
    public int compareTo(Re rhs0) {
        if (rhs0 == null)
            return 1;
        var lhs = this.unwrapCapture();
        var rhs = rhs0.unwrapCapture();
        var ord = lhs.typeTag().compareTo(rhs.typeTag());
        if (ord != 0) return ord;
        return lhs.visit(new Visitor<>() {
            @Override
            public Integer visit(Branch b) {
                return b.compareRe((Branch) rhs);
            }

            @Override
            public Integer visit(Rep r) {
                return r.compareRe((Rep) rhs);
            }

            @Override
            public Integer visit(Lit l) {
                return l.val.compareTo(((Lit) rhs).val);
            }

            @Override
            public Integer visit(Void v) {
                return 0;
            }

            @Override
            public Integer visit(CharSet c) {
                return c.compareToCharSet((CharSet) rhs);
            }

            @Override
            public Integer visit(Capture c) {
                throw new IllegalStateException("BUG");
            }
        });
    }

    public Re capture() {
        return Capture.from(this);
    }

    private Re unwrapCapture() {
        return this;
    }


    public Re stripCaptures() {
        return this;
    }

    public int countCaptures() { return 0; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Re && compareTo((Re) obj) == 0;
    }

    @Override
    public int hashCode() {
        return visitIgnoreCapture(new Visitor<>() {
            @Override
            public Integer visit(Branch b) {
                return Objects.hash(b.isSeq, b.a, b.b);
            }

            @Override
            public Integer visit(Rep r) {
                return Objects.hash(r.min, r.max, r.re);
            }

            @Override
            public Integer visit(Lit l) {
                return Objects.hash(l.val);
            }

            @Override
            public Integer visit(Void v) {
                return System.identityHashCode(v);
            }

            @Override
            public Integer visit(CharSet c) {
                throw new IllegalStateException("BUG");
            }

            @Override
            public Integer visit(Capture c) {
                throw new IllegalStateException("BUG");
            }
        });
    }
}

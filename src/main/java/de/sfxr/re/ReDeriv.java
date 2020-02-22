package de.sfxr.re;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReDeriv {

    public static Re deriv(Re re, int ch) {
        return re.visitIgnoreCapture(new Re.Visitor<Re>() {
            @Override
            public Re visit(Re.Branch b) {
                // d_a (r s) = (d_a r) s + nu(r) d_a s
                if (b.isSeq) {
                    var d = deriv(b.a, ch).seq(b.b);
                    if (b.a.matchesEmpty())
                        d = d.alt(deriv(b.b, ch));
                    return d;
                }

                // d_a (r + s) = d_a r + d_a s
                return deriv(b.a, ch).alt(deriv(b.b, ch));
            }

            @Override
            public Re visit(Re.Rep r) {
                // first:
                // n > 1: d_a (r{n}) = d_a (r r{n-1}) = d_a r r{n - 1} + nu(r) d_a r{n - 1}
                // if nu(r) = VOID: d_a r r{n - 1}
                // if nu(r) = EMPTY: d_a r r{n - 1} + d_a r r{n - 2} .. d_a r
                //                 = d_a r (r{n-1} + r{n-2} + .. + EMPTY)
                //                 = d_a r r{0, n-1}
                //
                // if nu(r) = VOID:
                // d_a r{0, m} = d_a (EMPTY + r + r{2} + .. + r{m}) = d_a r + d_a r r ... + d_a r r{m - 1}
                //             = d_a r r{0,m - 1}
                // if nu(r) = EMPTY
                // d_a r{0, m} = d_a (EMPTY + r + r{2} + .. + r{m})
                //             = d_a r + (d_a r r + d_a r) + (d_a r r{2} + d_a r r + d_a r) + ... +  (d_a r r{m - 1} + d_a r r{m - 2} + ... d_a r)
                //             = d_a r + d_a r r{0,1} + d_a r r{0, 2} + ... + d_a r r{0, m - 1}
                //             = d_a r r{0, m - 1}
                // => d_a r{0, m} = d_a r r{0, m - 1}
                //
                // d_a (r{n, m}) = d_a (r{n} r{0, m - n})
                //               = d_a (r{n}) r{0, m - n} + nu(r) d_a r{0, m - n}
                //
                // if nu(r) = VOID:
                // d_a (r{n, m}) = d_a (r{n} r{0, m - n})
                //               = d_a (r{n}) r{0, m - n}
                //               = d_a r r{n - 1} r{0, m - n}
                //               = d_a r r{n - 1, m - 1}
                // if nu(r) = EMPTY:
                // d_a (r{n, m}) = d_a (r{n} r{0, m - n})
                //               = d_a (r{n}) r{0, m - n} + d_a r{0, m - n}
                //               = d_a r r{0, n - 1} r{0, m - n} + d_a r r{0, m - n - 1}
                //               = d_a r (r{0, m - 1} + r{0, m - n - 1})
                //               = d_a r r{0, m - 1}

                var diff = deriv(r.re, ch);
                if (r.re.matchesEmpty()) {
                    return diff.range(0, Re.Rep.sub(r.max, 1));
                } else {
                    return diff.range(Integer.max(0, r.min - 1), Re.Rep.sub(r.max, 1));
                }
            }

            @Override
            public Re visit(Re.Lit l) {
                if (l.val.codePointAt(0) == ch)
                    return Re.Lit.from(l.val.substring(Character.charCount(ch)));
                return Re.Void.val;
            }

            @Override
            public Re visit(Re.Void v) {
                return Re.Void.val;
            }

            @Override
            public Re visit(CharSet c) {
                return c.containsChar(ch) ? Re.Lit.EMPTY : Re.Void.val;
            }

            @Override
            public Re visit(Re.Capture c) {
                throw new IllegalStateException("BUG");
            }
        });
    }

    public static Set<CharSet> intersections(Set<CharSet> r, Set<CharSet> s) {
        var u = new HashSet<CharSet>();
        for (var x : r) for (var y : s)
            u.add(x.intersect(y));
        return u;
    }

    public static Set<CharSet> equivs(Re re) {
        return re.visitIgnoreCapture(new Re.Visitor<>() {
            @Override
            public Set<CharSet> visit(Re.Branch b) {
                var cr = equivs(b.a);
                if (b.isSeq && !b.a.matchesEmpty())
                    return cr;
                return intersections(cr, equivs(b.b));
            }

            @Override
            public Set<CharSet> visit(Re.Rep r) {
                return equivs(r.re);
            }

            @Override
            public Set<CharSet> visit(Re.Lit l) {
                if (l.isEmpty())
                    return Collections.singleton(CharSet.ANY);
                return visit(CharSet.from(l.val.codePointAt(0)));
            }

            @Override
            public Set<CharSet> visit(Re.Void v) {
                throw new IllegalStateException();
            }

            @Override
            public Set<CharSet> visit(CharSet c) {
                return Set.of(c, c.complement());
            }

            @Override
            public Set<CharSet> visit(Re.Capture c) {
                throw new IllegalStateException("BUG");
            }
        });
    }
}

package de.sfxr.rederiv;

import de.sfxr.rederiv.Re.Neg;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReDeriv {

    public static Re deriv(Re re, int ch) {
        return re.visitIgnoreCapture(
                new Re.Visitor<Re>() {
                    @Override
                    public Re visit(Re.Branch br) {
                        switch (br.kind) {
                            case SEQ:
                                // d_a (r s) = (d_a r) s + nu(r) d_a s
                                var d = deriv(br.a, ch).seq(br.b);
                                if (br.a.matchesEmpty()) d = d.alt(deriv(br.b, ch));
                                return d;
                            case ALT:
                                // d_a (r + s) = d_a r + d_a s
                                return deriv(br.a, ch).alt(deriv(br.b, ch));
                            case IS:
                                return deriv(br.a, ch).isect(deriv(br.b, ch));
                        }
                        return Re.unreachable();
                    }

                    @Override
                    public Re visit(Re.Rep rep) {
                        // first:
                        // n > 1: d_a (r{n}) = d_a (r r{n-1}) = d_a r r{n - 1} + nu(r) d_a r{n - 1}
                        // if nu(r) = VOID: d_a r r{n - 1}
                        // if nu(r) = EMPTY: d_a r r{n - 1} + d_a r r{n - 2} .. d_a r
                        //                 = d_a r (r{n-1} + r{n-2} + .. + EMPTY)
                        //                 = d_a r r{0, n-1}
                        //
                        // if nu(r) = VOID:
                        // d_a r{0, m} = d_a (EMPTY + r + r{2} + .. + r{m}) = d_a r + d_a r r ... +
                        // d_a r r{m - 1}
                        //             = d_a r r{0,m - 1}
                        // if nu(r) = EMPTY
                        // d_a r{0, m} = d_a (EMPTY + r + r{2} + .. + r{m})
                        //             = d_a r + (d_a r r + d_a r) + (d_a r r{2} + d_a r r + d_a r)
                        // + ... +  (d_a r r{m - 1} + d_a r r{m - 2} + ... d_a r)
                        //             = d_a r + d_a r r{0,1} + d_a r r{0, 2} + ... + d_a r r{0, m -
                        // 1}
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

                        var n = rep.re.matchesEmpty() ? 0 : Integer.max(0, rep.min - 1);
                        var m = ReAlg.cardSub(rep.max, 1);
                        return deriv(rep.re, ch).seq(rep.re.range(n, m));
                    }

                    @Override
                    public Re visit(Re.Lit l) {
                        if (!l.val.isEmpty() && l.val.codePointAt(0) == ch)
                            return Re.Lit.from(l.val.substring(Character.charCount(ch)));
                        return CharSet.NONE;
                    }

                    @Override
                    public Re visit(CharSet cs) {
                        return cs.containsChar(ch) ? Re.Lit.EMPTY : CharSet.NONE;
                    }

                    @Override
                    public Re visit(Re.Capture cap) {
                        throw new IllegalStateException("BUG");
                    }

                    @Override
                    public Re visit(Neg neg) {
                        return deriv(re, ch).neg();
                    }
                });
    }

    public static Set<CharSet> intersections(Set<CharSet> r, Set<CharSet> s) {
        if (s.size() < r.size()) {
            var t = s;
            s = r;
            r = t;
        }
        var u = new HashSet<CharSet>();
        for (var x : r) {
            if (x.isEmptySet()) u.add(x);
            else for (var y : s) u.add(x.intersect(y));
        }
        return u;
    }

    private static final Re.Visitor<Set<CharSet>> derivClassesVis =
            new Re.Visitor<>() {
                @Override
                public Set<CharSet> visit(Re.Branch br) {
                    var cr = derivClasses(br.a);
                    switch (br.kind) {
                        case SEQ:
                            if (!br.a.matchesEmpty()) return cr;
                        case ALT:
                        case IS:
                            return intersections(cr, derivClasses(br.b));
                    }
                    return Re.unreachable();
                }

                @Override
                public Set<CharSet> visit(Re.Rep rep) {
                    return derivClasses(rep.re);
                }

                @Override
                public Set<CharSet> visit(Re.Lit l) {
                    if (l.isEmpty()) return Collections.singleton(CharSet.ANY);
                    return visit(CharSet.setFromChar(l.val.codePointAt(0)));
                }

                @Override
                public Set<CharSet> visit(CharSet cs) {
                    return Set.of(cs, cs.complement());
                }

                @Override
                public Set<CharSet> visit(Re.Capture cap) {
                    throw new IllegalStateException("BUG");
                }

                @Override
                public Set<CharSet> visit(Re.Neg neg) {
                    return derivClasses(neg.re);
                }
            };

    public static Set<CharSet> derivClasses(Re re) {
        return re.visitIgnoreCapture(derivClassesVis);
    }
}

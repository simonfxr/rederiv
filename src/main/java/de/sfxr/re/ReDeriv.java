package de.sfxr.re;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReDeriv {

    public static Re deriv(Re re, int ch) {
        return re.visitIgnoreCapture(new Re.Visitor<Re>() {
            @Override
            public Re visit(Re.Branch br) {
                // d_a (r s) = (d_a r) s + nu(r) d_a s
                if (br.isSeq) {
                    var d = deriv(br.a, ch).seq(br.b);
                    if (br.a.matchesEmpty())
                        d = d.alt(deriv(br.b, ch));
                    return d;
                }

                // d_a (r + s) = d_a r + d_a s
                return deriv(br.a, ch).alt(deriv(br.b, ch));
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

                var n = rep.re.matchesEmpty() ? 0 : Integer.max(0, rep.min - 1);
                var m = Re.Rep.sub(rep.max, 1);
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
        });
    }

    public static Set<CharSet> intersections(Set<CharSet> r, Set<CharSet> s) {
        if (s.size() < r.size()) {
            var t = s; s = r; r = t;
        }
        var u = new HashSet<CharSet>();
        for (var x : r) {
            if (x.isEmptySet()) u.add(x);
            else for (var y : s) u.add(x.intersect(y));
        }
        return u;
    }

    private final static Re.Visitor<Set<CharSet>> equivsVis = new Re.Visitor<>() {
        @Override
        public Set<CharSet> visit(Re.Branch br) {
            var cr = equivs(br.a);
            if (br.isSeq && !br.a.matchesEmpty())
                return cr;
            return intersections(cr, equivs(br.b));
        }

        @Override
        public Set<CharSet> visit(Re.Rep rep) {
            return equivs(rep.re);
        }

        @Override
        public Set<CharSet> visit(Re.Lit l) {
            if (l.isEmpty())
                return Collections.singleton(CharSet.ANY);
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
    };

    public static Set<CharSet> equivs(Re re) { return re.visitIgnoreCapture(equivsVis); }
}

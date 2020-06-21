package de.sfxr.rederiv.support;

import de.sfxr.rederiv.CharSet;
import de.sfxr.rederiv.Re;
import de.sfxr.rederiv.ReAlg;

public final class EnumerateTrie {
    private EnumerateTrie() {}

    private static final Re.Visitor<Trie> ENUMERATOR =
            new Re.Visitor<>() {

                @Override
                public Trie visit(Re.Branch br) {
                    var l = enumerate(br.a);
                    var r = enumerate(br.b);
                    switch (br.kind) {
                        case ALT:
                            return l.union(r);
                        case SEQ:
                            return l.concat(r);
                        case IS:
                            return l.intersection(r);
                    }
                    return Re.unreachable();
                }

                @Override
                public Trie visit(Re.Neg neg) {
                    throw new UnsupportedOperationException();
                }

                private Trie kleene(Trie t) {
                    var self = new Trie[] {null};
                    return self[0] = Trie.epsilon().union(t.concat(Trie.deferred(() -> self[0])));
                }

                @Override
                public Trie visit(Re.Rep rep) {

                    var t = enumerate(rep.re);
                    var u = Trie.epsilon();
                    for (int i = 0; i < rep.min; ++i) u = t.concat(u);

                    Trie tail;
                    if (rep.max == ReAlg.INF_CARD) {
                        tail = u.concat(kleene(t));
                    } else {
                        tail = Trie.epsilon();
                        for (int i = 0; i < rep.max - rep.min; ++i)
                            tail = Trie.epsilon().union(t.concat(tail));
                    }

                    return u.concat(tail);
                }

                @Override
                public Trie visit(Re.Lit l) {
                    return Trie.fromLit(l.val);
                }

                @Override
                public Trie visit(CharSet cs) {
                    return Trie.of(cs.toIntervalSet());
                }

                @Override
                public Trie visit(Re.Capture cap) {
                    throw new IllegalStateException("IMPOSSIBLE");
                }
            };

    public static Trie enumerate(Re re) {
        return Trie.deferred(() -> re.visitIgnoreCapture(ENUMERATOR));
    }
}

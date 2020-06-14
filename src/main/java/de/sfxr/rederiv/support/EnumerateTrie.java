package de.sfxr.rederiv.support;

import de.sfxr.rederiv.CharSet;
import de.sfxr.rederiv.Re;
import de.sfxr.rederiv.ReAlg;

public final class EnumerateTrie {
    private EnumerateTrie() {}

    private final static Re.Visitor<Trie> ENUMERATOR = new Re.Visitor<>() {

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

        @Override
        public Trie visit(Re.Rep rep) {
            var t = enumerate(rep.re).concat(enumerate(rep.re.range(Integer.max(rep.min - 1, 0),
                    ReAlg.cardSub(rep.max, 1))));
            return rep.min == 0 ? t.union(Trie.epsilon()) : t;
        }

        @Override
        public Trie visit(Re.Lit l) {
            return Trie.fromLit(l.val);
        }

        @Override
        public Trie visit(CharSet cs) {
            throw new UnsupportedOperationException("NYI");
        }

        @Override
        public Trie visit(Re.Capture cap) {
            throw new IllegalStateException("IMPOSSIBLE");
        }
    };

    public static Trie enumerate(Re re) {
        return re.visitIgnoreCapture(ENUMERATOR);
    }
}
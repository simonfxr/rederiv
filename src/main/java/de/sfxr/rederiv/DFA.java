package de.sfxr.rederiv;

import de.sfxr.rederiv.support.IntervalSet;
import de.sfxr.rederiv.support.OrderedSemigroup;
import java.util.*;

public class DFA<Re extends ReAlg<Re>> {

    private final DFABuilder<Re> builder;

    private final IntervalSet<Integer>[] trans;
    private final int initial;

    private final Re re;

    private static int newState(int q, boolean accepting) {
        return (q << 1) | (accepting ? 1 : 0);
    }

    private static int stateQ(int s) {
        return s >>> 1;
    }

    private static boolean isAcceptingState(int s) {
        return (s & 1) != 0;
    }

    private static int mkDFAState(DFABuilder<?> builder, int q) {
        return newState(q, builder.accepting.contains(q));
    }

    private static final OrderedSemigroup<Integer> INTS = new OrderedSemigroup<>() {
        @Override
        public int compare(Integer x, Integer y) {
            return Integer.compare(x, y);
        }

        @Override
        public Integer apply(Integer x, Integer y) {
            if (x.intValue() == y)
                return x;
            throw new IllegalStateException("IMPOSSIBLE: " + x + " != " + y);
        }
    };

    private DFA(DFABuilder<Re> builder, Re re) {
        this.builder = builder;
        @SuppressWarnings("unchecked")
        var trans = (IntervalSet<Integer>[]) new IntervalSet[builder.Q.size()];
        this.trans = trans;
        this.initial = newState(0, builder.accepting.contains(0));
        this.re = Objects.requireNonNull(re);

        for (int i = 0; i < this.trans.length; ++i) {
            var d = builder.delta.get(i);
            if (d == null) {
                trans[i] = IntervalSet.empty();
            } else {
                trans[i] = d.entrySet().stream()
                        .map(e -> e.getKey().toIntervalSet().map(ignored -> mkDFAState(builder, e.getValue()), INTS))
                        .reduce(IntervalSet.empty(), (x, y) -> x.union(y, INTS));
            }
        }
    }

    public boolean matches(String s) {
        if (s == null)
            throw new NullPointerException();

        var x = initial;
        int cp;

        for (int i = 0; !isAcceptingState(x); i += Character.charCount(cp)) {
            if (i >= s.length())
                return false;
            cp = s.codePointAt(i);
            var xx = trans[stateQ(x)].find(cp);
            if (xx == null)
                return false;
            x = xx;
        }

        return true;
    }

    public static <Re extends ReAlg<Re>> DFA<Re> compile(Re re) {
        var builder = new DFABuilder<Re>();
        builder.build(re);
        return new DFA<>(builder, re);
    }

    public Re re() { return re; }

    @Override
    public String toString() {
        return builder.toString();
    }
}

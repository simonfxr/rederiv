package de.sfxr.rederiv;

import de.sfxr.rederiv.support.Checking;
import java.util.*;
import java.util.stream.Collectors;

final class DFABuilder {

    private static final boolean CHECKING = Checking.isCheckingEnabled(DFABuilder.class);

    private Integer nextQ = 0;
    final Map<Re, Integer> Q = new HashMap<>();
    final Map<Integer, Map<CharSet, Integer>> delta = new HashMap<>();
    final Set<Integer> accepting = new HashSet<>();

    private int putQ(Re q) {
        var old = Q.putIfAbsent(q, nextQ);
        if (CHECKING && old == null) System.err.println("Q.ins: " + nextQ);
        return old != null ? old : nextQ++;
    }

    private void putDelta(int qI, CharSet S, int dqI) {
        var t = delta.get(qI);
        if (t == null) {
            t = new HashMap<>();
            delta.put(qI, t);
        }
        if (CHECKING) System.err.printf("delta(%d, %s) -> %d\n", qI, S, dqI);
        t.put(S, dqI);
    }

    private void buildRec(Re q, CharSet S) {
        if (S.isEmptySet()) return;
        var qI = putQ(q);
        var repr = S.pickOne();
        if (CHECKING) System.out.println("q={" + qI + "}" + q + ", S=" + S + ", repr=" + Character.toString(repr));
        var dq = ReDeriv.deriv(q, repr);
        if (!dq.isVoid()) {
            var fresh = Q.get(dq) == null;
            var dqI = putQ(dq);
            if (CHECKING) System.out.println("fresh=" + fresh + ", dq={" + dqI + "}" + dq);
            putDelta(qI, S, dqI);
            if (fresh) explore(dq);
        } else {
            if (CHECKING) System.out.println("dq={" + Q.get(dq) + "}" + dq);
        }
    }

    private void explore(Re q) {
        for (var S : ReDeriv.derivClasses(q)) buildRec(q, S);
    }

    void build(Re q) {
        explore(q);
        for (var ent : Q.entrySet())
            if (ent.getKey().matchesEmpty()) accepting.add(ent.getValue().intValue());
        if (q.matchesEmpty()) accepting.add(0);
    }

    @Override
    public String toString() {
        var states = Q.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getValue(), e.getKey()))
                .collect(Collectors.toList());
        Collections.sort(states, Comparator.comparing(Map.Entry::getKey));

        var sb = new StringBuilder("NFA.Builder {\n");

        for (var ent : states)
            sb.append("  S")
                    .append(ent.getKey())
                    .append(accepting.contains(ent.getKey().intValue()) ? " F " : "   ")
                    .append(": ")
                    .append(ent.getValue())
                    .append('\n');
        sb.append('\n');

        for (var ent : states) {
            var q = ent.getKey().intValue();
            var tt = delta.get(q);
            sb.append("  S").append(q).append("{");
            if (tt == null) {
                sb.append("}\n");
                continue;
            }
            sb.append('\n');
            for (var t : tt.entrySet())
                sb.append("    ")
                        .append(t.getKey().toPattern())
                        .append(" -> S")
                        .append(t.getValue().intValue())
                        .append('\n');
            sb.append("  }\n");
        }

        sb.append("}");
        return sb.toString();
    }
}

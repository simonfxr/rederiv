package de.sfxr.rederiv;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import de.sfxr.rederiv.support.Checking;
import java.util.*;

final class DFABuilder {

    private static final boolean CHECKING = Checking.isCheckingEnabled(DFABuilder.class);

    private Integer nextQ = 0;
    final BiMap<Re, Integer> Q = HashBiMap.create();
    // int -> (CharSet -> int)
    final Map<Integer, Map<CharSet, Integer>> delta = new HashMap<>();
    final Set<Integer> accepting = new HashSet<>();

    private int putQ(Re q) {
        var old = Q.putIfAbsent(q, nextQ);
        var ret = old != null ? old : nextQ++;
        if (Q.get(q) != ret) throw new IllegalStateException();
        return ret;
    }

    private void putDelta(int qI, CharSet S, int qcI) {
        var t = delta.get(qI);
        if (t == null) {
            t = new HashMap<>();
            delta.put(qI, t);
        }
        t.put(S, qcI);
    }

    private void buildRec(Re q, CharSet S) {
        if (S.isEmptySet()) return;
        if (CHECKING) System.out.println("q=" + q + ", S=" + S);
        var qc = ReDeriv.deriv(q, S.pickOne());
        if (CHECKING) System.out.println("qc=" + qc);
        var qcI = Q.get(qc);
        if (CHECKING) System.out.println("qcI=" + qcI);
        if (!qc.isVoid()) {
            putDelta(putQ(q), S, qcI == null ? putQ(qc) : qcI);
            if (qcI == null) explore(qc);
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
        var states = new ArrayList<>(Q.inverse().entrySet());
        Collections.sort(states, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));

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

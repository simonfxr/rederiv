package de.sfxr.rederiv;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.koloboke.collect.map.IntObjMap;
import com.koloboke.collect.map.ObjIntMap;
import com.koloboke.collect.map.hash.HashIntObjMaps;
import com.koloboke.collect.map.hash.HashObjIntMaps;
import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;
import java.util.*;

public class DFA {

    private final Builder builder = new Builder();
    private final Re re;

    private DFA(Re re) {
        this.re = Objects.requireNonNull(re);
        builder.build(re);
    }

    public boolean matches(String s) {
        return builder.matches(s);
    }

    public static DFA compile(Re re) {
        return new DFA(re);
    }

    @Override
    public String toString() {
        return builder.toString();
    }

    private static final class Builder {
        private Integer nextQ = 0;
        public final BiMap<Re, Integer> Q = HashBiMap.create();
        public final IntObjMap<ObjIntMap<CharSet>> delta = HashIntObjMaps.newMutableMap();
        public final IntSet accepting = HashIntSets.newMutableSet();

        private int putQ(Re q) {
            var old = Q.putIfAbsent(q, nextQ);
            return old != null ? old : nextQ++;
        }

        private void putDelta(int qI, CharSet S, int qcI) {
            var t = delta.get(qI);
            if (t == null) {
                t = HashObjIntMaps.newMutableMap();
                delta.put(qI, t);
            }
            t.put(S, qcI);
        }

        private void buildRec(Re q, CharSet S) {
            if (S.isEmptySet()) return;
            System.out.println("q=" + q + ", S=" + S);
            var qc = ReDeriv.deriv(q, S.pickOne());
            System.out.println("qc=" + qc);
            var qcI = Q.get(qc);
            if (!qc.isVoid()) {
                putDelta(putQ(q), S, qcI == null ? putQ(qc) : qcI);
                if (qcI == null) explore(qc);
            }
        }

        private void explore(Re q) {
            for (var S : ReDeriv.equivs(q)) buildRec(q, S);
        }

        public void build(Re q) {
            explore(q);
            for (var ent : Q.entrySet()) {
                if (ent.getKey().matchesEmpty()) accepting.add(ent.getValue().intValue());
            }
        }

        public int initialState() {
            var q0 = Q.inverse().get(0);
            return q0 != null ? 0 : -1;
        }

        public boolean matches(String s) {
            int q = initialState();
            if (q < 0 || s == null) return false;
            loop:
            for (int i = 0, cp = 0;
                    i < s.length() && !accepting.contains(q);
                    i += Character.charCount(cp)) {
                cp = s.codePointAt(i);
                for (var S : delta.get(q).entrySet()) {
                    if (S.getKey().containsChar(cp)) {
                        q = S.getValue();
                        continue loop;
                    }
                    return false;
                }
            }
            return accepting.contains(q);
        }

        @Override
        public String toString() {
            var states = new ArrayList<>(Q.inverse().entrySet());
            Collections.sort(
                    states,
                    new Comparator<Map.Entry<Integer, Re>>() {
                        @Override
                        public int compare(Map.Entry<Integer, Re> o1, Map.Entry<Integer, Re> o2) {
                            return o1.getKey().compareTo(o2.getKey());
                        }
                    });

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
}

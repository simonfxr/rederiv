package de.sfxr.rederiv;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.Random;
import java.util.regex.Pattern;

public class DFABenchmark {

    @State(Scope.Thread)
    public static class S {
        static ReBuilder re = ReBuilder.get();

        static Re PAT = re.seq(re.any().many()).seq(re.r("a")).seq(re.any().many()).seq(re.r("bb"));

        static Pattern JPAT = Pattern.compile(PAT.toPattern());
        static DFA dfa = DFA.compile(PAT);
        static String INP;

        static {
            var r = new Random(0xDEADBEEF);
            var sb = new StringBuilder();
            var b64 = "abcdefghijklmnopqrstuvwxyz".toCharArray();

            for (int i = 0; i < 65536; ++i)
                sb.append(b64[r.nextInt(26)]);

            System.err.println("DFA: " + dfa);

            INP = sb.toString();
            var m = JPAT.matcher(INP);
            if (m.matches()) {
                System.err.println("MATCH END: " + m.end());
            } else {
                System.err.println("NOMATCH");
            }
        }
    }

    @Benchmark
    public boolean rederivMatching() {
        return S.dfa.matches(S.INP);
    }

    @Benchmark
    public boolean javaReMatching() {
        return S.JPAT.matcher(S.INP).matches();
    }
}

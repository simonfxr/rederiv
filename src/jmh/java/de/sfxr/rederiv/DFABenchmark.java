package de.sfxr.rederiv;

import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Fork(warmups = 0, value = 1)
@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
public class DFABenchmark {

    @State(Scope.Thread)
    public static class S {
        static ReBuilder re = ReBuilder.get();

        static Re PAT = re.r("a").seq(re.any().many()).seq(re.r("bb"));

        static Pattern JPAT = Pattern.compile(PAT.toPattern());
        static DFA dfa = DFA.compile(PAT);
        static String INP;

        static {
            var r = new Random(0xDEADBEEF);
            var sb = new StringBuilder();
            var alpha = "abcdefghijklmnopqrstuvwxyz".toCharArray();

            for (int i = 0; i < 65536; ++i)
                sb.append(alpha[r.nextInt(alpha.length)]);

            System.err.println("RE: " + PAT.toPattern());
            System.err.println("DFA: " + dfa);

            INP = "a" + sb.toString();
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
        return S.JPAT.matcher(S.INP).lookingAt();
    }
}

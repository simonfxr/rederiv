package de.sfxr.rederiv;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

public class Enumerated {

    private static final ReBuilder re = ReBuilder.get();
    public static final List<Re> PATTERNS =
            Arrays.asList(
                    re.r(""),
                    re.r("ABC"),
                    re.alt("A", re.r("B")),
                    re.alt(re.r("A"), re.r("B"), re.r("C")),
                    re.alt(re.r("A").many(), re.r("B").some()),
                    re.r("AB").seq(re.r("CD").alt(re.r("A")).range(3, 5)).seq(re.r("E")),
                    re.seq("B").seq(re.r("X").repeat(3).some()).seq(re.r("A")),
                    re.seq("B").seq(re.r("X").alt(re.r("Y")).repeat(3).some()).seq(re.r("A")));

    private static final int MAX_ENUMERATIONS = 150;

    private void assertMatches(Re pat, DFA dfa, String s) {
        if (!dfa.matches(s)) {
            throw new AssertionFailedError(
                    String.format(
                            "DFA does not match pattern:\n  PAT = %s\n  INP = %s\nDFA:%s",
                            pat, s, dfa));
        }
    }

    void testOne(Re pat) {
        var jpat = Pattern.compile(pat.toPattern());
        var dfa = DFA.compile(pat);
        int i = 0;
        for (var s : Enumerate.enumerate(pat)) {
            if (++i >= MAX_ENUMERATIONS) break;
            assertTrue(jpat.matcher(s).matches());
            assertMatches(pat, dfa, s);
        }
    }

    @Test
    void testEnumerated() {
        PATTERNS.forEach(this::testOne);
    }
}

package de.sfxr.rederiv;

import static org.junit.jupiter.api.Assertions.*;

import de.sfxr.rederiv.support.EnumerateTrie;
import de.sfxr.rederiv.support.TestUtil;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

public class Enumerated {

    static {
        TestUtil.init();
    }

    private static final ReBuilder re = ReBuilder.get();
    public static final List<Re> PATTERNS =
            Arrays.asList(
                    re.r(""),
                    re.r("ABC"),
                    re.alt("A", re.r("B")),
                    re.alt(re.r("A"), re.r("B"), re.r("C")),
                    re.alt(re.r("A").many(), re.r("B").some()),
                    re.seq(re.any().many()).seq(re.r("aa")),
                    re.seq(re.any().many()).seq(re.r("a")).seq(re.any().many()).seq(re.r("b")),
                    re.r("AB").seq(re.r("CD").alt(re.r("A")).range(3, 5)).seq(re.r("E")),
                    re.seq("B").seq(re.r("X").repeat(3).some()).seq(re.r("A")),
                    re.seq("B").seq(re.r("X").alt(re.r("Y")).repeat(3).some()).seq(re.r("A"))
            );

    private static final int MAX_ENUMERATIONS = 150;

    private void assertMatches(Re pat, DFA dfa, String s) {
        if (!dfa.matches(s)) {
            dfa.matches(s);
            throw new AssertionFailedError(
                    String.format(
                            "DFA does not match pattern:\n  PAT = %s\n  INP = %s\nDFA:%s",
                            pat, s, dfa));
        }
    }

    void testOne(Re pat) {
        System.err.println("PAT:" + pat.toPattern());
        var jpat = Pattern.compile(pat.toPattern());
        var dfa = DFA.compile(pat);
        System.err.println("dfa=" + dfa);
        var trie = EnumerateTrie.enumerate(pat);
        var strings = trie.streamUnique()
                .filter(s -> s.codePoints().allMatch(CharSet::isPrintable))
                .limit(MAX_ENUMERATIONS);
        strings.forEach(s -> {
            System.err.println("n=" + s.length() + ",s=|" + s + "|");

            if (s.contains("\n") || s.contains("\r"))
                return;
            var jpatMatches = jpat.matcher(s).lookingAt();
            if (!jpatMatches) {
                System.err.println("FAILED");
            }
            assertTrue(jpatMatches, () -> String.format("jpat=%s s='%s'", jpat, s));
            assertMatches(pat, dfa, s);
        });
    }

    @Test
    void testSimple() {
        var p = re.r('a').seq(re.any().many()).seq(re.r("bb"));
        var dfa = DFA.compile(p);
        assertTrue(dfa.matches("abb"));
        assertTrue(dfa.matches("axxxxbb"));
        assertTrue(dfa.matches("axxxxbbxxxxxx"));
    }

    @Test
    void testEnumerated() {
        PATTERNS.forEach(this::testOne);
    }
}

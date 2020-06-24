package de.sfxr.rederiv;

import static org.junit.jupiter.api.Assertions.*;

import de.sfxr.rederiv.support.TestUtil;
import org.junit.jupiter.api.Test;

public class ParserTests {

    static {
        TestUtil.init();
    }

    private final static ReBuilder re = ReBuilder.get();

    private final static void ptest(String inp, Re re) {
        var parsed = Parser.parse(inp);
        assertEquals(re, parsed);
    }

    private final static void petest(String inp, Re re) {
        var parsed = Parser.parseExtended(inp);
        assertEquals(re, parsed);
    }

    @Test
    void testBasic() {
        ptest("", re.r());
        ptest("abc", re.r("abc"));
        ptest("[abc]", re.r('a').alt(re.r('b')).alt(re.r('c')));
        ptest("[a-c]", re.r('a').alt(re.r('b')).alt(re.r('c')));
        ptest("x?", re.r('x').opt());
        ptest("ab|y", re.r("ab").alt(re.r("y")));
        ptest("a{3}", re.r("a").repeat(3));
        ptest("b{3,10}", re.r("b").range(3, 10));
        var r1 = re.any().seq(re.r('a')).seq(re.any());
        ptest(".a.", r1);
        var r2 = re.any().seq(re.r('b')).seq(re.any());
        ptest(".a.|.b.", r1.alt(r2));
        ptest("(.a.)?", r1.opt());
    }

}

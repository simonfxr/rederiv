package de.sfxr.rederiv;

import java.util.Arrays;
import java.util.List;

public class Examples {

    public static List<Re> patterns(ReBuilder re) {
        return Arrays.asList(
                re.seq("/users/by-group/group-", re.digits().capture()),
                re.seq(
                        "/participants/group-",
                        re.digits().capture(),
                        re.r("/user-"),
                        re.digits().capture()),
                re.seq(
                        "/participants/group-",
                        re.digits().capture(),
                        re.r("/guest-"),
                        re.digits().capture()),
                re.seq("/participants/group-", re.digits().capture(), re.r("/unknown")));
    }

    public static void printEnumeration(Re re, int n) {
        System.out.println("Enumerating RE: " + re);
        System.out.println();
        int i = 0;
        for (var s : Enumerate.enumerate(re)) {
            if (i++ >= n) break;
            System.out.println(String.format("%3d", i) + "    " + s);
        }
    }

    public static void main(String[] args) {
        var re = ReBuilder.get();
        var pats = patterns(re);

        for (var p : pats) {
            System.out.print(p.toString());
            System.out.print(" -> ");
            System.out.println(p.toPattern());
            System.out.print("Prefix: ");
            System.out.println(p.litPrefix());
            System.out.println();
        }

        {
            var re1 = re.alt(re.r("a"), re.r("ba"), re.r("c"));
            System.out.println("re1=" + re1);
            var Cre1 = ReDeriv.equivs(re1);
            System.out.println("C(re1) = " + Cre1);
        }

        CharSet XY = CharSet.fromString("XY");
        CharSet S123 = CharSet.fromString("123");

        Re re1 = XY;
        System.out.println("re1=" + re1);
        printEnumeration(re1, 10);
        var dfa = DFA.compile(re1);
        System.out.println("DFA\n" + dfa);

        System.out.println();
        System.out.println();
        System.out.println();

        re1 = re.r("ABC");
        printEnumeration(re1, 10);
        System.out.println("re1=" + re1);
        dfa = DFA.compile(re1);
        System.out.println("DFA\n" + dfa);

        System.out.println();
        System.out.println();
        System.out.println();

        re1 = re.seq(CharSet.fromString("XY"), re.r("A").range(2, 4)).seq(S123);
        printEnumeration(re1, 100);
        System.out.println("re1=" + re1);
        dfa = DFA.compile(re1);
        System.out.println("DFA\n" + dfa);
    }
}

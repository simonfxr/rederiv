package de.sfxr.re;


import java.util.Arrays;
import java.util.List;

public class Main {

    public static List<Re> patterns(ReBuilder re) {
        return Arrays.asList(
                re.seq("/users/by-group/group-", re.digits().capture()),
                re.seq("/participants/group-", re.digits().capture(), re.r("/user-"), re.digits().capture()),
                re.seq("/participants/group-", re.digits().capture(), re.r("/guest-"), re.digits().capture()),
                re.seq("/participants/group-", re.digits().capture(), re.r("/unknown"))
        );
    }

    public static void main(String[] args) {
        var pats = patterns(ReBuilder.get());

        for (var p : pats) {
            System.out.print(p.toString());
            System.out.print(" -> ");
            System.out.println(p.toPattern());
            System.out.print("Prefix: ");
            System.out.println(p.litPrefix());
        }
    }
}

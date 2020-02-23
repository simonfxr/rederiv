package de.sfxr.re;

public final class ReBuilder {
    private static final ReBuilder instance = new ReBuilder();

    private ReBuilder() {}

    public static final ReBuilder get() {
        return instance;
    }

    public Re seq(Re... res) {
        if (res.length == 0) return Re.Lit.EMPTY;
        Re re = res[res.length - 1];
        for (var i = res.length - 1; --i >= 0; ) re = res[i].seq(re);
        return re;
    }

    public Re seq(String s, Re... res) {
        return r(s).seq(seq(res));
    }

    public Re alt(Re... res) {
        if (res.length == 0) return CharSet.NONE;
        Re re = res[res.length - 1];
        for (var i = res.length - 1; --i >= 0; ) re = res[i].alt(re);
        return re;
    }

    public Re alt(String s, Re... res) {
        return r(s).alt(alt(res));
    }

    public Re r(String s) {
        return Re.Lit.from(s);
    }

    public Re r(char c) {
        return r(Character.toString(c));
    }

    public Re r() {
        return Re.Lit.EMPTY;
    }

    public Re any() {
        return CharSet.ANY;
    }

    public Re digit() {
        return CharSet.DIGIT;
    }

    public Re digits() {
        return digit().some();
    }
}

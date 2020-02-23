package de.sfxr.re;

public interface ReAlg<R extends ReAlg<R>> extends Comparable<R> {
    int INF_RANGE = Integer.MAX_VALUE;

    R alt(R re);

    R seq(R re);

    R range(int min, int max);
    default R opt() { return range(0, 1); }
    default R many() { return range(0, INF_RANGE); }
    default R some() { return range(1, INF_RANGE); }

    R fromLit(String lit);
    R fromChar(int cp);
    default R fromChar(char ch) { return fromChar((int) ch); }

    default R asEmpty() { return fromLit(""); }

    R asVoid();
}

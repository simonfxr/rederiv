package de.sfxr.rederiv;

import java.util.Set;

public interface ReAlg<R extends ReAlg<R>> extends Comparable<R> {
    int INF_CARD = Integer.MAX_VALUE;

    static int cardSub(int c, int n) {
        if (c == INF_CARD) return c;
        if (c < n) return 0;
        return c - n;
    }

    static int cardMul(int c, int d) {
        if (c < 0 || d < 0) throw new IllegalArgumentException();
        if (c == INF_CARD || d == INF_CARD) return INF_CARD;
        if (c >= INF_CARD / d) throw new IllegalArgumentException("Overflow in product");
        return c * d;
    }

    R alt(R re);

    R seq(R re);

    R range(int min, int max);

    default R repeat(int n) {
        return range(n, n);
    }

    R capture();

    default R atLeast(int n) {
        return range(0, n);
    }

    default R opt() {
        return atLeast(1);
    }

    default R many() {
        return range(0, INF_CARD);
    }

    default R some() {
        return range(1, INF_CARD);
    }

    R fromLit(String lit);

    R fromChar(int cp);

    default R fromChar(char ch) {
        return fromChar((int) ch);
    }

    default R asEmpty() {
        return fromLit("");
    }

    R deriv(int cp);

    Set<CharSet> derivClasses();

    R asVoid();
}

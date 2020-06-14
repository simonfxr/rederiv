package de.sfxr.rederiv.support;

import java.util.function.BiFunction;

@FunctionalInterface
public interface Semigroup<T> extends BiFunction<T, T, T> {

    static <T> T apply(Semigroup<T> m, T x, T y) {
        if (m == null) return null;
        return m.apply(x, y);
    }
}

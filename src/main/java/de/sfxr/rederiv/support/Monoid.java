package de.sfxr.rederiv.support;

public interface Monoid<T> extends Semigroup<T> {
    T neutral();
}

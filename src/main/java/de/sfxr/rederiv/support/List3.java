package de.sfxr.rederiv.support;

import java.util.Collections;
import java.util.List;

public final class List3<A> {
    public final A _0;
    public final A _1;
    public final A _2;

    private List3(A _0, A _1, A _2) {
        this._0 = _0;
        this._1 = _1;
        this._2 = _2;
    }

    public static <A> List3<A> of() {
        return new List3<>(null, null, null);
    }

    public static <A> List3<A> of(A _0) {
        return new List3<>(_0, null, null);
    }

    public static <A> List3<A> of(A _0, A _1) {
        return new List3<>(_0, _1, null);
    }

    public static <A> List3<A> of(A _0, A _1, A _2) {
        return new List3<>(_0, _1, _2);
    }

    public List<A> toList() {
        if (_0 == null) return Collections.emptyList();
        if (_1 == null) return List.of(_0);
        if (_2 == null) return List.of(_0, _1);
        return List.of(_0, _1, _2);
    }
}

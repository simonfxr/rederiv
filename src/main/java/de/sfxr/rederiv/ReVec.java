package de.sfxr.rederiv;

import com.google.common.collect.ForwardingList;
import com.google.common.collect.Streams;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReVec<Re extends ReAlg<Re>> extends ForwardingList<Re> implements ReAlg<ReVec<Re>> {

    public final List<Re> res;

    public ReVec(List<Re> res) {
        this.res = Objects.requireNonNull(res);
    }

    @Override
    public ReVec<Re> alt(ReVec<Re> re) {
        return zipped(re, ReAlg::alt);
    }

    @Override
    public ReVec<Re> seq(ReVec<Re> re) {
        return zipped(re, ReAlg::seq);
    }

    @Override
    public ReVec<Re> range(int min, int max) {
        return mapped(re -> re.range(min, max));
    }

    @Override
    public ReVec<Re> capture() {
        return mapped(ReAlg::capture);
    }

    @Override
    public ReVec<Re> fromLit(String lit) {
        return replicate(re -> re.fromLit(lit));
    }

    @Override
    public ReVec<Re> fromChar(int cp) {
        return replicate(re -> re.fromChar(cp));
    }

    @Override
    public ReVec<Re> asVoid() {
        return replicate(ReAlg::asVoid);
    }

    @Override
    public ReVec<Re> asEmpty() {
        return replicate(ReAlg::asEmpty);
    }

    private ReVec<Re> replicate(Function<Re, Re> f) {
        if (res.isEmpty()) return this;
        var nres = new ArrayList<>(res);
        Collections.fill(nres, f.apply(res.get(0)));
        nres.trimToSize();
        return new ReVec<>(nres);
    }

    private ReVec<Re> mapped(Function<Re, Re> f) {
        if (res.isEmpty()) return this;
        return new ReVec<>(res.stream().map(f).collect(Collectors.toList()));
    }

    private ReVec<Re> zipped(ReVec<Re> rhs, BiFunction<Re, Re, Re> f) {
        if (res.isEmpty()) return this;
        if (rhs.res.isEmpty()) return rhs;
        return new ReVec<>(
                Streams.zip(res.stream(), rhs.res.stream(), f).collect(Collectors.toList()));
    }

    @Override
    public int compareTo(ReVec<Re> rhs) {
        if (rhs == null) return 1;
        int n = Integer.min(res.size(), rhs.res.size());
        for (int i = 0; i < n; ++i) {
            var r = res.get(i).compareTo(rhs.res.get(i));
            if (r != 0) return r;
        }
        return Integer.compare(res.size(), rhs.res.size());
    }

    @Override
    public String toString() {
        return "ReVec<Re>{" + res + '}';
    }

    @Override
    public ReVec<Re> deriv(int cp) {
        return mapped(re -> re.deriv(cp));
    }

    @Override
    protected List<Re> delegate() {
        return res;
    }

    @Override
    public Set<CharSet> derivClasses() {
        if (isEmpty()) return Collections.emptySet();
        Set<CharSet> cs = null;
        for (var re : res) {
            var ds = re.derivClasses();
            cs = cs == null ? ds : ReDeriv.intersections(cs, ds);
        }
        return cs;
    }
}

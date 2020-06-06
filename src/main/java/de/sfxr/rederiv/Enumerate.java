package de.sfxr.rederiv;

import com.google.common.collect.Iterables;
import java.util.*;

public class Enumerate {

    public static <T> Iterable<T> interleave(List<Iterable<T>> xss) {
        if (xss.size() == 0) return Collections.emptyList();
        if (xss.size() == 1) return xss.get(1);
        return () ->
                new Iterator<T>() {
                    Iterator<T>[] iter = new Iterator[xss.size()];
                    int i = 0;
                    T next;
                    boolean hasNext;

                    private boolean findNext() {
                        int i0 = i;
                        do {
                            if (iter[i] != null) {
                                if (iter[i].hasNext()) {
                                    next = iter[i].next();
                                    return true;
                                } else {
                                    iter[i] = null;
                                }
                            }
                            i = (i + 1);
                            if (i >= iter.length) i = 0; // avoid modulo division
                        } while (i != i0);
                        return false;
                    }

                    {
                        int k = 0;
                        for (var it : xss) iter[k++] = it.iterator();
                        hasNext = findNext();
                    }

                    @Override
                    public boolean hasNext() {
                        return hasNext;
                    }

                    @Override
                    public T next() {
                        var x = next;
                        hasNext = findNext();
                        return x;
                    }
                };
    }

    /** FIXME Do a Cantor style diagonalization (also for the case of more streams) */
    public static Iterable<String> cross(Iterable<String> xs, Iterable<String> ys) {
        return () ->
                new Iterator<>() {
                    String a, b;
                    Iterator<String> ai = xs.iterator();
                    Iterator<String> bi;
                    int hasNext = -1;

                    private boolean fwd() {
                        for (; ; ) {
                            if (bi == null) {
                                bi = ys.iterator();
                                if (!ai.hasNext()) return false;
                                a = ai.next();
                            }
                            if (bi.hasNext()) {
                                b = bi.next();
                                return true;
                            }
                            bi = null;
                        }
                    }

                    @Override
                    public boolean hasNext() {
                        if (hasNext >= 0) return hasNext != 0;
                        hasNext = fwd() ? 1 : 0;
                        return hasNext != 0;
                    }

                    @Override
                    public String next() {
                        var ret = a + b;
                        hasNext = -1;
                        return ret;
                    }
                };
    }

    public static <T> Iterable<T> cons(T x, Iterable<T> xs) {
        return () ->
                new Iterator<>() {
                    Iterator<T> iter = null;

                    @Override
                    public boolean hasNext() {
                        return iter == null || iter.hasNext();
                    }

                    @Override
                    public T next() {
                        if (iter == null) {
                            iter = Objects.requireNonNull(xs.iterator());
                            return x;
                        }
                        return iter.next();
                    }
                };
    }

    public static Iterable<String> cross(List<Iterable<String>> xss) {
        if (xss.isEmpty()) return Collections.emptyList();
        if (xss.size() == 1) return xss.get(0);
        if (xss.size() == 2) return cross(xss.get(0), xss.get(1));
        return cross(xss.get(0), () -> cross(xss.subList(1, xss.size())).iterator());
    }

    public static Iterable<String> intersections(Iterable<String> xs, Iterable<String> ys) {
        throw new UnsupportedOperationException();
    }

    public static Iterable<String> intersections(List<Iterable<String>> xss) {
        switch (xss.size()) {
        case 0:
            return Collections.emptyList();
        case 1:
            return xss.get(0);
        case 2:
            return intersections(xss.get(0), xss.get(1));
        default:
            return intersections(xss.get(0), () -> intersections(xss.subList(1, xss.size())).iterator());
        }
    }

    public static Iterable<String> repeatUpTo(int n, Iterable<String> xs) {
        if (n == 0) return List.of("");
        return cons("", cross(xs, () -> repeatUpTo(ReAlg.cardSub(n, 1), xs).iterator()));
    }

    public static Iterable<String> enumerate(Re re) {
        return re.visitIgnoreCapture(
                new Re.Visitor<>() {
                    @Override
                    public Iterable<String> visit(Re.Branch br) {
                        var xss = Arrays.asList(enumerate(br.a), enumerate(br.b));
                        switch (br.kind) {
                            case SEQ:
                                return cross(xss);
                            case ALT:
                                return interleave(xss);
                            case IS:
                                return intersections(xss);
                        }
                        return Re.unreachable();
                    }

                    @Override
                    public Iterable<String> visit(Re.Rep rep) {
                        var xs = enumerate(rep.re);
                        var ys = repeatUpTo(ReAlg.cardSub(rep.max, rep.min), xs);
                        if (rep.min == 0) return xs;
                        List<Iterable<String>> ss = new ArrayList<>();
                        for (int i = 0; i < rep.min; ++i) ss.add(xs);
                        ss.add(ys);
                        return cross(ss);
                    }

                    @Override
                    public Iterable<String> visit(Re.Lit l) {
                        return List.of(l.val);
                    }

                    @Override
                    public Iterable<String> visit(Re.Neg n) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Iterable<String> visit(CharSet cs) {
                        return Iterables.transform(cs, (ch) -> Character.toString(ch));
                    }

                    @Override
                    public Iterable<String> visit(Re.Capture cap) {
                        throw new IllegalArgumentException("BUG");
                    }
                });
    }
}

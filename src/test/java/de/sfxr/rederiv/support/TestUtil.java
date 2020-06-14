package de.sfxr.rederiv.support;

public final class TestUtil {

    private TestUtil() {}

    static {
        System.setProperty("de.sfxr.rederiv.checking", "true");
    }

    public static void init() {}

    public static final OrderedSemigroup<Integer> INTS =
            new OrderedSemigroup<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return Integer.compare(o1, o2);
                }

                @Override
                public Integer apply(Integer x, Integer y) {
                    return x + y;
                }
            };
}

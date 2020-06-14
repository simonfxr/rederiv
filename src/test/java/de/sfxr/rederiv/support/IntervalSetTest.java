package de.sfxr.rederiv.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class IntervalSetTest {

    private final Random rng = new Random(0x75a1018be0a8a876L);

    @BeforeAll
    static void setup() {
        TestUtil.init();
    }

    private <T> List<T> shuffle(List<T> xs) {
        Collections.shuffle(xs, rng);
        return xs;
    }

    private <T> IntervalSet<T> unionAll(List<Interval<T>> ivs, OrderedSemigroup<T> m) {
        var z = IntervalSet.<T>empty();
        for (var iv : ivs) z = z.union(IntervalSet.of(iv), m);
        return z;
    }

    private <T> IntervalSet<T> propUnionAllPermutationInvariance(
            List<Interval<T>> ivs, OrderedSemigroup<T> m) {
        var z1 = unionAll(ivs, m);
        shuffle(ivs);
        var z2 = unionAll(ivs, m);
        assertEquals(z1.asList(), z2.asList());
        return z1;
    }

    @Test
    void test() {

        {
            var ivs = new ArrayList<Interval<Integer>>();
            // 1   2  3  4
            // {1, 1}
            //    {1, 1}
            //       {1, 1}
            // {1, 2, 2, 1}
            // -> [1, 2): 1, [2, 4): 2, [4, 5): 1
            var expected =
                    List.of(Interval.of(1, 2, 1), Interval.of(2, 4, 2), Interval.of(4, 5, 1));
            for (int i = 1; i <= 3; ++i) ivs.add(Interval.of(i, i + 2, 1));
            for (int i = 0; i < 100; ++i) {
                var z = propUnionAllPermutationInvariance(ivs, TestUtil.INTS);
                assertEquals(expected, z.asList());
            }
        }
    }
}
